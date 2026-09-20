# Backend–Escrow 연동 인수인계 가이드

> 대상: `service/` 백엔드 개발자  
> 기준일: 2026-09-20  
> 기준 구현: 현재 저장소의 `escrow/` 애플리케이션  
> 목적: 기존 Mock Server 호출을 실제 Escrow 서비스 호출로 교체할 때 필요한 계약, 데이터, 보안, 재시도 및 배포 조건을 한 문서에서 확인할 수 있게 한다.

---

## 1. 결론

현재 Escrow 서버에는 회원 금융계정 발급, 1원 인증, 계약 예치, 신고 동결·해제, 지급·수수료·환불, 조회 및 운영 대사 API가 구현되어 있다. 그러나 Backend가 Mock의 URL만 Escrow URL로 바꾸는 것만으로는 연동이 완료되지 않는다.

Backend에는 다음 작업이 반드시 필요하다.

1. Escrow 전용 HTTP Client와 요청·응답 DTO를 구현한다.
2. Backend 서비스용 `clientApiKey`를 발급하고 Escrow DB의 `service_clients`에 등록한다.
3. 모든 요청에 Escrow 공통 `Header`를 생성한다.
4. 회원별 SSAFY `userKey`, 계좌번호, 은행코드를 암호화하여 저장한다.
5. 쓰기 요청별 안정적인 `idempotencyKey`를 생성하고 재시도 시 동일한 값을 재사용한다.
6. HTTP 503 또는 `Q1000`, 거래 상태 `PROCESSING`/`UNKNOWN`을 최종 실패로 취급하지 않는다.
7. 가입, 계약, 신고, 정산 흐름의 로컬 DB 상태와 Escrow 상태를 연결한다.
8. 환경변수와 K3s Secret/ConfigMap 전달 경로를 추가한다.

즉, Escrow 자체 API는 실제 SSAFY 금융망을 호출하도록 구현되어 있지만, Backend 쪽에는 아직 이 계약을 사용하는 어댑터와 데이터 저장 구조가 필요하다.

---

## 2. 시스템 경계

### 2.1 Backend의 책임

- 사용자 인증과 인가
- 회원, 계약, 신고, 작업 및 서비스 도메인 상태 관리
- 사용자에게 노출할 금융계정 표시 정보 관리
- Escrow API 호출에 필요한 업무 식별자 및 멱등키 생성·보관
- Escrow 응답을 Backend 도메인 상태로 반영
- 일시 장애 시 동일 업무의 안전한 재호출

### 2.2 Escrow의 책임

- 서비스별 API 키 인증과 데이터 격리
- 민감정보 암호화 및 조회용 HMAC 저장
- SSAFY 금융망 호출
- 회원 금융 사용자와 입출금 계좌 발급
- 1원 인증 요청과 검증
- 가상계좌 기반 계약 예치
- 지급, 수수료, 환불
- 신고에 따른 동결과 해제
- 원장, 금융 호출 시도 및 대사 정보 관리
- Active-Active Pod 간 중복 금융 호출 방지

### 2.3 호출 방향

```text
사용자/프론트엔드
        |
        v
Backend(service)
  - 사용자 인증
  - 업무 권한 확인
  - Header/idempotencyKey 생성
        |
        v
Escrow
  - clientApiKey 인증
  - 서비스 소유권 확인
  - 원장/멱등성/동시성 제어
        |
        v
SSAFY 금융망
```

프론트엔드는 Escrow를 직접 호출하지 않는다. `clientApiKey`, 회원 `userKey`, 전체 계좌번호는 브라우저에 전달하거나 로그에 남기지 않는다.

---

## 3. 접속 주소와 환경변수

### 3.1 권장 Backend 환경변수

현재 Backend 개발 단계에서는 Mock Server를 호출한다.

```dotenv
# service/.env 및 현재 개발 배포의 GitLab CI/CD Variables
ESCROW_API_BASE_URL=https://komit.site/mock/escrow
ESCROW_CLIENT_API_KEY=mock-client-api-key
```

`ESCROW_CLIENT_API_KEY` 변수는 Mock 개발 중에도 필요하다. Backend는 환경과 관계없이 이 값을 요청 JSON의 `Header.clientApiKey`에 넣는다. Mock에서는 Mock이 인식하는 고정값 `mock-client-api-key`를 사용하고, 실제 Escrow에서는 별도로 발급한 `komit_be_...` 키를 사용한다.

실제 Escrow 통합 단계에서는 환경변수만 다음과 같이 교체한다.

```dotenv
# 실제 K3s Escrow 통합 시 GitLab CI/CD Variables
ESCROW_API_BASE_URL=http://escrow:8081/api
ESCROW_CLIENT_API_KEY=komit_be_<발급한 Backend 전용 랜덤 키>
```

K3s 내부 DNS를 사용하는 실제 통합 환경의 Base URL은 `http://escrow:8081/api`이다. Java Controller 경로가 `/api/v1/...`이므로 Client에서 아래처럼 조합한다.

```text
Base URL: http://escrow:8081/api
Endpoint: /v1/escrow/createMemberAccount
최종 URL: http://escrow:8081/api/v1/escrow/createMemberAccount
```

외부 프록시를 거쳐 호출한다면 실제 Ingress 설정의 prefix를 확인해야 한다. 서버 간 호출은 외부 TLS/Ingress 장애와 불필요한 네트워크 왕복을 줄이기 위해 K3s 내부 Service 주소를 우선한다.

환경별 값은 다음과 같다.

| 환경 | `ESCROW_API_BASE_URL` | `ESCROW_CLIENT_API_KEY` | 비고 |
| --- | --- | --- | --- |
| Backend 로컬 개발 | `https://komit.site/mock/escrow` | `mock-client-api-key` | `service/.env` 사용 |
| 현재 개발 K3s 배포 | `https://komit.site/mock/escrow` | `mock-client-api-key` | GitLab CI/CD Variables 사용 |
| 실제 Escrow 로컬 통합 | `http://localhost:8081/api` | `komit_be_...` | `service/.env` 값 교체 |
| 실제 Escrow K3s 통합 | `http://escrow:8081/api` | `komit_be_...` | GitLab CI/CD Variables 값 교체 |

