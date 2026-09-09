"""service — 코밋 목 앱의 기준 API.

코밋 앱은 /service 에 마운트된다 (app/main.py).
응답 형식·상태코드·description 작성 방식을 이 API에 맞춘다.
"""

from fastapi import APIRouter, Path, Request
from fastapi.encoders import jsonable_encoder
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

router = APIRouter(prefix="/api", tags=["default"])


class DefaultResponse(BaseModel):
    id: int = Field(
        description="요청 경로로 받은 default 예시 id를 그대로 돌려준 값.",
        examples=[1],
    )
    message: str = Field(
        description="목 서버가 정상 응답했음을 알리는 고정 문구.",
        examples=["default"],
    )


class ErrorResponse(BaseModel):
    message: str = Field(
        description="실패 사유.",
        examples=["default error"],
    )


def _error_response(status_code: int, message: str) -> JSONResponse:
    error = ErrorResponse(message=message)
    return JSONResponse(status_code=status_code, content=jsonable_encoder(error))


async def validation_exception_handler(
    request: Request, exc: RequestValidationError
) -> JSONResponse:
    """요청 검증 실패를 이 앱의 실패 응답 형식으로 바꾼다.

    FastAPI 기본 422 응답은 `detail` 배열이라 이 앱이 문서에 선언한 ErrorResponse와
    모양이 다르다. 앱에 이 핸들러를 등록해(app/main.py) 실패 응답을 한 형식으로 맞춘다.
    """
    return _error_response(422, "요청 형식이 올바르지 않습니다.")


@router.get(
    "/default/{id}",
    response_model=DefaultResponse,
    responses={
        422: {"model": ErrorResponse, "description": "요청 형식 오류"},
        500: {"model": ErrorResponse, "description": "서버 내부 오류"},
    },
    summary="기본 응답 조회",
    description=(
        "응답 형식·상태코드·description 작성 방식의 기준이 되는 기본 API다.\n"
        "도메인 데이터를 다루지 않고, 받은 id에 따라 성공과 실패 응답을 각각 보여준다.\n\n"
        "- **200**: `id`가 1일 때. 받은 id를 그대로 담아 성공 응답을 돌려준다\n"
        "- **500**: `id`가 2일 때. 서버 내부 오류 상황을 확인하기 위한 값이다\n"
        "- 그 밖의 `id`는 200과 같은 성공 응답을 돌려준다\n"
    ),
)
def get_default(
    id: int = Path(
        description="default 예시 id. 어떤 도메인의 식별자도 아니며, 응답을 고르기 위한 값이다.",
        examples=[1],
    ),
) -> DefaultResponse | JSONResponse:
    if id == 2:
        return _error_response(500, "default error")
    return DefaultResponse(id=id, message="default")
