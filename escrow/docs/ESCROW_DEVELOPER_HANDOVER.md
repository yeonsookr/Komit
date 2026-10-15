# Escrow 개발 인수인계서

작성 기준일: 2026-09-20  
기준 브랜치: `S15P21A201-229/be-escrow/fix/escrow_mock_review`  
대상 모듈: `escrow/`, 관련 CI/CD 및 K3s 설정

> 이 문서는 다음 담당자가 현재 구현을 이어가기 위한 최신 진입점이다. 정책은 `docs/RULES.md`, 서비스 방향은 `docs/proposal.md`, 실제 API 요청·응답은 코드와 `escrow/dosc/BACKEND_ESCROW_INTEGRATION_GUIDE.md`를 함께 확인한다. 이 문서에는 실제 비밀번호와 API 키를 기록하지 않는다.

## 1. 가장 먼저 확인할 미해결 장애

### 현상

운영 주소 `https://komit.site/escrow/admin`에서 Escrow 전용 로그인 화면은 표시되지만, 로그인 버튼을 누르면 Escrow 운영 콘솔이 아니라 Komit 본 서비스의 `https://komit.site/admin/login`으로 이동한다.

Komit 관리자 로그인과 Escrow 관리자 로그인은 서로 다른 인증 체계다. `/escrow/admin/**` 요청이 도메인 루트의 `/admin/**`로 유출되면 안 된다.

### 코드에서 이미 시도한 수정

- `AdminSecurityConfig`의 성공·실패·미인증·로그아웃 리다이렉트가 `APP_PUBLIC_BASE_URL`의 path(`/escrow`)를 붙이도록 변경했다.
- 로그인 form action은 `${adminPrefix + '/admin/login'}`으로 변경했다.
- 로그아웃 form action은 `${adminPrefix + '/admin/logout'}`으로 변경했다.
- 운영 세션 쿠키 이름을 `ESCROWSESSION`, path를 `/escrow`로 분리했다.
- nginx 문서에 정확 일치 경로 `location = /escrow { return 308 /escrow/; }`를 추가했다.

그러나 운영 환경에서는 문제가 계속 재현된다. 따라서 **코드 수정 완료로 간주하지 말고, 실제 배포 이미지·Pod 환경변수·nginx 전달 경로를 함께 조사해야 한다.**

### 가능성이 높은 원인

1. 최신 커밋 또는 작업 트리의 form-action 수정이 포함된 이미지가 운영 Pod에 배포되지 않았다.
2. 운영 Pod의 `APP_PUBLIC_BASE_URL`이 `https://komit.site/escrow`가 아니라 루트 주소로 들어갔다.
3. nginx의 `/escrow/` location이 upstream으로 전달할 때 URI를 예상과 다르게 rewrite한다.
4. nginx가 `X-Forwarded-Prefix: /escrow`를 보내지 않거나, 별도 ingress/proxy 계층이 `Location` 헤더를 다시 쓴다.
5. `ESCROWSESSION` cookie의 path 또는 Secure/SameSite 속성 때문에 인증 세션이 후속 요청에 전달되지 않는다.
6. Kustomize Secret/ConfigMap 변경은 반영됐지만 Deployment가 이전 이미지 digest를 계속 사용한다.

### 재현 및 진단 순서

다음 순서로 확인하면 코드 문제와 프록시/배포 문제를 분리할 수 있다.

```bash
# 1. 실제 Pod 이미지와 환경변수
sudo k3s kubectl -n komit get deploy escrow \
  -o jsonpath='{.spec.template.spec.containers[0].image}{"\n"}'
sudo k3s kubectl -n komit get pods -l app.kubernetes.io/name=escrow -o wide
sudo k3s kubectl -n komit exec deploy/escrow -- printenv APP_PUBLIC_BASE_URL

# 2. 클러스터 내부에서 프록시를 거치지 않고 Escrow 확인
# 서비스 DNS로 GET /admin 요청 시 Location이 /escrow/admin/login이어야 한다.
curl -kis http://escrow:8081/admin
curl -kis http://escrow:8081/admin/login

# 3. 외부 프록시 경로 확인
curl -kis https://komit.site/escrow/admin
curl -kis https://komit.site/escrow/admin/login

# 4. 브라우저 개발자 도구 Network에서 로그인 POST 확인
# Request URL이 반드시 아래여야 한다.
# https://komit.site/escrow/admin/login
# 응답 Location은 성공 시 /escrow/admin, 실패 시 /escrow/admin/login?error여야 한다.
```

