# 에스크로 ↔ SSAFY 금융망 매핑

에스크로의 회원 발급과 세 자금 흐름(예치·지급·환불)을 SSAFY 금융망 API에 맞춘 참고 문서다.
엔티티·서비스를 짤 때 이 문서의 필드 대응을 기준으로 한다. 값과 불변식은 `docs/RULES.md`가,
계약 흐름은 `docs/proposal.md`가 상위 출처다. 여기서는 금융망 연동 상세만 고정한다.

| 항목 | 내용 |
| --- | --- |
| 대상 | `escrow`(be-escrow) 앱 전용 |
| 스키마 | 배포 전 통합한 `V1__create_escrow_schema.sql`: 회원·계좌 발급, 예치, 정산, 감사 기록 |
| 금융망 | SSAFY 교육용 금융망 API |

---

## 0. 확정된 설계 결정

이 문서와 스키마·목이 공유하는 전제다. 바꾸려면 관련 문서를 함께 고친다.

| # | 결정 | 근거 |
| --- | --- | --- |
| D-1 | **집금(BaaS) 모델.** 에스크로 서비스가 SSAFY 회원 1개(`userKey`)와 보관계좌 1개를 소유한다. 계약마다 보관계좌에 연결된 가상계좌(VA) 1개로 입금받고, 입금 검증 후 보관계좌로 정산한다. | `R-PAY-05`, `R-PAY-06` |
| D-2 | **Model A — 자금 이동은 전부 에스크로가 실행한다.** 예치 이체도 에스크로가 한다. 출금 계좌는 회원가입 시 발급한 의뢰인의 SSAFY 계좌로 고정하고, Komit이 보관한 `userKey`·은행코드·계좌번호를 요청에 담아 보낸다. be-api는 금융망 이체 API를 부르지 않는다. | `proposal.md` §5·§7, `R-ACC-09`, `R-PAY-09` |
| D-3 | **일괄 지급.** 마일스톤 분할 지급은 없다. 정상 완료는 전액, 중도 종료는 통과분 합계를 한 번에 지급하고 나머지는 환불한다. | `R-PAY-01`, `R-PAY-03`, `R-PAY-04` |
| D-4 | **단일 동결.** 한 에스크로의 활성 동결은 최대 1개. `escrows.hold_status`로 가른다. | `R-RPT-05` |
| D-5 | **회원 발급·계좌 확인은 에스크로가 금융망에 요청한다.** 발급 정보의 원본은 Komit이 보관하고 후속 금융 요청에 담는다. 에스크로는 응답 유실·재요청 복구를 위해 통합 `V1`에 `userKey`·계좌번호의 암호화 사본과 은행코드를 보관한다. | `R-ACC-09`, `proposal.md` §7 |
| D-6 | **원장은 별도 테이블이 아니라 `escrow_transactions` 집계.** 잔액·보관총액은 저장 컬럼이 아니라 계산값이다. | `inquireReconciliation` 설계 |
| D-7 | **중개 수수료는 확정 작업 대금에서 공제한다.** 계약 생성 때 요율을 고정한다. 보관계좌와 동일한 에스크로 SSAFY 사용자 명의의 별도 플랫폼 수익 계좌를 두고, 작업자 순지급과 수수료 이체를 각각 기록한다. 환불액에는 수수료를 부과하지 않는다. | `R-PAY-07` |
| D-8 | **최종 정산은 `settlements` 한 행으로 묶는다.** 지급·수수료·환불은 각자 `escrow_transactions` 행을 갖고 같은 정산을 참조한다. 자금 유출 없는 `FAILED` 정산 외에는 계약당 새 정산을 만들 수 없다. 원장 집계는 여전히 D-6을 따른다. | `R-PAY-01`, `R-PAY-12` |
| D-9 | **예치 출금 계좌는 회원 발급 기록과 연결한다.** `Header.userKey`의 서비스별 HMAC 조회값으로 발급 기록을 찾고, 요청 계좌와 복호화한 발급 계좌를 비교한 뒤 계약에 기록 ID를 연결한다. | `R-ACC-09`, `R-PAY-09` |
| D-10 | **결과 불명 해소와 수동 조치는 추가 전용 감사 사건으로 남긴다.** 작업 주체, 근거와 금융망 증빙 참조를 기록하고 원문 계좌·키는 넣지 않는다. | `proposal.md` §7 |
| D-11 | **취소·동결·해제의 멱등 결과를 별도로 남긴다.** 세 API는 금융망 이체 없이 계약 상태를 바꾸므로 상태 변경과 멱등 기록을 같은 DB 트랜잭션에서 확정한다. | `cancelEscrow`·`holdEscrow`·`unholdEscrow` 계약, `R-RPT-05` |
| D-12 | **금융망 호출은 DB에서 거래별 실행권을 얻은 Active Pod 하나만 수행한다.** 호출 직전에 `DISPATCHING`을 커밋하고, 그 뒤 장애가 나면 재전송 대신 `UNKNOWN` 조회·대사로 복구한다. | `HIGH_AVAILABILITY.md`, `R-PAY-08`, `R-PAY-12` |

---

## 1. 회원가입 시 사용자·계좌 발급

은행 앱이 없는 SSAFY 교육용 금융망 데모를 위한 발급 단계다. 정상적인 발급 요청은 성공을 전제로 한다.

Komit이 회원가입 중 에스크로의 `POST /escrow/api/v1/escrow/createMemberAccount`를 호출한다. 요청에는 공통 `Header`와 SSAFY 회원 등록용 이메일 형식 `userId`를 담고, 쓰기 요청이므로 `idempotencyKey`를 포함한다. 아직 발급 전이므로 `Header.userKey`는 필요하지 않다.

에스크로는 자기 SSAFY 앱 키로 회원 등록(`POST /ssafy/api/v1/member/`)과 입출금 계좌 개설(`POST /ssafy/api/v1/edu/demandDeposit/createDemandDepositAccount`)을 처리한다. **사용자와 계좌가 모두 준비된 결과만** 응답한다. 같은 `userId`의 재요청에는 기존 발급값을 반환한다.