Backend 코드에는 위 주소나 키를 하드코딩하지 않는다. Mock에서 실제 Escrow로 전환할 때 코드를 수정하는 것이 아니라 환경변수만 교체한다.

### 3.2 키의 구분

| 값 | 소유자 | 용도 | Backend 전달 여부 |
| --- | --- | --- | --- |
| `ESCROW_CLIENT_API_KEY` | Backend | Backend가 Escrow에 자신을 인증 | 필요 |
| `CLIENT_API_KEY_HMAC_SECRET` | Escrow | 위 API 키를 DB 조회용 HMAC으로 변환 | 전달 금지 |
| `OPERATOR_CLIENT_API_KEY` | Escrow 운영자 | 전체 서비스 대사 API 호출 | 일반 Backend에 전달 금지 |
| `SSAFY_FINANCE_API_KEY` | Escrow | Escrow가 SSAFY 금융망을 호출 | 전달 금지 |
| `SENSITIVE_DATA_HMAC_SECRET` | Escrow | 민감정보 lookup hash | 전달 금지 |
| `FIELD_ENCRYPTION_KEY_BASE64` | Escrow | Escrow DB 민감정보 암복호화 | 전달 금지 |

`ESCROW_CLIENT_API_KEY`는 위의 기존 Escrow 설정값들과 전혀 다른 별도 키다. 특히 `CLIENT_API_KEY_HMAC_SECRET` 자체를 Backend에 주면 안 된다.

### 3.3 로컬 환경

- Escrow 자체 SSAFY 설정은 `escrow/.env`에서 관리하고 Git에 커밋하지 않는다.
- Backend의 Escrow URL과 Client API Key는 `service/.env`에 둔다. `application-local.yml`이 이 파일을 선택적으로 읽는다.
- 현재 `service/.env`의 기본 개발 대상은 Mock Server이며 `mock-client-api-key`를 사용한다.
- 실제 로컬 Escrow 통합 시험 때만 URL을 `http://localhost:8081/api`, 키를 발급한 `komit_be_...` 값으로 바꾼다.
- `.env.example`에는 변수명과 안전한 예시만 기록하고 실제 키는 넣지 않는다.
- 테스트에서는 실제 키 대신 테스트 전용 키와 Stub/WireMock을 사용한다.

### 3.4 CI/CD와 K3s

Backend용 변수를 GitLab CI/CD Variables에 추가한 뒤 다음 전달 경로를 모두 확인해야 한다.

현재 개발 배포에서는 GitLab 변수에도 Mock 값을 등록한다.

```dotenv
ESCROW_API_BASE_URL=https://komit.site/mock/escrow
ESCROW_CLIENT_API_KEY=mock-client-api-key
```

실제 Escrow 통합을 시작할 때 동일한 GitLab 변수의 값만 실제 내부 주소와 `komit_be_...` 키로 교체한다. `ESCROW_API_BASE_URL`을 Java 코드, `deploy.sh`, K3s 운영 YAML에 직접 고정하지 않는다.

현재 배포 스크립트는 `ESCROW_CLIENT_API_KEY`를 Backend의 `service-auth` Secret에 넣고, 같은 값을 Escrow의 `escrow-security` Secret에 `ESCROW_BOOTSTRAP_CLIENT_API_KEY`라는 이름으로 전달한다. Escrow는 시작할 때 이 원문을 HMAC으로 변환하여 `Komit Backend` 서비스 레코드를 등록하거나 키를 갱신한다. 원문은 DB에 저장되지 않는다.

```text
GitLab CI/CD Variable
  -> 배포 Job 필수값 검사
  -> SSH 환경 전달
  -> deploy.sh의 Kustomize 생성
  -> service ConfigMap/Secret
  -> service Deployment envFrom
  -> Backend Pod 환경변수
```

권장 분류는 다음과 같다.

| 변수 | GitLab 표시 | Kubernetes 저장소 |
| --- | --- | --- |
| `ESCROW_API_BASE_URL` | Visible, Protected | `service-config` ConfigMap |
| `ESCROW_CLIENT_API_KEY` | Masked, Protected | `service-auth` Secret |

변수를 GitLab에 등록하는 것만으로는 충분하지 않다. 배포 스크립트가 그 값을 받아 Kubernetes 리소스를 생성하고 Pod에 주입하는 부분까지 있어야 한다.

---

## 4. Backend 서비스 등록

Escrow는 요청의 `clientApiKey` 원문을 DB에 저장하지 않는다. 아래 방식의 HMAC-SHA256 결과만 `service_clients.api_key_hash`에 저장한다.

```text
HMAC key   = CLIENT_API_KEY_HMAC_SECRET
domain     = "client-api-key"
input      = ESCROW_CLIENT_API_KEY 원문
output     = lowercase hex HMAC-SHA256
```

`service_clients`에는 최소한 다음 정보가 필요하다.

| 컬럼 | 의미 |
| --- | --- |
| `name` | 호출 서비스명. 예: `Komit Backend` |
| `api_key_hash` | 원문 API 키의 HMAC 해시 |
| `status` | 반드시 `ACTIVE` |
| `one_won_auth_message` | 1원 송금 시 사용자 거래내역에 표시할 인증 문구 |

주의사항:

- 실제 원문 API 키는 Backend의 `service-auth` Secret과 자동 등록을 수행하는 Escrow의 `escrow-security` Secret에만 존재한다.
- Escrow DB에는 해시만 저장한다.
- HMAC 생성 시 운영 Escrow와 동일한 `CLIENT_API_KEY_HMAC_SECRET`을 사용해야 한다.
- 운영자 키를 일반 업무 API에 사용하면 `E5001`로 거절된다.
- `status`가 `SUSPENDED` 또는 `CLOSED`인 서비스는 인증되지 않는다.
- 서비스 등록을 자동화하지 않았다면 배포 전 DBA/운영자가 명시적으로 등록해야 한다.

---

## 5. 공통 요청 Header 계약

