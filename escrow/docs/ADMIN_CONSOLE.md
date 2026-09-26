# Escrow 운영 콘솔

Escrow 운영 콘솔은 별도 SPA가 아니라 Escrow Spring Boot 애플리케이션이 Thymeleaf로 렌더링하는 조회 전용 페이지다. 운영 자격증명과 금융망 키를 브라우저에 전달하지 않고 서버에서 DB·금융망 정보를 조합한다.

## 접속 주소

- 로컬: `http://localhost:8081/admin/login`
- 운영: `https://komit.site/escrow/admin/login`

`https://komit.site/escrow`는 nginx에서 `/escrow/`로 정규화되고, Escrow 루트는 `/admin`으로 이동한다. 따라서 정상 구성에서는 Base URL로 접속해도 운영 로그인 화면에 도착한다. nginx에 `location = /escrow { return 308 /escrow/; }`가 없으면 요청이 기본 FE location으로 빠져 카카오 로그인 화면이 나타날 수 있다.

## 제공 정보

- 전체·활성·동결·종료 에스크로 건수
- 처리 중 또는 결과 불명 거래 건수
- 예치·지급·수수료·환불 합계와 원장 잔액
- 보관계좌·수수료계좌 마스킹 번호와 금융망 잔액
- 최근 대사 결과
- 최근 에스크로 100건
- 서비스 클라이언트 상태와 1원 인증 문구
- 회원계좌 발급 완료·미완료 건수
- 복식부기 원장 계정별 잔액과 거래 원장 대조 (`R-PAY-15`)

### 복식부기 원장 패널

| 항목 | 뜻 |
| --- | --- |
| 보관계좌 현금 (자산) | `CUSTODY_CASH` 잔액. 대사가 금융망 보관계좌 잔액과 비교하는 금액 |
| 예치금 반환 의무 (부채) | 모든 계약의 `ESCROW_LIABILITY` 잔액 합계 |
| 수수료계좌 현금 (자산) | `FEE_CASH` 잔액 |
| 수수료 수익 | `FEE_REVENUE` 잔액 |
| 거래 원장과 대조 | 아래 조건을 모두 만족하면 `일치` |

`일치` 조건은 다음과 같다.

- 자산 합계 = 부채 + 수익
- 보관계좌 현금 = 반환 의무 = 완료 거래로 계산한 원장 잔액
- 수수료계좌 현금 = 수수료 수익
- 계약별 분개 잔액과 거래 잔액이 모두 같음
- 분개가 없는 완료 거래가 없음
- 분개 뒤 상태나 금액이 바뀐 거래가 없음

`불일치`이면 잔액이 다른 계약 수, 분개 없는 완료 거래 수, 분개 뒤 바뀐 거래 수를 함께 표시한다. 이때 `LEDGER_READ_SOURCE`를 `LEDGER`로 바꾸지 않는다. 분개는 고치거나 지울 수 없으므로 원인 거래를 먼저 확인한다.

자금 변경, 강제 지급·환불, 계좌 초기화 기능은 제공하지 않는다.

## 로그인 설정

운영 환경은 GitLab CI/CD Variables에 다음 값을 등록한다.

```dotenv
ESCROW_ADMIN_USERNAME=<운영자 아이디>
ESCROW_ADMIN_PASSWORD_HASH={bcrypt}<BCrypt 해시>
```

`ESCROW_ADMIN_PASSWORD_HASH`에는 원문 비밀번호가 아니라 Spring Security 형식의 BCrypt 해시를 넣는다. 운영자 로그인 비밀번호와 `OPERATOR_CLIENT_API_KEY`는 서로 다른 값을 사용한다.

로컬 기본 계정은 개발 전용 `admin` / `admin`이다. 운영에서는 배포 스크립트가 두 변수를 필수 검사하므로 기본 계정이 사용되지 않는다.

## 보안 경계

- `/admin/login`만 익명 접근을 허용한다.
- `/admin/**`는 `ROLE_ESCROW_ADMIN` 세션이 있어야 한다.
- 로그인 POST와 로그아웃 POST에는 CSRF 검증이 적용된다.
- `/api/**`는 기존 JSON Header 기반 `clientApiKey` 인증을 유지한다.
- 계좌번호는 앞·뒤 네 자리만 표시하고 `userKey`, API Key와 암호화 원문은 표시하지 않는다.
- 로그아웃 시 서버 세션을 폐기하고 `JSESSIONID`를 제거한다.
- 운영 리다이렉트는 `APP_PUBLIC_BASE_URL`의 `/escrow` 경로를 사용하므로 Komit의 `/admin/login`과 섞이지 않는다.
- 로그인·로그아웃 form action도 같은 공개 경로를 사용한다. nginx가 `/escrow`를 제거한 뒤 Escrow의 `/admin/login`·`/admin/logout`으로 전달한다.
- 운영 세션 쿠키는 `ESCROWSESSION`, Path는 `/escrow`로 제한하여 Komit 서비스의 세션 쿠키와 분리한다.
- 세션 기본 만료 시간은 30분이며 `ESCROW_ADMIN_SESSION_TIMEOUT`으로 변경할 수 있다.

## 운영 확인

배포 후 다음을 확인한다.

1. 비로그인 상태의 `/admin` 요청이 `/admin/login`으로 이동한다.
2. 잘못된 비밀번호로 로그인할 수 없다.
3. 정상 로그인 후 대시보드가 표시된다.
4. 계좌번호가 마스킹되어 있다.
5. 로그아웃 후 이전 대시보드 URL에 다시 접근할 수 없다.
6. 기존 `/api/v1/...` 호출의 Client API Key 인증이 그대로 동작한다.
