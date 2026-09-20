# Escrow 운영 콘솔

Escrow 운영 콘솔은 별도 SPA가 아니라 Escrow Spring Boot 애플리케이션이 Thymeleaf로 렌더링하는 조회 전용 페이지다. 운영 자격증명과 금융망 키를 브라우저에 전달하지 않고 서버에서 DB·금융망 정보를 조합한다.

## 접속 주소

- 로컬: `http://localhost:8081/admin/login`
- 운영: `https://komit.site/escrow/admin/login`

## 제공 정보

- 전체·활성·동결·종료 에스크로 건수
- 처리 중 또는 결과 불명 거래 건수
- 예치·지급·수수료·환불 합계와 원장 잔액
- 보관계좌·수수료계좌 마스킹 번호와 금융망 잔액
- 최근 대사 결과
- 최근 에스크로 100건
- 서비스 클라이언트 상태와 1원 인증 문구
- 회원계좌 발급 완료·미완료 건수

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
- 세션 기본 만료 시간은 30분이며 `ESCROW_ADMIN_SESSION_TIMEOUT`으로 변경할 수 있다.

## 운영 확인

배포 후 다음을 확인한다.

1. 비로그인 상태의 `/admin` 요청이 `/admin/login`으로 이동한다.
2. 잘못된 비밀번호로 로그인할 수 없다.
3. 정상 로그인 후 대시보드가 표시된다.
4. 계좌번호가 마스킹되어 있다.
5. 로그아웃 후 이전 대시보드 URL에 다시 접근할 수 없다.
6. 기존 `/api/v1/...` 호출의 Client API Key 인증이 그대로 동작한다.