모든 API 요청 Body에는 대문자 `Header` 필드가 필요하다.

```json
{
  "Header": {
    "apiName": "createEscrow",
    "transmissionDate": "20260920",
    "transmissionTime": "153012",
    "institutionCode": "00100",
    "fintechAppNo": "001",
    "apiServiceCode": "createEscrow",
    "institutionTransactionUniqueNo": "20260920153012000123",
    "clientApiKey": "<ESCROW_CLIENT_API_KEY>",
    "idempotencyKey": "escrow:create:contract-123",
    "userKey": "<회원 SSAFY userKey>"
  }
}
```

### 5.1 필드 규칙

| 필드 | 규칙 |
| --- | --- |
| `apiName` | 호출 endpoint의 마지막 이름과 정확히 일치 |
| `transmissionDate` | 호출 시점의 `yyyyMMdd` |
| `transmissionTime` | 호출 시점의 `HHmmss` |
| `institutionCode` | 고정값 `00100` |
| `fintechAppNo` | 고정값 `001` |
| `apiServiceCode` | `apiName`과 정확히 동일 |
| `institutionTransactionUniqueNo` | 정확히 숫자 20자리, 매 HTTP 호출마다 새 값 |
| `clientApiKey` | Backend 서비스 전용 원문 API 키 |
| `idempotencyKey` | 쓰기 API에서 필수, 최대 255자 |
| `userKey` | 회원 금융계정이 필요한 API에서 필수, 최대 40자 |

### 5.2 기관거래고유번호와 멱등키의 차이

두 필드는 수명과 목적이 다르다.

| 필드 | 동일 업무 재시도 시 | 목적 |
| --- | --- | --- |
| `institutionTransactionUniqueNo` | 새 값 생성 | 개별 HTTP 호출 추적 |
| `idempotencyKey` | 반드시 기존 값 재사용 | 동일 업무의 중복 처리 방지 |

예를 들어 계약 생성 요청의 응답을 받지 못해 재시도한다면 `idempotencyKey`는 그대로 두고 `institutionTransactionUniqueNo`만 새로 생성한다.

### 5.3 20자리 거래번호 생성 권장안

```text
yyyyMMddHHmmss + 6자리 sequence/random
```

예: `20260920153012000123`

멀티 Pod 환경에서 충돌하지 않아야 하므로 단순 메모리 증가값만 쓰지 않는다. DB sequence, Redis 원자 증가값 또는 충돌 가능성이 충분히 낮은 안전한 생성기를 사용한다.

### 5.4 API별 Header 요구사항

| API | `idempotencyKey` | `userKey` |
| --- | --- | --- |
| `createMemberAccount` | 필수 | 불필요 |
| `openAccountAuth` | 필수 | 필수 |
| `checkAuthCode` | 불필요 | 필수 |
| `inquireTransactionHistoryList` | 불필요 | 필수 |
| `createEscrow` | 필수 | 필수 |
| `cancelEscrow` | 필수 | 불필요 |
| `holdEscrow` | 필수 | 불필요 |
| `unholdEscrow` | 필수 | 불필요 |
| `releaseEscrow` | 필수 | 불필요 |
| `refundEscrow` | 필수 | 불필요 |
| `inquireEscrow` | 불필요 | 불필요 |
| `inquireEscrowList` | 불필요 | 불필요 |
| `inquireReconciliation` | 불필요 | 불필요, 운영자 키 필요 |

---

## 6. 공통 응답 계약

### 6.1 성공

성공 응답은 `Header`와 `REC`를 가진다.

```json
{
  "Header": {
    "responseCode": "H0000",
    "responseMessage": "정상처리 되었습니다.",
    "apiName": "createEscrow",
    "transmissionDate": "20260920",
    "transmissionTime": "153012",
    "institutionCode": "00100",
    "fintechAppNo": "001",
    "apiServiceCode": "createEscrow",
    "institutionTransactionUniqueNo": "20260920153012000123"
  },
  "REC": {}
}
```

HTTP 200과 `Header.responseCode=H0000`을 함께 확인한다. 자금 업무에서는 이것만으로 모든 하위 거래가 완료되었다고 가정하지 말고 `REC.status`와 상세 조회의 거래별 `status`도 확인한다.

### 6.2 오류

오류 응답에는 `REC`가 없고 `Header`만 있다.

```json
{
  "Header": {
    "responseCode": "E5002",
    "responseMessage": "멱등키가 필요합니다.",
    "apiName": "createEscrow",
    "transmissionDate": "20260920",
    "transmissionTime": "153012",
    "institutionCode": "00100",
    "fintechAppNo": "001",
    "apiServiceCode": "createEscrow",
    "institutionTransactionUniqueNo": "20260920153012000123"
  }
}
```

오류 역직렬화 DTO는 성공 DTO와 분리하거나, `REC`를 nullable로 처리해야 한다.

---

## 7. API 상세 계약

모든 API는 `POST`다. 아래 경로는 `/api`가 이미 Base URL에 포함된 경우를 기준으로 한다.

### 7.1 회원 금융계정 발급

`POST /v1/escrow/createMemberAccount`

SSAFY 금융망에 사용자를 등록하고, 입출금 계좌를 개설하고, 테스트용 시드머니를 입금한다. 가입 직후 한 번 호출하되 결과 불명 시 같은 멱등키로 재호출한다.

요청:

```json
{
  "Header": {
    "apiName": "createMemberAccount",
    "apiServiceCode": "createMemberAccount",
    "idempotencyKey": "member-account:user-123",
    "userKey": null
  },
  "userId": "member-123@example.com"
}
```

`userId`는 이메일 형식이며 최대 40자다. 실제 회원 이메일을 그대로 쓸 수 있지만, SSAFY 금융망에서 유일해야 하므로 환경별 중복과 탈퇴 후 재가입 정책을 고려한다.

응답 `REC`:

```json
{
  "userId": "member-123@example.com",
  "userKey": "4fb71aca-2122-4f5b-8e27-5ddbf6841100",
  "bankCode": "001",
  "accountNo": "0015337596722826"
}
```