로그인 POST를 쿠키까지 직접 확인할 때는 먼저 로그인 페이지의 CSRF 토큰을 받아야 한다. 브라우저 Network 탭을 쓰는 것이 가장 빠르다. 확인할 항목은 `Request URL`, POST 응답의 `Location`, `Set-Cookie: ESCROWSESSION=...; Path=/escrow`, 다음 GET의 `Cookie`다.

### nginx 기대 설정

실제 호스트 nginx 설정은 저장소가 자동 관리하지 않을 수 있다. 다음 의미를 만족해야 한다.

```nginx
location = /escrow {
    return 308 /escrow/;
}

location /escrow/ {
    proxy_pass http://<escrow-upstream>/;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-Host $host;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header X-Forwarded-Prefix /escrow;
}
```

`proxy_pass` 뒤의 `/` 유무는 upstream에 전달되는 URI를 바꾸므로 현재 서버 설정을 그대로 확인해야 한다. 현재 Spring Controller와 Security matcher는 upstream에서 `/admin/**`를 받는 구성을 전제로 한다.

### 관련 파일

- `escrow/src/main/java/com/a501/escrow/admin/AdminSecurityConfig.java`
- `escrow/src/main/java/com/a501/escrow/admin/AdminController.java`
- `escrow/src/main/resources/templates/admin/login.html`
- `escrow/src/main/resources/templates/admin/dashboard.html`
- `escrow/src/main/resources/application-prod.yml`
- `deploy/k3s/deploy.sh`
- `deploy/k3s/README.md`

## 2. 현재 구현된 기능

### 내부 API 보안과 공통 계약

- 모든 업무 API는 JSON body의 `Header.clientApiKey`로 서비스 클라이언트를 인증한다.
- API 키 원문은 DB에 저장하지 않고 `CLIENT_API_KEY_HMAC_SECRET`으로 HMAC 처리한다.
- 사용자 키·계좌번호는 암호화 사본과 조회용 HMAC을 분리해 저장한다.
- 멱등키와 요청 지문을 비교한다. 같은 키·다른 요청은 충돌로 거절한다.
- 일반 서비스 키와 운영자 키(`OPERATOR_CLIENT_API_KEY`)를 구분한다.
- 애플리케이션 시작 시 `ESCROW_BOOTSTRAP_CLIENT_API_KEY`를 HMAC 처리해 `service_clients`의 `Komit Backend` 레코드를 등록 또는 갱신한다.
- 1원 인증 문구는 `service_clients.one_won_auth_message`에 저장하며 서비스 키의 소유자를 기준으로 읽는다.

### 회원 계좌 발급

`POST /api/v1/escrow/createMemberAccount`

- SSAFY 사용자 등록, 입출금 계좌 발급, 데모 자금 1회 입금을 순차 처리한다.
- 단계와 결과를 `member_account_provisions`에 기록해 중단·재요청을 처리한다.
- 동일 서비스의 `userId` 및 멱등키 중복을 제한한다.
- 반환값은 `userId`, `userKey`, `bankCode`, `accountNo`다. Backend가 회원 금융정보를 안전하게 보관해야 한다.

### 1원 인증 및 거래내역

- `POST /api/v1/edu/accountAuth/openAccountAuth`
- `POST /api/v1/edu/accountAuth/checkAuthCode`
- `POST /api/v1/edu/demandDeposit/inquireTransactionHistoryList`

인증 문구는 요청자가 보내지 않고 인증된 서비스 클라이언트 설정에서 가져온다. 교육용 정책에 따라 네 자리 코드 형식과 만료·소유 관계를 확인하며, 실제 입금 코드 일치 검증은 하지 않는다. 자세한 정책은 `R-ACC-02`, `R-ACC-11`을 확인한다.

### 계약 생성과 예치