계좌 개설 직후 에스크로가 SSAFY 금융망의
[`updateDemandDepositAccountDeposit`](https://project.ssafy.com/mkdocs/ssafy-finance/api-demand-deposit.html#249)을
호출해 회원 계좌에 데모 자금을 한 번 입금한다. 요청에는 새 `userKey`를 Header에,
발급된 계좌번호를 `accountNo`에, `R-PAY-10`의 데모 입금액을 십진수 문자열로
변환해 `transactionBalance`에 담는다.
`transactionSummary`에는 `seed_request_reference`를 포함한 고유 표식을 넣는다. 응답의
`transactionUniqueNo`를 기록하고 입금 성공이 확인된 뒤에만 `createMemberAccount`의
기존 성공 응답을 반환한다. 이 입금은 회원 계좌 자금이며 계약 예치 원장에는 넣지 않는다.
(`R-PAY-10`)

2026-09-18 실계 금융망 테스트에서 테스트 회원을 등록하고 위 상품으로 계좌를 개설한 뒤,
`updateDemandDepositAccountDeposit`으로 1원을 입금했다. 입금 전후
`inquireDemandDepositAccountBalance`의 잔액은 각각 `0`, `1`이었고 입금 응답은
`H0000`과 `transactionUniqueNo`를 반환했다. **요청 JSON의 `transactionBalance`는
숫자 `1`이 아닌 문자열 `"1"`로 전송해야 성공했다.** 숫자로 전송하면 `Q1001`을
반환했다. 1원은 연동 검증용 금액이며 가입 시 초기 입금액은 `R-PAY-10`을 따른다.
같은 날 별도 테스트 회원·계좌에서 `R-PAY-10`의 전체 금액을 한 번의 입금 요청으로
보냈고, `H0000`과 거래번호를 받았다. 잔액 조회도 입금 전 `0`에서 정책 금액만큼
증가한 결과를 확인했다.

회원가입 전에 에스크로가 `createDemandDeposit`으로 `R-ACC-10`의 상품을 한 번 등록한다. 요청에는 `bankCode=001`, `accountName=Komit 데모 입출금`을 사용하고, 응답의 `accountType=DOMESTIC`을 확인한 뒤 `accountTypeUniqueNo`를 에스크로 설정에 둔다. 회원가입마다 이 고유번호를 `createDemandDepositAccount`에 사용한다. 상품 등록 응답의 고유번호는 환경에 따라 달라질 수 있으므로 문서의 예시값을 고정 설정값으로 쓰지 않는다. Komit은 상품을 고르거나 이 고유번호를 보내지 않는다.

2026-09-18 SSAFY 데모 환경에서 `createDemandDeposit` 성공 응답을 두 번 받았다. 두 상품 모두 `bankCode=001`, `accountTypeCode=1`, `accountType=DOMESTIC`, `accountName=Komit 데모 입출금`이다. **회원 계좌 개설에 사용할 상품 고유번호는 두 번째 응답의 `001-1-231ecb61d2554c`로 확정한다.** 같은 날 `inquireDemandDepositList` 응답에서도 이 번호와 상품 속성을 확인했다. 첫 번째 응답의 `001-1-c40bb57b2fc749`는 회원 계좌 개설에 사용하지 않는다. 이 고유번호는 해당 SSAFY 환경의 발급 결과다.

통합 `V1.member_account_provisions`는 서비스·`userId`와 멱등키를 각각 유일하게 묶는다.
발급된 `userKey`는 암호문과 서비스별 HMAC-SHA-256 조회값을 함께 저장한다.
HMAC 비밀키는 데이터베이스 밖에서 관리하고, 변경할 때 기존 조회값을 재계산하는 절차가 필요하다.
`provision_step`은 `USER_REGISTER → ACCOUNT_OPEN → SEED_DEPOSIT → COMPLETED`로 진행한다.
현재 단계의 `provision_status`는 `PENDING` 또는 `UNKNOWN`이고, 전체 발급이 끝나면
`COMPLETED`다. 외부 생성·입금 API를 부르기 직전에 `step_requested_at`을 영속 저장하고,
단계를 넘길 때는 비운다. 현재 단계가 `PENDING`이면서 이 시각이 없으면 아직 호출을
시작하지 않은 것이므로 진행할 수 있다. 시각이 있으면 실제 전송·응답 유실 가능성이
있으므로 먼저 금융망 결과를 확인하고, 결과가 불명확하면 재호출하지 않는다.

회원 등록 응답의 `userKey`를 암호화해 저장한 뒤에만 계좌 개설을 시작한다. 계좌 개설
응답의 은행코드와 암호화한 계좌번호를 저장한 뒤에만 데모 입금을 시작한다. 암호문은
키 버전·nonce·인증 태그를 포함하는 봉투 형식으로 저장하고, 평문은 로그와 일반 조회
응답에 남기지 않는다. Komit이 받은 발급 정보의 원본을 관리하며, Escrow의 사본은
동일 발급 업무의 재시도·응답 재생에만 사용한다.

데모 입금 호출 전에는 `step_requested_at`과 `seed_request_reference`를 함께 저장한다. 응답이 유실되면
`provision_status`와 `seed_status`를 `UNKNOWN`으로 두고 회원 계좌의 입금 거래내역에서
표식·금액을 확인한다. 확인 전에는 같은 회원에게 재입금하지 않는다. `COMPLETED`인
동일 `userId`의 재요청에는 암호화해 보관한 기존 발급 결과를 복호화해 반환하고 입금은
반복하지 않는다. 발급 API의 요청·응답 형식은 바꾸지 않는다.

| 현재 단계 | 영속 기록 후 다음 작업 | 응답 유실·재요청 시 |
| --- | --- | --- |
| `USER_REGISTER` | SSAFY 회원 등록 → `user_key_encrypted` 저장 → `ACCOUNT_OPEN` | `userId`로 기존 회원 결과를 확인한다. 확인 전 재등록하지 않는다 |
| `ACCOUNT_OPEN` | SSAFY 계좌 개설 → `bank_code`·`account_no_encrypted` 저장 → `SEED_DEPOSIT` | 계좌 목록에서 은행·상품명·계좌종류·통화가 맞는 후보를 확인한다. 고유한 결과로 특정할 수 없으면 `UNKNOWN`을 유지하고 재개설하지 않는다 |
| `SEED_DEPOSIT` | 요청 표식 저장 → 1회 입금 → 거래번호·성공 시각 저장 → `COMPLETED` | 입금 내역에서 표식·금액을 확인한다. 확인 전 재입금하지 않는다 |

2026-09-18 SSAFY 실호출에서 신규 테스트 회원을 등록한 뒤 `POST /ssafy/api/v1/member/search`에
같은 `userId`와 앱 `apiKey`를 보내자 등록 응답과 같은 `userKey`를 받았다(HTTP 201).
같은 `userId`의 회원 등록 재호출은 HTTP 400 / `E4002`였고 새 `userKey`를 반환하지
않았다. 따라서 회원 등록 응답이 유실되면 재등록보다 회원 조회로 `userKey`를 복구한다.

같은 테스트 회원의 계좌 개설 응답을 잃었다고 가정하고
`inquireDemandDepositAccountList`를 호출하자 방금 만든 계좌번호가 목록에 있었다
(HTTP 200 / `H0000`). **목록 항목에는 `accountTypeUniqueNo`가 없고**
`bankCode`·`accountName`·`accountTypeCode`·`accountCreatedDate`·`currency` 등이
있다. 같은 상품으로 새 기관거래고유번호를 써 계좌 개설을 다시 호출하자
HTTP 201 / `H0000`으로 **서로 다른 두 번째 계좌가 실제 생성**됐다. 따라서 목록에서
발급 대상 계좌를 하나로 특정할 수 있을 때만 그 계좌번호를 복구한다. 같은 조건의
후보가 여러 개이거나 목록만으로 발급 결과를 특정할 수 없으면 `UNKNOWN`을 유지한다.
목록에 후보가 없다는 사실만으로 새 계좌 개설이 안전하다고 판단하지 않는다.

테스트 계좌에는 복구 검증용으로 1원을 한 번 입금했다. 같은
`institutionTransactionUniqueNo`로 입금 요청을 재전송하자 HTTP 400 / `H1007`이
나왔고, `inquireTransactionHistoryList`에서는 요청 `transactionSummary`와
금액이 일치하는 거래 **한 건**과 원래 입금 응답의 `transactionUniqueNo`가
확인됐다(HTTP 200 / `H0000`). 입금 응답 유실 시 이 조회로 성공을 확정할 수 있다.
`H1007`만으로는 원래 거래번호를 얻을 수 없으므로 조회 전 재입금하지 않는다.
이 테스트는 응답 유실을 호출 측에서 가정해 조회 경로를 검증한 것이며, 실제 통신
단절을 발생시킨 테스트는 아니다.

| 응답 `REC` | 용도 |
| --- | --- |
| `userId` | 발급 대상 확인 |
| `userKey` | Komit DB에 보관하고 후속 금융 요청의 `Header.userKey`로 전달 |
| `bankCode`·`accountNo` | Komit DB에 보관하고 후속 계좌 관련 요청에 전달 |

목 서버는 실제 SSAFY를 호출하거나 상태를 저장하지 않는다. 데모 입금까지 완료된
성공 사례를 응답하며, 같은 `userId`에는 같은 테스트 발급값을 반환한다. 발급 API의
요청·응답 형식은 바꾸지 않는다. (`R-ACC-09`, `R-PAY-10`)

2026-09-20에는 `SsafyFinanceClient` 구현 후 승인된 테스트 API Key로 실제 금융망을
호출했다. 고유 테스트 회원 생성, `001-1-231ecb61d2554c` 상품 계좌 개설, R-PAY-10
금액 1회 입금, 거래내역의 요청 표식·금액 대조, 1원 송금, 거래내역에서 인증코드 확인,
금융망 `checkAuthCode=SUCCESS`까지 연속 검증했다. 검증 계좌의 최종 잔액은 시드 금액과
1원 인증 입금의 합이었다. 구현 클라이언트의 회원·계좌·거래내역 조회도 선택적 라이브
테스트 `SsafyFinanceLiveTest`로 재검증했다. 라이브 테스트는
`SSAFY_LIVE_TEST=true`, `SSAFY_FINANCE_API_KEY`, `SSAFY_LIVE_TEST_USER_ID`가 명시된
승인 환경에서만 실행한다. 첫 시험 스크립트는 회원 생성의 HTTP 201 응답을 공통 Header
응답으로 잘못 판정해 계좌 없는 테스트 회원 한 건을 남겼으며, 자금 호출은 수행하지 않았다.

---

## 2. 공통 Header

모든 계좌 조작 API는 공통 `Header`를 쓴다. 에스크로는 아래를 자동으로 채운다.

| Header 필드 | 값 | 비고 |
| --- | --- | --- |
| `apiName` / `apiServiceCode` | 엔드포인트 마지막 path | `step`이 결정 |
| `transmissionDate` / `transmissionTime` | 요청 시각 (YYYYMMDD / HHMMSS) | 요청시각 ±5분 |
| `institutionCode` / `fintechAppNo` | `00100` / `001` 고정 | |
| `institutionTransactionUniqueNo` | 20자리, **매 호출 새 값** | → `financial_operation_attempts.request_reference` |
| `apiKey` | 에스크로 앱 키 | 환경변수 |
| `userKey` | 자금 이동 주체의 신원 | 발급 요청에는 없음. 후속 요청에는 Komit이 전달한 회원 키 또는 에스크로 보관계좌 소유자 키 |

**멱등 규칙:** `request_reference`를 외부 호출 **전에** 채번·저장(`requested_at`)하고 그 값을 `institutionTransactionUniqueNo`로 보낸다. SSAFY 거래내역 조회 응답에는 이 헤더 값이 없으므로, 자금 이동 요청의 거래 요약에도 동일한 요청 식별자를 포함해 조회용 표식으로 쓴다. 이체에서는 출금 계좌 쪽 `withdrawalTransactionSummary`에 넣는다. 응답을 잃으면 계좌 거래내역 목록에서 이 표식·금액·상대 계좌·시간을 대조하고, 찾은 거래의 `transactionUniqueNo`로 단건 조회한다. 목록에 없다는 사실만으로 실패를 확정하거나 새 번호로 재송금하지 않는다. 실패가 확정된 뒤에만 `attempt_no`를 올리고 새 `request_reference`를 쓴다.

**실행권 획득:** Writer는 외부 호출 전에 해당 시도를 `REQUESTED`에서 `DISPATCHING`으로 원자적으로 갱신하면서 `dispatched_by`와 `dispatched_at`을 기록하고 커밋한다. 갱신된 행이 1개인 실행자만 금융망을 호출한다. `dispatched_by`에는 재사용 가능한 역할명 대신 Pod UID처럼 실행 생명주기 동안 변하지 않는 인스턴스 식별자를 쓴다. `dispatched_at`은 실행권을 얻은 시각이며 요청이 금융망에 도달했다는 증거가 아니다. Writer가 `DISPATCHING` 뒤에 중단되면 새 Writer는 같은 요청을 바로 재전송하지 않고 `UNKNOWN`으로 전환한 다음 기존 `request_reference`와 거래 내역으로 결과를 확인한다.

호출 상태 전이는 `REQUESTED → DISPATCHING → SUCCEEDED | FAILED | UNKNOWN`이다. `responded_at`에는 해당 호출의 직접 응답을 받은 시각만 기록한다. 응답 유실 후 조회로 성공·실패를 확정하더라도 원래 시도의 `responded_at`은 NULL을 유지하고 조회 시도와 감사 사건에 증빙을 남긴다. 같은 거래·단계에는 미확정 시도(`REQUESTED`, `DISPATCHING`, `UNKNOWN`)를 하나만 허용한다. 읽기 전용 조회 시도 자체가 실패한 경우에는 조회를 FAILED로 종결하고 다시 조회할 수 있지만, 원래 자금 이동의 UNKNOWN을 실패로 바꾸지는 않는다. 응답을 받지 못했거나 응답만으로 자금 이동 여부를 확정할 수 없으면 `UNKNOWN`으로 두며, 조회 호출도 별도의 시도 행으로 기록한다.

2026-09-18 실호출에서 같은 `institutionTransactionUniqueNo`로 1원 이체 요청을 재전송하자 HTTP 400의 최상위 `responseCode=H1007`이 반환됐다. 보관계좌 출금과 수취 계좌 입금은 각각 한 번만 일어났다. 중복 오류는 첫 이체의 성공 여부나 거래번호를 돌려주지 않으므로, 결과 불명 상태는 거래내역 조회로 해소해야 한다.

데모 보관계좌를 사용하기 전에 `inquireDemandDepositAccount`로 1회·1일 이체 한도를 확인한다. `R-PAY-11`보다 낮으면 보관계좌 소유자의 `userKey`로 `updateTransferLimit`을 호출하고 다시 조회한다. 요청의 `oneTimeTransferLimit`과 `dailyTransferLimit`은 숫자의 십진수 **문자열**로 전송한다. 확인 전에는 이 보관계좌에서 지급·환불을 시작하지 않는다. 회원 계좌에는 이 상향 절차를 적용하지 않는다.

2026-09-18 테스트 보관계좌에서 1회 한도는 유지하고 1일 한도를 `R-PAY-11`로 변경했다. `updateTransferLimit`이 HTTP 200 / `H0000`을 반환했고, `inquireDemandDepositAccount` 재조회에서도 정책 한도가 확인됐다. 이는 테스트 계좌에 대한 변경이며 실제 운영 보관계좌의 설정 완료를 뜻하지 않는다.

---

## 3. 예치 (DEPOSIT)

`autoTransfer=false`로 발급해, **검증(VERIFY)을 통과한 뒤에만 보관계좌로 정산(SETTLE)**한다.
이로써 "외부 결과가 일치하는지 확인한 뒤 완료"(`proposal.md` §7, `R-PAY-08`)가 강제된다.

| deposit_step | attempts.step | SSAFY API | 응답 → 컬럼 |
| --- | --- | --- | --- |
| VA_CREATING→VA_CREATED | `VA_CREATE` | `createVirtualAccount`<br>req: `mainAccountNo`(보관계좌), `depositorName`, `expectedAmount`, `expiryDate`, `autoTransfer=false` | `virtualAccountId`→`virtual_account_id`<br>`virtualAccountNo`→`virtual_account_no` |
| TRANSFER_SENDING→TRANSFERRED | `DEPOSIT_TRANSFER` | `updateDemandDepositAccountTransfer`<br>req: `depositAccountNo`(VA번호), `withdrawalAccountNo`(회원가입 때 발급한 의뢰인 계좌), `transactionBalance`(십진수 문자열), `depositTransactionSummary`(`createVirtualAccount.depositorName`과 동일한 값), `withdrawalTransactionSummary`(요청 식별자 포함)<br>Header.userKey = Komit이 전달한 의뢰인 키 (`R-PAY-09`) | `source_bank_code`, `source_account_encrypted` |
| VERIFIED | `DEPOSIT_VERIFY` | `inquireVirtualAccountDeposits`<br>req: `virtualAccountId` | `deposits[].depositAmount`→`verified_deposit_amount`<br>`nameMatched`→`depositor_name_matched`<br>→`deposit_verification_status` |
| SETTLED | `VA_SETTLE` | `settleVirtualAccount`<br>req: `virtualAccountId` | `settledAmount`, `settledAt`→`completed_at` |
| VA_CLOSED | `VA_CLOSE` | `closeVirtualAccount`<br>req: `virtualAccountId` | status=CLOSED→`virtual_account_closed_at` |

- `external_reference_id` = **`INITIAL`** (예치 고정)
- `createEscrow.depositorName`은 예치 거래를 생성할 때 `depositor_name_encrypted`에 암호화 봉투로 저장한다. 금융망 호출 전 기록을 확정하고, VA 생성과 예치 이체의 `depositTransactionSummary`에는 저장한 값을 복호화해 동일하게 사용한다. 서버 재시작·응답 유실 후에도 요청 지문만으로 원문을 추정하거나 새 입금자명을 만들지 않는다. 평문은 로그와 일반 조회 응답에 남기지 않는다.
- `createEscrow`는 `Header.userKey`의 HMAC 조회값으로 같은 이용 서비스의 발급 기록을 찾는다. 요청한 출금 은행코드·계좌번호를 해당 발급 기록의 복호화한 값과 비교하고, 일치한 `member_account_provisions.id`를 `escrows.payer_provision_id`에 저장한다. 일치하는 발급 기록이 없으면 이체를 시작하지 않는다. (`R-PAY-09`, D-9)
- `deposit_verification_status`: `verified_deposit_amount` == `escrows.expected_amount` → MATCHED, 다르면 MISMATCHED(부분·초과), 미확인 UNKNOWN
- **VA 입금은 출처 계좌를 응답에 주지 않는다.** `source_*`는 be-api가 넘긴 참고값이지 금융망 확인값이 아니다.
- 활성 가상계좌 수는 에스크로에서 숫자로 추정하거나 고정하지 않는다. SSAFY의 [`createVirtualAccount` 명세](https://project.ssafy.com/mkdocs/ssafy-finance/api-virtual-account.html#2141)에 따라 `E4065`(활성 가상계좌 수 초과)가 반환되면 발급 실패로 처리하고, 해당 계약의 이체·정산을 시작하지 않는다. 명세에는 최대 개수가 적혀 있지 않다.
- 2026-09-18 실호출에서 회원 계좌 → 에스크로 소유 VA로 1,000원을 이체하고 `inquireVirtualAccountDeposits`로 조회한 뒤 `settleVirtualAccount`로 보관계좌에 정산했다. 회원 잔액은 1,000원 감소했고 보관계좌 잔액은 1,000원 증가했다. 세 호출 모두 `H0000`이었다.
- 입금자명 일치 판정은 이체 요청의 **`depositTransactionSummary`를 VA 발급 시의 `depositorName`과 동일하게 보냈을 때** `nameMatched=true`였다. 실제 회원 예금주명과 VA 지정 입금자명이 같아도 거래 요약을 다른 값으로 보내면 `false`였다. `withdrawalTransactionSummary`는 다른 값을 유지한 추가 이체에서도 `true`였다. 따라서 이체를 구성할 때 두 값을 맞춘다. 이 검증 결과는 거래 요약과 지정 이름의 일치를 보여주며, 출금 계좌의 실제 명의 확인을 증명하지 않는다.
- 테스트한 세 가상계좌는 정산 후 입금 내역의 `transferred=true`, `settledAt` 존재를 확인했고, `closeVirtualAccount`로 모두 `CLOSED` 처리했다. 보관계좌 최종 잔액은 테스트 이체 합계인 3,000원이었다.

### 예치 상태 확정

- `createEscrow`는 같은 HTTP 200 / `H0000`에서도, 응답 시점에 검증·정산·로컬 기록이 모두 확정됐으면 `ACTIVE`, 아직 처리 중이면 `DEPOSIT_PENDING`을 반환한다. `H0000`만으로 예치 완료를 판단하지 않는다.
- `DEPOSIT_PENDING`에서는 완료된 DEPOSIT이 없으므로 `depositedAmount=0`, `balance=0`이다. `inquireEscrow`는 같은 계약의 최신 상태를 돌려준다. `ACTIVE`가 확인된 뒤에만 Komit이 예치 완료 및 착수를 처리한다.
- 금융망 결과가 불명확하면 거래를 `UNKNOWN`으로 두고 조회로 해소한다. 확정 실패는 거래 `FAILED`로 기록한다. `escrows.status`에 `FAILED`는 없다.
- `VA_CLOSE`는 `SETTLE` 이후의 별도 정리 단계다. 해지가 늦어져도 정산과 로컬 완료 기록이 확정됐다면 `ACTIVE`로 볼 수 있다.
- 같은 계약의 재요청은 기존 에스크로와 거래의 현재 상태를 반환한다. 처리 중이거나 결과 불명인 자금 이동을 새 이체로 중복 실행하지 않는다.

---

## 4. 지급 (RELEASE) / 환불 (REFUND)

작업자 순지급·플랫폼 수수료·의뢰인 환불은 모두 보관계좌에서 나가는 이체이며 목적지가 다르다.

| attempts.step | SSAFY API | 매핑 |
| --- | --- | --- |
| `HOLDER_VERIFY` | `inquireDemandDepositAccountHolderName`<br>req: `accountNo`(수취), Header.userKey = 에스크로 보관계좌 소유자 키 | 응답 `userName` ↔ 요청한 수취 예금주명 대조. 다르면 이체 중단 |
| `PAYOUT_TRANSFER` / `FEE_TRANSFER` / `REFUND_TRANSFER` | `updateDemandDepositAccountTransfer`<br>req: `depositAccountNo`(수취), `withdrawalAccountNo`(보관계좌), `transactionBalance`(`amount`의 십진수 문자열), `withdrawalTransactionSummary`(요청 식별자 포함)<br>Header.userKey = 에스크로 보관계좌 소유자 키 | 응답의 출금 거래 `transactionUniqueNo`→각 거래의 `financial_transaction_id` |
| `RESULT_QUERY` | 먼저 `inquireTransactionHistoryList`<br>req: `accountNo`(보관계좌), `startDate`, `endDate`, `transactionType=D`<br>요약 표식·금액·수취 계좌를 대조하고, 찾은 거래의 `transactionUniqueNo`로 `inquireTransactionHistory` 단건 조회 | 일치하는 출금 거래를 확인하면 성공 확정. 목록에서 못 찾으면 `UNKNOWN` 유지 |

- 작업자 순지급·수수료·환불은 **같은 정산의 독립된 거래**다. `external_reference_id` = **`SETTLEMENT:<정산ID>`** 공유, `transaction_type`으로 구분한다. 각 이체는 자체 `financial_operation_attempts` 및 금융망 요청 식별자를 가지므로 결과 불명 시 해당 다리만 조회·복구한다.
- `settlements`가 같은 정산의 승인 총액·환불액·업무 상태를 기록하고 각 거래는 `settlement_id`로 연결된다. `releaseEscrow`와 `refundEscrow`의 서로 다른 요청 멱등키는 개별 거래에서 관리한다. 계약 행을 잠그고 기존 정산과 잔액을 검사한 뒤 부모 정산과 해당 거래들을 한 DB 트랜잭션에서 생성한다. 수수료 거래는 요청 멱등키에서 파생한 별도의 고유 키를 사용한다. 자금 유출이 전혀 없다고 확인된 `FAILED` 정산 뒤에만 새 정산 식별자를 허용한다. (`R-PAY-12`, D-8)
- `releaseEscrow.amount`는 수수료 공제 전 승인 총액이다. `escrows.fee_rate_bps`는 계약 생성 때 설정된 값을 고정한다. 수수료는 `floor(승인 총액 × fee_rate_bps / 10000)`원, 작업자 순지급액은 승인 총액에서 수수료를 뺀 금액이다. `escrow_transactions.amount`는 `RELEASE`에 순지급액, `FEE`에 수수료액을 저장한다. 수수료가 0원이면 FEE 거래·이체를 생략한다. `REFUND`는 별도로 남은 금액 전액이며 수수료 계산 대상이 아니다. (D-3, D-7)
- FEE 수취 계좌는 보관계좌와 **같은 에스크로 SSAFY `userKey` 명의로 개설한 별도 입출금 계좌**다. 두 계좌번호는 달라야 한다. 수익 계좌번호는 에스크로 운영 설정으로 고정하고 Komit 요청으로 받지 않는다. 이체 당시 수취 계좌는 FEE 거래에 암호화해 기록한다. 수익 계좌는 배포 전에 한 번 개설하고 계좌번호를 확인해 설정한다. 기동할 때마다 계좌 개설을 재호출하지 않는다. 같은 상품으로 개설을 재호출하면 두 번째 계좌가 만들어진 실호출 결과가 있기 때문이다. 보관계좌에서 수익 계좌로의 이체·예금주 조회는 아직 검증하지 않았으므로 실제 수수료 이체 전에 소액 실호출로 확인해야 한다.
- `releaseEscrow`는 요청을 정상 처리했으면 HTTP 200 / `H0000`과 현재 정산 `status`를 돌려준다. `H0000`만으로 지급 완료를 판단하지 않는다. `releasedAmount`는 이번 정산에서 완료가 확인된 RELEASE+FEE의 합계, `feeAmount`는 완료된 FEE, `netAmount`는 완료된 RELEASE다. `balance`는 완료된 DEPOSIT−RELEASE−FEE−REFUND이며, 진행 중인 이체는 차감하지 않는다. 승인 총액은 요청의 `amount`와 같은 정산의 거래 예정액으로 보관한다. (`R-PAY-12`)
- 작업자 순지급 이체를 확인한 뒤에만 수수료 이체를 시작한다. 둘 다 확인됐거나 수수료가 0원이고 작업자 이체가 확인됐을 때만 정산 `COMPLETED`다. 한쪽이 진행 중이면 `PROCESSING`, 결과를 확인할 수 없으면 `UNKNOWN`이다. `FAILED`는 출금이 전혀 없고 실패가 확정된 경우에만 사용한다. 작업자 이체 후 수수료 이체가 확정 실패해도 정산 전체는 `FAILED`가 아니라 `PROCESSING`으로 두고 수수료 다리만 복구한다. (`R-PAY-07`, `R-PAY-12`)
- `PROCESSING`·`UNKNOWN`인 정산이 남아 있으면 같은 계약의 새 지급과 환불을 막는다. 재요청은 같은 `referenceId`·동일한 요청 내용으로만 받으며, 현재 상태를 반환한다. 응답을 잃은 Service는 같은 요청을 재전송하거나 `inquireEscrow`의 거래 상태를 조회한다. 이미 확인된 이체를 다시 실행하지 않고 미완료 다리만 기존 업무 참조로 복구한다. 표시되는 `balance`는 완료가 확인된 거래 기준이며 정산 진행 중에는 새 지급·환불에 사용할 수 없다. `FAILED`는 유출액이 0인 종결 결과이므로, 원인을 고친 뒤 새로 승인된 요청은 새 정산 식별자로 시작할 수 있다. (`R-PAY-12`)
- 예: 예치 650,000원, 승인 총액 400,000원에서 작업자에게 396,000원을 지급했고 수수료 4,000원 이체 결과가 불명확하면 `status=UNKNOWN`, `releasedAmount=396000`, `feeAmount=0`, `netAmount=396000`, `balance=254000`이다. 수수료 이체를 확인한 뒤에는 `COMPLETED`, `releasedAmount=400000`, `feeAmount=4000`, `balance=250000`으로 바뀐다. Mock은 상태를 저장하지 않으므로 각 상태를 고정 계약 사례로 제공한다.
- 지급 수취 계좌는 **호출자(be-api)가 지정**한다. 에스크로는 등록 계좌 원장을 강제하지 않는다.
- 환불 계좌는 해당 계약의 예치에 사용한 회원가입 발급 계좌로 고정한다 (`R-PAY-04`). Komit이 `refundBankCode`·`refundAccountNo`로 그 계좌를 전달하고, 에스크로는 완료된 예치 거래의 `source_bank_code`·복호화한 `source_account_encrypted`와 비교한다. 불일치하면 `E5009`로 거절하고 송금하지 않는다. 별도 1원 인증 계좌의 등록·교체는 환불 계좌를 바꾸지 않는다.
- `escrows.hold_status`가 HELD면 지급·일반 환불을 막는다 (D-4)
- 2026-09-18 실호출에서 에스크로 보관계좌 소유자의 `userKey`로 다른 테스트 회원 계좌를 조회하자 `H0000`과 그 계좌의 `userName`이 반환됐다. 반대 방향 조회도 성공했다. 따라서 같은 SSAFY 앱에 등록된 다른 회원의 입출금 계좌는 지급·환불 직전에 예금주 조회가 가능하다. 다른 앱이나 외부 은행 계좌까지 확인한 결과는 아니다.
- SSAFY 테스트 계좌의 `userName`은 회원 등록에 사용한 이메일의 `@` 앞부분이었다. Komit 프로필명과 같다고 가정하지 말고, 지급·환불 요청의 수취 예금주명에는 금융망 계좌의 실제 `userName`을 사용한다.
- 같은 날 테스트 보관계좌의 3,000원 중 1,000원을 별도 작업자 테스트 계좌로 지급하고, 1,000원을 원래 의뢰인 테스트 계좌로 환불했다. 두 건 모두 수취 예금주 조회와 이체가 `H0000`이었고 이체 응답 `REC`는 출금·입금 거래 2건이었다. 지급 후 보관계좌 2,000원·작업자 계좌 1,000원, 환불 후 보관계좌 1,000원·의뢰인 계좌 1,000원 증가를 잔액 조회로 확인했다. 이는 금융망 이체 경로의 검증이며, 에스크로 애플리케이션의 지급·환불 상태 전이와 원장 기록은 아직 검증하지 않았다.
- 같은 거래들을 보관계좌의 출금 내역 목록에서 거래 요약·금액·수취 계좌로 각각 한 건씩 찾았고, 목록의 `transactionUniqueNo`로 단건 조회했을 때 내역이 일치했다. 목록·단건 응답에는 `institutionTransactionUniqueNo`가 없었다. 응답을 실제로 유실시킨 시험은 아니므로, 목록 반영 지연 시간과 조회 실패 시 운영 처리는 별도로 검증해야 한다.

### 취소·동결·해제의 멱등 처리

`cancelEscrow`·`holdEscrow`·`unholdEscrow`의 성공 요청은 `escrow_state_commands`에 남긴다. 멱등 키 범위는 `(service_client_id, api_name, idempotency_key)`다. 요청 지문은 API 이름, `escrowId`, 동결·해제의 `reportId`를 정규화해 계산하며 매 호출마다 달라지는 전송 시각과 `institutionTransactionUniqueNo`는 제외한다. 인증된 서비스 ID는 요청 Header 값 대신 검증된 API 키에서 얻는다.

계약 행을 잠그고 상태 전이와 명령 기록을 한 DB 트랜잭션에서 확정한다. 같은 키·같은 지문으로 다시 오면 상태를 재변경하지 않고 기록된 `result_state`로 당시 성공 응답의 `REC`를 재구성한다. 같은 키·다른 지문이면 `E5003`을 반환한다. 응답 Header의 전송 추적값은 이번 요청에 맞춰 만든다. 검증에 실패해 상태가 바뀌지 않은 요청은 이 테이블에 기록하지 않는다. 이후 현재 상태가 필요하면 `inquireEscrow`로 조회한다. 이 기록만으로 서로 다른 신고 사건의 도착 순서를 판정하지는 않는다.

---

## 5. 계좌 확인 (1원 인증)

Komit이 회원가입 때 보관한 `userKey`와 계좌번호를 에스크로에 전달한다. 에스크로는 SSAFY 계좌 확인 API를 중계하고 `authText`를 주입한다. 인증 결과와 사용 계좌는 Komit이 보관한다. 통합 V1의 `account_auth_requests`는 1원 송금의 멱등 키·요청 지문·암호화한 사용자 키와 계좌·최초 만료 시각을 저장한다. 등록 계좌의 원본과 사용자 화면의 인증 세션은 Komit이 관리한다.

| 단계 | 에스크로 API·금융망 처리 | Komit으로 전달할 결과 |
| --- | --- | --- |
| 인증 요청 | `openAccountAuth`<br>req: `accountNo`, `authText`(에스크로 설정값) | `transactionUniqueNo`, `accountNo`, `expiresAt` |
| 인증 검증 | 에스크로 `checkAuthCode`<br>req: `transactionUniqueNo`, `accountNo`, `authCode`(4자리 숫자). 은행 앱이 없는 데모이므로 실제 코드 일치 검사는 하지 않는다 | `status=SUCCESS`, `transactionUniqueNo`, `accountNo` |

- **be-api는 `Header.userKey`와 `accountNo`를 넘기고, `authText`는 에스크로가 채운다.** (목의 요청에 `authText`가 없는 이유)
- `R-ACC-02`: 유효 시간 안에 네 자리 숫자를 입력하면 어떤 값이든 성공한다. `expiresAt`은 에스크로 응답을 Komit이 인증 세션과 함께 관리한다.
- `R-ACC-11`: 이 성공 결과는 실제 계좌 소유 증명이 아니다.
- `R-ACC-04`: Komit 회원당 유효 계좌 1개. 이 제약을 에스크로 통합 `V1`의 party 테이블에 의존하지 않는다.

---

1원 인증 요청도 Writer가 `REQUESTED → DISPATCHING` 조건부 갱신을 커밋한 뒤 호출한다. 같은 서비스·멱등키의 재요청은 사용자 키·계좌번호를 포함한 요청 지문을 대조하며, 성공한 요청은 동일한 거래번호와 `expires_at`을 반환한다. 처리 중이거나 결과 불명이면 503으로 알리고 원래 키를 유지한다. 금융망이 1원 인증 거래번호를 확실히 복구할 조회 수단을 제공하지 않으면 UNKNOWN을 유지하고 운영 확인으로 넘긴다. 임의의 새 송금이나 만료 연장은 하지 않는다.

`checkAuthCode`는 `transactionUniqueNo`가 같은 서비스·사용자·계좌의 성공 요청인지와 `expires_at`을 먼저 확인한다. 만료는 E5011, 존재하지 않거나 소유 정보가 다른 요청은 E5012다. 네 자리 숫자 무조건 성공은 이 조건을 만족한 데모 요청에만 적용한다. 이 API는 상태를 변경하지 않으므로 반복 호출이 가능하다. 동일 키로 만료된 성공 요청을 다시 열어도 최초 결과를 반환하며 새 인증은 새 키를 사용한다.

## 6. 대사 (Reconciliation)

원장(집계)과 은행 잔액을 맞춰 본다. `inquireReconciliation`은 보관계좌 전체에 대한 에스크로 운영 전용 조회다. 일반 이용 서비스의 `clientApiKey`는 이 API에서 `E5001`로 거절한다. 에스크로가 배포 환경의 Secret으로 관리하는 별도 운영 키를 같은 `Header.clientApiKey`에 담는다. 이 운영 키는 다른 서비스 API에 사용할 수 없다. (`R-PAY-13`)

| 필드 | 출처 |
| --- | --- |
| `ledgerTotal` | 보관계좌에 연결된 **모든 이용 서비스**의 `escrow_transactions` 집계: 완료 DEPOSIT − RELEASE(작업자 순지급) − FEE(플랫폼 수수료) − REFUND (`completed_at` 기준) |
| `bankBalance` | SSAFY `inquireDemandDepositAccountBalance` 응답 (**ERD 밖 — SSAFY 대응 필드**) |
| `differenceAmount` | `bankBalance − ledgerTotal`. 0이면 MATCHED |

`escrowCount`도 모든 이용 서비스에서 보관액이 남은 계약 수다. 개별 이용 서비스는 자기 계약의 `inquireEscrow`·`inquireEscrowList`만 조회하며 보관계좌 전체 잔액을 받지 않는다. 예를 들어 Komit의 완료 원장 잔액이 650,000원이고 다른 이용 서비스가 350,000원이면, 대사는 전체 원장 1,000,000원과 은행 잔액 1,000,000원을 비교한다. 원장에 잡힌 계약이 없어도 은행 잔액이 0이 아니면 `BANK_MISMATCH`다.

운영 보관계좌는 계약 예치금 전용으로 두고 사용 전 초기 잔액을 확인한다. 테스트 이체나 별도 자금 유입은 운영 대사 계좌에 섞지 않는다. 원장 밖 자금이 남으면 숫자를 임의로 맞추지 않고 불일치로 표시해 원인을 확인한다. 플랫폼 수익 계좌의 잔액은 이 보관계좌 대사에 합산하지 않는다. (`R-PAY-05`, `R-PAY-07`, `R-PAY-13`)

---

## 7. Komit ↔ Escrow 번역 매핑

계층이 다르므로 필드명·타입·상태 어휘가 다르다. be-api가 escrow 클라이언트를 짤 때 아래 대응을 구현한다.

**계층 규약**: FE → Komit 은 간소화 형식, Komit → Escrow 는 SSAFY 금융망 형식(`Header`/`REC`)을 쓴다.
FE는 SSAFY 형식을 몰라도 된다.

### 식별자

| Komit (FE 대면) | Escrow | 비고 |
| --- | --- | --- |
| `contractId` (int, 예 130) | `external_contract_id` (str) | Komit 정수를 문자열로 변환해 넘긴다 |
| — | `escrowId` (int) | escrow가 발급. Komit은 계약↔escrow 매핑을 보관 |

Komit은 단건 조회 `inquireEscrow`에 저장한 `escrowId`와 자신의 `contractId`(문자열)를 함께 전달한다. Escrow는 두 값이 같은 계약을 가리키는지 확인하고, 다르면 `E5004`를 반환한다. `contractId`를 생략한 기존 조회 형식도 유지한다.

### 예치 출금 계좌

Komit의 `GET /api/payment-methods`는 회원가입 때 발급한 SSAFY 계좌 한 건만 반환한다.
FE가 전달한 `paymentMethodId`는 Komit 내부에서 이 계좌를 찾는 데만 사용한다.
Komit은 계좌 소유자를 확인하고 보관 중인 금융정보를 에스크로 요청으로 변환한다. (`R-PAY-09`)

| Komit (FE 대면) | Escrow (`createEscrow`) |
| --- | --- |
| `paymentMethodId`로 찾은 회원 발급 계좌 | `Header.userKey`, `withdrawalBankCode`, `withdrawalAccountNo` |
| `idempotencyKey` | `Header.idempotencyKey` |

### 예치 상태

Komit `EscrowDepositResponse.status`는 escrow의 상태를 번역한 값이다.

| Komit status | 파생 출처 (escrow) |
| --- | --- |
| `PENDING` | `escrows.status` = DEPOSIT_PENDING |
| `DEPOSITED` | `escrows.status` = ACTIVE |
| `FAILED` | 예치 `escrow_transactions.status` = FAILED 또는 createEscrow 에러 응답. `escrows.status`에는 FAILED가 없다 |

### 정산 뷰

| Komit (`ContractDepositResponse`) | Escrow (`inquireEscrow`) |
| --- | --- |
| `depositedAmount` | `depositedAmount` |
| `releasedAmount` | `releasedAmount` |
| 수수료·작업자 실수령액 | `feeAmount`·`netAmount` (`releaseEscrow` 응답). Service 화면 노출 여부는 Service 담당자가 결정 |
| `remainingAmount` | `balance` |

### 최종 정산 상태

Service는 `releaseEscrow`의 HTTP 200 / `H0000`을 정산 요청의 처리 결과 수신으로 해석하고, 지급 완료 판정에는 반드시 `REC.status`를 사용한다. (`R-PAY-12`)

| Escrow `REC.status` | Service 처리 |
| --- | --- |
| `PROCESSING` | 지급 진행 중으로 유지. 같은 정산 식별자로 재조회·재요청하고 새 지급·환불은 시작하지 않음 |
| `UNKNOWN` | 자금 이동 결과 확인 중으로 유지. 같은 정산 식별자로 재조회·재요청하고 새 지급·환불은 시작하지 않음 |
| `COMPLETED` | 작업자 지급과 수수료 이체가 모두 확인된 정산으로 처리 |
| `FAILED` | 자금 유출 없는 확정 실패로 처리. 원인을 고친 뒤 승인된 새 요청은 새 정산 식별자 사용 |

`inquireEscrow.status=ACTIVE`만으로 정산 가능 여부를 판단하지 않는다. 같은 정산 참조를 가진 RELEASE·FEE 거래의 상태를 함께 확인한다. 진행 중 응답의 `balance`는 완료된 거래 기준 잔액이지만 환불 가능액이 아니며, 이때 새 지급·환불 요청은 `E5010`으로 거절된다.

### 계좌 1원 인증

| Komit (`AccountVerificationResponse`) | Escrow (`openAccountAuth` / `checkAuthCode`) |
| --- | --- |
| `verificationId` (int) | 상관키는 `accountNo`. Komit이 자체 세션 ID를 발급하고 `accountNo` 원문을 암호화해 보관하며 화면에는 가린 값만 전달 |
| `expiresAt` | `openAccountAuth` 응답의 `expiresAt`. Komit이 인증 세션과 함께 관리하고 FE에 전달한다 |
| (검증 요청) | `checkAuthCode` req: `transactionUniqueNo`, `accountNo`, `authCode`(4자리 숫자). 실제 코드 일치 여부는 검사하지 않는다 (`R-ACC-02`) |

---

## 8. 미결 (확정 필요)

키 발급·실호출 뒤 확인하거나 팀이 정할 항목.

| 항목 | 상태 |
| --- | --- |
| 운영 감사 기록 | 통합 V1에 추가 전용 `operation_audit_events` 테이블을 정의했다. UNKNOWN 해소·미결 처리 때 작업 주체·근거·증빙 참조를 실제 서버가 기록하고 운영자 권한을 검증하는 구현은 아직 필요하다 |
| 가입 발급 결과 복구 | 2026-09-18 실호출로 회원 조회·계좌 목록·입금내역 조회를 확인했다. 같은 상품의 계좌 개설 재호출은 새 계좌를 생성한다. 계좌 목록에 상품 고유번호가 없어 후보가 여러 개면 자동 복구할 수 없으므로 `UNKNOWN`을 유지한다. 실제 통신 단절·조회 지연 때의 동작은 미검증 |
| Service Mock 환불 계좌 설명 | `mock/app/routers/service.py`는 별도 등록 계좌를 의뢰인 환불 계좌로 설명한다. Service 담당자가 `R-PAY-04`에 맞춰 회원가입 발급 계좌로 설명을 수정해야 한다 |
| 플랫폼 수익 계좌 준비 | 보관계좌와 같은 에스크로 SSAFY 사용자 명의의 별도 계좌로 결정했다. 계좌 개설·운영 설정과 같은 사용자 명의 두 계좌 간 소액 실이체·예금주 조회는 아직 하지 않았다. 확인 전에는 실제 수수료 이체를 시작할 수 없다 |
