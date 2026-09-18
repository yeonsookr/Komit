# 에스크로 ↔ SSAFY 금융망 매핑

에스크로의 세 자금 흐름(예치·지급·환불)을 SSAFY 금융망 API와 DB 스키마(`V1~V3`)에 맞춘 참고 문서다.
엔티티·서비스를 짤 때 이 문서의 필드 대응을 기준으로 한다. 값과 불변식은 `docs/RULES.md`가,
계약 흐름은 `docs/proposal.md`가 상위 출처다. 여기서는 금융망 연동 상세만 고정한다.

| 항목 | 내용 |
| --- | --- |
| 대상 | `escrow`(be-escrow) 앱 전용 |
| 스키마 | `escrow/src/main/resources/db/migration/V1~V3` |
| 금융망 | SSAFY 교육용 금융망 API |

---

## 0. 확정된 설계 결정

이 문서와 스키마·목이 공유하는 전제다. 바꾸려면 관련 문서를 함께 고친다.

| # | 결정 | 근거 |
| --- | --- | --- |
| D-1 | **집금(BaaS) 모델.** 에스크로는 SSAFY 회원 1개(`userKey`)와 보관계좌 1개를 소유하고, 계약마다 그 보관계좌에 연결한 가상계좌(VA)로 받는다. | `R-PAY-05` |
| D-2 | **Model A — 자금 이동은 전부 에스크로가 실행한다.** 예치 이체도 에스크로가 한다. Komit Service는 금융망 이체 API를 직접 부르지 않고, 필요한 `userKey`와 계좌번호를 에스크로 요청에 담는다. | `proposal.md` §5·§7 |
| D-3 | **일괄 지급.** 마일스톤 분할 지급은 없다. 정상 완료는 전액, 중도 종료는 통과분 합계를 한 번에 지급하고 나머지는 환불한다. | `R-PAY-01`, `R-PAY-03`, `R-PAY-04` |
| D-4 | **단일 동결.** 한 에스크로의 활성 동결은 최대 1개. `escrows.hold_status`로 가른다. | `R-RPT-05` |
| D-5 | **금융 식별정보는 Komit Service가 소유한다.** Service가 회원가입 시 발급한 `userKey`와 개인 계좌를 보관하고, 에스크로는 계좌 확인을 중계하되 해당 정보를 저장하지 않는다. | `R-ACC-09`, `R-ACC-10` |
| D-6 | **원장은 별도 테이블이 아니라 `escrow_transactions` 집계.** 잔액·보관총액은 저장 컬럼이 아니라 계산값이다. | `inquireReconciliation` 설계 |

---

## 1. 공통 Header

모든 계좌 조작 API는 공통 `Header`를 쓴다. 에스크로는 아래를 자동으로 채운다.

| Header 필드 | 값 | 비고 |
| --- | --- | --- |
| `apiName` / `apiServiceCode` | 엔드포인트 마지막 path | `step`이 결정 |
| `transmissionDate` / `transmissionTime` | 요청 시각 (YYYYMMDD / HHMMSS) | 요청시각 ±5분 |
| `institutionCode` / `fintechAppNo` | `00100` / `001` 고정 | |
| `institutionTransactionUniqueNo` | 20자리, **매 호출 새 값** | → `financial_operation_attempts.request_reference` |
| `apiKey` | 에스크로 앱 키 | 환경변수 |
| `userKey` | 자금 이동 주체의 신원 | 보관계좌 소유자 키 또는 Komit Service가 요청마다 전달한 사용자 키 |

**멱등 규칙:** `request_reference`를 외부 호출 **전에** 채번·저장(`requested_at`)하고 그 값을 `institutionTransactionUniqueNo`로 보낸다. 재시도는 `attempt_no`를 올리고 **새 `request_reference`**를 쓴다. 응답 유실 시 이 값으로 조회한다.

---

## 2. 예치 (DEPOSIT)

`autoTransfer=false`로 발급해, **검증(VERIFY)을 통과한 뒤에만 보관계좌로 정산(SETTLE)**한다.
이로써 "외부 결과가 일치하는지 확인한 뒤 완료"(`proposal.md` §7)가 강제된다.