- `POST /api/v1/escrow/createEscrow`
- `POST /api/v1/escrow/cancelEscrow`
- `POST /api/v1/escrow/inquireEscrow`
- `POST /api/v1/escrow/inquireEscrowList`

계약별 가상계좌를 만들고 회원 발급 계좌에서 예치한 뒤 보관계좌로 정산한다. 금융 호출 전 실행 기록을 저장하고, 결과가 불명확하면 임의 재송금하지 않고 `UNKNOWN` 복구 대상으로 둔다.

### 신고 동결과 해제

- `POST /api/v1/escrow/holdEscrow`
- `POST /api/v1/escrow/unholdEscrow`

한 계약의 활성 동결은 하나다. `reportId`와 멱등 결과를 `escrow_state_commands`에 저장하고, 지급·환불 시작 전 동결 상태를 다시 확인한다.

### 지급·수수료·환불

- `POST /api/v1/escrow/releaseEscrow`
- `POST /api/v1/escrow/refundEscrow`

최종 정산은 `settlements` 한 건으로 묶고 RELEASE, FEE, REFUND는 각각 `escrow_transactions`에 기록한다. 지급과 수수료 이체가 모두 확인되어야 완료로 본다. 처리 중 또는 결과 불명 상태에서 새 정산을 시작하면 안 된다.

### 금융 호출 복구와 대사

- `financial_operation_attempts`로 금융 호출 단계·실행 Pod·결과를 관리한다.
- `REQUESTED → DISPATCHING` 조건부 갱신에 성공한 한 Pod만 금융망을 호출한다.
- `DISPATCHING`에서 장애가 나면 재송금 대신 거래내역 조회로 복구한다.
- `POST /api/v1/escrow/inquireReconciliation`은 운영자 키 전용이다.
- 원장 잔액과 SSAFY 보관계좌 실제 잔액을 비교해 `MATCHED` 또는 `BANK_MISMATCH`를 저장한다.

### 운영 콘솔

- Spring MVC + Thymeleaf 기반 조회 전용 화면이다.
- 에스크로 건수·상태, 원장 합계, 복식부기 원장 계정 잔액과 대조, 계좌 잔액, 최근 대사, 최근 에스크로, 서비스 클라이언트와 인증 문구를 표시한다.
- 자금 초기화, 강제 지급·환불 같은 변경 기능은 없다.
- 로그인은 Spring Security의 메모리 사용자 한 명을 사용한다.
- 현재 운영 prefix 라우팅 장애는 이 문서 1절을 따른다.

## 3. 데이터베이스와 실행 구조

Flyway migration은 다음 네 파일이다. 이미 적용된 파일은 고치지 않고 다음 번호로 추가한다.

- `V1__create_escrow_schema.sql`: 서비스 클라이언트, 회원 발급, 1원 인증, 에스크로, 상태 명령, 정산, 원장 거래, 금융 시도, 감사 이벤트
- `V2__create_reconciliation_snapshots.sql`: 대사 결과 이력
- `V3__create_double_entry_ledger.sql`: 복식부기 원장. 계정과목 `ledger_accounts`, 분개 `ledger_postings`, 분개 트리거, 기존 완료 거래 이관
- `V4__limit_one_transfer_attempt.sql`: 같은 거래·송금 단계에 거절(FAILED)되지 않은 송금 시도를 하나만 허용하는 부분 유니크 인덱스

### 같은 송금을 다시 보내지 않는 방식 (`R-PAY-12`)

