# FE 환경 변수

## 값이 필요한 시점 — **빌드가 아니라 실행할 때**

서버 주소는 첫 요청에서 읽습니다. 그래서

- `npm run build` 는 변수 없이도 통과합니다.
- 결과 이미지에 주소가 박히지 않아 **한 번 구운 이미지를 환경마다 그대로 씁니다.**
- 값을 빠뜨린 채 실행하면 그 요청이 실패합니다. 안전하지 않은 기본값으로 돌지 않습니다.

GitLab CI/CD 변수는 **빌드 잡이 아니라 실행(배포) 단계**에 주입합니다.

```bash
docker run -d \
  -e BACKEND_API_BASE_URL="$BACKEND_API_BASE_URL" \
  -e AI_API_BASE_URL="$AI_API_BASE_URL" \
  -e KAKAO_CLIENT_ID="$KAKAO_CLIENT_ID" \
  -e KAKAO_REDIRECT_URI="$KAKAO_REDIRECT_URI" \
  -e NEXT_PUBLIC_SITE_URL="$NEXT_PUBLIC_SITE_URL" \
  <이미지>
```

## 변수 목록

| 변수 | 노출 | 필요 시점 | 값(현재) |
| --- | --- | --- | --- |
| `BACKEND_API_BASE_URL` | 서버 전용 | 실행 | 배포별 Komit API 주소. 운영 루트는 실제 BE, `/test`는 mock |
| `AI_API_BASE_URL` | 서버 전용 | 실행 | 배포별 AI API 주소. 운영 루트와 `/test` 값을 CI/CD에서 분리 |
| `ESCROW_API_BASE_URL` | 서버 전용 | 실행 | `https://komit.site/mock/escrow` |
| `KAKAO_CLIENT_ID` | 서버 전용 | 실행 | 카카오 개발자 콘솔의 REST API 키 |
| `KAKAO_REDIRECT_URI` | 서버 전용 | 실행 | `<배포 도메인>/api/auth/kakao/callback` |
| `NEXT_PUBLIC_SITE_URL` | **공개** | **빌드** | 배포 도메인 |

### 서버 전용 변수

브라우저로 나가지 않습니다. 실제 호출은 Next Route Handler 가 대신합니다.
**`NEXT_PUBLIC_` 접두사를 붙이지 마세요** — 붙이는 순간 브라우저 번들에 박힙니다.

`ESCROW_API_BASE_URL` 은 아직 화면에서 쓰지 않지만, 에스크로 연동이 붙을 때
같은 자리에서 읽도록 미리 적어 둡니다.

### 카카오 로그인 두 변수

인가 코드를 받아 오는 앞단은 프론트가 맡습니다(`/api/auth/kakao` → 카카오 → `/api/auth/kakao/callback`).
그래서 REST API 키가 Next 서버에 필요합니다. 브라우저에는 나가지 않습니다.

- `KAKAO_REDIRECT_URI` 는 카카오 콘솔에 등록한 값과 **글자까지 같아야** 합니다. 요청의 origin 에서
  만들어 내지 않고 값으로 받는 이유입니다 — nginx 뒤에서는 origin 이 달라질 수 있습니다.
- 로컬에서 로그인까지 확인하려면 카카오 개발자 콘솔에 앱을 하나 만들고 `http://localhost:3000/api/auth/kakao/callback`
  을 Redirect URI 로 등록합니다. 코밋 서버는 목이라 어떤 인가 코드를 넘겨도 로그인됩니다.
- 두 값이 없으면 「카카오로 시작하기」가 500 으로 실패합니다. 다른 화면은 영향이 없습니다.

운영에서는 루트와 `/test`가 같은 호스트에 있어도 별도 배포입니다. GitLab에는 루트용
`BACKEND_API_BASE_URL`, `AI_API_BASE_URL`, `KAKAO_REDIRECT_URI`와 테스트용
`TEST_BACKEND_API_BASE_URL`, `TEST_AI_API_BASE_URL`, `TEST_KAKAO_REDIRECT_URI`를 각각
등록합니다. `/test` 이미지는 쿠키 이름과 Path도 분리하므로 두 로그인 세션이 섞이지 않습니다.

### `NEXT_PUBLIC_SITE_URL` 만 빌드 시점

Next 는 `NEXT_PUBLIC_` 변수를 빌드할 때 번들에 넣습니다. 도메인이 바뀌면
**다시 빌드해야** 합니다. 메타데이터·canonical URL 처럼 공개해도 되는 값만 둡니다.

## 로컬에서

`.env.example` 을 `.env.local` 로 복사해 씁니다. `.env.local` 은 커밋되지 않습니다.

```bash
cp .env.example .env.local
```

목 명세를 고쳐 가며 작업할 때는 로컬 목 서버가 빠릅니다.

```bash
# mock/ 에서
uvicorn app.main:app --reload --port 8080

# fe/.env.local
BACKEND_API_BASE_URL=http://localhost:8080/service
```