Backend는 반환된 `userKey`와 `accountNo`를 암호화하여 저장해야 한다. API 응답 또는 로그에 재노출하지 않는다.

### 7.2 1원 인증 요청

`POST /v1/edu/accountAuth/openAccountAuth`

```json
{
  "Header": {
    "apiName": "openAccountAuth",
    "apiServiceCode": "openAccountAuth",
    "idempotencyKey": "account-auth:user-123:attempt-1",
    "userKey": "<회원 userKey>"
  },
  "accountNo": "0015337596722826"
}
```

응답:

```json
{
  "transactionUniqueNo": "금융망 인증 거래번호",
  "accountNo": "0015337596722826",
  "expiresAt": "만료시각"
}
```

`transactionUniqueNo`는 인증 확인에 필요하므로 인증 세션에 저장한다. 동일 인증 업무 재호출에는 같은 `idempotencyKey`를 사용한다.

### 7.3 1원 인증번호 확인

`POST /v1/edu/accountAuth/checkAuthCode`

```json
{
  "Header": {
    "apiName": "checkAuthCode",
    "apiServiceCode": "checkAuthCode",
    "idempotencyKey": null,
    "userKey": "<회원 userKey>"
  },
  "transactionUniqueNo": "<openAccountAuth 응답값>",
  "accountNo": "0015337596722826",
  "authCode": "1234"
}
```

`authCode`는 숫자 4자리다. 응답 필드는 `status`, `transactionUniqueNo`, `accountNo`다. Backend는 `status`가 실제 성공 상태인지 확인한 뒤에만 계좌 인증 완료로 저장한다.

### 7.4 계좌 거래내역 조회

`POST /v1/edu/demandDeposit/inquireTransactionHistoryList`

```json
{
  "Header": {
    "apiName": "inquireTransactionHistoryList",
    "apiServiceCode": "inquireTransactionHistoryList",
    "userKey": "<회원 userKey>"
  },
  "accountNo": "0015337596722826",
  "startDate": "20260901",
  "endDate": "20260920",
  "transactionType": "A",
  "orderByType": "DESC"
}
```

- `transactionType`: `M`, `D`, `A` 중 하나
- `orderByType`: 생략 가능, 지정 시 `ASC` 또는 `DESC`
- 응답: `totalCount`, `list[]`
- 각 내역: 거래번호, 일자, 시각, 유형, 유형명, 거래금액, 거래 후 잔액, 거래요약

### 7.5 계약 생성 및 예치

`POST /v1/escrow/createEscrow`

```json
{
  "Header": {
    "apiName": "createEscrow",
    "apiServiceCode": "createEscrow",
    "idempotencyKey": "escrow:create:contract-123",
    "userKey": "<의뢰인 userKey>"
  },
  "contractId": "CONTRACT-123",
  "contractType": "OUTSOURCING",
  "depositorName": "홍길동",
  "amount": 650000,
  "withdrawalBankCode": "001",
  "withdrawalAccountNo": "0015337596722826"
}
```

검증 규칙:

- `contractId`: 1~100자, 서비스 내 계약 식별자로 사용
- `contractType`: 1~30자
- `depositorName`: 1~20자
- `amount`: 1 이상 `9,999,999,999,999,999,999` 이하의 정수
- 은행코드: 숫자 3자리
- 계좌번호: 숫자 16자리
- Header의 `userKey`, 은행코드와 계좌번호는 앞서 발급 완료된 회원계좌와 모두 일치해야 함

응답:

```json
{
  "escrowId": 123,
  "status": "ACTIVE",
  "depositedAmount": 650000,
  "balance": 650000,
  "transactionId": 456
}
```

`escrowId`는 Backend 계약에 저장한다. 같은 계약에 새 멱등키를 만들어 중복 요청하지 않는다.

### 7.6 계약 취소

`POST /v1/escrow/cancelEscrow`

```json
{
  "Header": {
    "apiName": "cancelEscrow",
    "apiServiceCode": "cancelEscrow",
    "idempotencyKey": "escrow:cancel:contract-123"
  },
  "escrowId": 123
}
```

응답은 `escrowId`, `status`다. 이미 자금 이동이 시작되었거나 예치 결과가 불명확한 상태에서는 취소가 거절될 수 있다.

### 7.7 신고 동결

`POST /v1/escrow/holdEscrow`

```json
{
  "Header": {
    "apiName": "holdEscrow",
    "apiServiceCode": "holdEscrow",
    "idempotencyKey": "escrow:hold:report-77"
  },
  "escrowId": 123,
  "reportId": "REPORT-77"
}
```

응답은 `escrowId`, `isHeld`다. 성공 시 `isHeld=true`를 기대한다.

### 7.8 신고 동결 해제

`POST /v1/escrow/unholdEscrow`

요청 형식은 동결과 같고 `apiName`은 `unholdEscrow`, 별도 멱등키를 사용한다. 동일한 `reportId`를 사용해야 한다. 성공 시 `isHeld=false`를 기대한다.

### 7.9 작업대금 지급 및 수수료 분리

`POST /v1/escrow/releaseEscrow`

```json
{
  "Header": {
    "apiName": "releaseEscrow",
    "apiServiceCode": "releaseEscrow",
    "idempotencyKey": "escrow:release:settlement-900"
  },
  "escrowId": 123,
  "referenceId": "SETTLEMENT:900",
  "amount": 400000,
  "recipientBankCode": "001",
  "recipientAccountNo": "0015337596722999",
  "recipientName": "김작업자"
}
```

규칙:

- `referenceId`는 `SETTLEMENT:`로 시작하고 최대 100자다.
- `amount`는 수수료 포함 총 승인 금액이다.
- Escrow가 정책에 따라 작업자 실수령액과 수수료를 분리한다.
- 수취 계좌 예금주 검증 후 이체한다.
- 동결 상태이거나 선행 `PROCESSING`/`UNKNOWN` 정산이 있으면 새 정산이 제한된다.

응답:

```json
{
  "transactionId": 1001,
  "releasedAmount": 400000,
  "feeAmount": 4000,
  "netAmount": 396000,
  "balance": 250000,
  "status": "COMPLETED"
}
```