| 경우 | 동작 |
| --- | --- |
| `COMPLETED`·`FAILED` 거래를 같은 멱등키로 다시 요청 | 송금하지 않고 현재 결과를 돌려준다. `FAILED`는 금융망이 거절해 자금 유출이 없는 확정 결과다 |
| 지급이 `FAILED` | 아직 보내지 않은 같은 정산의 수수료도 `FAILED`(`PAYOUT_FAILED`)로 닫아 정산이 `FAILED`로 확정된다. 이후 요청은 새 정산 식별자를 쓴다 |
| 금융망이 이체를 정상 응답했는데 본문을 해석하지 못함 | 돈이 움직였을 수 있으므로 `FAILED`가 아니라 결과 불명(`UNKNOWN`)으로 두고 거래내역 조회로 확인한다. 예치 이체도 같다 |
| 두 Pod가 같은 거래를 동시에 처리 | 송금 시도 생성(`prepareTransferAttempt`)은 거래 행을 잠그고 `PENDING`·`PROCESSING`일 때만 만든다. 결과 기록(`transferSucceeded`·`transferFailed`)도 거래 행을 먼저 잠가 한 트랜잭션으로 남기므로, 뒤에 온 Pod는 끝난 거래를 보고 멈춘다 |
| 그래도 새 시도가 들어오면 | V4 인덱스가 성공·진행 중인 송금 시도가 있는 거래에 두 번째 시도를 거절한다 |

### 복식부기 원장 (`R-PAY-15`)

`escrow_transactions`가 `COMPLETED`로 바뀌는 순간 DB 트리거가 같은 트랜잭션 안에서 분개를 남긴다. 애플리케이션 쓰기 코드는 분개를 직접 쓰지 않는다.

| 거래 | 차변 | 대변 |
| --- | --- | --- |
| 예치 `DEPOSIT` | `CUSTODY_CASH` 보관계좌 현금 | `ESCROW_LIABILITY` 예치금 반환 의무 |
| 지급 `RELEASE` | `ESCROW_LIABILITY` | `CUSTODY_CASH` |
| 환불 `REFUND` | `ESCROW_LIABILITY` | `CUSTODY_CASH` |
| 수수료 `FEE` 1줄 | `ESCROW_LIABILITY` | `FEE_REVENUE` 수수료 수익 |
| 수수료 `FEE` 2줄 | `FEE_CASH` 수수료계좌 현금 | `CUSTODY_CASH` |

- 한 행이 차변 1건과 대변 1건이고 금액이 같아 행마다 대차가 맞는다.
- `(transaction_id, line_no)`가 유일해 같은 거래를 두 번 분개하지 않는다. 완료 뒤 다시 `COMPLETED`로 갱신해도 새로 분개하지 않는다.
- 분개와 계정과목은 추가만 허용한다. UPDATE·DELETE·TRUNCATE는 트리거가 거절한다.
- 트리거는 거래를 거절하지 않는다. 분개 뒤 거래 상태나 금액이 바뀌면 운영 화면의 원장 대조가 불일치로 표시한다.
- 불변식: 보관계좌 현금 = 모든 계약의 반환 의무 합계, 수수료계좌 현금 = 수수료 수익.

잔액·정산 합계·대사 합계를 어느 기록에서 읽을지는 `app.ledger.read-source`(환경변수 `LEDGER_READ_SOURCE`)가 정한다.

| 값 | 읽는 곳 |
| --- | --- |
| `LEDGER` (기본) | 분개 합산 (`LedgerRepository`) |
| `TRANSACTIONS` | 완료 거래 합산. V3 이전과 같다. 원장에 문제가 생겼을 때 되돌리는 용도 |

운영 콘솔의 "복식부기 원장" 대조가 불일치이면 원인을 확인하는 동안 `TRANSACTIONS`로 되돌릴 수 있다. 값은 모든 Pod에 같게 준다.

JPA와 QueryDSL은 Escrow에서 사용하지 않는다. 접근 방식은 Spring JDBC(`JdbcClient`, `JdbcTemplate`)다.

애플리케이션은 2개 이상 Pod의 Active-Active 구성을 전제로 한다. 모든 Pod는 `APP_INSTANCE_ROLE=WRITER`이며, 이는 단일 Active 서버라는 의미가 아니다. 거래별 금융 호출 실행권은 DB의 조건부 갱신과 제약으로 한 Pod만 얻는다.

DB는 CloudNativePG Primary-Standby 구조다.

- 쓰기 및 최신성이 필요한 조회: `escrow-db-rw`
- 복제 지연을 허용하는 조회: `escrow-db-ro`
- 장애 승격과 실제 K3s 다중 Pod 시험은 배포 환경에서 별도 수행해야 한다.

상세 기준은 `escrow/docs/HIGH_AVAILABILITY.md`를 확인한다.

