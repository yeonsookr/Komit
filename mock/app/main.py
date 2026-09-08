"""FE 개발용 목 서버 — DB 연결도 비즈니스 로직도 없다.

응답은 API 명세의 예시 JSON을 그대로 반환한다.
"""

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from .routers import ROUTERS

app = FastAPI(title="Komit BE Mock Server", version="0.1.0")

app.add_middleware(
    CORSMiddleware,
    allow_origin_regex=r"http://(localhost|127\.0\.0\.1)(:\d+)?",
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

for router in ROUTERS:
    app.include_router(router)


@app.get("/health")
def health():
    return {"status": "ok"}