`releasedAmount`는 승인 총액이며 `netAmount`와 `feeAmount`의 합이다.

### 7.10 잔액 환불

`POST /v1/escrow/refundEscrow`

```json
{
  "Header": {
    "apiName": "refundEscrow",
    "apiServiceCode": "refundEscrow",
    "idempotencyKey": "escrow:refund:settlement-900"
  },
  "escrowId": 123,
  "amount": 250000,
  "refundBankCode": "001",
  "refundAccountNo": "0015337596722826",
  "refundHolderName": "홍길동",
  "referenceId": "SETTLEMENT:900"
}
```

환불은 최초 예치 출금계좌와 일치해야 하며 부분 환불은 허용되지 않는다. 응답은 `transactionId`, `refundedAmount`, `balance`, `status`다.

### 7.11 계약 상세 조회

`POST /v1/escrow/inquireEscrow`

```json
{
  "Header": {
    "apiName": "inquireEscrow",
    "apiServiceCode": "inquireEscrow"
  },
  "escrowId": 123,
  "contractId": "CONTRACT-123"
}
```

`contractId`는 생략 가능하지만 제공하면 `escrowId`와 일치해야 한다. 응답은 다음 정보를 포함한다.

- `escrowId`, `contractId`, 계약 `status`
- `depositedAmount`, `releasedAmount`, `feeAmount`, `refundedAmount`, `balance`
- `isHeld`
- `transactions[]`: `transactionId`, `transactionType`, `amount`, `status`, `referenceId`

이 API는 결과 불명 상태를 복구하거나 화면에 최종 상태를 표시할 때 기준 조회로 사용한다.

### 7.12 계약 목록 조회

`POST /v1/escrow/inquireEscrowList`

```json
{
  "Header": {
    "apiName": "inquireEscrowList",
    "apiServiceCode": "inquireEscrowList"
  },
  "contractIds": ["CONTRACT-123", "CONTRACT-124"],
  "status": "ACTIVE"
}
```

- `contractIds`: 생략 가능, 최대 100개, 각 값 1~100자
- `status`: 생략 가능. 허용값은 `CREATED`, `DEPOSIT_PENDING`, `ACTIVE`, `COMPLETED`, `REFUNDED`, `CANCELLED`
- 응답: `totalCount`, `list[]`
- 목록 항목: `escrowId`, `contractId`, `status`, `balance`, `isHeld`
- 결과가 없으면 정상 응답이며 `totalCount=0`, `list=[]`다.

### 7.13 운영 대사 조회

`POST /v1/escrow/inquireReconciliation`

이 API는 일반 Backend 업무용이 아니다. `Header.clientApiKey`에 `OPERATOR_CLIENT_API_KEY`가 있어야 하며 전체 서비스가 공유하는 보관계좌의 금융망 잔액과 Escrow 원장을 비교한다.

```json
{
  "Header": {
    "apiName": "inquireReconciliation",
    "apiServiceCode": "inquireReconciliation",
    "clientApiKey": "<OPERATOR_CLIENT_API_KEY>"
  },
  "days": 7
}
```

`days`는 생략 시 7, 허용 범위는 1~30이다. 일반 Backend Pod에 운영자 키를 배포하지 않는 것을 권장한다.

---

## 8. 상태 해석

### 8.1 계약 상태

| 상태 | 의미 | Backend 처리 |
| --- | --- | --- |
| `CREATED` | 계약 원장 생성 | 예치 완료로 표시하지 않음 |
| `DEPOSIT_PENDING` | 예치 처리 또는 확인 중 | 조회/동일 멱등키 재호출 |
| `ACTIVE` | 예치 완료, 지급/환불 가능 | 정상 활성 계약 |
| `COMPLETED` | 정산 완료 | 추가 자금 이동 차단 |
| `REFUNDED` | 환불 완료 | 추가 자금 이동 차단 |
| `CANCELLED` | 예치 전 취소 | 종료 상태 |

### 8.2 거래 상태

| 상태 | 의미 | Backend 처리 |
| --- | --- | --- |
| `COMPLETED` | 금융망 및 원장 반영 완료 | 성공 확정 |
| `PROCESSING` | 다른 Pod 또는 현재 작업이 처리 중 | 새 업무 생성 금지, 조회/재시도 |
| `UNKNOWN` | 금융 요청 결과 불명 | 실패 확정 금지, 같은 멱등키로 복구 |
| `FAILED` | 실패가 확인됨 | 오류 표시 및 정책에 따른 재시도 |

`PROCESSING`과 `UNKNOWN`은 사용자에게 “실패”로 보여 새 지급 버튼을 활성화하면 안 된다.

---

## 9. 멱등성 및 재시도 규칙

### 9.1 멱등키 저장 원칙

멱등키를 HTTP 요청 직전에 임의 생성만 하지 말고 Backend DB 업무 레코드에 먼저 저장한다.

권장 예시:

| 업무 | 멱등키 예시 |
| --- | --- |
| 회원계좌 발급 | `member-account:{memberId}` |
| 1원 인증 요청 | `account-auth:{memberId}:{verificationId}` |
| 예치 | `escrow:create:{contractId}` |
| 취소 | `escrow:cancel:{contractId}` |
| 동결 | `escrow:hold:{reportId}` |
| 해제 | `escrow:unhold:{reportId}` |
| 지급 | `escrow:release:{settlementId}` |
| 환불 | `escrow:refund:{settlementId}` |

문자열 형태는 예시이며 중요한 것은 동일 논리 업무에서 값이 변하지 않는다는 점이다.

### 9.2 재호출 판단표

| 상황 | 처리 |
| --- | --- |
| 연결 전에 실패가 명확함 | 동일 멱등키, 새 거래번호로 재호출 가능 |
| 타임아웃/연결 종료/HTTP 503/`Q1000` | 결과 불명. 동일 멱등키로 재호출 또는 상세 조회 |
| HTTP 200 + `COMPLETED` | 성공 확정, 재호출 불필요 |
| HTTP 200 + `PROCESSING`/`UNKNOWN` | 상세 조회 또는 동일 멱등키 재호출 |
| `E5003` | 같은 멱등키에 다른 요청 Body 사용. 자동 재시도 금지 |
| 명시적 유효성/업무 오류 | 요청을 수정하기 전 자동 재시도 금지 |

