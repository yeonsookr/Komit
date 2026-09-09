"""service — 코밋 목 앱의 기준 API.

코밋 앱은 /service 에 마운트된다 (app/main.py).
응답 스키마·상태코드·description 작성 방식을 이 파일의 API에 맞춘다.

규약 요약 (확정)
    - 공통 응답 래퍼를 쓰지 않는다. 성공 응답은 리소스를 그대로 반환한다.
    - 목록만 content + 페이지 정보로 감싼다.
    - 에러 응답은 {code, message}. code는 문자열이다.
    - 유효성 검증 실패는 422이며 errors에 필드별 사유를 담는다.
    - 에러 message에 내부 정보를 담지 않는다. 500은 고정 문구만 반환한다.
"""

from typing import Annotated, Literal

from fastapi import APIRouter, Path, Query, Request
from fastapi.encoders import jsonable_encoder
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

router = APIRouter(prefix="/api", tags=["default"])


# --------------------------------------------------------------------------- #
# 응답 모델
# --------------------------------------------------------------------------- #


class DefaultResponse(BaseModel):
    """단건 조회 성공 응답. 공통 래퍼 없이 리소스를 그대로 반환한다."""

    id: int = Field(
        description="요청한 default 예시 id. 어떤 도메인의 식별자도 아니다.",
        examples=[1],
    )
    message: str = Field(
        description="목 서버가 정상 응답했음을 알리는 고정 문구.",
        examples=["default"],
    )


class DefaultListResponse(BaseModel):
    """목록 조회 성공 응답.

    항목 배열만으로는 전체 개수와 다음 페이지 유무를 표현할 수 없어
    목록에 한해 content와 페이지 정보로 감싼다.
    """

    content: list[DefaultResponse] = Field(
        description="현재 페이지에 속한 항목. 결과가 없으면 빈 배열이다.",
    )
    page: int = Field(
        description="현재 페이지 번호. 1부터 시작한다.",
        examples=[1],
    )
    size: int = Field(
        description="한 페이지에 담긴 최대 항목 수.",
        examples=[20],
    )
    totalElements: int = Field(
        description="조건에 해당하는 전체 항목 수.",
        examples=[137],
    )
    totalPages: int = Field(
        description="전체 페이지 수. 전체 항목이 없으면 0이다.",
        examples=[7],
    )


class ErrorResponse(BaseModel):
    """실패 응답.

    code는 클라이언트가 분기에 쓰는 값이고, message는 사용자에게 그대로
    보여줄 수 있는 문장이다. 내부 정보(예외·쿼리·경로 등)는 담지 않는다.
    """

    code: str = Field(
        description="에러 종류를 나타내는 문자열 코드. 화면 분기·재시도 판단에 쓴다.",
        examples=["DEFAULT_NOT_FOUND"],
    )
    message: str = Field(
        description="사용자에게 그대로 보여줄 수 있는 문구.",
        examples=["요청한 정보를 찾을 수 없습니다."],
    )


class FieldError(BaseModel):
    """유효성 검증에 실패한 필드 하나."""

    field: str = Field(
        description="검증에 실패한 요청 필드명. 요청에 보낸 속성명과 같다.",
        examples=["size"],
    )
    reason: str = Field(
        description=(
            "실패 사유. 서버가 요구하는 조건만 담고 보낸 값은 되돌려주지 않는다."
        ),
        examples=["100 이하여야 합니다."],
    )


class ValidationErrorResponse(ErrorResponse):
    """유효성 검증 실패 응답. 실패 응답에 errors만 추가한 형태다."""

    errors: list[FieldError] = Field(
        description="검증에 실패한 필드 목록. 폼 화면에서 필드별 안내에 쓴다.",
    )


# --------------------------------------------------------------------------- #
# 실패 응답 생성
# --------------------------------------------------------------------------- #

