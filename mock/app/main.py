"""FE 개발용 목 서버 — DB 연결도 비즈니스 로직도 없다.

응답은 API 명세의 예시 JSON을 그대로 반환한다.

코밋 서버와 에스크로 서버는 실제로 분리되므로 목에서도 앱을 나눠 마운트한다.
마운트 경로는 ASGI root_path로 각 앱에 전달되므로 문서도 자기 경로 기준으로 뜬다.
문서 상단 드롭다운으로 두 문서를 오갈 수 있다.

    /service   코밋 서버 목
    /escrow    에스크로 서버 목

nginx가 /mock 아래로 넘기므로 밖에서는 /mock/service/..., /mock/escrow/... 가 된다.
"""

from fastapi import FastAPI

from .factory import create_app
from .routers import ESCROW_ROUTERS, SERVICE_ROUTERS, SERVICE_TAGS
from .routers.service import validation_exception_handler as service_validation_handler
from .routers.escrow import validation_exception_handler as escrow_validation_handler

# 문서 상단 드롭다운에 실을 목록. (표시 이름, 마운트 경로)
DOCS = [("코밋", "service"), ("에스크로", "escrow")]

# 루트는 서브 앱을 붙이기만 한다. 자체 문서는 두지 않는다.
app = FastAPI(docs_url=None, redoc_url=None, openapi_url=None)

service_app = create_app(
    "Komit Service Mock Server",
    "service",
    SERVICE_ROUTERS,
    DOCS,
    validation_error_handler=service_validation_handler,
    openapi_tags=SERVICE_TAGS,
)
escrow_app = create_app(
    "Komit Escrow Mock Server",
    "escrow",
    ESCROW_ROUTERS,
    DOCS,
    validation_error_handler=escrow_validation_handler,
)

app.mount("/service", service_app)
app.mount("/escrow", escrow_app)


@app.get("/health")
def health():
    return {"status": "ok"}