## 4. SSAFY 금융망 연동 상태

`SsafyFinanceClient`가 실제 SSAFY 교육 금융망을 호출한다. 설정 기본 URL은 `https://finopenapi.ssafy.io/ssafy`다.

확인된 항목:

- 사용자 등록과 계좌 발급
- 데모 자금 입금과 잔액 조회
- 1원 인증 요청
- 가상계좌 생성·입금 확인·보관계좌 정산
- 보관계좌에서 작업자 계좌 지급
- 보관계좌에서 의뢰인 계좌 환불
- 보관계좌와 수수료계좌 사이 소액 왕복 이체
- 거래내역 목록·단건 조회를 이용한 복구 경로

주의사항:

- 실호출 테스트는 승인된 테스트 계좌와 소액으로만 실행한다.
- 응답을 잃었다고 새 식별자로 즉시 재송금하지 않는다.
- 라이브 테스트는 환경변수/시스템 프로퍼티가 없으면 skip되도록 작성되어 있다.
- 문서의 과거 성공 기록은 현재 금융망 상태를 보장하지 않는다.
- 상세 요청 필드와 확인 결과는 `escrow/docs/FINANCE_MAPPING.md`를 확인한다.

## 5. 환경변수

### Escrow 애플리케이션 필수값

| 변수 | 목적 | 주입 위치 |
| --- | --- | --- |
| `APP_INSTANCE_ROLE` | 인스턴스 역할. 운영 Active Pod는 `WRITER` | ConfigMap/Deployment |
| `APP_PUBLIC_BASE_URL` | 외부 공개 주소. 운영 기대값은 `https://komit.site/escrow` | `escrow-config` |
| `DB_URL`, `DB_READ_URL` | Primary/Standby JDBC 주소 | Deployment |
| `DB_USERNAME`, `DB_PASSWORD` | Escrow DB 계정 | Secret |
| `CLIENT_API_KEY_HMAC_SECRET` | 서비스 API 키 HMAC | `escrow-security` |
| `SENSITIVE_DATA_HMAC_SECRET` | 민감정보 조회 HMAC | `escrow-security` |
| `FIELD_ENCRYPTION_KEY_BASE64` | 32바이트 필드 암호화 키의 Base64 | `escrow-security` |
| `OPERATOR_CLIENT_API_KEY` | 대사 등 운영 API 전용 키 | `escrow-security` |
| `ESCROW_BOOTSTRAP_CLIENT_API_KEY` | Backend가 Escrow를 호출할 원문 키 | `escrow-security` |
| `ESCROW_CLIENT_ONE_WON_AUTH_MESSAGE` | Backend 서비스의 1원 인증 문구 | `escrow-security` |
| `SSAFY_FINANCE_API_KEY` | SSAFY 금융망 앱 키 | `escrow-security` |
| `SSAFY_DEMAND_DEPOSIT_PRODUCT_NO` | 회원 계좌 발급 상품 고유번호 | `escrow-config` |
| `SSAFY_ESCROW_USER_KEY` | 보관·수수료 계좌 소유 Escrow 사용자 키 | `escrow-security` |
| `SSAFY_ESCROW_ACCOUNT_NO` | 예치금 보관계좌 번호 | `escrow-security` |
| `SSAFY_FEE_ACCOUNT_NO` | 플랫폼 수수료 계좌 번호 | `escrow-security` |
| `SSAFY_VIRTUAL_ACCOUNT_VALIDITY_DAYS` | 가상계좌 유효 일수. 현재 검증 범위 1~30 | `escrow-config` |
| `ESCROW_ADMIN_USERNAME` | 운영 콘솔 사용자명 | `escrow-config` |
| `ESCROW_ADMIN_PASSWORD_HASH` | `{bcrypt}...` 형식 비밀번호 해시 | `escrow-security` |

`ESCROW_ADMIN_PASSWORD_HASH`처럼 `$`가 포함된 값은 GitLab 변수의 **Expand variable reference를 끈다.** Protected 브랜치에서 배포한다면 변수의 Protected 범위도 맞아야 한다.

### Backend가 사용하는 값