| deposit_step | attempts.step | SSAFY API | 응답 → 컬럼 |
| --- | --- | --- | --- |
| VA_CREATING→VA_CREATED | `VA_CREATE` | `createVirtualAccount`<br>req: `mainAccountNo`(보관계좌), `depositorName`, `expectedAmount`, `expiryDate`, `autoTransfer=false` | `virtualAccountId`→`virtual_account_id`<br>`virtualAccountNo`→`virtual_account_no` |
| TRANSFER_SENDING→TRANSFERRED | `DEPOSIT_TRANSFER` | `updateDemandDepositAccountTransfer`<br>req: `depositAccountNo`(VA번호), `withdrawalAccountNo`(의뢰인 계좌), `transactionBalance`<br>Header.userKey = Komit Service가 전달한 의뢰인 키 | 응답 거래번호를 시도 기록에 연결. 사용자 `userKey`와 계좌번호는 저장하지 않음 |
| VERIFIED | `DEPOSIT_VERIFY` | `inquireVirtualAccountDeposits`<br>req: `virtualAccountId` | `deposits[].depositAmount`→`verified_deposit_amount`<br>`nameMatched`→`depositor_name_matched`<br>→`deposit_verification_status` |
| SETTLED | `VA_SETTLE` | `settleVirtualAccount`<br>req: `virtualAccountId` | `settledAmount`, `settledAt`→`completed_at` |
| VA_CLOSED | `VA_CLOSE` | `closeVirtualAccount`<br>req: `virtualAccountId` | status=CLOSED→`virtual_account_closed_at` |

- `external_reference_id` = **`INITIAL`** (예치 고정)
- `deposit_verification_status`: `verified_deposit_amount` == `escrows.expected_amount` → MATCHED, 다르면 MISMATCHED(부분·초과), 미확인 UNKNOWN
- **VA 입금은 출처 계좌를 응답에 주지 않는다.** `source_*`는 be-api가 넘긴 참고값이지 금융망 확인값이 아니다.

---

## 3. 지급 (RELEASE) / 환불 (REFUND)

둘 다 보관계좌에서 나가는 이체로 구조가 같고 목적지만 다르다.

| attempts.step | SSAFY API | 매핑 |
| --- | --- | --- |
| `HOLDER_VERIFY` | `inquireDemandDepositAccountHolderName`<br>req: `accountNo`(수취) | 응답 `userName` ↔ `recipient_name` 대조. 다르면 이체 중단 |
| `PAYOUT_TRANSFER` / `REFUND_TRANSFER` | `updateDemandDepositAccountTransfer`<br>req: `depositAccountNo`(수취), `withdrawalAccountNo`(보관계좌), `transactionBalance`(`amount`) | 응답 `transactionUniqueNo`→`financial_transaction_id` |
| `RESULT_QUERY` | `inquireTransactionHistory`<br>req: `accountNo`(보관계좌), `transactionUniqueNo` | UNKNOWN 해소용. 성공·실패 확정 |

- 지급·환불은 **같은 정산의 두 다리**다. `external_reference_id` = **`SETTLEMENT:<정산ID>`** 공유, `transaction_type`으로 구분
- `amount`: RELEASE = 정상 완료 전액 또는 중도 종료 통과분 합계 (D-3)
- 수취 계좌는 **호출자(be-api)가 지정**한다. 에스크로는 등록 계좌 원장을 강제하지 않는다
- `escrows.hold_status`가 HELD면 지급·일반 환불을 막는다 (D-4)

---

## 4. 계좌 확인 (1원 인증)

에스크로는 Komit Service가 전달한 `userKey`와 계좌번호로 SSAFY 금융망의 인증 API를
중계한다. 인증 세션과 등록 계좌의 원본은 Komit Service가 관리하며, 에스크로는
`userKey`, 계좌번호, 인증 결과를 자기 DB에 저장하지 않는다. 에스크로가 `authText`를
주입한다.