### 9.3 금지 사항

- 타임아웃 후 새 멱등키로 다시 송금하지 않는다.
- `Q1000`을 곧바로 최종 실패로 DB에 확정하지 않는다.
- 재시도할 때 금액, 수취계좌, 계약 ID 등 업무 본문을 바꾸지 않는다.
- 사용자가 버튼을 여러 번 눌렀다는 이유로 새 정산 ID를 계속 만들지 않는다.

---

## 10. 오류 코드와 Backend 매핑

### 10.1 공통 Header 오류

| 코드 | 의미 | Backend 조치 |
| --- | --- | --- |
| `H1000` | Header 누락/유효성 오류 | Client 구현 오류, 자동 재시도 금지 |
| `H1001` | API 이름 불일치 | endpoint와 `apiName` 수정 |
| `H1002` | 전송일자 오류 | `yyyyMMdd` 생성 로직 수정 |
| `H1003` | 전송시각 오류 | `HHmmss` 생성 로직 수정 |
| `H1004` | 기관코드 오류 | `00100` 사용 |
| `H1005` | 핀테크 앱 번호 오류 | `001` 사용 |
| `H1006` | API 서비스코드 오류 | `apiName`과 동일하게 설정 |
| `H1007` | 기관거래번호 중복 | 새 20자리 거래번호로 재호출, 멱등키는 유지 |
| `H1009` | userKey 오류 | 저장값 및 API 요구조건 확인 |
| `H1010` | 기관거래번호 형식 오류 | 숫자 20자리로 수정 |

### 10.2 인증·멱등·입력 오류

| 코드 | 의미 | Backend 조치 |
| --- | --- | --- |
| `E5001` | Client API Key/권한 오류 | Secret 및 `service_clients` 상태 확인 |
| `E5002` | 멱등키 누락 | 쓰기 요청의 안정적 멱등키 추가 |
| `E5003` | 같은 멱등키의 요청 지문 불일치 | 기존 요청을 조회하고 새 본문 재사용 금지 |
| `Q1001` | Body 타입/형식 오류 | DTO와 validation 수정 |

### 10.3 주요 업무 오류

| 코드 | 의미/대표 상황 | Backend 처리 방향 |
| --- | --- | --- |
| `E5004` | Escrow를 찾지 못함 | 소유 서비스와 ID 확인 |
| `E5005` | 지급 가능 상태 아님 | 계약 상태 재조회 |
| `E5006` | 예치 금액 불일치 | 계약 금액과 요청 확인 |
| `E5007` | 동결 상태로 업무 불가 | 신고 처리 후 해제 필요 |
| `E5008` | 잔액 부족 등 지급 불가 | 금액 재계산 |
| `E5009` | 지급 수취 정보 문제 | 수취 정보 확인 |
| `E5010` | 정산 충돌/완료 상태 | 기존 정산 조회 |
| `E5011` | 1원 인증 만료 | 새 인증 세션 생성 |
| `E5012` | 인증 요청 없음/소유정보 불일치 | 인증 세션과 회원 확인 |
| `E5013` | 부분 환불 금지 | 남은 전액으로 요청 |
| `E5014` | 신고 동결/해제 충돌 | reportId 및 현재 동결 상태 확인 |
| `E5015` | 예치 미확정 상태 취소 불가 | 같은 멱등키 복구 후 판단 |
| `E5016` | 예금주 불일치 | 수취/환불 예금주 확인 |
| `E5017` | 회원 발급계좌 불일치 | 저장된 회원 금융계정 사용 |
| `Q1000` | 금융망/처리 결과 불명 또는 일시 장애 | 최종 실패 금지, 조회 및 같은 멱등키 재시도 |

사용자 응답에는 내부 코드와 안전한 메시지만 매핑하고 API 키, userKey, 전체 계좌번호, 내부 예외 stack trace는 노출하지 않는다.

---

## 11. Backend 데이터 모델 권장사항

기존 `User` 또는 `Account` 엔티티에 최소 다음 정보를 보관해야 한다.

| 필드 | 저장 방식 | 용도 |
| --- | --- | --- |
| Escrow 금융계정 발급 상태 | 평문 enum | 가입 완료/복구 판단 |
| SSAFY `userKey` | 애플리케이션 암호화 | 회원 금융 API 호출 |
| 계좌번호 | 애플리케이션 암호화 | 예치/인증/환불 |
| 은행코드 | 평문 가능 | 요청 조립 |
| 마스킹 계좌번호 | 평문 가능 | 사용자 화면 표시 |
| 발급 멱등키 | 평문 식별자 | 결과 불명 복구 |
| 1원 인증 상태/거래번호/만료시각 | 민감도에 맞춰 보호 | 인증 확인 |

계약 또는 별도 금융연동 테이블에는 다음을 둔다.

- Escrow의 `escrowId`
- 예치 멱등키
- Escrow 계약 상태의 마지막 확인값과 확인 시각
- 지급/환불별 settlement ID, referenceId, 멱등키
- 마지막 오류 코드
- 결과 불명 여부

암호화 키는 DB와 분리된 Secret에서 관리한다. 검색이 필요한 민감값은 암호문 비교가 아니라 별도의 HMAC lookup hash를 고려한다.

---

## 12. 권장 업무 흐름

### 12.1 회원가입

```text
1. Backend 사용자 레코드 생성 또는 가입 진행 상태 저장
2. member-account:{memberId} 멱등키 저장
3. createMemberAccount 호출
4. 성공 시 userKey/accountNo/bankCode 암호화 저장
5. 503/Q1000이면 가입을 최종 실패 처리하지 않고 동일 멱등키로 복구
6. 금융계정 발급 완료 후 가입 완료 상태 전환
```

