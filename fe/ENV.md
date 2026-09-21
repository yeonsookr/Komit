# FE 환경 변수

`fe/.env.example`이 프론트 실행 계약의 기준입니다. 기본 FE와 `/test` FE는 같은 소스를
사용하지만, 별도 이미지와 별도 ConfigMap으로 배포합니다.

## 변수와 주입 시점

| 변수 | 기본 FE (`komit.site`) | test FE (`komit.site/test`) | 시점 |
| --- | --- | --- | --- |
| `BACKEND_API_BASE_URL` | 실제 Service | Mock의 `/service` | 빌드 + 실행 |
| `AI_API_BASE_URL` | 현재 지정된 AI 주소 | Mock의 `/service` | 빌드 + 실행 |
| `BASE_PATH` | 빈 값 | `/test` | 빌드 + 실행 |
| `KAKAO_CLIENT_ID` | 배포 환경 값 | 배포 환경 값 | 실행 |
| `KAKAO_REDIRECT_URI` | `/api/auth/kakao/callback` | `/test/api/auth/kakao/callback` | 실행 |

`BACKEND_API_BASE_URL`과 `AI_API_BASE_URL`은 정적 페이지를 만들 때도 서버 데이터를 읽을 수
있어 Docker 빌드 인자와 실행 환경 양쪽에 같은 값을 넣습니다. `BASE_PATH`는 Next.js 빌드
결과에 포함되므로 두 FE가 같은 이미지를 공유할 수 없습니다.

카카오 값은 Route Handler가 실행할 때만 읽습니다. 자격증명이 없으면 로그인 요청만 실패하며,
Service/Mock 연결과 일반 페이지 확인에는 영향을 주지 않습니다.

## GitLab CI/CD 변수 매핑

기본 FE 빌드와 `fe-config`는 아래 값을 그대로 받습니다.

```text
BACKEND_API_BASE_URL=https://komit.site/service
AI_API_BASE_URL=<현재 AI 주소>
KAKAO_REDIRECT_URI=https://komit.site/api/auth/kakao/callback
```

test FE 빌드 잡은 별도 변수를 애플리케이션이 읽는 이름으로 매핑하고,
`fe-test-config`에도 같은 값을 넣습니다.

```text
TEST_BACKEND_API_BASE_URL=https://komit.site/mock/service
TEST_AI_API_BASE_URL=https://komit.site/mock/service
TEST_KAKAO_REDIRECT_URI=https://komit.site/test/api/auth/kakao/callback
BASE_PATH=/test
```

따라서 `TEST_*` 값을 기본 FE 변수에 넣거나, 기본 FE 값을 test ConfigMap에 재사용하지 않습니다.
운영 Docker 빌더는 k3s 내부 DNS를 사용할 수 없으므로 `BACKEND_API_BASE_URL`에는
`service.komit.svc.cluster.local`이 아니라 공개 실제 BE 주소를 넣습니다. 로컬 k3s 오버레이만
클러스터 내부 Service 주소를 사용합니다.

## 로컬 실행

기본 FE는 `.env.example`을 `.env.local`로 복사하고 실제 Service를 8080 포트에 띄운 뒤 실행합니다.

```bash
cp .env.example .env.local
npm run dev
```

Mock을 직접 보려면 별도 test 빌드에서 다음 값을 사용합니다.

```text
BASE_PATH=/test
BACKEND_API_BASE_URL=http://localhost:8080/service
AI_API_BASE_URL=http://localhost:8080/service
KAKAO_REDIRECT_URI=http://localhost:3001/test/api/auth/kakao/callback
```