| 단계 | SSAFY API | 매핑 |
| --- | --- | --- |
| 인증 요청 | `openAccountAuth`<br>req: `accountNo`, `authText`(= `service_clients.one_won_auth_message`) | 응답을 Komit Service에 그대로 전달 |
| 인증 검증 | `checkAuthCode`<br>req: `accountNo`, `authText`, `authCode`(4자리) | 응답을 Komit Service에 그대로 전달 |

- **be-api는 `accountNo`만 넘기고, `authText`는 에스크로가 채운다.** (목의 요청에 `authText`가 없는 이유)
- `R-ACC-02`: 인증 4자리 / 유효 시간. 인증 세션 만료 시각은 Komit Service가 관리한다
- `R-ACC-11`: 은행 앱이 없는 현재 모의 환경에서는 형식이 맞는 네 자리이면 일치 여부와 무관하게 성공한다. 실제 계좌 소유 인증이 아니다
- `R-ACC-04`: 사용자당 유효 계좌는 Komit Service에 하나만 유지한다

현재 `V3__create_account_verification_tables.sql`의 `financial_parties`,
`registered_accounts`, `account_verifications`는 이 결정 전의 소유권 모델이다. 실제
Escrow 구현에서는 해당 테이블을 기준으로 신규 기능을 만들지 않고, 별도 스키마 변경
작업에서 제거 또는 대체한다.

---

## 5. 대사 (Reconciliation)

원장(집계)과 은행 잔액을 맞춰 본다. `inquireReconciliation`.

| 필드 | 출처 |
| --- | --- |
| `ledgerTotal` | `escrow_transactions` 집계: 완료 DEPOSIT − RELEASE − REFUND (`completed_at` 기준) |
| `bankBalance` | SSAFY `inquireDemandDepositAccountBalance` 응답 (**ERD 밖 — SSAFY 대응 필드**) |
| `differenceAmount` | `bankBalance − ledgerTotal`. 0이면 MATCHED |

---

## 6. Komit ↔ Escrow 번역 매핑

계층이 다르므로 필드명·타입·상태 어휘가 다르다. be-api가 escrow 클라이언트를 짤 때 아래 대응을 구현한다.

**계층 규약**: FE → Komit 은 간소화 형식, Komit → Escrow 는 SSAFY 금융망 형식(`Header`/`REC`)을 쓴다.
FE는 SSAFY 형식을 몰라도 된다.

### 식별자

| Komit (FE 대면) | Escrow | 비고 |
| --- | --- | --- |
| `contractId` (int, 예 130) | `external_contract_id` (str) | Komit 정수를 문자열로 변환해 넘긴다 |
| — | `escrowId` (int) | escrow가 발급. Komit은 계약↔escrow 매핑을 보관 |

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
| `remainingAmount` | `balance` |

### 계좌 1원 인증

| Komit (`AccountVerificationResponse`) | Escrow (`openAccountAuth` / `checkAuthCode`) |
| --- | --- |
| `verificationId` (int) | 상관키는 `accountNo`. Komit이 자체 세션 ID를 발급하고 `accountNo`(가린 값)와 함께 보관 |
| `expiresAt` | `openAccountAuth` 응답의 `expiresAt`. Komit Service가 인증 세션과 함께 관리한다 |
| (검증 요청) | `checkAuthCode` req: `accountNo`, `authCode`(4자리). `authText`는 escrow가 주입 |

---

## 7. 미결 (확정 필요)

키 발급·실호출 뒤 확인하거나 팀이 정할 항목.

| 항목 | 상태 |
| --- | --- |
| `HOLDER_VERIFY`가 타인 계좌도 조회되나 | 미확인. 안 되면 수취 검증을 등록 시점으로 이동 |
| VA 활성 상한 (`E4065`) | 명세에 값 없음 = 동시 진행 계약 수 상한. 실호출 확인 |
| 이체 한도 상향 | 기본 1회 1억/1일 5억. 집금 계좌는 `updateTransferLimit` 필요. 상향폭 미정 |
| 예치 확정 동기/비동기 | createEscrow가 ACTIVE 또는 DEPOSIT_PENDING. 문서화 여부 보류 |
| 운영 감사 기록 | UNKNOWN 해소·미결 처리 이력 테이블 미설계 |
