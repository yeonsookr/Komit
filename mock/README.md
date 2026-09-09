# mock

> **⚠️ 제1원칙 — 도메인만 바꾸면 실서버에 그대로 붙어야 한다.**
> 실서버에 없는 속성을 추가해 예외 분기 트리거로 쓰지 않는다. 실패 응답은 **실제 속성의 값**으로
> 고른다. 자세한 내용은 [AGENTS.md](./AGENTS.md) 최상단.

FE 개발용 목 서버. **DB 연결 없음 · 비즈니스 로직 없음.**

응답은 API 명세의 예시 JSON을 그대로 하드코딩해 돌려준다.

코밋 서버와 에스크로 서버는 실제로 분리되므로 목에서도 앱을 나눈다. 컨테이너는 하나지만
그 안에 앱이 둘이고, **문서도 따로** 뜬다.

```
/service   코밋 서버 목
/escrow    에스크로 서버 목
```

## 실행

```bash
python -m venv .venv
.venv/Scripts/activate      # macOS/Linux: source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8080
```

도커로 띄우려면:

```bash
docker build -t komit-mock:local .
docker run --rm -p 8080:8080 komit-mock:local
```

## 주소

배포에서는 nginx가 `/mock` 아래로 넘긴다. 그 접두사는 컨테이너 안에 없고, 컨테이너에
`--root-path /mock`을 주면 문서가 알아서 반영한다.

| | 로컬 | 배포 |
|---|---|---|
| 코밋 문서 | http://localhost:8080/service/docs | `/mock/service/docs` |
| 에스크로 문서 | http://localhost:8080/escrow/docs | `/mock/escrow/docs` |
| 코밋 API | `/service/api/...` | `/mock/service/api/...` |
| 에스크로 API | `/escrow/api/...` | `/mock/escrow/api/...` |
| 헬스 | `/health` | `/mock/health` |

- 루트(`/`)에는 문서가 없다. 문서는 위 두 곳뿐이고, 문서 상단 드롭다운으로 서로 오갈 수 있다.
- CORS: `localhost`/`127.0.0.1` 전 포트 허용 (credentials 포함)

## 구조

```
app/
├── main.py              루트 앱 — 서브 앱 두 개를 마운트, 문서 목록(DOCS) 정의
├── factory.py           서브 앱 생성기 — CORS, /health, Swagger UI 페이지
└── routers/
    ├── __init__.py      SERVICE_ROUTERS / ESCROW_ROUTERS
    ├── service.py       코밋 기준 API
    └── escrow.py        에스크로 기준 API
```

라우터를 추가할 때는 `routers/`에 파일을 만들고 `SERVICE_ROUTERS`나 `ESCROW_ROUTERS` 중
맞는 쪽에 등록한다. 그러면 그 앱의 문서에만 나온다.

문서 페이지는 FastAPI 기본 것을 쓰지 않고 `factory.py`에서 직접 만든다. 상단 전환
드롭다운이 Swagger UI의 `StandaloneLayout`에만 있는데, 그 레이아웃은
`swagger-ui-standalone-preset.js`를 따로 실어야 하기 때문이다.

## 동작 원칙

- 응답은 명세 예시 JSON을 그대로 반환한다. 경로 파라미터는 응답의 해당 필드에 echo한다.
- `PATCH`류는 보낸 필드만 응답에 반영한다 (저장은 하지 않음).
- 상태를 저장하지 않는다. 호출 순서에 따라 응답이 달라지면 안 된다.
- **에러는 명세에 있는 것만 재현한다.** 실패 응답이 필요하면 명세가 정한 예약값으로 고른다.
  (예: 코밋은 `id=2` → 500, 에스크로는 `userKey=invalid-user-key` → `H1009`)

## 실패 응답 형식

두 앱은 형식이 다르다. 코밋은 자체 형식, 에스크로는 SSAFY 금융망 형식을 따른다.

| | 코밋 | 에스크로 |
|---|---|---|
| 본문 | `{"message": "..."}` | `{"Header": {"responseCode": "...", ...}}` |
| 검증 실패 | 422 | 400 `H1000` / `Q1001` |

요청 검증 실패는 엔드포인트에서 try/except 하지 않는다. 각 앱에 등록한
`validation_exception_handler`(`routers/service.py`, `routers/escrow.py`)가 FastAPI의
`RequestValidationError`를 그 앱의 형식으로 바꾼다.