| 환경 | `ESCROW_API_BASE_URL` | `ESCROW_CLIENT_API_KEY` |
| --- | --- | --- |
| Mock 개발 | `https://komit.site/mock/escrow` 또는 로컬 Mock 주소 | Mock 고정 키 |
| 실제 Escrow | 클러스터 내부 주소 `http://escrow:8081/api` | 실제 Backend 전용 키 |

Backend의 실제/Mock 전환은 GitLab CI/CD 변수와 로컬 `.env` 값으로 수행한다. Java 코드나 K3s YAML에 운영 URL과 키를 고정하지 않는다. 상세 계약은 `escrow/dosc/BACKEND_ESCROW_INTEGRATION_GUIDE.md`에 있다.

### 자격증명 보안 조치

개발 과정의 채팅이나 화면 공유에 노출된 SSAFY API 키, Backend client key, 관리자 원문 비밀번호는 새 담당자가 **교체 여부를 반드시 확인**해야 한다. Git 저장소나 인수인계 문서에 실제 값을 추가하지 않는다. 교체 시 GitLab 변수, K8s Secret, 관련 Pod rollout, `service_clients` bootstrap 갱신을 함께 확인한다.

## 6. CI/CD와 K3s 배포

`.gitlab-ci.yml`은 Escrow 변경 시 다음을 수행한다.

1. Gradle assemble
2. PostgreSQL 서비스와 함께 테스트 및 JaCoCo 리포트 생성
3. SonarQube 분석
4. 이미지 빌드·푸시
5. 배포 Job에서 필수 GitLab 변수를 SSH 대상에 전달
6. `deploy/k3s/deploy.sh`가 Secret/ConfigMap/Kustomize overlay를 생성해 적용
7. rollout 상태와 health를 확인

새 환경변수를 추가할 때는 다음 네 구간을 한 MR에서 모두 수정한다.

1. `application*.yml`
2. `.gitlab-ci.yml` 필수 변수 검사 및 원격 전달
3. `deploy/k3s/deploy.sh` Secret 또는 ConfigMap 생성
4. `deploy/k3s/base/escrow-deployment.yaml`의 `envFrom`/직접 env 주입

GitLab 변수만 등록하거나 application 설정만 추가하면 Pod가 CrashLoopBackOff에 빠질 수 있다. 과거 실패 원인은 필수 보안 변수 누락과 길이 검증 실패였다.

현재 SonarQube 원격 프로젝트의 품질 게이트는 기존 분석의 신규 커버리지 문제로 ERROR였던 이력이 있다. 로컬 테스트 성공과 원격 Quality Gate 통과를 같은 의미로 보지 말고, 새 분석 결과에서 신규 코드 이슈와 커버리지를 별도로 확인한다.

## 7. 로컬 실행과 검증

```powershell
Push-Location escrow
.\gradlew.bat test bootJar --no-daemon
Pop-Location

# Kustomize 렌더링
kubectl kustomize deploy/k3s/prod

# 변경 파일 공백/충돌 검사
git diff --check
```

최근 admin form-action 수정 직후 `test bootJar`는 성공했다. 다만 이는 운영 nginx와 실제 배포 이미지까지 검증한 결과가 아니다.

라이브 금융 테스트는 실제 자금 이동이 발생할 수 있으므로 테스트 클래스의 조건과 대상 계좌를 먼저 읽고 실행한다.

- `SsafyFinanceLiveTest`
- `SettlementFinanceLiveTest`

일반 회귀 테스트의 주요 파일:

- `DataAccessIntegrationTests`
- `MemberAccountServiceTest`
- `AccountAuthServiceTest`
- `EscrowDepositServiceTest`
- `EscrowSettlementServiceTest`
- `FinancialRecoveryServiceTest`
- `ServiceClientBootstrapTest`

## 8. 현재 작업 트리와 배포 시 주의

현재 브랜치 HEAD `8f41bae`에는 로그인/로그아웃 form action 수정을 포함한 운영 콘솔 관련 커밋이 들어 있다. 이 인수인계 문서를 제외한 admin 관련 미커밋 변경은 작성 시점에 없다. 다만 인수 시점에는 상태가 달라질 수 있으므로 아래를 다시 확인한다.