외부 호출을 긴 DB 트랜잭션 안에서 수행하지 않는다. “요청 준비 저장 → 트랜잭션 종료 → 외부 호출 → 결과 저장” 형태를 권장한다.

### 12.2 1원 인증

```text
1. 인증 세션과 멱등키 생성
2. openAccountAuth 호출
3. transactionUniqueNo, expiresAt 저장
4. 사용자가 4자리 코드 입력
5. checkAuthCode 호출
6. 성공 status 확인 후 인증 완료 저장
```

계좌 발급과 1원 인증 요청은 서로 다른 API이며 자동으로 같은 단계에서 완료되는 것이 아니다.

### 12.3 계약 생성과 예치

```text
1. Backend 계약 및 예치 요청 상태 저장
2. 계약별 고정 멱등키 저장
3. 회원의 복호화된 userKey/계좌정보로 createEscrow 호출
4. 성공 시 escrowId와 상태 저장
5. 결과 불명이면 동일 멱등키 재호출 또는 inquireEscrow 조회
6. ACTIVE 확인 후에만 Backend 계약을 예치 완료로 전환
```

### 12.4 신고 동결/해제

```text
신고 접수 -> holdEscrow -> isHeld=true 확인 -> 지급 차단 상태 반영
신고 해결 -> unholdEscrow(같은 reportId) -> isHeld=false 확인 -> 정책에 따라 지급 재개
```

Backend DB에서 지급 가능으로 보이더라도 Escrow가 동결되어 있으면 지급은 실패한다. 최종 자금 상태의 기준은 Escrow 조회 결과다.

### 12.5 지급과 환불

```text
1. settlement 레코드와 referenceId=SETTLEMENT:{id} 생성
2. 지급 멱등키 저장
3. releaseEscrow 호출
4. COMPLETED 확인
5. 남은 잔액을 환불해야 하면 별도 환불 멱등키로 refundEscrow 호출
6. inquireEscrow로 거래별 완료 및 balance=0 여부 확인
7. 모든 필요한 거래 완료 후 Backend 계약 종료
```

지급과 환불은 서로 다른 금융 업무이므로 멱등키도 서로 달라야 한다.

---

## 13. HTTP Client 구현 권장사항

### 13.1 구성 요소

- `EscrowProperties`: Base URL, Client API Key, timeout
- `EscrowHeaderFactory`: 공통 Header 생성
- `EscrowClient`: endpoint별 호출
- `EscrowErrorDecoder`: HTTP/responseCode 해석
- `EscrowIntegrationService`: 도메인 레코드와 멱등키를 연결
- 민감정보 암복호화 Component

### 13.2 Timeout과 재시도

- Connection timeout은 짧고 명확하게 설정한다.
- Read timeout은 SSAFY 금융망 왕복 시간을 고려하되 무제한으로 두지 않는다.
- HTTP Client 라이브러리의 무조건 자동 재시도 기능은 끈다.
- 재시도는 업무 계층에서 멱등키를 확보한 뒤 수행한다.
- 4xx 업무 오류는 자동 재시도하지 않는다.
- 503/네트워크 오류는 결과 불명으로 기록하고 제한된 backoff 후 동일 멱등키로 재시도한다.

### 13.3 로그

로그에 남겨도 되는 값:

- `apiName`
- `institutionTransactionUniqueNo`
- `idempotencyKey` 또는 그 해시
- Backend의 contract/report/settlement ID
- HTTP status와 Escrow `responseCode`

로그에 남기면 안 되는 값:

- `clientApiKey`
- `userKey`
- 전체 계좌번호
- 암호화 키/HMAC secret
- 1원 인증번호

---

## 14. Mock에서 실제 Escrow로 교체할 때 확인할 차이

### 14.1 전환 방법

Backend Client, DTO, Header 생성 및 오류 처리 코드는 Mock 개발 단계부터 실제 Escrow 계약과 동일하게 작성한다. 전환 시 코드나 배포 YAML에 URL을 새로 하드코딩하지 않고 GitLab CI/CD Variables만 변경한다.

```dotenv
# 전환 전: Mock
ESCROW_API_BASE_URL=https://komit.site/mock/escrow
ESCROW_CLIENT_API_KEY=mock-client-api-key

# 전환 후: 실제 Escrow
ESCROW_API_BASE_URL=http://escrow:8081/api
ESCROW_CLIENT_API_KEY=komit_be_<발급한 Backend 전용 랜덤 키>
```

실제 키를 주입하면 Escrow 시작 시 해당 키의 HMAC 해시가 `service_clients`에 자동 등록된다. Mock을 사용하는 동안에는 이 DB 등록 결과가 호출에 사용되지 않지만, 향후 실제 전환을 위해 생성한 `komit_be_...` 키와 자동 등록 기능은 유지한다.

### 14.2 실제 Escrow에서 추가로 확인할 사항

1. 실제 Escrow는 DB에 등록된 서비스 API 키를 요구한다.
2. 실제 Escrow는 `apiName`과 endpoint, `apiServiceCode`의 일치를 엄격히 검사한다.
3. 쓰기 API는 실제 DB 멱등성과 요청 지문을 검사한다.
4. 실제 금융망 지연과 결과 불명(`Q1000`)이 존재한다.
5. 회원 발급계좌와 예치 요청의 userKey/은행/계좌번호 일치를 검증한다.
6. 서비스별 소유권이 분리되어 다른 서비스의 `escrowId`를 조회할 수 없다.
7. 수취 계좌 예금주 검증, 동결, 선행 거래 상태가 지급에 영향을 준다.
8. 금액은 JavaScript 안전 정수 범위를 넘을 수 있으므로 프론트까지 전달할 때 정밀도 손실에 주의한다.

Mock 테스트 통과는 DTO의 기본 형태를 확인한 것이며, 실제 서비스의 보안·동시성·금융망 복구 동작을 완전히 대체하지 않는다.

---

## 15. 테스트 전략

### 15.1 단위 테스트

- API별 `Header` 생성값
- 쓰기/읽기 API별 idempotencyKey와 userKey 포함 여부
- 성공/오류 응답 역직렬화
- 금액, 계좌번호, 은행코드 validation
- 오류 코드의 Backend 예외 변환
- 로그 마스킹

