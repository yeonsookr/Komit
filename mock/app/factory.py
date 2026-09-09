"""서브 앱 생성기. 코밋(service)·에스크로(escrow) 앱이 같은 설정을 공유한다."""

import json

from fastapi import APIRouter, FastAPI
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


def create_app(title: str, mount: str, routers: list[APIRouter], docs: list) -> FastAPI:
    # 기본 문서 페이지는 끄고 아래에서 직접 만든다.
    app = FastAPI(title=title, version="0.1.0", docs_url=None)

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