# 사용자에게 보여줄 문구만 둔다. 내부 사정을 문구로 흘리지 않는다.
NOT_FOUND = ("DEFAULT_NOT_FOUND", "요청한 정보를 찾을 수 없습니다.")
FORBIDDEN = ("FORBIDDEN", "접근 권한이 없습니다.")
INTERNAL_ERROR = (
    "INTERNAL_ERROR",
    "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.",
)
INVALID_REQUEST = ("INVALID_REQUEST", "요청 형식이 올바르지 않습니다.")


def _error_response(status_code: int, code_message: tuple[str, str]) -> JSONResponse:
    code, message = code_message
    error = ErrorResponse(code=code, message=message)
    return JSONResponse(status_code=status_code, content=jsonable_encoder(error))


# Pydantic 오류 종류를 사용자 문구로 옮긴다. 보낸 값은 문구에 넣지 않는다.
_REASONS: dict[str, str] = {
    "missing": "필수 항목입니다.",
    "int_parsing": "정수여야 합니다.",
    "int_type": "정수여야 합니다.",
    "string_type": "문자열이어야 합니다.",
    "bool_parsing": "true 또는 false여야 합니다.",
    "json_invalid": "JSON 형식이 올바르지 않습니다.",
}


def _reason(error: dict) -> str:
    """검증 오류 하나를 사용자 문구로 바꾼다.

    Pydantic 기본 메시지는 영문이고 보낸 값이 섞일 수 있어 그대로 쓰지 않는다.
    한계값(ctx)은 서버가 정한 조건이라 노출해도 된다.
    """
    error_type = error.get("type", "")
    context = error.get("ctx") or {}

    if error_type in {"greater_than_equal", "greater_than"}:
        return f"{context.get('ge', context.get('gt'))} 이상이어야 합니다."
    if error_type in {"less_than_equal", "less_than"}:
        return f"{context.get('le', context.get('lt'))} 이하여야 합니다."
    if error_type == "string_pattern_mismatch":
        return "형식이 올바르지 않습니다."
    if error_type == "literal_error":
        expected = context.get("expected")
        return f"{expected} 중 하나여야 합니다." if expected else "허용되지 않는 값입니다."

    return _REASONS.get(error_type, "값이 올바르지 않습니다.")


def _field_name(location: tuple) -> str:
    """오류 위치에서 요청 필드명만 뽑는다.

    Pydantic은 ("query", "size")처럼 위치 종류를 앞에 붙인다. 클라이언트가 보낸
    이름과 맞추기 위해 그 접두어를 뗀다.
    """
    parts = [str(part) for part in location if part not in {"body", "query", "path"}]
    return ".".join(parts) if parts else "body"


async def validation_exception_handler(
    request: Request, exc: RequestValidationError
) -> JSONResponse:
    """요청 검증 실패를 이 앱의 실패 응답 형식으로 바꾼다.

    FastAPI 기본 422 응답은 detail 배열이라 이 앱이 문서에 선언한 형식과 다르다.
    앱에 이 핸들러를 등록해(app/main.py) 실패 응답을 한 형식으로 맞춘다.
    엔드포인트에서 try/except로 검증하지 않는다.
    """
    code, message = INVALID_REQUEST
    body = ValidationErrorResponse(
        code=code,
        message=message,
        errors=[
            FieldError(field=_field_name(error.get("loc", ())), reason=_reason(error))
            for error in exc.errors()
        ],
    )
    return JSONResponse(status_code=422, content=jsonable_encoder(body))


# --------------------------------------------------------------------------- #
# 목 데이터
# --------------------------------------------------------------------------- #

# 목록 응답에 쓰는 전체 항목 수. 상태를 저장하지 않으므로 고정값이다.
TOTAL_ELEMENTS = 137

SINGLE_EXAMPLE = {"id": 1, "message": "default"}

