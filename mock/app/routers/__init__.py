"""명세 영역별 라우터. 명세가 확정되면 여기에 도메인 단위로 추가한다.

앱이 둘로 갈리므로 라우터도 어느 쪽에 붙는지에 따라 목록을 나눈다.
"""

from . import escrow, service

SERVICE_ROUTERS = [service.router]
ESCROW_ROUTERS = [escrow.router]

# 태그(명세 영역) 설명. 문서에서 각 영역 제목 아래에 실린다.
# 에스크로도 필요해지면 같은 방식으로 ESCROW_TAGS 를 만들어 넘긴다.
SERVICE_TAGS = service.SERVICE_TAGS
