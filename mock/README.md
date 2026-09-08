# mock

FE 개발용 목 서버. **DB 연결 없음 · 비즈니스 로직 없음.**

응답은 API 명세의 예시 JSON을 그대로 하드코딩해 돌려준다.

## 실행

```bash
python -m venv .venv
.venv/Scripts/activate      # macOS/Linux: source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8080
```

- Swagger UI: http://localhost:8080/docs
- CORS: `localhost`/`127.0.0.1` 전 포트 허용 (credentials 포함)

## 구조

```
app/
├── main.py       FastAPI 앱 + CORS + /health
└── routers/      명세 영역별 라우터 (명세 확정 후 도메인 단위로 추가)
```

## 동작 원칙

- 응답은 명세 예시 JSON을 그대로 반환한다. 경로 파라미터는 응답의 해당 필드에 echo한다.
- `PATCH`류는 보낸 필드만 응답에 반영한다 (저장은 하지 않음).
- 에러 분기 없음 — 어떤 ID로 호출해도 성공 응답을 준다.