LIST_EXAMPLE = {
    "content": [
        {"id": 1, "message": "default"},
        {"id": 2, "message": "default"},
    ],
    "page": 1,
    "size": 20,
    "totalElements": TOTAL_ELEMENTS,
    "totalPages": 7,
}

NOT_FOUND_EXAMPLE = {
    "code": "DEFAULT_NOT_FOUND",
    "message": "요청한 정보를 찾을 수 없습니다.",
}

FORBIDDEN_EXAMPLE = {"code": "FORBIDDEN", "message": "접근 권한이 없습니다."}

INTERNAL_ERROR_EXAMPLE = {
    "code": "INTERNAL_ERROR",
    "message": "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.",
}

VALIDATION_ERROR_EXAMPLE = {
    "code": "INVALID_REQUEST",
    "message": "요청 형식이 올바르지 않습니다.",
    "errors": [{"field": "size", "reason": "100 이하여야 합니다."}],
}


# --------------------------------------------------------------------------- #
# 단건 조회
# --------------------------------------------------------------------------- #


@router.get(
    "/default/{id}",
    response_model=DefaultResponse,
    responses={
        200: {"content": {"application/json": {"example": SINGLE_EXAMPLE}}},
        403: {
            "model": ErrorResponse,
            "description": "접근 권한 없음",
            "content": {"application/json": {"example": FORBIDDEN_EXAMPLE}},
        },
        404: {
            "model": ErrorResponse,
            "description": "리소스 없음",
            "content": {"application/json": {"example": NOT_FOUND_EXAMPLE}},
        },
        422: {
            "model": ValidationErrorResponse,
            "description": "요청 스키마 유효성 검증 실패",
            "content": {"application/json": {"example": VALIDATION_ERROR_EXAMPLE}},
        },
        500: {
            "model": ErrorResponse,
            "description": "서버 내부 오류",
            "content": {"application/json": {"example": INTERNAL_ERROR_EXAMPLE}},
        },
    },
    summary="기본 응답 단건 조회",
    description=(
        "코밋 서비스 API의 **단건 조회 기준 API**다. 성공 응답 스키마, 에러 응답 스키마, "
        "HTTP 상태코드 사용 방식을 이 API에 맞춘다.\n\n"
        "성공 응답은 공통 래퍼로 감싸지 않고 리소스를 그대로 반환한다. 성공·실패 판단은 "
        "HTTP 상태코드로 하고, 실패일 때만 `code`로 세부 사유를 구분한다.\n\n"
        "도메인 데이터를 다루지 않으며, 상태를 저장하지 않는다. 실패 응답은 경로 변수 `id`의 "
        "예약값으로 재현한다.\n\n"
        "- **200**: `id`가 1이거나 아래 예약값이 아닌 정수일 때. 요청한 id를 그대로 담아 반환한다\n"
        "- **404 / DEFAULT_NOT_FOUND**: `id`가 2일 때. 리소스가 없거나 삭제된 경우다. "
        "타인의 리소스에 접근한 경우도 존재 여부를 감추기 위해 이 응답을 준다\n"
        "- **403 / FORBIDDEN**: `id`가 3일 때. 리소스의 존재가 공개된 경우에만 쓴다\n"
        "- **500 / INTERNAL_ERROR**: `id`가 4일 때. 원인은 응답에 담지 않고 고정 문구만 반환한다\n"
        "- **422 / INVALID_REQUEST**: `id`가 정수가 아닐 때. `errors`에 필드별 사유가 담긴다\n\n"
        "에러 `message`는 사용자에게 그대로 보여줄 수 있는 문장이다. 예외·쿼리·경로 같은 "
        "내부 정보는 담지 않으므로 화면에 그대로 노출해도 된다."
    ),
)
def get_default(
    id: Annotated[
        int,
        Path(
            description=(
                "default 예시 id. 어떤 도메인의 식별자도 아니며, 응답 사례를 고르는 값이다. "
                "2는 404, 3은 403, 4는 500 재현용 예약값이다."
            ),
            examples=[1],
        ),
    ],
) -> DefaultResponse | JSONResponse:
    if id == 2:
        return _error_response(404, NOT_FOUND)
    if id == 3:
        return _error_response(403, FORBIDDEN)
    if id == 4:
        return _error_response(500, INTERNAL_ERROR)
    return DefaultResponse(id=id, message="default")