### 15.2 통합 테스트

- Mock 또는 WireMock으로 200/H0000
- 400/E5003 요청 지문 충돌
- 503/Q1000 후 동일 멱등키 재호출
- 네트워크 timeout 후 상세 조회 복구
- `PROCESSING`/`UNKNOWN` 동안 새 지급 차단
- 동일 사용자/계약 요청이 동시에 들어왔을 때 한 업무 ID만 사용

### 15.3 배포 후 Smoke Test

테스트 전용 사용자와 계약으로 아래 순서를 수행한다.

1. Backend Pod에서 `escrow:8081` DNS와 포트 연결 확인
2. 유효하지 않은 Client API Key로 `E5001` 확인
3. 테스트 서비스 키로 회원계좌 발급
4. 1원 인증 요청 및 확인
5. 소액 계약 생성/예치
6. 상세 조회에서 `ACTIVE`, 예치 거래 `COMPLETED` 확인
7. 동결 후 지급 차단 확인
8. 해제 후 지급
9. 잔액 환불
10. 상세 조회에서 거래 금액 합계와 잔액 확인

실제 SSAFY 금융망을 사용하는 테스트는 돈과 금융망 테스트 데이터를 생성하므로 전용 계정, 전용 계약 ID 및 실행 기록을 사용한다.

---

## 16. 구현 순서

다음 순서가 의존성과 위험을 가장 적게 만든다.

1. Backend DB migration: 금융계정, 멱등키, Escrow ID, 연동 상태 필드
2. 민감정보 암호화 저장 계층
3. `EscrowProperties`와 Secret/ConfigMap/CI 변수 전달
4. 공통 Header Factory와 거래번호 생성기
5. 공통 응답 및 오류 Decoder
6. 회원계좌 발급 연동
7. 1원 인증 연동
8. 계약 생성·예치 연동
9. 상세/목록 조회 연동
10. 신고 동결·해제 연동
11. 지급·수수료·환불 연동
12. 결과 불명 복구 Worker 또는 재처리 절차
13. 통합 테스트 및 실제 SSAFY Smoke Test

---

## 17. 완료 체크리스트

### 보안

- [ ] Backend 전용 API 키를 충분히 무작위로 생성했다.
- [ ] 원문은 Backend Secret에만 저장했다.
- [ ] Escrow DB에는 HMAC 해시와 `ACTIVE` 서비스 레코드를 등록했다.
- [ ] userKey와 전체 계좌번호를 암호화 저장한다.
- [ ] 민감값이 로그, APM, 오류 응답에 노출되지 않는다.
- [ ] 운영자 키를 일반 Backend에 배포하지 않았다.

### 계약

- [ ] 모든 요청 Body의 필드명이 대소문자까지 일치한다.
- [ ] `Header`는 대문자 H다.
- [ ] `apiName`과 `apiServiceCode`가 endpoint 이름과 같다.
- [ ] 기관코드는 `00100`, 앱 번호는 `001`이다.
- [ ] 기관거래번호가 숫자 20자리이며 호출마다 유일하다.
- [ ] 쓰기 API에 안정적인 멱등키가 있다.
- [ ] API별 userKey 요구 여부를 지켰다.

### 업무

- [ ] 회원가입 결과의 userKey, 계좌번호, 은행코드를 저장한다.
- [ ] 1원 인증 요청과 확인을 별도 단계로 처리한다.
- [ ] 계약에 `escrowId`를 저장한다.
- [ ] 신고 `reportId`가 동결과 해제에서 일치한다.
- [ ] 정산 `referenceId`가 `SETTLEMENT:` 형식이다.
- [ ] 지급과 환불은 별도 멱등키를 사용한다.
- [ ] `PROCESSING`/`UNKNOWN` 중 새 자금 업무를 차단한다.

### 복구

- [ ] 503, timeout, `Q1000`을 최종 실패로 확정하지 않는다.
- [ ] 동일 업무 재시도에 동일 멱등키를 사용한다.
- [ ] 재호출마다 새 기관거래번호를 생성한다.
- [ ] 상세 조회로 계약·거래 상태를 복구할 수 있다.
- [ ] 자동 재시도 횟수와 backoff가 제한되어 있다.
- [ ] 수동 운영 복구 절차와 추적 ID가 있다.

### 배포

- [ ] `ESCROW_API_BASE_URL`이 ConfigMap에 전달된다.
- [ ] `ESCROW_CLIENT_API_KEY`가 Secret에 전달된다.
- [ ] service Deployment가 두 값을 환경변수로 주입한다.
- [ ] NetworkPolicy가 있다면 service → escrow 호출을 허용한다.
- [ ] readiness 완료 전 트래픽을 받지 않는다.
- [ ] 배포 후 K3s 내부 DNS 기반 Smoke Test를 통과했다.

---

## 18. 관련 문서 및 코드

- 실제 금융망 매핑: `escrow/docs/FINANCE_MAPPING.md`
- Escrow 구현 인수인계: `escrow/docs/IMPLEMENTATION_HANDOFF.md`
- 고가용성 구조: `escrow/docs/HIGH_AVAILABILITY.md`
- DB 스키마: `escrow/src/main/resources/db/migration/V1__create_escrow_schema.sql`
- 공통 Header: `escrow/src/main/java/com/a501/escrow/common/api/EscrowRequestHeader.java`
- Header 검증: `escrow/src/main/java/com/a501/escrow/common/security/EscrowHeaderValidator.java`
- 내부 API 인증: `escrow/src/main/java/com/a501/escrow/common/security/InternalApiSecurity.java`
- API DTO: `escrow/src/main/java/com/a501/escrow/domain/**/dto/`
- API Controller: `escrow/src/main/java/com/a501/escrow/domain/**/controller/`

이 문서와 코드가 충돌하면 배포된 코드와 OpenAPI(`/docs`, `/v3/api-docs`)를 우선 확인하고, 차이를 같은 변경사항에서 문서에도 반영한다.
