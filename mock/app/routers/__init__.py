"""명세 영역별 라우터. 명세가 확정되면 여기에 도메인 단위로 추가한다.

앱이 둘로 갈리므로 라우터도 어느 쪽에 붙는지에 따라 목록을 나눈다.
"""

from . import default, escrow

SERVICE_ROUTERS = [default.router]
ESCROW_ROUTERS = [escrow.router]