# --------------------------------------------------------------------------- #
# 목록 조회
# --------------------------------------------------------------------------- #


@router.get(
    "/defaults",
    response_model=DefaultListResponse,
    responses={
        200: {"content": {"application/json": {"example": LIST_EXAMPLE}}},
        422: {
            "model": ValidationErrorResponse,
            "description": "페이지네이션 파라미터 유효성 검증 실패",
            "content": {"application/json": {"example": VALIDATION_ERROR_EXAMPLE}},
        },
        500: {
            "model": ErrorResponse,
            "description": "서버 내부 오류",
            "content": {"application/json": {"example": INTERNAL_ERROR_EXAMPLE}},
        },
    },
    summary="기본 응답 목록 조회",
    description=(
        "코밋 서비스 API의 **목록 조회 기준 API**다. 페이지네이션 파라미터와 목록 응답 "
        "스키마를 이 API에 맞춘다.\n\n"
        "목록은 항목 배열만으로 전체 개수와 다음 페이지 유무를 알 수 없어, 목록에 한해 "
        "`content`와 페이지 정보로 감싼다. 단건 조회는 감싸지 않는다.\n\n"
        f"전체 항목 수는 {TOTAL_ELEMENTS}건으로 고정돼 있고, 요청한 `page`와 `size`에 맞춰 "
        "해당 구간의 항목을 만들어 반환한다.\n\n"
        "- **200**: 파라미터가 유효할 때. 마지막 페이지를 넘어선 `page`를 요청하면 `content`가 "
        "빈 배열이고 나머지 페이지 정보는 그대로 채워진다. 조회 결과가 없는 것은 오류가 아니다\n"
        "- **422 / INVALID_REQUEST**: `page`가 1 미만, `size`가 1 미만이거나 100 초과, "
        "`sort`가 허용된 값이 아닐 때. `errors`에 어떤 필드가 왜 틀렸는지 담긴다\n"
        "- **500 / INTERNAL_ERROR**: `page`가 99일 때. 서버 오류 화면을 확인하기 위한 예약값이다\n\n"
        "정렬은 `필드명,asc|desc` 형식이다. 이 기준 API는 `id` 필드만 지원한다."
    ),
)
def get_defaults(
    page: Annotated[
        int,
        Query(
            ge=1,
            description="페이지 번호. 1부터 시작한다. 99는 500 재현용 예약값이다.",
            examples=[1],
        ),
    ] = 1,
    size: Annotated[
        int,
        Query(
            ge=1,
            le=100,
            description="한 페이지에 담을 항목 수. 최대 100이다.",
            examples=[20],
        ),
    ] = 20,
    sort: Annotated[
        Literal["id,asc", "id,desc"],
        Query(
            description="정렬 기준. `필드명,asc|desc` 형식이며 이 API는 id만 지원한다.",
            examples=["id,asc"],
        ),
    ] = "id,asc",
) -> DefaultListResponse | JSONResponse:
    if page == 99:
        return _error_response(500, INTERNAL_ERROR)

    total_pages = (TOTAL_ELEMENTS + size - 1) // size
    start = (page - 1) * size
    end = min(start + size, TOTAL_ELEMENTS)

    ids = list(range(start + 1, end + 1)) if start < TOTAL_ELEMENTS else []
    if sort == "id,desc":
        ids.reverse()

    return DefaultListResponse(
        content=[DefaultResponse(id=id, message="default") for id in ids],
        page=page,
        size=size,
        totalElements=TOTAL_ELEMENTS,
        totalPages=total_pages,
    )
