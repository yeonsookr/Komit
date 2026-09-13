"""서브 앱 생성기. 코밋(service)·에스크로(escrow) 앱이 같은 설정을 공유한다."""

import json

from typing import Callable

from fastapi import APIRouter, FastAPI
from fastapi.exceptions import RequestValidationError
from fastapi.responses import HTMLResponse
from fastapi.middleware.cors import CORSMiddleware

SWAGGER_DIST = "https://cdn.jsdelivr.net/npm/swagger-ui-dist@5"

# 문서 상단 드롭다운(topbar)은 StandaloneLayout에만 있고, 그 레이아웃은
# swagger-ui-standalone-preset.js 를 따로 실어야 쓸 수 있다.
# FastAPI 기본 문서 페이지는 bundle 만 싣기 때문에 페이지를 직접 만든다.
DOCS_HTML = """<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <title>{title}</title>
  <link rel="stylesheet" href="{dist}/swagger-ui.css">
  <style>
    /* 태그(명세 영역) 머리.
       기본 레이아웃은 이름과 설명을 한 줄에 좌우로 붙여서, 설명이 길면 이름이 세로
       가운데로 밀리고 엔드포인트가 한참 아래로 내려간다. 설명을 이름 아래 블록으로
       내려 읽는 순서를 이름 → 설명 → 엔드포인트로 맞춘다. */
    .swagger-ui .opblock-tag {{
      flex-wrap: wrap;
      align-items: center;
      padding: 16px 20px 14px;
      margin: 28px 0 0;
      border-bottom: 1px solid #dfdfe8;
    }}
    .swagger-ui .opblock-tag > a.nostyle {{ order: 1; flex: 0 1 auto; }}
    .swagger-ui .opblock-tag > a.nostyle span {{ font-size: 19px; font-weight: 700; }}
    .swagger-ui .opblock-tag > button {{ order: 2; margin-left: auto; }}
    .swagger-ui .opblock-tag small {{
      order: 3;
      flex: 1 1 100%;
      margin: 12px 0 2px;
      padding: 2px 0 2px 14px;
      border-left: 3px solid #6d34e2;
      font-size: 13px;
      line-height: 1.75;
      color: #5a5966;
    }}
    .swagger-ui .opblock-tag small p {{ margin: 0 0 6px; }}
    .swagger-ui .opblock-tag small p:last-child {{ margin-bottom: 0; }}
    .swagger-ui .opblock-tag small strong {{ color: #111019; font-weight: 600; }}
    .swagger-ui .opblock-tag small code {{
      background: #f3f2f8;
      border: 1px solid #ececf1;
      border-radius: 3px;
      padding: 1px 5px;
      font-size: 12px;
      color: #3a3945;
    }}
  </style>
</head>
<body>
  <div id="swagger-ui"></div>
  <script src="{dist}/swagger-ui-bundle.js"></script>
  <script src="{dist}/swagger-ui-standalone-preset.js"></script>
  <script>
    window.ui = SwaggerUIBundle({{
      urls: {urls},
      "urls.primaryName": {primary},
      dom_id: "#swagger-ui",
      presets: [SwaggerUIBundle.presets.apis, SwaggerUIStandalonePreset],
      plugins: [SwaggerUIBundle.plugins.DownloadUrl],
      layout: "StandaloneLayout",
      deepLinking: true
    }});
  </script>
</body>
</html>
"""


def _docs_page(title: str, mount: str, docs: list[tuple[str, str]]) -> str:
    """다른 앱 문서로 넘어가는 드롭다운이 달린 Swagger UI 페이지.

    주소를 상대경로로 적는다. 문서 페이지가 `<root_path>/<mount>/docs`이므로
    `openapi.json`은 자기 것, `../<다른 mount>/openapi.json`은 상대편 것이 된다.
    배포에서 root_path가 `/mock`으로 붙어도 그대로 맞는다.
    """
    urls = [
        {
            "name": name,
            "url": "openapi.json" if seg == mount else f"../{seg}/openapi.json",
        }
        for name, seg in docs
    ]
    primary = next(name for name, seg in docs if seg == mount)
    return DOCS_HTML.format(
        title=title,
        dist=SWAGGER_DIST,
        urls=json.dumps(urls, ensure_ascii=False),
        primary=json.dumps(primary, ensure_ascii=False),
    )


def create_app(
    title: str,
    mount: str,
    routers: list[APIRouter],
    docs: list,
    validation_error_handler: Callable | None = None,
    openapi_tags: list[dict] | None = None,
) -> FastAPI:
    # 기본 문서 페이지는 끄고 아래에서 직접 만든다.
    #
    # openapi_tags 는 문서에서 태그(명세 영역) 제목 아래에 설명을 싣는다.
    # 소스 주석은 문서에 나오지 않으므로, 영역이 무엇을 다루는지와 어떤 화면이 붙는지를
    # 읽는 사람에게 전하려면 이쪽에 담아야 한다. 두 앱 모두 쓸 수 있고, 넘기지 않으면
    # 태그 설명 없이 예전과 같이 동작한다.
    app = FastAPI(
        title=title, version="0.1.0", docs_url=None, openapi_tags=openapi_tags
    )

    # 검증 실패 응답을 그 앱의 형식으로 바꿔야 하면 핸들러를 받는다.
    # 넘기지 않으면 FastAPI 기본 422 응답을 그대로 쓴다.
    if validation_error_handler is not None:
        app.add_exception_handler(RequestValidationError, validation_error_handler)

    app.add_middleware(
        CORSMiddleware,
        allow_origin_regex=r"http://(localhost|127\.0\.0\.1)(:\d+)?",
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    for router in routers:
        app.include_router(router)

    @app.get("/docs", include_in_schema=False)
    def swagger_ui() -> HTMLResponse:
        return HTMLResponse(_docs_page(title, mount, docs))

    @app.get("/health")
    def health():
        return {"status": "ok"}

    return app
