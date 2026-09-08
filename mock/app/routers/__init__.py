"""명세 영역별 라우터. 명세가 확정되면 여기에 도메인 단위로 추가한다."""

from . import default

ROUTERS = [default.router]