```powershell
git branch --show-current
git status --short
git diff -- escrow/src/main/java/com/a501/escrow/admin
git diff -- escrow/src/main/resources/templates/admin
git diff -- escrow/docs
```

커밋·푸시만으로 운영 반영을 단정하지 않는다. 해당 파이프라인에서 Escrow 이미지 태그가 새로 만들어졌는지, deploy Job이 그 태그를 받았는지, Deployment image와 Pod imageID가 바뀌었는지 확인한다.

```bash
sudo k3s kubectl -n komit get deploy escrow -o wide
sudo k3s kubectl -n komit rollout status deploy/escrow --timeout=300s
sudo k3s kubectl -n komit get pods -l app.kubernetes.io/name=escrow \
  -o custom-columns=NAME:.metadata.name,IMAGE:.spec.containers[0].image,IMAGE_ID:.status.containerStatuses[0].imageID,READY:.status.containerStatuses[0].ready
```

## 9. 남은 작업과 우선순위

1. 운영 콘솔 POST가 `/admin/login`으로 빠지는 원인을 실제 Network·nginx·Pod 기준으로 확정하고 수정한다.
2. 최신 이미지 배포 여부를 자동 검증할 수 있도록 commit SHA 또는 build version을 actuator/info와 로그에 노출하는 방안을 검토한다.
3. Escrow admin 경로의 프록시 통합 테스트를 CI 또는 배포 smoke test에 추가한다.
4. 실제 K3s에서 다중 Pod 동시 요청, RollingUpdate 중 멱등성, Pod 강제 종료 후 UNKNOWN 복구를 검증한다.
5. CloudNativePG Primary 장애 승격과 `-rw`/`-ro` 재연결을 시험한다.
6. 운영 감사 이벤트의 수동 UNKNOWN 확정 절차와 권한 검증을 구현한다.
7. Backend가 Mock에서 실제 Escrow로 전환하기 전에 연동 가이드의 요청·응답과 상태 번역을 계약 테스트로 고정한다.
8. 노출 가능성이 있는 모든 자격증명을 회전하고 이전 키가 더 이상 동작하지 않는지 확인한다.

## 10. 관련 문서 지도

| 문서 | 내용 |
| --- | --- |
| `escrow/dosc/BACKEND_ESCROW_INTEGRATION_GUIDE.md` | Backend 담당자에게 전달할 상세 API·환경변수·상태 번역 |
| `escrow/docs/FINANCE_MAPPING.md` | SSAFY 금융망 API와 Escrow 업무 매핑, 실호출 기록 |
| `escrow/docs/HIGH_AVAILABILITY.md` | Active-Active 앱과 Primary-Standby DB, 복구 기준 |
| `escrow/docs/ADMIN_CONSOLE.md` | 운영 콘솔 사용·보안 설정 |
| `escrow/docs/ERDCLOUD_SCHEMA.sql` | ERDCloud 참고용 스키마 |
| `escrow/src/main/resources/db/migration/` | 실제 적용되는 PostgreSQL Flyway 스키마 |
| `docs/RULES.md` | 금액·기한·불변식의 최상위 기준 |
| `docs/proposal.md` | 서비스 범위와 사용자 흐름 |

## 11. 인수 완료 체크리스트

- [ ] 현재 브랜치와 미커밋 변경을 확인했다.
- [ ] 실제 운영 Pod 이미지와 commit SHA를 확인했다.
- [ ] GitLab 변수와 K8s Secret/ConfigMap의 존재 여부를 값 노출 없이 확인했다.
- [ ] `/escrow/admin/login`의 GET, POST, redirect, cookie 경로를 Network 탭에서 확인했다.
- [ ] Escrow 전체 테스트와 bootJar를 실행했다.
- [ ] SonarQube의 최신 분석 결과를 확인했다.
- [ ] Mock API 계약과 실제 Escrow API 계약 차이를 Backend 담당자와 확인했다.
- [ ] 라이브 금융 테스트 계좌와 자금 이동 범위를 확인했다.
- [ ] 노출된 자격증명의 교체 여부를 결정하고 기록했다.
