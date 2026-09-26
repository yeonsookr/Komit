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

import copy
import re
from datetime import datetime, timedelta, timezone
from typing import Annotated, Literal

from fastapi import (
    APIRouter,
    File,
    Path,
    Query,
    Request,
    Response,
    UploadFile,
)
from fastapi.encoders import jsonable_encoder
from fastapi.exception_handlers import (
    http_exception_handler as fastapi_http_exception_handler,
)
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field, ValidationInfo, field_validator, model_validator
from pydantic_core import PydanticCustomError
from starlette.exceptions import HTTPException as StarletteHTTPException

from .. import scan_result

# 태그는 라우터가 아니라 엔드포인트마다 단다. 라우터에 달면 FastAPI가 엔드포인트 태그와
# 합쳐버려서, 영역별 API가 기준 API 태그에도 함께 나온다.
router = APIRouter(prefix="/api")


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


def _field_error(field: str, reason: str) -> JSONResponse:
    """스키마로는 거를 수 없고 값을 들여다봐야 아는 검증 실패를 422로 돌려준다."""
    code, message = INVALID_REQUEST
    body = ValidationErrorResponse(
        code=code,
        message=message,
        errors=[FieldError(field=field, reason=reason)],
    )
    return JSONResponse(status_code=422, content=jsonable_encoder(body))


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
    if error_type == "string_too_short":
        return f"{context.get('min_length')}자 이상이어야 합니다."
    if error_type == "string_too_long":
        return f"{context.get('max_length')}자 이하여야 합니다."
    if error_type == "too_short":
        return f"{context.get('min_length')}개 이상이어야 합니다."
    if error_type == "too_long":
        return f"{context.get('max_length')}개 이하여야 합니다."
    if error_type == "string_pattern_mismatch":
        return "형식이 올바르지 않습니다."
    if error_type == "invalid_calendar_date":
        return "형식이 올바르지 않습니다."
    if error_type == "career_period":
        return CAREER_PERIOD_REASON
    if error_type == "duplicate_issue":
        return DUPLICATE_ISSUE_REASON
    if error_type == "literal_error":
        expected = context.get("expected")
        return f"{expected} 중 하나여야 합니다." if expected else "허용되지 않는 값입니다."

    return _REASONS.get(error_type, "값이 올바르지 않습니다.")


def _field_name(location: tuple) -> str:
    """오류 위치에서 요청 필드명만 뽑는다.

    Pydantic은 ("query", "size")처럼 위치 종류를 앞에 붙인다. 클라이언트가 보낸
    이름과 맞추기 위해 그 접두어를 뗀다. 접두어는 언제나 첫 항목이므로 자리로
    떼어낸다. 이름으로 거르면 `body`처럼 접두어와 같은 이름의 필드가 지워진다.
    """
    parts = [str(part) for part in location[1:]]
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


async def http_exception_handler(
    request: Request, exc: StarletteHTTPException
) -> Response:
    """본문을 읽다 난 400을 이 앱의 실패 응답 형식으로 바꾼다.

    FastAPI는 본문이 UTF-8로 읽히지 않으면 `RequestValidationError`가 아니라
    `HTTPException(400)`을 던진다. 그래서 `validation_exception_handler`를 거치지
    않고 Starlette 기본 응답(`{"detail": ...}`)이 나간다. 같은 '본문을 못 읽겠다'인데
    바이트 인코딩 하나로 응답 형식이 갈리므로, 깨진 JSON과 같은 422로 맞춘다.

    404·405처럼 경로 단계에서 나는 것은 돌려줄 `code`가 정해져 있지 않다. 임의로
    만들지 않고 기본 처리에 맡긴다.
    """
    if exc.status_code == 400:
        code, message = INVALID_REQUEST
        body = ValidationErrorResponse(
            code=code,
            message=message,
            errors=[
                FieldError(field="body", reason=_REASONS["json_invalid"]),
            ],
        )
        return JSONResponse(status_code=422, content=jsonable_encoder(body))
    return await fastapi_http_exception_handler(request, exc)


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
    tags=["default"],
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
    tags=["default"],
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


# =========================================================================== #
# 영역 1 — 랜딩 · 로그인
#
# 랜딩에서 무엇을 파는지 보고 로그인하는 구간이다.
# 화면은 `1_랜딩홈`과 `1_로그인` 둘이며, 랜딩 홈은 히어로·증상별로 찾기·추천 개발자·
# 진행 순서·사전 점검·푸터 여섯 섹션이 한 페이지에 들어간다.
# =========================================================================== #


# --------------------------------------------------------------------------- #
# 영역 1 — 응답 모델
# --------------------------------------------------------------------------- #


class LandingSummaryResponse(BaseModel):
    """랜딩 히어로 아래에 붙는 지표 한 줄."""

    weeklyInquiryRoomCount: int = Field(
        description="이번 주에 열린 문의 방 수. 랜딩의 '이번 주 상담 N건'에 쓴다.",
        examples=[412],
    )
    weekStartAt: str = Field(
        description="집계 기준이 되는 주의 시작 시각.",
        examples=["2026-09-07T00:00:00+09:00"],
    )


class CodeResponse(BaseModel):
    """코드 항목 하나. 무엇의 코드인지는 이 모델을 담는 필드가 정한다."""

    codeId: int = Field(description="코드 항목 식별자.", examples=[41])
    codeName: str = Field(
        description="화면에 그대로 보여주는 코드 항목 이름.",
        examples=["인터넷에 올리기"],
    )


class QaCriterionResponse(BaseModel):
    """확인 문장 하나. 의뢰인이 이걸 보고 통과를 정한다.

    요청서의 요구사항과 계약의 작업이 같은 문장을 쓴다. 요구사항에 적힌 문장이
    계약서로 넘어가 작업의 완료 조건이 되므로 한 모델로 둔다.
    """

    qaCriterionId: int = Field(description="확인 문장 식별자.", examples=[201])
    testCase: str | None = Field(
        description="무엇을 해보면 되는지. 적지 않았으면 null이다.",
        examples=["관리자 주소로 들어가면 로그인 화면이 뜬다"],
    )
    condition: str | None = Field(
        description="확인하기 전에 갖춰져야 할 조건. 없으면 null이다.",
        examples=[None],
    )
    status: Literal["UNRESOLVED", "RESOLVED"] = Field(
        description="확인 결과. RESOLVED면 의뢰인이 체크한 것이다.",
        examples=["RESOLVED"],
    )
    issueId: int | None = Field(
        description=(
            "진단에서 나온 요구사항이면 그 식별자. 작업 전용으로 만든 문장이면 null이다."
        ),
        examples=[312],
    )


class TaskPriceResponse(BaseModel):
    """작업 유형별 가격 한 줄. 개발자가 등록한 가격표의 유형별 최저가다."""

    taskType: Literal["CONSULT", "DEVELOP"] = Field(
        description="작업 종류. CONSULT는 컨설팅, DEVELOP은 개발이다.",
        examples=["CONSULT"],
    )
    price: int = Field(
        description="해당 유형에서 이 개발자가 받는 최저 금액. 원 단위 정수다.",
        examples=[50000],
    )


class FeaturedDeveloperResponse(BaseModel):
    """랜딩에 노출하는 추천 개발자 한 명.

    로그인 전에도 보이는 공개 정보라 표시 이름과 프로필 사진까지만 담는다.
    """

    developerId: int = Field(
        description="개발자 식별자. 사용자 식별자가 아니다.",
        examples=[3],
    )
    name: str = Field(description="화면에 표시하는 이름.", examples=["이준"])
    profileImageFileId: int | None = Field(
        description=(
            "직접 올린 프로필 사진의 파일 식별자. 올린 사진이 없으면 null이고, "
            "그때 화면은 `profileImageUrl`의 카카오 사진이나 이름 첫 글자를 보여준다."
        ),
        examples=[None],
    )
    profileImageUrl: str | None = Field(
        description=(
            "화면에 그대로 거는 프로필 사진 주소. 계정의 `profileImageUrl`과 같은 방식으로, "
            "저장된 값이 아니라 응답을 만들 때마다 새로 만든다. 직접 올린 사진이 있으면 그 "
            "사진의 임시 주소, 없으면 카카오 프로필 사진 주소, 둘 다 없으면 null이다.\n\n"
            "임시 주소는 한동안만 쓸 수 있다. 만료되면 다시 받아야 하므로 저장해 두고 "
            "오래 쓰지 않는다."
        ),
        examples=[None],
    )
    intro: str = Field(
        description="개발자가 적은 한 줄 소개.",
        examples=["간단한 상담도 받습니다."],
    )
    yearsOfExperience: float | None = Field(
        description="경력 연차. 등록하지 않았으면 null이다.",
        examples=[5.0],
    )
    field: CodeResponse | None = Field(
        description=(
            "주로 담당하는 개발 분야. 코드 분류 `DEV_FIELD`의 항목이며, "
            "등록하지 않았으면 null이다."
        ),
    )
    devRating: float | None = Field(
        description="개발력 평균 평점. 받은 리뷰가 없으면 null이다.",
        examples=[4.4],
    )
    explanatoryRating: float | None = Field(
        description="설명력 평균 평점. 받은 리뷰가 없으면 null이다.",
        examples=[4.9],
    )
    prices: list[TaskPriceResponse] = Field(
        description=(
            "작업 유형별 최저가. 가격표를 등록하지 않은 유형은 빠진다. "
            "무료 상담은 가격표에 없으므로 이 목록에 담기지 않는다."
        ),
    )


class FeaturedDeveloperListResponse(BaseModel):
    """추천 개발자 목록.

    노출 수가 적고 고정이라 페이지를 넘길 일이 없어 content만 담는다.
    페이지를 넘기는 목록은 기준 API의 목록 응답 형태를 쓴다.
    """

    content: list[FeaturedDeveloperResponse] = Field(
        description="추천 개발자 목록. 노출할 개발자가 없으면 빈 배열이다.",
    )


class KakaoLoginRequest(BaseModel):
    """카카오 로그인 요청."""

    code: str = Field(
        description="카카오 로그인 화면에서 받은 인가 코드. 한 번만 쓸 수 있다.",
        examples=["Xk9f2LmQ7t"],
    )
    redirectUri: str = Field(
        description="인가 코드를 받을 때 쓴 주소. 그때와 같은 값이어야 한다.",
        examples=["https://komit.example.com/login/kakao"],
    )


class LoginUserResponse(BaseModel):
    """로그인 직후 화면을 그리는 데 필요한 최소 사용자 정보."""

    userId: int = Field(
        description="사용자 식별자. 개발자 식별자가 아니다.",
        examples=[12],
    )
    name: str = Field(
        description="화면에 표시하는 이름. 처음에는 카카오 닉네임이 들어간다.",
        examples=["김미미"],
    )
    profileImageFileId: int | None = Field(
        description=(
            "직접 올린 프로필 사진의 파일 식별자. 올린 사진이 없으면 null이고, "
            "그때 화면은 이름 첫 글자를 동그라미에 넣어 보여준다."
        ),
        examples=[None],
    )
    hasDevProfile: bool = Field(
        description=(
            "개발자 프로필 보유 여부. 마이페이지가 이 값으로 「프로필 생성」과 "
            "「프로필 편집」을 가른다. 계정이 개발자로 바뀌는 것이 아니라 역할이 하나 "
            "늘어난 것이다. (`R-ACC-08`)"
        ),
        examples=[False],
    )


ACCESS_TOKEN_EXPIRES_IN_SECONDS = 900  # R-AUTH-01


class TokenResponse(BaseModel):
    """토큰 발급 응답. 재발급도 같은 형태를 쓴다."""

    accessToken: str = Field(
        description=(
            "API 호출에 쓰는 토큰. `Authorization` 헤더에 `Bearer <accessToken>` "
            "형태로 실어 보낸다. 사용자 id와 만료 시각만 들어 있어, 이름이나 역할이 "
            "필요하면 내 정보 조회로 받는다."
        ),
        examples=["eyJhbGciOiJIUzI1NiJ9.example"],
    )
    refreshToken: str = Field(
        description=(
            "액세스 토큰을 다시 받을 때 쓰는 토큰. 재발급하면 이전 값은 못 쓴다."
        ),
        examples=["f8c1d2e3-4b5a-6789-abcd-ef0123456789"],
    )
    expiresIn: int = Field(
        description="액세스 토큰이 유효한 시간. 초 단위이며 R-AUTH-01을 따른다.",
        examples=[ACCESS_TOKEN_EXPIRES_IN_SECONDS],
    )


class KakaoLoginResponse(TokenResponse):
    """카카오 로그인 응답. 토큰 응답에 가입 여부와 사용자 정보를 더한다."""

    isNewUser: bool = Field(
        description="가입을 완료하지 않았는지. 첫 로그인 또는 중단한 가입이면 true다.",
        examples=[False],
    )
    user: LoginUserResponse = Field(description="로그인한 사용자.")


class RequiredTermsResponse(BaseModel):
    """가입 화면에서 확인하는 필수 약관 동의 상태. settings의 네 필드다."""

    isEscrowTermsAgreed: bool = Field(description="에스크로 약관 동의 여부.")
    isPrivacyCollectAgreed: bool = Field(description="개인정보 수집 동의 여부.")
    isPrivacyThirdPartyAgreed: bool = Field(description="개인정보 제3자 제공 동의 여부.")
    isServiceTermsAgreed: bool = Field(description="서비스 이용약관 동의 여부.")


class AgreeRequiredTermsRequest(BaseModel):
    """가입을 계속하기 위해 각각 명시적으로 동의하는 필수 약관."""

    isEscrowTermsAgreed: bool = Field(description="에스크로 약관 동의.")
    isPrivacyCollectAgreed: bool = Field(description="개인정보 수집 동의.")
    isPrivacyThirdPartyAgreed: bool = Field(description="개인정보 제3자 제공 동의.")
    isServiceTermsAgreed: bool = Field(description="서비스 이용약관 동의.")


class RefreshTokenRequest(BaseModel):
    """액세스 토큰 재발급 요청."""

    refreshToken: str = Field(
        description="로그인할 때 받은 리프레시 토큰.",
        examples=["f8c1d2e3-4b5a-6789-abcd-ef0123456789"],
    )


class MeResponse(BaseModel):
    """모든 화면의 헤더를 채우는 내 정보."""

    userId: int = Field(
        description="사용자 식별자. 개발자 식별자가 아니다.",
        examples=[12],
    )
    name: str = Field(description="화면에 표시하는 이름.", examples=["김미미"])
    profileImageFileId: int | None = Field(
        description=(
            "직접 올린 프로필 사진의 파일 식별자. 올린 사진이 없으면 null이다."
        ),
        examples=[512],
    )
    profileImageUrl: str | None = Field(
        description=(
            "헤더 아바타에 그대로 거는 프로필 사진 주소. 직접 올린 사진이 있으면 "
            "그 사진의 주소이고, 없으면 카카오 사진 주소다. 둘 다 없으면 null이며 "
            "이때 화면은 이름 첫 글자를 동그라미에 넣어 보여준다.\n\n"
            "직접 올린 사진의 주소는 한동안만 쓸 수 있는 임시 주소다. 만료되면 다시 "
            "받아야 하므로 저장해 두고 오래 쓰지 않는다."
        ),
        examples=["https://cdn.komit.example.com/profiles/512.png"],
    )
    hasDevProfile: bool = Field(
        description=(
            "개발자 프로필 보유 여부. 마이페이지가 이 값으로 「프로필 생성」과 "
            "「프로필 편집」을 가른다."
        ),
        examples=[False],
    )
    developerId: int | None = Field(
        description=(
            "내 개발자 식별자. 개발자 프로필이 없으면 null이다. 방의 "
            "`developerId`와 견주어 '받은 의뢰'를 가린다 — 같으면 내가 개발자로 들어간 "
            "방이고, 다르거나 이 값이 null이면 내가 의뢰인인 방이다."
        ),
        examples=[None],
    )
    unreadNotificationCount: int = Field(
        description="아직 읽지 않은 알림 수. 헤더 알림 배지에 쓴다.",
        examples=[3],
    )


# --------------------------------------------------------------------------- #
# 영역 1 — 실패 응답 문구
# --------------------------------------------------------------------------- #

# 로그인 실패는 계정이 있는지 없는지 구분해 알려주지 않는다. 한 문구로 묶는다.
KAKAO_AUTH_FAILED = ("KAKAO_AUTH_FAILED", "로그인하지 못했습니다. 다시 시도해 주세요.")
KAKAO_UNAVAILABLE = (
    "KAKAO_UNAVAILABLE",
    "카카오 서비스에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.",
)
REFRESH_TOKEN_INVALID = (
    "REFRESH_TOKEN_INVALID",
    "로그인이 만료되었습니다. 다시 로그인해 주세요.",
)
UNAUTHORIZED = ("UNAUTHORIZED", "로그인이 필요합니다.")

# featured-developers의 size는 1~10이라 공용 예시(size ≤ 100)와 한계값이 다르다.
FEATURED_SIZE_ERROR_EXAMPLE = {
    "code": "INVALID_REQUEST",
    "message": "요청 형식이 올바르지 않습니다.",
    "errors": [{"field": "size", "reason": "10 이하여야 합니다."}],
}

# 카카오 로그인은 code·redirectUri가 둘 다 필수다. 빈 바디를 보내면 두 항목이 함께 온다.
KAKAO_LOGIN_ERROR_EXAMPLE = {
    "code": "INVALID_REQUEST",
    "message": "요청 형식이 올바르지 않습니다.",
    "errors": [
        {"field": "code", "reason": "필수 항목입니다."},
        {"field": "redirectUri", "reason": "필수 항목입니다."},
    ],
}

# 토큰 재발급은 refreshToken 하나만 받고, 그것이 필수다.
TOKEN_REFRESH_ERROR_EXAMPLE = {
    "code": "INVALID_REQUEST",
    "message": "요청 형식이 올바르지 않습니다.",
    "errors": [{"field": "refreshToken", "reason": "필수 항목입니다."}],
}


# --------------------------------------------------------------------------- #
# 영역 1 — 목 데이터
# --------------------------------------------------------------------------- #

WEEK_START_AT = "2026-09-07T00:00:00+09:00"

FEATURED_DEVELOPERS = [
    {
        "developerId": 3,
        "name": "이준",
        "profileImageFileId": None,
        "profileImageUrl": None,
        "intro": "간단한 상담도 받습니다.",
        "yearsOfExperience": 5.0,
        "field": {"codeId": 42, "codeName": "DB 연결"},
        "devRating": 4.4,
        "explanatoryRating": 4.9,
        "prices": [
            {"taskType": "CONSULT", "price": 50000},
            {"taskType": "DEVELOP", "price": 100000},
        ],
    },
    {
        "developerId": 7,
        "name": "한서",
        "profileImageFileId": None,
        "profileImageUrl": None,
        "intro": "급한 장애 대응 잘합니다.",
        "yearsOfExperience": 6.0,
        "field": {"codeId": 47, "codeName": "버그 잡기"},
        "devRating": 4.7,
        "explanatoryRating": 4.1,
        "prices": [
            {"taskType": "CONSULT", "price": 60000},
            {"taskType": "DEVELOP", "price": 120000},
        ],
    },
    {
        "developerId": 11,
        "name": "박선",
        "profileImageFileId": None,
        "profileImageUrl": None,
        "intro": "상담으로 먼저 방향을 잡아드립니다.",
        "yearsOfExperience": 8.0,
        "field": {"codeId": 46, "codeName": "비용 진단"},
        "devRating": 4.9,
        "explanatoryRating": 3.4,
        "prices": [
            {"taskType": "CONSULT", "price": 80000},
            {"taskType": "DEVELOP", "price": 150000},
        ],
    },
]

# 로그인 실패 사례를 재현하는 인가 코드. 속성을 늘리지 않고 code 값으로만 고른다.
INVALID_CODE = "invalid-code"
UNAVAILABLE_CODE = "kakao-unavailable"
NEW_USER_CODE = "new-user"

# 재발급 실패 사례를 재현하는 리프레시 토큰.
EXPIRED_REFRESH_TOKEN = "expired-refresh-token"

ACCESS_TOKEN = "eyJhbGciOiJIUzI1NiJ9.example"
REFRESH_TOKEN = "f8c1d2e3-4b5a-6789-abcd-ef0123456789"
REISSUED_REFRESH_TOKEN = "9a2b7c40-1d3e-4f56-8a9b-0c1d2e3f4a5b"

SUMMARY_EXAMPLE = {"weeklyInquiryRoomCount": 412, "weekStartAt": WEEK_START_AT}

FEATURED_EXAMPLE = {"content": FEATURED_DEVELOPERS[:1]}

LOGIN_USER = {
    "userId": 12,
    "name": "김미미",
    "profileImageFileId": None,
    "hasDevProfile": False,
}

LOGIN_EXAMPLE = {
    "accessToken": ACCESS_TOKEN,
    "refreshToken": REFRESH_TOKEN,
    "expiresIn": ACCESS_TOKEN_EXPIRES_IN_SECONDS,
    "isNewUser": False,
    "user": LOGIN_USER,
}

REQUIRED_TERMS_PENDING_EXAMPLE = {
    "isEscrowTermsAgreed": False,
    "isPrivacyCollectAgreed": False,
    "isPrivacyThirdPartyAgreed": False,
    "isServiceTermsAgreed": False,
}
REQUIRED_TERMS_AGREED_EXAMPLE = {
    field: True for field in REQUIRED_TERMS_PENDING_EXAMPLE
}
REQUIRED_TERMS_INVALID_EXAMPLE = {
    "code": "INVALID_REQUEST",
    "message": "요청 형식이 올바르지 않습니다.",
    "errors": [{"field": "isEscrowTermsAgreed", "reason": "동의가 필요합니다."}],
}

TOKEN_EXAMPLE = {
    "accessToken": ACCESS_TOKEN,
    "refreshToken": REISSUED_REFRESH_TOKEN,
    "expiresIn": ACCESS_TOKEN_EXPIRES_IN_SECONDS,
}

ME_EXAMPLE = {
    "userId": 12,
    "name": "김미미",
    "profileImageFileId": 512,
    "profileImageUrl": "https://cdn.komit.example.com/profiles/512.png",
    "hasDevProfile": True,
    "developerId": 9,
    "unreadNotificationCount": 3,
}

KAKAO_AUTH_FAILED_EXAMPLE = {
    "code": "KAKAO_AUTH_FAILED",
    "message": "로그인하지 못했습니다. 다시 시도해 주세요.",
}

KAKAO_UNAVAILABLE_EXAMPLE = {
    "code": "KAKAO_UNAVAILABLE",
    "message": "카카오 서비스에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.",
}

REFRESH_TOKEN_INVALID_EXAMPLE = {
    "code": "REFRESH_TOKEN_INVALID",
    "message": "로그인이 만료되었습니다. 다시 로그인해 주세요.",
}

UNAUTHORIZED_EXAMPLE = {"code": "UNAUTHORIZED", "message": "로그인이 필요합니다."}

# 영역 1의 모든 엔드포인트가 함께 쓰는 실패 응답 묶음.
AREA1_ERRORS: dict = {
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
}

AREA1_UNAUTHORIZED: dict = {
    401: {
        "model": ErrorResponse,
        "description": "인증되지 않음",
        "content": {"application/json": {"example": UNAUTHORIZED_EXAMPLE}},
    },
}


# --------------------------------------------------------------------------- #
# 영역 1 — 랜딩
# --------------------------------------------------------------------------- #


@router.get(
    "/landing/summary",
    response_model=LandingSummaryResponse,
    tags=["1 랜딩 · 로그인"],
    responses={
        200: {"content": {"application/json": {"example": SUMMARY_EXAMPLE}}},
        500: AREA1_ERRORS[500],
    },
    summary="랜딩 지표 조회",
    description=(
        "**화면 — `1_랜딩홈` › 히어로**\n\n"
        "랜딩 히어로 아래에 붙는 '이번 주 상담 N건' 한 줄을 내려준다. "
        "로그인하지 않은 방문자도 보는 공개 정보다.\n\n"
        "- **200**: 항상. 이번 주에 열린 문의 방이 하나도 없으면 0을 반환한다. "
        "0은 오류가 아니며, 숨길지 그대로 보여줄지는 화면이 정한다\n"
        "- **500 / INTERNAL_ERROR**: 지표를 만들지 못했을 때. 이 줄은 랜딩의 핵심이 "
        "아니므로 실패해도 페이지 전체를 막지 않고 해당 줄만 비운다"
    ),
)
def get_landing_summary() -> LandingSummaryResponse:
    return LandingSummaryResponse(**SUMMARY_EXAMPLE)


@router.get(
    "/landing/featured-developers",
    response_model=FeaturedDeveloperListResponse,
    tags=["1 랜딩 · 로그인"],
    responses={
        200: {"content": {"application/json": {"example": FEATURED_EXAMPLE}}},
        422: {
            "model": ValidationErrorResponse,
            "description": "요청 스키마 유효성 검증 실패",
            "content": {"application/json": {"example": FEATURED_SIZE_ERROR_EXAMPLE}},
        },
        500: AREA1_ERRORS[500],
    },
    summary="추천 개발자 목록 조회",
    description=(
        "**화면 — `1_랜딩홈` › 추천 개발자**\n\n"
        "랜딩의 '추천 개발자' 카드에 쓰는 목록이다. 로그인하지 않은 방문자도 보는 "
        "공개 정보라 표시 이름과 프로필 사진까지만 담는다.\n\n"
        "카드에는 무료 상담 가격이 따로 표시되지 않는다. 무료 상담은 가격표에 없는 "
        "고정 정책이고, 카드 아래 '상담 시작하기' 버튼이 그 자리를 대신한다.\n\n"
        "- **200**: 항상. 노출할 개발자가 없으면 `content`가 빈 배열이다. 평점은 "
        "리뷰가 없으면 null이고, 경력 연차와 분야도 등록 전이면 null이다\n"
        "- **422 / INVALID_REQUEST**: `size`가 1 미만이거나 10 초과일 때\n"
        "- **500 / INTERNAL_ERROR**: 목록을 만들지 못했을 때"
    ),
)
def get_featured_developers(
    size: Annotated[
        int,
        Query(
            ge=1,
            le=10,
            description="내려받을 개발자 수. 랜딩은 3명을 쓴다.",
            examples=[3],
        ),
    ] = 3,
) -> FeaturedDeveloperListResponse:
    return FeaturedDeveloperListResponse(content=FEATURED_DEVELOPERS[:size])


# --------------------------------------------------------------------------- #
# 영역 1 — 인증
# --------------------------------------------------------------------------- #


@router.post(
    "/auth/kakao",
    response_model=KakaoLoginResponse,
    tags=["1 랜딩 · 로그인"],
    responses={
        200: {"content": {"application/json": {"example": LOGIN_EXAMPLE}}},
        401: {
            "model": ErrorResponse,
            "description": "카카오 인가 코드로 로그인하지 못함",
            "content": {"application/json": {"example": KAKAO_AUTH_FAILED_EXAMPLE}},
        },
        422: {
            "model": ValidationErrorResponse,
            "description": "요청 스키마 유효성 검증 실패",
            "content": {"application/json": {"example": KAKAO_LOGIN_ERROR_EXAMPLE}},
        },
        500: AREA1_ERRORS[500],
        503: {
            "model": ErrorResponse,
            "description": "카카오 서비스에 연결하지 못함",
            "content": {"application/json": {"example": KAKAO_UNAVAILABLE_EXAMPLE}},
        },
    },
    summary="카카오 로그인",
    description=(
        "**화면 — `1_로그인` › 카카오로 시작하기**\n\n"
        "카카오 로그인 화면에서 받은 인가 코드로 로그인한다. 처음 들어온 사용자라면 "
        "가입 진행 상태로 저장하고 `isNewUser`를 true로 돌려준다. 중단한 가입으로 "
        "다시 로그인해도 true다. 가입 화면에서 필수 약관에 "
        "명시적으로 동의한 뒤 계좌 등록을 선택하거나 건너뛴다.\n\n"
        "가입 완료 요청 시 에스크로의 `createMemberAccount` API로 SSAFY 금융망 "
        "`userKey`와 개인 계좌 발급을 요청한 뒤, 결과를 Komit Service DB에 저장한다. "
        "이 금융 정보까지 "
        "준비되어야 가입이 완료된 것으로 처리한다. 기존 사용자의 로그인에서는 다시 "
        "발급하지 않는다.\n\n"
        "로그인만으로 `settings`의 필수 약관 동의 값은 바뀌지 않는다. "
        "동의 화면이 `PUT /api/settings/terms`를 호출해야 값이 기록된다. "
        "개발자 판매자 약관은 개발자 프로필을 만들 때 별도로 처리한다.\n\n"
        "응답의 `accessToken`·`refreshToken`·`expiresIn`은 코밋 서버가 발급만 하는 값이라 "
        "DB에 저장하지 않고 ERD에도 없다(`mock/AGENTS.md` ERD 예외). ERD의 "
        "`users.kakao_refresh_token`은 카카오 API를 부를 때 쓰는 카카오 쪽 토큰으로, 여기 "
        "`refreshToken`과 다르다. 두 토큰의 형식과 만료는 `docs/jwt-samples.md`를, "
        "리프레시 토큰 저장은 `docs/BACKEND_CONVENTIONS.md` 6.4(Redis, TTL 만료)를 따른다.\n\n"
        "- **200**: 로그인에 성공했을 때. 가입을 마치지 않았으면 `isNewUser`가 true다\n"
        "- **401 / KAKAO_AUTH_FAILED**: `code`가 `invalid-code`일 때. 인가 코드가 "
        "만료되었거나 이미 쓴 값이다. 계정이 있는지 없는지는 구분해 알려주지 않는다\n"
        "- **422 / INVALID_REQUEST**: `code`나 `redirectUri`가 없을 때\n"
        "- **500 / INTERNAL_ERROR**: 로그인을 처리하지 못했을 때\n"
        "- **503 / KAKAO_UNAVAILABLE**: `code`가 `kakao-unavailable`일 때. 카카오 쪽 "
        "문제라 잠시 뒤 다시 시도하면 된다\n\n"
        "`code`가 `new-user`이면 새로 가입한 응답을, 그 밖의 값이면 기존 사용자로 "
        "로그인한 응답을 준다."
    ),
)
def login_with_kakao(request: KakaoLoginRequest) -> KakaoLoginResponse | JSONResponse:
    if request.code == INVALID_CODE:
        return _error_response(401, KAKAO_AUTH_FAILED)
    if request.code == UNAVAILABLE_CODE:
        return _error_response(503, KAKAO_UNAVAILABLE)

    return KakaoLoginResponse(
        accessToken=ACCESS_TOKEN,
        refreshToken=REFRESH_TOKEN,
        expiresIn=ACCESS_TOKEN_EXPIRES_IN_SECONDS,
        isNewUser=request.code == NEW_USER_CODE,
        user=LoginUserResponse(**LOGIN_USER),
    )


@router.get(
    "/settings/terms",
    response_model=RequiredTermsResponse,
    tags=["1 랜딩 · 로그인"],
    responses={
        200: {"content": {"application/json": {"example": REQUIRED_TERMS_AGREED_EXAMPLE}}},
        **AREA1_UNAUTHORIZED,
        500: AREA1_ERRORS[500],
    },
    summary="필수 약관 동의 상태 조회",
    description=(
        "**화면 — 가입 › 약관 동의**\n\n"
        "로그인한 자신의 `settings`에 저장된 필수 약관 동의 네 항목을 조회한다. "
        "가입 도중 화면을 닫았다가 다시 들어와도 미동의 상태를 확인할 수 있다. "
        "사용자 식별자는 토큰에서만 읽는다.\n\n"
        "- **200**: 현재 동의 상태를 반환한다\n"
        "- **401 / UNAUTHORIZED**: 토큰이 없거나 만료·위조되었을 때\n"
        "- **500 / INTERNAL_ERROR**: 상태를 읽지 못했을 때"
    ),
)
def get_required_terms() -> RequiredTermsResponse:
    # 목은 상태를 저장하지 않으므로 이미 동의한 사용자 예시를 고정 반환한다.
    return RequiredTermsResponse(**REQUIRED_TERMS_AGREED_EXAMPLE)


@router.put(
    "/settings/terms",
    response_model=RequiredTermsResponse,
    tags=["1 랜딩 · 로그인"],
    responses={
        200: {"content": {"application/json": {"example": REQUIRED_TERMS_AGREED_EXAMPLE}}},
        **AREA1_UNAUTHORIZED,
        422: {
            "model": ValidationErrorResponse,
            "description": "필수 약관 누락 또는 미동의",
            "content": {"application/json": {"example": REQUIRED_TERMS_INVALID_EXAMPLE}},
        },
        500: AREA1_ERRORS[500],
    },
    summary="필수 약관 동의",
    description=(
        "**화면 — 가입 › 약관 동의 › 계속하기**\n\n"
        "본인이 필수 약관 네 항목을 각각 확인하고 동의했을 때만 저장한다. "
        "하나라도 없거나 false이면 저장하지 않는다. 같은 동의를 다시 보내도 결과는 같다. "
        "`settings.is_seller_terms_agreed`는 이 요청으로 바꾸지 않는다. "
        "사용자 식별자는 토큰에서만 읽는다.\n\n"
        "- **200**: 네 항목의 동의가 저장되었을 때\n"
        "- **401 / UNAUTHORIZED**: 토큰이 없거나 만료·위조되었을 때\n"
        "- **422 / INVALID_REQUEST**: 항목이 없거나 false일 때\n"
        "- **500 / INTERNAL_ERROR**: 저장하지 못했을 때"
    ),
)
def agree_required_terms(request: AgreeRequiredTermsRequest) -> RequiredTermsResponse | JSONResponse:
    missing = [
        FieldError(field=name, reason="동의가 필요합니다.")
        for name, agreed in request.model_dump().items()
        if not agreed
    ]
    if missing:
        return JSONResponse(
            status_code=422,
            content=jsonable_encoder(
                ValidationErrorResponse(
                    code=INVALID_REQUEST[0], message=INVALID_REQUEST[1], errors=missing
                )
            ),
        )
    return RequiredTermsResponse(**REQUIRED_TERMS_AGREED_EXAMPLE)


SIGNUP_PENDING = ("SIGNUP_PENDING", "가입이 아직 완료되지 않았습니다.")
TERMS_NOT_AGREED = ("TERMS_NOT_AGREED", "필수 약관에 동의해 주세요.")


@router.get(
    "/auth/signup/completion",
    status_code=204,
    response_class=Response,
    tags=["1 랜딩 · 로그인"],
    responses={
        204: {"description": "가입 완료 상태. 본문 없음."},
        **AREA1_UNAUTHORIZED,
        409: {"model": ErrorResponse, "description": "가입 진행 중",
              "content": {"application/json": {"example": {"code": SIGNUP_PENDING[0], "message": SIGNUP_PENDING[1]}}}},
        500: AREA1_ERRORS[500],
    },
    summary="가입 완료 상태 확인",
    description=(
        "가입 완료 화면에서 서버의 완료 상태를 확인한다. 사용자 식별자는 토큰에서 읽는다.\n\n"
        "- **204**: 가입 완료 요청이 성공해 서비스 이용 상태일 때\n"
        "- **401 / UNAUTHORIZED**: 토큰이 없거나 유효하지 않을 때\n"
        "- **409 / SIGNUP_PENDING**: 가입 진행 중일 때"
    ),
)
def get_signup_completion() -> Response:
    return Response(status_code=204)


@router.post(
    "/auth/signup/completion",
    status_code=204,
    response_class=Response,
    tags=["1 랜딩 · 로그인"],
    responses={
        204: {"description": "가입 완료. 다시 요청해도 본문 없이 204."},
        **AREA1_UNAUTHORIZED,
        409: {"model": ErrorResponse, "description": "필수 약관 미동의",
              "content": {"application/json": {"example": {"code": TERMS_NOT_AGREED[0], "message": TERMS_NOT_AGREED[1]}}}},
        503: {"model": ErrorResponse, "description": "Escrow 발급 실패 또는 결과 불명확",
              "content": {"application/json": {"example": {"code": "ESCROW_UNAVAILABLE", "message": "지금은 계좌를 확인할 수 없습니다. 잠시 뒤 다시 시도해 주세요."}}}},
        500: AREA1_ERRORS[500],
    },
    summary="가입 완료",
    description=(
        "**화면 — 가입 › 계좌 등록 제안 › 다음에 하기 또는 1원 인증 성공**\n\n"
        "필수 약관에 모두 동의하고 회원 발급 계좌가 준비되면 가입을 완료한다. "
        "계좌 등록을 건너뛰어도 회원 발급 계좌는 준비되어야 한다. 같은 요청은 멱등이다.\n\n"
        "- **204**: 가입이 완료되었거나 이미 완료된 경우\n"
        "- **401 / UNAUTHORIZED**: 토큰이 없거나 유효하지 않을 때\n"
        "- **409 / TERMS_NOT_AGREED**: 필수 약관에 아직 동의하지 않았을 때\n"
        "- **503 / ESCROW_UNAVAILABLE**: 계좌 발급을 확정할 수 없을 때. 가입 진행 상태를 유지한다"
    ),
)
def complete_signup() -> Response:
    return Response(status_code=204)


@router.post(
    "/auth/token/refresh",
    response_model=TokenResponse,
    tags=["1 랜딩 · 로그인"],
    responses={
        200: {"content": {"application/json": {"example": TOKEN_EXAMPLE}}},
        401: {
            "model": ErrorResponse,
            "description": "리프레시 토큰을 쓸 수 없음",
            "content": {
                "application/json": {"example": REFRESH_TOKEN_INVALID_EXAMPLE}
            },
        },
        422: {
            "model": ValidationErrorResponse,
            "description": "요청 스키마 유효성 검증 실패",
            "content": {"application/json": {"example": TOKEN_REFRESH_ERROR_EXAMPLE}},
        },
        500: AREA1_ERRORS[500],
    },
    summary="액세스 토큰 재발급",
    description=(
        "**화면 — 전 영역 공통. 액세스 토큰이 만료되는 어느 화면에서나 부른다**\n\n"
        "액세스 토큰이 만료되었을 때 리프레시 토큰으로 새 액세스 토큰을 받는다. "
        "재발급하면 리프레시 토큰도 함께 새 값으로 바뀌고 이전 값은 쓸 수 없다.\n\n"
        "응답의 `accessToken`·`refreshToken`·`expiresIn`은 코밋 서버가 발급만 하는 값이라 "
        "DB에 저장하지 않고 ERD에도 없다(`mock/AGENTS.md` ERD 예외). ERD의 "
        "`users.kakao_refresh_token`은 카카오 API를 부를 때 쓰는 카카오 쪽 토큰으로, 여기 "
        "`refreshToken`과 다르다. 두 토큰의 형식과 만료는 `docs/jwt-samples.md`를, "
        "리프레시 토큰 저장은 `docs/BACKEND_CONVENTIONS.md` 6.4(Redis, TTL 만료)를 따른다.\n\n"
        "- **200**: 리프레시 토큰이 아직 살아 있을 때\n"
        "- **401 / REFRESH_TOKEN_INVALID**: `refreshToken`이 `expired-refresh-token`일 "
        "때. 만료되었거나 이미 다시 발급받아 쓸 수 없는 값이다. 이때는 로그인 화면으로 "
        "보낸다\n"
        "- **422 / INVALID_REQUEST**: `refreshToken`이 없을 때\n"
        "- **500 / INTERNAL_ERROR**: 재발급을 처리하지 못했을 때"
    ),
)
def refresh_access_token(request: RefreshTokenRequest) -> TokenResponse | JSONResponse:
    if request.refreshToken == EXPIRED_REFRESH_TOKEN:
        return _error_response(401, REFRESH_TOKEN_INVALID)

    return TokenResponse(
        accessToken=ACCESS_TOKEN,
        refreshToken=REISSUED_REFRESH_TOKEN,
        expiresIn=ACCESS_TOKEN_EXPIRES_IN_SECONDS,
    )


@router.post(
    "/auth/logout",
    status_code=204,
    response_class=Response,
    tags=["1 랜딩 · 로그인"],
    responses={
        204: {"description": "로그아웃됨. 응답 본문이 없다."},
        **AREA1_UNAUTHORIZED,
        500: AREA1_ERRORS[500],
    },
    summary="로그아웃",
    description=(
        "**화면 — `8_아바타메뉴` › 로그아웃. 전 영역의 헤더에서 열린다**\n\n"
        "로그인 상태를 끝낸다. 이후 이 리프레시 토큰으로는 토큰을 다시 받을 수 "
        "없다.\n\n"
        "카카오 연결까지 끊지는 않는다. 연결 끊기는 탈퇴에서 다룬다.\n\n"
        "- **204**: 로그아웃했을 때. 이미 로그아웃된 상태로 다시 요청해도 같은 응답을 "
        "주므로 화면은 성공으로 다루면 된다\n"
        "- **401 / UNAUTHORIZED**: 액세스 토큰이 없거나 만료되었을 때\n"
        "- **500 / INTERNAL_ERROR**: 처리하지 못했을 때"
    ),
)
def logout() -> Response:
    return Response(status_code=204)


@router.get(
    "/users/me",
    response_model=MeResponse,
    tags=["1 랜딩 · 로그인"],
    responses={
        200: {"content": {"application/json": {"example": ME_EXAMPLE}}},
        **AREA1_UNAUTHORIZED,
        500: AREA1_ERRORS[500],
    },
    summary="내 정보 조회",
    description=(
        "**화면 — 전 영역 헤더 › 아바타 · 알림 배지**\n\n"
        "모든 화면의 헤더를 채우는 내 정보다. 아바타와 알림 배지를 한 번에 받는다.\n\n"
        "`profileImageUrl`은 직접 올린 사진, 카카오 사진 순서로 고른 주소다. 둘 다 "
        "없으면 null이며 화면은 이름 첫 글자를 보여준다. 직접 올린 사진의 주소는 "
        "임시 주소이므로 화면을 다시 열 때 이 API에서 새로 받는다.\n\n"
        "`hasDevProfile`과 `developerId`는 헤더가 쓰지 않는다. 헤더에 의뢰인·개발자를 "
        "가르는 전환은 없다. 두 값은 마이페이지가 「프로필 생성」과 「프로필 편집」을 "
        "가를 때, 그리고 방 목록이 '받은 의뢰'를 가릴 때 쓴다.\n\n"
        "- **200**: 로그인되어 있을 때. 개발자 프로필이 없으면 `hasDevProfile`이 "
        "false이고 `developerId`가 null이다. 읽지 않은 알림이 없으면 배지 수가 0이다\n"
        "- **401 / UNAUTHORIZED**: 액세스 토큰이 없거나 만료되었을 때. 이때는 로그인 "
        "화면으로 보낸다\n"
        "- **500 / INTERNAL_ERROR**: 조회하지 못했을 때"
    ),
)
def get_me() -> MeResponse:
    return MeResponse(**ME_EXAMPLE)


# =========================================================================== #
# 영역 2 — 사전점검
#
# 설문 4단계로 상황을 받고, 사용자가 직접 돌린 검사 결과를 올리면 진단서를 만든다.
# 진단서에서 고칠 항목을 고르면 요청서로 넘어간다.
#
# 화면: 2_설문1 · 2_설문2 · 2_설문3 · 2_설문4 · 2_진단서 · 2_요구사항선택
#       2_클릭_직접해결했어요 · 2_요청서 · 2_의뢰인_요청서수정
#
# 요청서는 따로 저장되는 문서가 아니다. 고르고 다듬은 요구사항과 그 확인 문장을 모아
# 부르는 이름이라, 조회는 결함 목록 API 가 그대로 맡고 저장은 요청서 저장 API 하나가
# 통째로 받는다. 요구사항 줄이나 확인 문장마다 API 를 두지 않는다.
#
# 요청서에 '보내기'는 없다. 요청서는 방에 걸려 있어 개발자가 언제든 같은 것을 보므로,
# 저장하면 그 자리에서 최신 내용이 된다.
#
# 함께 쓰는 API (다른 영역에 있다):
#   GET /api/users/me   전 영역 헤더
# =========================================================================== #


# --------------------------------------------------------------------------- #
# 영역 2 — 응답 모델
# --------------------------------------------------------------------------- #


class TechCategoryResponse(BaseModel):
    """프로젝트가 쓰는 기술 한 줄."""

    techCodeId: int = Field(description="기술 항목 식별자.", examples=[21])
    codeName: str = Field(
        description="화면에 그대로 보여주는 기술 이름.",
        examples=["Next.js"],
    )


class UnfinishedSurveyResponse(BaseModel):
    """설문을 끝내지 않은 진단 하나. `2_설문1` 아래 「진행 중인 설문」에 쓴다."""

    diagnosisId: int = Field(description="진단 식별자.", examples=[31])
    projectName: str = Field(
        description="프로젝트 이름. 목록에서 이것으로 알아본다.",
        examples=["미미헤어샵 예약"],
    )
    updatedAt: str = Field(
        description=(
            "마지막으로 저장한 시각. 답을 저장할 때마다 갱신된다. 목록은 이 값의 "
            "내림차순으로 온다."
        ),
        examples=["2026-09-16T17:40:00+09:00"],
    )


class UnfinishedSurveyListResponse(BaseModel):
    """끝내지 않은 설문 목록."""

    content: list[UnfinishedSurveyResponse] = Field(
        description="끝내지 않은 설문. 없으면 빈 배열이다."
    )


class DiagnosisProjectResponse(BaseModel):
    """진단이 걸린 프로젝트."""

    projectId: int = Field(description="프로젝트 식별자.", examples=[8])
    name: str = Field(
        description="프로젝트 이름.",
        examples=["수제 비누 가게"],
    )
    description: str | None = Field(
        description="한 줄 설명. 적지 않았으면 null이다.",
        examples=["수제 비누를 파는 작은 온라인 가게"],
    )
    domain: str | None = Field(
        description="연결된 도메인 주소. 없으면 null이다.",
        examples=[None],
    )
    techCategories: list[TechCategoryResponse] = Field(
        description="프로젝트에 쓰인 기술. 검사에서 확인되지 않으면 빈 배열이다.",
    )


class DiagnosisResponse(BaseModel):
    """진단 한 건. 진단서와 요구사항 선택 화면의 머리를 함께 채운다."""

    diagnosisId: int = Field(description="진단 식별자.", examples=[31])
    version: int = Field(
        description="진단 회차. 재진단할 때마다 1씩 오른다.",
        examples=[2],
    )
    project: DiagnosisProjectResponse = Field(description="진단 대상 프로젝트.")
    userInput: str | None = Field(
        description="사용자가 적은 현재 증상. 적지 않았으면 null이다.",
        examples=["결제 버튼을 누르면 가끔 아무 반응이 없어요"],
    )
    readinessScore: int | None = Field(
        description="런칭 준비도 점수. 검사가 끝나기 전이면 null이다.",
        examples=[52],
    )
    scannedAt: str | None = Field(
        description="검사를 실행한 시각. 검사 전이면 null이다.",
        examples=["2026-08-17T09:12:00+09:00"],
    )
    unresolvedIssueCount: int = Field(
        description="아직 해결되지 않은 결함 수.",
        examples=[9],
    )
    totalIssueCount: int = Field(
        description="이번 회차에서 발견된 결함 수.",
        examples=[11],
    )
    resolvedSinceLastVersionCount: int = Field(
        description=(
            "지난 회차에 있었는데 이번 회차에서 사라진 결함 수. 1회차면 0이다."
        ),
        examples=[3],
    )


class SurveyOptionResponse(BaseModel):
    """설문 보기 하나. 고르지 않은 보기도 그대로 내려간다."""

    optionId: int = Field(description="설문 보기 식별자.", examples=[104])
    content: str = Field(
        description="객관식이면 제시된 보기 문구, 주관식이면 사용자가 적은 답.",
        examples=["보안이 걱정돼요"],
    )
    isSelected: bool = Field(
        description="이 보기를 골랐는지. 주관식은 답이 있으면 항상 참이다.",
        examples=[True],
    )


class SurveyQuestionResponse(BaseModel):
    """설문 문항 하나."""

    questionId: int = Field(description="설문 문항 식별자.", examples=[52])
    content: str = Field(
        description="사용자에게 보여줄 질문 문구.",
        examples=["어떤 상태인가요?"],
    )
    answerType: Literal["TEXT", "SINGLE", "MULTI"] = Field(
        description=(
            "답을 받는 방식. TEXT는 주관식, SINGLE은 객관식 단수, MULTI는 객관식 복수다."
        ),
        examples=["MULTI"],
    )
    options: list[SurveyOptionResponse] = Field(
        description="제시된 보기. 주관식이면 답이 들어 있는 행 하나이고, 답 전이면 빈 배열이다.",
    )


class SurveyResponse(BaseModel):
    """진단 한 회차에 딸린 설문지 한 벌."""

    surveyId: int = Field(description="설문 식별자.", examples=[19])
    diagnosisVersion: int = Field(
        description="이 설문을 받은 시점의 진단 회차.",
        examples=[2],
    )
    questions: list[SurveyQuestionResponse] = Field(
        description="설문 문항. 화면에 보여줄 순서대로 내려간다.",
    )
    isDone: bool = Field(
        description=(
            "이 설문을 끝냈는지. 설문4까지 마치고 완료를 부르면 참이 된다. 거짓인 "
            "설문은 `2_설문1` 의 「진행 중인 설문」 목록에 올라간다."
        ),
        examples=[False],
    )


class StartDiagnosisRequest(BaseModel):
    """진단 시작 요청.

    projectId를 주면 그 프로젝트를 다시 진단하고, 주지 않으면 프로젝트를 새로 만든다.
    """

    projectId: int | None = Field(
        default=None,
        description="다시 진단할 프로젝트 식별자. 새로 만들 때는 비운다.",
        examples=[None],
    )
    name: str | None = Field(
        default=None,
        description=(
            "새로 만들 프로젝트 이름. projectId를 주지 않았다면 반드시 채운다."
        ),
        examples=["수제 비누 가게"],
    )
    description: str | None = Field(
        default=None,
        description="프로젝트 한 줄 설명. 적지 않아도 된다.",
        examples=["수제 비누를 파는 작은 온라인 가게"],
    )


class SurveyAnswerRequest(BaseModel):
    """설문 문항 하나에 대한 답."""

    questionId: int = Field(description="답할 설문 문항 식별자.", examples=[52])
    selectedOptionIds: list[int] = Field(
        default_factory=list,
        description=(
            "고른 보기 식별자. 객관식 단수는 하나, 복수는 여럿, 주관식은 빈 배열이다."
        ),
        examples=[[104]],
    )
    text: str | None = Field(
        default=None,
        description="주관식 답. 객관식이면 null이다.",
        examples=[None],
    )


class SaveSurveyAnswersRequest(BaseModel):
    """설문 한 단계의 답 저장 요청."""

    answers: list[SurveyAnswerRequest] = Field(
        description="이 단계에서 답한 문항들. 한 문항만 바꿔도 그 문항만 보내면 된다.",
    )


class InspectionResponse(BaseModel):
    """검사 한 건. 검사중 화면이 이 상태를 확인한다."""

    inspectionId: int = Field(description="검사 식별자.", examples=[27])
    diagnosisVersion: int = Field(
        description="이 검사를 돌린 시점의 진단 회차.",
        examples=[2],
    )
    status: Literal["PENDING", "IN_PROGRESS", "DONE"] = Field(
        description=(
            "검사 진행. PENDING은 대기, IN_PROGRESS는 검사중, DONE이면 진단서를 볼 수 있다."
        ),
        examples=["IN_PROGRESS"],
    )
    readinessScore: int | None = Field(
        description="런칭 준비도 점수. 끝나기 전이면 null이다.",
        examples=[None],
    )
    scannedAt: str | None = Field(
        description="검사를 실행한 시각. 끝나기 전이면 null이다.",
        examples=[None],
    )


class IssueResponse(BaseModel):
    """검사에서 나온 결함 하나. 진단서와 요구사항 선택에서 함께 쓴다."""

    issueId: int = Field(description="결함 식별자.", examples=[311])
    title: str = Field(
        description="결함 내용. 요구사항 선택에서는 이 문장이 요구사항이 된다.",
        examples=["관리자 API에 인증 추가"],
    )
    detail: str | None = Field(
        description="자세한 설명. 없으면 null이다.",
        examples=["로그인한 사람이면 남의 주문 내역까지 열람 가능합니다"],
    )
    location: str | None = Field(
        description="문제가 있는 위치. 코드에서 찾지 못했으면 null이다.",
        examples=["app/api/checkout/route.ts:47"],
    )
    isInput: bool | None = Field(
        description=(
            "사용자가 적은 증상과 관련된 결함인지. 진단서는 참인 것을 위에 따로 보여준다."
        ),
        examples=[False],
    )
    category: CodeResponse = Field(
        description="결함 분류. 보안·위법·결제·기타처럼 화면에 칩으로 붙는다.",
    )
    status: Literal[
        "UNRESOLVED", "RESOLVED", "SUGGESTED", "CONTRACTED", "DISABLED"
    ] = Field(
        description=(
            "결함 처리. UNRESOLVED는 미해결, RESOLVED는 해결, SUGGESTED는 작업으로 "
            "제안됨, CONTRACTED는 계약에 포함됨, DISABLED는 제안이 만료됨이다."
        ),
        examples=["UNRESOLVED"],
    )
    severity: Literal["LOW", "MEDIUM", "HIGH"] = Field(
        description="위험도. LOW는 낮음, MEDIUM은 보통, HIGH는 높음이다.",
        examples=["HIGH"],
    )
    fixedVersion: int | None = Field(
        description="문제가 사라진 것을 확인한 진단 회차. 아직 남아 있으면 null이다.",
        examples=[None],
    )
    originIssueId: int | None = Field(
        description=(
            "지난 회차에서 이어진 결함이면 그 최초 결함 식별자. 이번에 처음 나왔으면 null이다."
        ),
        examples=[None],
    )
    qaCriteria: list[QaCriterionResponse] = Field(
        description=(
            "이 요구사항의 확인 문장. 검사 직후에는 비어 있고, 요청서를 다듬으며 "
            "의뢰인이 채운다. 계약서로 넘어가면 그대로 작업의 완료 조건이 된다."
        ),
    )


class IssueListResponse(BaseModel):
    """결함 목록."""

    content: list[IssueResponse] = Field(
        description="현재 페이지에 속한 결함. 결과가 없으면 빈 배열이다.",
    )
    page: int = Field(description="현재 페이지 번호. 1부터 시작한다.", examples=[1])
    size: int = Field(description="한 페이지에 담긴 최대 항목 수.", examples=[20])
    totalElements: int = Field(
        description="조건에 해당하는 전체 결함 수.",
        examples=[11],
    )
    totalPages: int = Field(description="전체 페이지 수.", examples=[1])


class UpdateIssueStatusRequest(BaseModel):
    """결함 처리 상태 변경 요청."""

    status: Literal["UNRESOLVED", "RESOLVED"] = Field(
        description=(
            "바꿀 상태. RESOLVED는 '직접 해결했어요', UNRESOLVED는 '되돌리기'다. "
            "계약에 들어간 결함은 이 API로 바꾸지 않는다."
        ),
        examples=["RESOLVED"],
    )


class WriteQaCriterionRequest(BaseModel):
    """저장할 확인 문장 하나."""

    qaCriterionId: int | None = Field(
        default=None,
        description="이미 있는 문장이면 그 식별자, 새로 적는 문장이면 null이다.",
        examples=[201],
    )
    testCase: str = Field(
        description="무엇을 해보면 되는지.",
        examples=["관리자 주소로 들어가면 로그인 화면이 뜬다"],
    )


class QaSuggestionResponse(BaseModel):
    """저장 전 확인 문장 후보. testCase는 qa_criteria.test_case에 대응한다."""

    testCase: str = Field(
        min_length=1,
        max_length=500,
        description=(
            "직접 실행해 통과 여부를 판단할 문장. 후보 반환만으로 요청서나 계약서에 "
            "저장되지 않는다. (R-REQ-04, R-CON-22)"
        ),
        examples=["로그인하지 않고 관리자 주소로 들어가면 로그인 화면이 나타난다."],
    )


class QaSuggestionListResponse(BaseModel):
    """확인 문장 후보 목록. default API의 페이지 형식을 따른다."""

    content: list[QaSuggestionResponse] = Field(
        min_length=3,
        max_length=3,
        description="저장 전 확인 문장 후보 세 건. (R-REQ-04, R-CON-22)",
    )
    page: int = Field(description="현재 페이지 번호. 후보는 한 페이지만 반환한다.", examples=[1])
    size: int = Field(description="한 페이지의 후보 개수.", examples=[3])
    totalElements: int = Field(description="전체 후보 개수.", examples=[3])
    totalPages: int = Field(description="전체 페이지 수.", examples=[1])


class ContractQaSuggestionRequest(BaseModel):
    """계약 편집 중인 작업의 확인 문장 후보 생성 요청."""

    issueId: int | None = Field(
        default=None,
        description=(
            "기존 요구사항에 붙은 작업이면 그 식별자. 아직 저장하지 않은 새 작업이면 null이다."
        ),
        examples=[312],
    )
    title: str = Field(
        min_length=1,
        max_length=200,
        description="현재 계약 편집기에 적힌 작업 이름.",
        examples=["관리자 API에 인증 추가"],
    )
    detail: str | None = Field(
        default=None,
        min_length=1,
        max_length=1000,
        description="현재 계약 편집기에 적힌 작업 설명. 적지 않았으면 null이다.",
        examples=["로그인하지 않은 요청은 로그인 화면으로 이동시킨다."],
    )


class WriteRequirementRequest(BaseModel):
    """저장할 요구사항 하나."""

    issueId: int | None = Field(
        default=None,
        description=(
            "검사에서 나왔거나 이미 추가한 요구사항이면 그 식별자, 이번에 새로 적는 "
            "것이면 null이다."
        ),
        examples=[312],
    )
    title: str = Field(
        description="요구사항 문장.",
        examples=["관리자 API에 인증 추가"],
    )
    detail: str | None = Field(
        description="자세한 설명. 적지 않아도 된다.",
        examples=["로그인한 사람이면 남의 주문 내역까지 열람 가능합니다"],
    )
    categoryCodeId: int = Field(description="결함 분류 식별자.", examples=[71])
    severity: Literal["LOW", "MEDIUM", "HIGH"] = Field(
        description="위험도.",
        examples=["HIGH"],
    )
    qaCriteria: list[WriteQaCriterionRequest] = Field(
        description=(
            "이 요구사항의 확인 문장 전부. 보낸 목록에 없는 기존 문장은 지운 것으로 본다."
        ),
    )


class WriteRequirementsRequest(BaseModel):
    """요청서 저장 요청. 화면의 저장 버튼 하나가 이 요청 하나를 보낸다."""

    requirements: list[WriteRequirementRequest] = Field(
        description=(
            "요청서에 담을 요구사항 전부. 보낸 순서가 요청서에 적히는 순서이고, "
            "보낸 목록에 없는 기존 요구사항은 요청서에서 뺀 것으로 본다."
        ),
    )


class CreateIssueRequest(BaseModel):
    """사용자가 직접 적는 요구사항 추가 요청."""

    title: str = Field(
        description="요구사항 문장.",
        examples=["장바구니에서 수량을 바꿀 수 있게 해주세요"],
    )
    detail: str | None = Field(
        default=None,
        description="자세한 설명. 적지 않아도 된다.",
        examples=[None],
    )
    categoryCodeId: int = Field(
        description="결함 분류 식별자.",
        examples=[73],
    )
    severity: Literal["LOW", "MEDIUM", "HIGH"] = Field(
        description="위험도. LOW는 낮음, MEDIUM은 보통, HIGH는 높음이다.",
        examples=["MEDIUM"],
    )


# --------------------------------------------------------------------------- #
# 영역 2 — 실패 응답 문구
# --------------------------------------------------------------------------- #

DIAGNOSIS_NOT_FOUND = ("DIAGNOSIS_NOT_FOUND", "진단을 찾을 수 없습니다.")
ISSUE_NOT_FOUND = ("ISSUE_NOT_FOUND", "요구사항을 찾을 수 없습니다.")
AI_RESPONSE_INVALID = (
    "AI_RESPONSE_INVALID",
    "확인 문장을 만들지 못했습니다. 잠시 후 다시 시도해 주세요.",
)
AI_SERVICE_UNAVAILABLE = (
    "AI_SERVICE_UNAVAILABLE",
    "확인 문장 생성 서비스를 사용할 수 없습니다. 잠시 후 다시 시도해 주세요.",
)
INSPECTION_NOT_DONE = (
    "INSPECTION_NOT_DONE",
    "검사가 아직 끝나지 않았습니다.",
)
INSPECTION_ALREADY_RUNNING = (
    "INSPECTION_ALREADY_RUNNING",
    "이미 검사가 진행 중입니다.",
)
ISSUE_STATUS_CONFLICT = (
    "ISSUE_STATUS_CONFLICT",
    "계약에 포함된 요구사항은 상태를 바꿀 수 없습니다.",
)
ISSUE_STATUS_CONFLICT_EXAMPLE = {
    "code": ISSUE_STATUS_CONFLICT[0],
    "message": ISSUE_STATUS_CONFLICT[1],
}
SCAN_FILE_INVALID = (
    "SCAN_FILE_INVALID",
    "검사 결과 파일을 읽지 못했습니다. 받은 파일 그대로 올려주세요.",
)
DIAGNOSIS_MISMATCH = (
    "DIAGNOSIS_MISMATCH",
    "다른 진단의 결과 파일입니다. 이 진단에서 받은 검사 파일로 다시 검사해 주세요.",
)
SCAN_VERSION_OUTDATED = (
    "SCAN_VERSION_OUTDATED",
    "검사 방법이 업데이트되었습니다. 새 검사 파일을 내려받아 다시 검사해 주세요.",
)
SCAN_VERSION = "0.1.0"
SCAN_FILE_NAME = "komit-scan.md"


def scan_file_body(diagnosis_id: int) -> bytes:
    """내려주는 검사 파일.

    실제 서버는 진단마다 프로젝트 이름과 설문 답을 채워 내려주지만, 목에서는
    그 부분을 고정 문구로 둔다. 검사 기준 본문은 ai/komit-scan/skills 에 있다.

    **진단 번호만은 요청받은 값을 박는다.** 검사 도구가 이 번호를 결과 파일의
    `diagnosisId` 로 옮겨 적고, 올릴 때 서버가 그것을 대조한다. 고정값을 내보내면
    안내대로 따라도 다른 진단의 파일이 되어 업로드가 막힌다.
    """
    return f"""# Komit 검사 파일

이 파일을 쓰시는 AI 코딩 도구에 읽히면 프로젝트를 검사해 결과 파일을 만듭니다.
소스 코드는 컴퓨터 밖으로 나가지 않습니다.

## 진단 정보

- 진단 번호: {diagnosis_id}
- 검사 지시문 판: {SCAN_VERSION}
- 프로젝트: 수제 비누 가게

## 의뢰인이 적은 증상

"결제 버튼을 누르면 가끔 아무 반응이 없어요"

## 어떻게 쓰나요

Claude Code 를 쓰신다면 아래 한 줄이면 됩니다.

    /komit-scan

그 밖의 도구라면 이 파일을 프로젝트에 넣고 이렇게 말해 주세요.

    komit-scan.md 를 읽고 이 프로젝트를 검사해줘

## 검사가 끝나면

`komit-result.md` 가 만들어집니다. 그 파일만 Komit 에 올려 주세요.
파일을 열어 무엇이 담겼는지 먼저 보셔도 됩니다.

## 무엇을 검사하나요

위법 · 보안 · 결제 · 데이터 · 비용 · 인계 · AI 잔재 · 기타 여덟 가지를 봅니다.
검사 기준 전문은 누구나 열어 볼 수 있게 공개되어 있습니다.
""".encode()
SCAN_FILE_INVALID_EXAMPLE = {
    "code": SCAN_FILE_INVALID[0],
    "message": SCAN_FILE_INVALID[1],
}
DIAGNOSIS_MISMATCH_EXAMPLE = {
    "code": DIAGNOSIS_MISMATCH[0],
    "message": DIAGNOSIS_MISMATCH[1],
}
SCAN_VERSION_OUTDATED_EXAMPLE = {
    "code": SCAN_VERSION_OUTDATED[0],
    "message": SCAN_VERSION_OUTDATED[1],
}


# --------------------------------------------------------------------------- #
# 영역 2 — 목 데이터
# --------------------------------------------------------------------------- #

# 실패 사례를 재현하는 진단 식별자. 속성을 늘리지 않고 경로 값으로만 고른다.
DIAGNOSIS_ID = 31
# 막 시작해 아직 아무것도 답하지 않은 진단. 31은 이어서 하는 쪽이라 답이 차 있어,
# 새로 시작한 화면까지 그것을 주면 남의 답이 체크된 채로 뜬다.
NEW_DIAGNOSIS_ID = 33
NOT_FOUND_DIAGNOSIS_ID = 404
# 설문을 아직 끝낼 수 없는 진단 재현용 예약값.
NOT_COMPLETABLE_DIAGNOSIS_ID = 409
SURVEY_NOT_COMPLETABLE = (
    "SURVEY_NOT_COMPLETABLE",
    "아직 끝내지 않은 문항이 있습니다.",
)
RUNNING_DIAGNOSIS_ID = 409
PENDING_DIAGNOSIS_ID = 202

# 실패 사례를 재현하는 결함 식별자.
NOT_FOUND_ISSUE_ID = 404
CONTRACTED_ISSUE_ID = 409

SCANNED_AT = "2026-08-17T09:12:00+09:00"

PROJECT_EXAMPLE = {
    "projectId": 8,
    "name": "수제 비누 가게",
    "description": "수제 비누를 파는 작은 온라인 가게",
    "domain": None,
    "techCategories": [
        {"techCodeId": 21, "codeName": "Next.js"},
        {"techCodeId": 22, "codeName": "Supabase"},
    ],
}

DIAGNOSIS_EXAMPLE = {
    "diagnosisId": DIAGNOSIS_ID,
    "version": 2,
    "project": PROJECT_EXAMPLE,
    "userInput": "결제 버튼을 누르면 가끔 아무 반응이 없어요",
    # ISSUES 의 미해결 결함이 높음 3 · 보통 3 · 낮음 2 이므로
    # 49 - (3 x 3) - (3 x 1) = 37 이다. (R-SCAN-18)
    "readinessScore": 37,
    "scannedAt": SCANNED_AT,
    "unresolvedIssueCount": 8,
    "totalIssueCount": 9,
    "resolvedSinceLastVersionCount": 1,
}

# 막 시작한 진단. 검사를 돌린 적이 없어 점수와 검사 시각이 비어 있다.
NEW_DIAGNOSIS_EXAMPLE = {
    **DIAGNOSIS_EXAMPLE,
    "diagnosisId": NEW_DIAGNOSIS_ID,
    "version": 1,
    "userInput": None,
    "readinessScore": None,
    "scannedAt": None,
    "unresolvedIssueCount": 0,
    "totalIssueCount": 0,
    "resolvedSinceLastVersionCount": 0,
}

UNFINISHED_SURVEY_LIST_EXAMPLE = {
    "content": [
        {
            "diagnosisId": 31,
            "projectName": "미미헤어샵 예약",
            "updatedAt": "2026-09-16T17:40:00+09:00",
        },
        {
            "diagnosisId": 28,
            "projectName": "사내 재고 관리",
            "updatedAt": "2026-08-20T11:05:00+09:00",
        },
        {
            "diagnosisId": 24,
            "projectName": "동네 모임 커뮤니티",
            "updatedAt": "2026-08-14T09:30:00+09:00",
        },
    ]
}

SURVEY_EXAMPLE = {
    "surveyId": 19,
    "diagnosisVersion": 2,
    "isDone": False,
    "questions": [
        {
            "questionId": 51,
            "content": "지금 어디까지 왔나요",
            "answerType": "SINGLE",
            "options": [
                {"optionId": 101, "content": "내 컴퓨터에서만 돌아갑니다", "isSelected": False},
                {
                    "optionId": 102,
                    "content": "배포는 했는데 아직 아무도 안 씁니다",
                    "isSelected": True,
                },
                {"optionId": 103, "content": "사용자가 조금 있습니다", "isSelected": False},
                {"optionId": 104, "content": "매출이 발생하고 있습니다", "isSelected": False},
            ],
        },
        {
            "questionId": 52,
            "content": "어떤 상태인가요?",
            "answerType": "MULTI",
            "options": [
                {"optionId": 111, "content": "화면은 되는데 기능이 안 돼요", "isSelected": False},
                {"optionId": 112, "content": "배포를 못 하고 있어요", "isSelected": False},
                {"optionId": 113, "content": "보안이 걱정돼요", "isSelected": True},
                {"optionId": 114, "content": "곧 실제 사용자를 받아요", "isSelected": True},
                {"optionId": 115, "content": "결제를 받아야 해요", "isSelected": False},
                {"optionId": 116, "content": "기능을 더 못 넣겠어요", "isSelected": False},
            ],
        },
        {
            "questionId": 53,
            "content": "무슨 일이 있었는지 적어주세요",
            "answerType": "TEXT",
            "options": [
                {
                    "optionId": 121,
                    "content": "결제 버튼을 누르면 가끔 아무 반응이 없어요. 배포하고 나서부터 그래요.",
                    "isSelected": True,
                }
            ],
        },
        {
            "questionId": 54,
            "content": "실제 결제를 받을 계획인가요?",
            "answerType": "SINGLE",
            "options": [
                {"optionId": 131, "content": "네, 실제 결제를 받습니다", "isSelected": True},
                {"optionId": 132, "content": "아니요, 테스트만 합니다", "isSelected": False},
            ],
        },
    ],
}

INSPECTION_RUNNING_EXAMPLE = {
    "inspectionId": 27,
    "diagnosisVersion": 2,
    "status": "IN_PROGRESS",
    "readinessScore": None,
    "scannedAt": None,
}

INSPECTION_DONE_EXAMPLE = {
    "inspectionId": 27,
    "diagnosisVersion": 2,
    "status": "DONE",
    "readinessScore": 37,
    "scannedAt": SCANNED_AT,
}

def _unanswered(example: dict) -> dict:
    """아무것도 고르지 않은 설문. 보기는 그대로 두고 선택만 지운다."""
    return {
        **example,
        "isDone": False,
        "questions": [
            {
                **question,
                "options": [
                    {**option, "isSelected": False} for option in question["options"]
                ],
            }
            for question in example["questions"]
        ],
    }


NEW_SURVEY_EXAMPLE = _unanswered(SURVEY_EXAMPLE)


def _survey_example(diagnosis_id: int) -> dict:
    """그 진단의 설문지. 조회·저장·완료가 같은 것을 봐야 한다."""
    example = NEW_SURVEY_EXAMPLE if diagnosis_id == NEW_DIAGNOSIS_ID else SURVEY_EXAMPLE
    return scan_result.survey_questions(diagnosis_id, example)


def _with_answers(example: dict, answers: list) -> dict:
    """보낸 답을 설문지에 얹는다.

    실서버는 저장한 뒤 저장된 상태를 돌려준다. 목이 원본을 그대로 돌려주면
    화면이 그것으로 갈아 끼우면서 방금 고른 답이 눈앞에서 지워진다.
    """
    picked = {answer.questionId: answer for answer in answers}
    questions = []
    for question in example["questions"]:
        answer = picked.get(question["questionId"])
        if answer is None:
            questions.append(question)
            continue
        chosen = set(answer.selectedOptionIds)
        questions.append(
            {
                **question,
                "options": [
                    {**option, "isSelected": option["optionId"] in chosen}
                    for option in question["options"]
                ],
            }
        )
    return {**example, "questions": questions}


ISSUES = [
    {
        "issueId": 311,
        "title": "결제 간헐 실패 수정",
        "detail": "결제 응답을 기다리지 않고 화면을 넘깁니다.",
        "location": "app/api/checkout/route.ts:47",
        "isInput": True,
        "category": {"codeId": 73, "codeName": "결제"},
        "status": "UNRESOLVED",
        "severity": "HIGH",
        "fixedVersion": None,
        "originIssueId": None,
        "qaCriteria": [
            {
                "qaCriterionId": 203,
                "testCase": "결제를 50번 연속으로 넣어도 모두 성공한다",
                "condition": None,
                "status": "UNRESOLVED",
                "issueId": 311,
            },
        ],
    },
    {
        "issueId": 312,
        "title": "관리자 API에 인증 추가",
        "detail": "로그인한 사람이면 남의 주문 내역까지 열람 가능합니다",
        "location": None,
        "isInput": False,
        "category": {"codeId": 71, "codeName": "보안"},
        "status": "UNRESOLVED",
        "severity": "HIGH",
        "fixedVersion": None,
        "originIssueId": 208,
        "qaCriteria": [
            {
                "qaCriterionId": 201,
                "testCase": "관리자 주소로 들어가면 로그인 화면이 뜬다",
                "condition": None,
                "status": "UNRESOLVED",
                "issueId": 312,
            },
            {
                "qaCriterionId": 202,
                "testCase": "로그인 없이 열면 막힌다",
                "condition": None,
                "status": "UNRESOLVED",
                "issueId": 312,
            },
        ],
    },
    {
        "issueId": 313,
        "title": "개인정보처리방침 · 이용약관 페이지 추가",
        "detail": "실제 사용자를 받는 순간부터 적용됩니다",
        "location": None,
        "isInput": False,
        "category": {"codeId": 72, "codeName": "위법"},
        "status": "UNRESOLVED",
        "severity": "MEDIUM",
        "fixedVersion": None,
        "originIssueId": None,
        "qaCriteria": [],
    },
    {
        "issueId": 314,
        "title": "service_role 키 노출 정리",
        "detail": None,
        "location": None,
        "isInput": False,
        "category": {"codeId": 71, "codeName": "보안"},
        "status": "RESOLVED",
        "severity": "HIGH",
        "fixedVersion": 2,
        "originIssueId": 205,
        "qaCriteria": [],
    },
    {
        "issueId": 315,
        "title": "주문 취소가 기록되지 않습니다",
        "detail": "취소해도 예전 값이 그대로 남아 매출이 어긋납니다",
        "location": "app/api/orders/cancel.ts:31",
        "isInput": False,
        "category": {"codeId": 75, "codeName": "데이터"},
        "status": "UNRESOLVED",
        "severity": "HIGH",
        "fixedVersion": None,
        "originIssueId": None,
        "qaCriteria": [],
    },
    {
        "issueId": 316,
        "title": "이미지를 원본 그대로 내려줍니다",
        "detail": "사람이 늘면 전송 요금이 그만큼 늡니다",
        "location": "app/components/Photo.tsx:12",
        "isInput": False,
        "category": {"codeId": 76, "codeName": "비용"},
        "status": "UNRESOLVED",
        "severity": "MEDIUM",
        "fixedVersion": None,
        "originIssueId": None,
        "qaCriteria": [],
    },
    {
        "issueId": 317,
        "title": "실행 방법 문서가 기본 문구 그대로입니다",
        "detail": "받는 사람이 혼자 띄울 수 없습니다",
        "location": "README.md:1",
        "isInput": False,
        "category": {"codeId": 77, "codeName": "인계"},
        "status": "UNRESOLVED",
        "severity": "MEDIUM",
        "fixedVersion": None,
        "originIssueId": None,
        "qaCriteria": [],
    },
    {
        "issueId": 318,
        "title": "예시 계정과 임시 주석이 남아 있습니다",
        "detail": "AI가 만든 자리 채움 코드가 그대로 돌고 있습니다",
        "location": "lib/seed/sample.ts:8",
        "isInput": False,
        "category": {"codeId": 78, "codeName": "AI 잔재"},
        "status": "UNRESOLVED",
        "severity": "LOW",
        "fixedVersion": None,
        "originIssueId": None,
        "qaCriteria": [],
    },
    {
        "issueId": 319,
        "title": "쓰지 않는 설정 파일이 남아 있습니다",
        "detail": "어느 것이 진짜 설정인지 알기 어렵습니다",
        "location": None,
        "isInput": False,
        "category": {"codeId": 74, "codeName": "기타"},
        "status": "UNRESOLVED",
        "severity": "LOW",
        "fixedVersion": None,
        "originIssueId": None,
        "qaCriteria": [],
    },
]

ISSUE_LIST_EXAMPLE = {
    "content": ISSUES[:2],
    "page": 1,
    "size": 20,
    "totalElements": 11,
    "totalPages": 1,
}

QA_SUGGESTION_LIST_EXAMPLE = {
    "content": [
        {"testCase": "로그인하지 않고 관리자 주소로 들어가면 로그인 화면이 나타난다."},
        {"testCase": "일반 회원으로 관리자 주소에 들어가면 접근할 수 없다는 안내가 나타난다."},
        {"testCase": "관리자로 로그인하고 관리자 주소에 들어가면 관리 화면이 열린다."},
    ],
    "page": 1,
    "size": 3,
    "totalElements": 3,
    "totalPages": 1,
}

# 진단별 요청서. PUT /requirements가 고른 이슈를 저장하면 GET /issues에서도
# SUGGESTED 상태로 보이게 해 다음 화면이 요청서 범위를 다시 알아낼 수 있다.
# 목 서버 프로세스가 내려가면 사라지는 데모용 상태다.
_SAVED_REQUIREMENTS: dict[int, list[dict]] = {}


def _base_issue_rows(diagnosis_id: int) -> list[dict]:
    """검사 결과 또는 고정 예시에서 진단의 전체 이슈를 읽는다."""
    kept = scan_result.stored(diagnosis_id)
    return kept["issues"] if kept else ISSUES


def _issue_rows(diagnosis_id: int) -> list[dict]:
    """전체 이슈에 저장된 요청서 상태와 편집 내용을 겹쳐 돌려준다."""
    rows = copy.deepcopy(_base_issue_rows(diagnosis_id))
    saved = _SAVED_REQUIREMENTS.get(diagnosis_id)
    if saved is None:
        return rows

    saved_by_id = {row["issueId"]: row for row in saved}
    merged = [saved_by_id.get(row["issueId"], row) for row in rows]
    known_ids = {row["issueId"] for row in rows}
    merged.extend(row for row in saved if row["issueId"] not in known_ids)
    return merged

DIAGNOSIS_NOT_FOUND_EXAMPLE = {
    "code": "DIAGNOSIS_NOT_FOUND",
    "message": "진단을 찾을 수 없습니다.",
}

ISSUE_NOT_FOUND_EXAMPLE = {
    "code": "ISSUE_NOT_FOUND",
    "message": "요구사항을 찾을 수 없습니다.",
}

# 영역 2에서 진단 경로를 쓰는 API가 함께 쓰는 실패 응답.
DIAGNOSIS_ERRORS: dict = {
    404: {
        "model": ErrorResponse,
        "description": "진단 없음",
        "content": {"application/json": {"example": DIAGNOSIS_NOT_FOUND_EXAMPLE}},
    },
    **AREA1_UNAUTHORIZED,
    **AREA1_ERRORS,
}

AREA2_TAG = "2 사전점검"


# --------------------------------------------------------------------------- #
# 영역 2 — 설문
# --------------------------------------------------------------------------- #


@router.get(
    "/diagnoses",
    response_model=UnfinishedSurveyListResponse,
    tags=[AREA2_TAG],
    responses={
        200: {
            "content": {
                "application/json": {"example": UNFINISHED_SURVEY_LIST_EXAMPLE}
            }
        },
        **AREA1_UNAUTHORIZED,
        **AREA1_ERRORS,
    },
    summary="끝내지 않은 설문 목록",
    description=(
        "**화면 — `2_설문1` › 진행 중인 설문**\n\n"
        "설문을 끝내지 않고 나간 진단을 돌려준다. 다시 들어온 사람이 처음부터 다시 "
        "하지 않고 이어서 하게 하려는 것이다.\n\n"
        "**끝냈는지는 설문의 완료 여부로 본다.** 설문4까지 마치고 완료를 부르면 그 "
        "설문은 여기서 빠진다. 검사를 돌렸는지와는 무관하다 — 설문3까지 하고 나간 "
        "사람도 남는다.\n\n"
        "내 프로젝트의 것만 내려간다. 마지막으로 저장한 시각의 내림차순이다.\n\n"
        "- **200**: 목록을 내줬을 때. 없으면 `content`가 빈 배열이다\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **500 / INTERNAL_ERROR**: 목록을 읽지 못했을 때"
    ),
)
def list_unfinished_surveys() -> UnfinishedSurveyListResponse:
    return UnfinishedSurveyListResponse(**UNFINISHED_SURVEY_LIST_EXAMPLE)


@router.post(
    "/diagnoses",
    response_model=DiagnosisResponse,
    status_code=201,
    tags=[AREA2_TAG],
    responses={
        201: {"content": {"application/json": {"example": NEW_DIAGNOSIS_EXAMPLE}}},
        **AREA1_UNAUTHORIZED,
        **AREA1_ERRORS,
    },
    summary="진단 시작",
    description=(
        "**화면 — `2_설문1` › 다음 버튼**\n\n"
        "사전점검을 시작한다. 이 요청으로 진단 회차가 열리고 설문지 한 벌이 만들어진다. "
        "프로젝트 이름을 설문1에서 받으므로 화면에 들어올 때가 아니라 **다음을 누를 때** "
        "부른다.\n\n"
        "`projectId`를 주면 그 프로젝트를 **다시 진단**하고 회차가 1 오른다. 주지 않으면 "
        "프로젝트를 새로 만들고 1회차로 시작한다.\n\n"
        "설문1에서 고른 '지금 어디까지 왔나요'의 답은 이어서 설문 답 저장으로 보낸다.\n\n"
        "- **201**: 진단이 열렸을 때. 검사 전이라 `readinessScore`와 `scannedAt`은 null이다\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **422 / INVALID_REQUEST**: `projectId`도 `name`도 없을 때. 새 프로젝트는 이름이 "
        "있어야 한다\n"
        "- **500 / INTERNAL_ERROR**: 진단을 열지 못했을 때"
    ),
)
def start_diagnosis(request: StartDiagnosisRequest) -> DiagnosisResponse | JSONResponse:
    if request.projectId is None and not request.name:
        body = ValidationErrorResponse(
            code=INVALID_REQUEST[0],
            message=INVALID_REQUEST[1],
            errors=[FieldError(field="name", reason="필수 항목입니다.")],
        )
        return JSONResponse(status_code=422, content=jsonable_encoder(body))

    return DiagnosisResponse(**NEW_DIAGNOSIS_EXAMPLE)


@router.get(
    "/diagnoses/{diagnosisId}/survey",
    response_model=SurveyResponse,
    tags=[AREA2_TAG],
    responses={
        200: {"content": {"application/json": {"example": SURVEY_EXAMPLE}}},
        **DIAGNOSIS_ERRORS,
    },
    summary="설문 조회",
    description=(
        "**화면 — `2_설문4` › 문항과 보기. 설문1~3은 이어하기에만 쓴다**\n\n"
        "설문 문항과 보기를 순서대로 내려준다. 이미 답한 보기는 `isSelected`가 참이라, "
        "뒤로 갔다 와도 고른 상태가 그대로 보인다.\n\n"
        "**설문1~3은 모든 프로젝트에 같은 고정 문항이라 화면이 문구를 갖고 있다.** 이 "
        "API는 나갔다 돌아왔을 때 고른 답을 되살리는 데만 쓴다.\n\n"
        "**설문4는 프로젝트마다 다르다.** 올린 검사 결과에서 나온 질문이라 반드시 이 "
        "API로 받아 그려야 한다. 검사 결과를 올리기 전에는 설문4 문항이 없다.\n\n"
        "- **200**: 진단이 있고 내 것일 때\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **404 / DIAGNOSIS_NOT_FOUND**: `diagnosisId`가 404일 때. 없는 진단이거나 남의 "
        "진단이다. 남의 것도 존재를 감추기 위해 같은 응답을 준다\n"
        "- **500 / INTERNAL_ERROR**: 설문을 읽지 못했을 때"
    ),
)
def get_survey(
    diagnosisId: Annotated[
        int,
        Path(
            description="진단 식별자. 404는 진단 없음 재현용 예약값이다.",
            examples=[DIAGNOSIS_ID],
        ),
    ],
) -> SurveyResponse | JSONResponse:
    if diagnosisId == NOT_FOUND_DIAGNOSIS_ID:
        return _error_response(404, DIAGNOSIS_NOT_FOUND)
    return SurveyResponse(**_survey_example(diagnosisId))


@router.patch(
    "/diagnoses/{diagnosisId}/survey/answers",
    response_model=SurveyResponse,
    tags=[AREA2_TAG],
    responses={
        200: {"content": {"application/json": {"example": SURVEY_EXAMPLE}}},
        **DIAGNOSIS_ERRORS,
    },
    summary="설문 답 저장",
    description=(
        "**화면 — `2_설문1` · `2_설문2` · `2_설문4` › 다음 버튼**\n\n"
        "한 단계에서 고른 답을 저장한다. 보낸 문항만 바뀌고 나머지는 그대로 남는다. "
        "같은 단계를 다시 보내도 결과가 같으므로 뒤로 갔다 와서 다시 눌러도 안전하다.\n\n"
        "저장 뒤의 설문지 전체를 돌려주므로 화면은 응답으로 상태를 맞추면 된다.\n\n"
        "- **200**: 저장했을 때\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **404 / DIAGNOSIS_NOT_FOUND**: `diagnosisId`가 404일 때\n"
        "- **422 / INVALID_REQUEST**: 단수 문항에 보기를 둘 이상 보냈거나, 주관식에 보기 "
        "식별자를 보냈을 때\n"
        "- **500 / INTERNAL_ERROR**: 저장하지 못했을 때"
    ),
)
def save_survey_answers(
    diagnosisId: Annotated[
        int,
        Path(
            description="진단 식별자. 404는 진단 없음 재현용 예약값이다.",
            examples=[DIAGNOSIS_ID],
        ),
    ],
    request: SaveSurveyAnswersRequest,
) -> SurveyResponse | JSONResponse:
    if diagnosisId == NOT_FOUND_DIAGNOSIS_ID:
        return _error_response(404, DIAGNOSIS_NOT_FOUND)
    return SurveyResponse(
        **_with_answers(_survey_example(diagnosisId), request.answers)
    )


@router.post(
    "/diagnoses/{diagnosisId}/survey/complete",
    response_model=SurveyResponse,
    tags=[AREA2_TAG],
    responses={
        200: {
            "content": {
                "application/json": {
                    "example": {**SURVEY_EXAMPLE, "isDone": True}
                }
            }
        },
        409: {
            "model": ErrorResponse,
            "description": "아직 끝낼 수 없거나 이미 끝냄",
            "content": {
                "application/json": {
                    "example": {
                        "code": "SURVEY_NOT_COMPLETABLE",
                        "message": "아직 끝내지 않은 문항이 있습니다.",
                    }
                }
            },
        },
        **DIAGNOSIS_ERRORS,
    },
    summary="설문 완료",
    description=(
        "**화면 — `2_설문4` › 진단서 보기**\n\n"
        "설문을 끝냈다고 알린다. 이 호출을 받아야 설문이 완료로 바뀌고 `2_설문1` 의 "
        "「진행 중인 설문」 목록에서 빠진다.\n\n"
        "**답 저장이 완료를 겸하지 않는다.** 설문4 문항은 검사 결과에서 나오므로 검사 "
        "전에는 존재하지 않는다. 답이 다 찼는지로 판정하면 설문2까지만 하고도 완료로 "
        "찍히기 때문에, 끝났다는 것은 화면이 따로 알린다.\n\n"
        "완료 조건은 서버가 확인한다 — 검사가 끝나 설문4 문항이 생겼고 모든 문항에 답이 "
        "있어야 한다. 이미 끝낸 설문을 다시 불러도 409다.\n\n"
        "- **200**: 완료로 바뀌었을 때. `isDone`이 참인 설문지를 돌려준다\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **404 / DIAGNOSIS_NOT_FOUND**: `diagnosisId`가 404일 때\n"
        "- **409 / SURVEY_NOT_COMPLETABLE**: `diagnosisId`가 409일 때. 답하지 않은 "
        "문항이 남았거나 이미 끝낸 설문이다\n"
        "- **500 / INTERNAL_ERROR**: 완료 처리를 하지 못했을 때"
    ),
)
def complete_survey(
    diagnosisId: Annotated[
        int,
        Path(
            description=(
                "진단 식별자. 404는 진단 없음, 409는 아직 끝낼 수 없음 재현용 "
                "예약값이다."
            ),
            examples=[DIAGNOSIS_ID],
        ),
    ],
) -> SurveyResponse | JSONResponse:
    if diagnosisId == NOT_FOUND_DIAGNOSIS_ID:
        return _error_response(404, DIAGNOSIS_NOT_FOUND)
    if diagnosisId == NOT_COMPLETABLE_DIAGNOSIS_ID:
        return _error_response(409, SURVEY_NOT_COMPLETABLE)
    return SurveyResponse(**{**_survey_example(diagnosisId), "isDone": True})


# --------------------------------------------------------------------------- #
# 영역 2 — 검사
# --------------------------------------------------------------------------- #


@router.post(
    "/diagnoses/{diagnosisId}/inspections",
    response_model=InspectionResponse,
    status_code=201,
    tags=[AREA2_TAG],
    responses={
        201: {
            "content": {"application/json": {"example": INSPECTION_RUNNING_EXAMPLE}}
        },
        400: {
            "model": ErrorResponse,
            "description": "검사 결과 파일 형식·진단 번호·검사 버전이 맞지 않음",
            "content": {
                "application/json": {
                    "examples": {
                        "scanFileInvalid": {
                            "summary": "결과 파일 형식 오류",
                            "value": SCAN_FILE_INVALID_EXAMPLE,
                        },
                        "diagnosisMismatch": {
                            "summary": "진단 번호 불일치",
                            "value": DIAGNOSIS_MISMATCH_EXAMPLE,
                        },
                        "scanVersionOutdated": {
                            "summary": "검사 버전 불일치",
                            "value": SCAN_VERSION_OUTDATED_EXAMPLE,
                        },
                    }
                }
            },
        },
        409: {
            "model": ErrorResponse,
            "description": "이미 검사가 진행 중",
            "content": {
                "application/json": {
                    "example": {
                        "code": "INSPECTION_ALREADY_RUNNING",
                        "message": "이미 검사가 진행 중입니다.",
                    }
                }
            },
        },
        **DIAGNOSIS_ERRORS,
    },
    summary="검사 결과 파일 올리기",
    description=(
        "**화면 — `2_설문3` › ③ 만들어진 결과 파일 올리기**\n\n"
        "사용자가 자기 AI 도구로 돌린 검사 결과 파일을 올린다. 코드 자체는 올라오지 않고 "
        "결과 파일만 받는다.\n\n"
        "올린 결과 파일에는 발견한 문제뿐 아니라, **코드만으로는 판단할 수 없어 사용자에게 "
        "물어야 할 질문**도 함께 들어 있다. 이 요청으로 그 질문들이 설문4 문항이 된다.\n\n"
        "- **201**: 파일을 받아 검사가 열렸을 때. 이어서 설문 조회로 설문4 문항을 받는다\n"
        "- **400 / SCAN_FILE_INVALID**: 받은 형식이 아닌 파일을 올렸을 때. 내려받은 파일을 "
        "그대로 올리도록 안내한다\n"
        "- **400 / DIAGNOSIS_MISMATCH**: 파일 내용에 진단 번호가 적혀 있고 그것이 URL의 "
        "`diagnosisId`와 다를 때. 이 진단에서 받은 검사 파일로 다시 검사하도록 안내한다. "
        "번호가 적혀 있지 않은 파일은 받는다\n"
        "- **400 / SCAN_VERSION_OUTDATED**: 파일 내용의 검사 버전이 현재 지원하는 버전과 "
        "다를 때. 새 검사 파일을 내려받아 다시 검사하도록 안내한다\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **404 / DIAGNOSIS_NOT_FOUND**: `diagnosisId`가 404일 때\n"
        "- **409 / INSPECTION_ALREADY_RUNNING**: `diagnosisId`가 409일 때. 이 회차에 이미 "
        "검사가 돌고 있다\n"
        "- **500 / INTERNAL_ERROR**: 검사를 시작하지 못했을 때"
    ),
)
def upload_scan_result(
    diagnosisId: Annotated[
        int,
        Path(
            description=(
                "진단 식별자. 404는 진단 없음, 409는 검사 진행 중 재현용 예약값이다."
            ),
            examples=[DIAGNOSIS_ID],
        ),
    ],
    file: Annotated[
        UploadFile,
        File(description="AI 도구가 만든 검사 결과 파일."),
    ],
) -> InspectionResponse | JSONResponse:
    if diagnosisId == NOT_FOUND_DIAGNOSIS_ID:
        return _error_response(404, DIAGNOSIS_NOT_FOUND)
    if diagnosisId == RUNNING_DIAGNOSIS_ID:
        return _error_response(409, INSPECTION_ALREADY_RUNNING)

    name = file.filename or ""
    if not (name.endswith(".md") or name.endswith(".json")):
        return _error_response(400, SCAN_FILE_INVALID)

    # 결과 파일을 읽어 두고 이후 진단서와 결함 목록에 쓴다.
    # 읽을 수 없으면 받지 않는다. 조용히 넘기면 의뢰인은 올라간 줄 알고
    # 예전 진단서를 보게 된다.
    parsed = scan_result.parse(file.file.read())
    if parsed is None:
        return _error_response(400, SCAN_FILE_INVALID)
    # 번호가 적혀 있을 때만 대조한다. 검사 파일을 받아 만든 결과 파일에는 번호가
    # 항상 들어 있어(SKILL.md 1단계) 다른 진단의 파일은 그대로 걸린다. 손으로 만든
    # 예시 파일에는 번호가 없는데, 그것까지 막으면 데모를 돌릴 수 없다.
    if parsed.get("diagnosisId") is not None and parsed["diagnosisId"] != diagnosisId:
        return _error_response(400, DIAGNOSIS_MISMATCH)
    if parsed.get("scanVersion") != SCAN_VERSION:
        return _error_response(400, SCAN_VERSION_OUTDATED)

    scan_result.remember(diagnosisId, parsed)
    return InspectionResponse(**INSPECTION_RUNNING_EXAMPLE)


# --------------------------------------------------------------------------- #
# 영역 2 — 진단서와 요구사항
# --------------------------------------------------------------------------- #


@router.get(
    "/diagnoses/{diagnosisId}",
    response_model=DiagnosisResponse,
    tags=[AREA2_TAG],
    responses={
        200: {"content": {"application/json": {"example": DIAGNOSIS_EXAMPLE}}},
        **DIAGNOSIS_ERRORS,
    },
    summary="진단 요약 조회",
    description=(
        "**화면 — `2_진단서` › 머리 · `2_요구사항선택` › 머리**\n\n"
        "진단서 위쪽의 준비도 점수와 회차, 요구사항 선택 화면 위쪽의 프로젝트 이름·기술· "
        "결함 수·지난 진단 대비 해결 수를 한 번에 채운다. 두 화면이 같은 머리를 쓴다.\n\n"
        "`scannedAt`과 `readinessScore`로 지금 어디까지 왔는지 알 수 있다. 둘 다 null이면 "
        "아직 검사 결과를 올리기 전이라 설문3으로, `scannedAt`만 있으면 설문4가 남은 "
        "것이라 설문4로 보내면 된다.\n\n"
        "- **200**: 진단이 있고 내 것일 때. 검사 전이면 점수와 시각이 null이고 결함 수는 0이다\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **404 / DIAGNOSIS_NOT_FOUND**: `diagnosisId`가 404일 때\n"
        "- **500 / INTERNAL_ERROR**: 요약을 만들지 못했을 때"
    ),
)
def get_diagnosis(
    diagnosisId: Annotated[
        int,
        Path(
            description="진단 식별자. 404는 진단 없음 재현용 예약값이다.",
            examples=[DIAGNOSIS_ID],
        ),
    ],
) -> DiagnosisResponse | JSONResponse:
    if diagnosisId == NOT_FOUND_DIAGNOSIS_ID:
        return _error_response(404, DIAGNOSIS_NOT_FOUND)
    example = (
        NEW_DIAGNOSIS_EXAMPLE
        if diagnosisId == NEW_DIAGNOSIS_ID
        else DIAGNOSIS_EXAMPLE
    )
    example = {**example, "diagnosisId": diagnosisId}
    return DiagnosisResponse(**scan_result.diagnosis(diagnosisId, example))


@router.get(
    "/diagnoses/{diagnosisId}/issues",
    response_model=IssueListResponse,
    tags=[AREA2_TAG],
    responses={
        200: {"content": {"application/json": {"example": ISSUE_LIST_EXAMPLE}}},
        **DIAGNOSIS_ERRORS,
    },
    summary="결함 목록 조회",
    description=(
        "**화면 — `2_진단서` › 문제점 목록 · `2_요구사항선택` › 요구사항 목록**\n\n"
        "이 진단 회차에서 나온 결함을 내려준다. 진단서는 사용자가 적은 증상과 관련된 것을 "
        "위에 따로 보여주므로 `isInput`으로 가른다. 요구사항 선택 화면은 해결한 것을 "
        "아래로 내리므로 `status`로 가른다.\n\n"
        "**요청서도 이 목록으로 그린다.** `2_요청서`와 `2_의뢰인_요청서수정`이 보여주는 "
        "요구사항과 확인 문장이 여기 담긴 값이다. 요청서는 따로 저장되는 문서가 아니라 "
        "이 요구사항들을 모아 부르는 이름이다.\n\n"
        "- **200**: 진단이 있을 때. 검사 전이면 `content`가 빈 배열이다\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **404 / DIAGNOSIS_NOT_FOUND**: `diagnosisId`가 404일 때\n"
        "- **422 / INVALID_REQUEST**: `status`나 `severity`가 허용된 값이 아닐 때\n"
        "- **500 / INTERNAL_ERROR**: 목록을 만들지 못했을 때"
    ),
)
def get_issues(
    diagnosisId: Annotated[
        int,
        Path(
            description="진단 식별자. 404는 진단 없음 재현용 예약값이다.",
            examples=[DIAGNOSIS_ID],
        ),
    ],
    status: Annotated[
        Literal["UNRESOLVED", "RESOLVED", "SUGGESTED", "CONTRACTED", "DISABLED"] | None,
        Query(
            description="처리 상태로 거른다. 비우면 전부 내려간다.",
            examples=["UNRESOLVED"],
        ),
    ] = None,
    isInput: Annotated[
        bool | None,
        Query(
            description=(
                "사용자가 적은 증상과 관련된 것만 거른다. 비우면 전부 내려간다."
            ),
            examples=[None],
        ),
    ] = None,
    page: Annotated[int, Query(ge=1, description="페이지 번호.", examples=[1])] = 1,
    size: Annotated[
        int, Query(ge=1, le=100, description="한 페이지 항목 수.", examples=[20])
    ] = 20,
) -> IssueListResponse | JSONResponse:
    if diagnosisId == NOT_FOUND_DIAGNOSIS_ID:
        return _error_response(404, DIAGNOSIS_NOT_FOUND)

    rows = _issue_rows(diagnosisId)
    if status is not None:
        rows = [row for row in rows if row["status"] == status]
    if isInput is not None:
        rows = [row for row in rows if row["isInput"] is isInput]

    total = len(rows)
    start = (page - 1) * size
    return IssueListResponse(
        content=rows[start : start + size],
        page=page,
        size=size,
        totalElements=total,
        totalPages=(total + size - 1) // size,
    )


@router.post(
    "/issues/{issueId}/qa-suggestions",
    response_model=QaSuggestionListResponse,
    tags=[AREA2_TAG],
    responses={
        200: {
            "content": {"application/json": {"example": QA_SUGGESTION_LIST_EXAMPLE}}
        },
        404: {
            "model": ErrorResponse,
            "description": "요구사항이 없거나 내 것이 아님",
            "content": {"application/json": {"example": ISSUE_NOT_FOUND_EXAMPLE}},
        },
        422: {
            "model": ValidationErrorResponse,
            "description": "요구사항 식별자 형식 오류",
        },
        502: {
            "model": ErrorResponse,
            "description": "AI가 계약에 맞는 후보를 만들지 못함",
            "content": {
                "application/json": {
                    "example": {
                        "code": AI_RESPONSE_INVALID[0],
                        "message": AI_RESPONSE_INVALID[1],
                    }
                }
            },
        },
        503: {
            "model": ErrorResponse,
            "description": "확인 문장 생성 서비스에 연결할 수 없음",
            "content": {
                "application/json": {
                    "example": {
                        "code": AI_SERVICE_UNAVAILABLE[0],
                        "message": AI_SERVICE_UNAVAILABLE[1],
                    }
                }
            },
        },
        **AREA1_UNAUTHORIZED,
        **AREA1_ERRORS,
    },
    summary="확인 문장 후보 생성",
    description=(
        "요구사항 하나의 확인 문장 후보를 세 건 반환한다. 의뢰인은 후보를 보고 채택하거나 "
        "문구를 수정할 수 있다. 후보를 보여주는 것만으로는 저장되지 않으며, 확정한 "
        "문장은 기존 요청서 저장 API로 저장한다. "
        "(R-REQ-01, R-REQ-04)\n\n"
        "응답은 default API의 목록 형식이다. `content[].testCase`는 서비스 ERD의 "
        "`qa_criteria.test_case`에 대응한다. 아직 저장하지 않았으므로 "
        "`qaCriterionId`와 상태는 반환하지 않는다.\n\n"
        "- **200**: 요구사항이 있고 계약에 맞는 후보를 만들었을 때\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **404 / ISSUE_NOT_FOUND**: 요구사항이 없거나 내 것이 아닐 때\n"
        "- **422 / INVALID_REQUEST**: `issueId`가 정수가 아닐 때\n"
        "- **502 / AI_RESPONSE_INVALID**: 생성 결과의 개수·길이·형식이 계약에 맞지 않을 때\n"
        "- **503 / AI_SERVICE_UNAVAILABLE**: 생성 서비스에 연결할 수 없을 때\n"
        "- **500 / INTERNAL_ERROR**: 그 밖의 서버 오류가 발생했을 때\n\n"
        "Mock은 고정된 200 응답만 반환한다. 502·503은 후속 Service 구현을 위한 "
        "응답 계약 예시이며 Mock 전용 실패 트리거를 만들지 않는다."
    ),
)
def suggest_qa_criteria(
    issueId: Annotated[
        int,
        Path(
            description="요구사항 식별자. 404는 요구사항 없음 재현용 예약값이다.",
            examples=[312],
        ),
    ],
) -> QaSuggestionListResponse | JSONResponse:
    if issueId == NOT_FOUND_ISSUE_ID:
        return _error_response(404, ISSUE_NOT_FOUND)
    return QaSuggestionListResponse(**QA_SUGGESTION_LIST_EXAMPLE)


@router.post(
    "/diagnoses/{diagnosisId}/issues",
    response_model=IssueResponse,
    status_code=201,
    tags=[AREA2_TAG],
    responses={
        201: {"content": {"application/json": {"example": ISSUES[0]}}},
        **DIAGNOSIS_ERRORS,
    },
    summary="요구사항 직접 추가",
    description=(
        "**화면 — `2_요구사항선택` › + 요구사항 추가**\n\n"
        "검사가 찾지 못했지만 고치고 싶은 것을 사용자가 직접 적어 넣는다. 검사에서 나온 "
        "결함과 같은 목록에 미해결로 들어가고, 요청서에도 함께 담긴다.\n\n"
        "- **201**: 추가했을 때. 위치는 코드에서 찾은 값이 아니므로 null이다\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **404 / DIAGNOSIS_NOT_FOUND**: `diagnosisId`가 404일 때\n"
        "- **422 / INVALID_REQUEST**: `title`이 비었거나 `severity`가 허용된 값이 아닐 때\n"
        "- **500 / INTERNAL_ERROR**: 추가하지 못했을 때"
    ),
)
def create_issue(
    diagnosisId: Annotated[
        int,
        Path(
            description="진단 식별자. 404는 진단 없음 재현용 예약값이다.",
            examples=[DIAGNOSIS_ID],
        ),
    ],
    request: CreateIssueRequest,
) -> IssueResponse | JSONResponse:
    if diagnosisId == NOT_FOUND_DIAGNOSIS_ID:
        return _error_response(404, DIAGNOSIS_NOT_FOUND)

    return IssueResponse(
        issueId=399,
        title=request.title,
        detail=request.detail,
        location=None,
        isInput=False,
        category=_issue_category(request.categoryCodeId),
        status="UNRESOLVED",
        severity=request.severity,
        fixedVersion=None,
        originIssueId=None,
        qaCriteria=[],
    )


@router.put(
    "/diagnoses/{diagnosisId}/requirements",
    response_model=IssueListResponse,
    tags=[AREA2_TAG],
    responses={
        200: {"content": {"application/json": {"example": ISSUE_LIST_EXAMPLE}}},
        409: {
            "model": ErrorResponse,
            "description": "계약에 포함된 요구사항은 뺄 수 없음",
            "content": {
                "application/json": {"example": ISSUE_STATUS_CONFLICT_EXAMPLE}
            },
        },
        **DIAGNOSIS_ERRORS,
    },
    summary="요청서 저장",
    description=(
        "**화면 — `2_의뢰인_요청서수정` › 저장하기**\n\n"
        "요청서를 통째로 받아 **보낸 내용으로 맞춘다.** 화면의 저장 버튼 하나가 이 요청 "
        "하나를 보내며, 요구사항 줄과 확인 문장을 줄마다 따로 부르는 API는 두지 "
        "않는다.\n\n"
        "보낸 목록에 없는 기존 요구사항은 요청서에서 뺀 것으로 보고, 요구사항 안에서 "
        "보낸 목록에 없는 확인 문장도 지운 것으로 본다. 새로 적는 줄은 식별자를 null로 "
        "보내면 서버가 만들어 응답에 담아 준다.\n\n"
        "**보내는 것이 아니라 저장하는 것이다.** 요청서는 방에 걸려 있어 개발자가 언제든 "
        "같은 것을 본다. 저장하면 그 자리에서 최신 내용이 되고, 따로 보내는 절차는 "
        "없다. 금액이나 기간이 달라질 내용이면 개발자가 계약서를 다시 보낸다.\n\n"
        "**이미 계약에 들어간 요구사항은 뺄 수 없다.** 계약서에 적힌 작업이 사라지면 "
        "안 되기 때문이다. 그런 줄을 빼고 보내면 409로 막는다.\n\n"
        "- **200**: 저장했을 때. 저장된 뒤의 요구사항 전부를 준다\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **403 / FORBIDDEN**: 내 진단이 아닐 때\n"
        "- **404 / DIAGNOSIS_NOT_FOUND**: `diagnosisId`가 404일 때\n"
        "- **409 / ISSUE_STATUS_CONFLICT**: 계약에 포함된 요구사항을 뺐을 때\n"
        "- **422 / INVALID_REQUEST**: `title`이 비었거나 `severity`가 허용된 값이 "
        "아닐 때\n"
        "- **500 / INTERNAL_ERROR**: 저장하지 못했을 때"
    ),
)
def write_requirements(
    diagnosisId: Annotated[
        int,
        Path(
            description="진단 식별자. 404는 진단 없음 재현용 예약값이다.",
            examples=[DIAGNOSIS_ID],
        ),
    ],
    request: WriteRequirementsRequest,
) -> IssueListResponse | JSONResponse:
    if diagnosisId == NOT_FOUND_DIAGNOSIS_ID:
        return _error_response(404, DIAGNOSIS_NOT_FOUND)

    source_by_id = {row["issueId"]: row for row in _issue_rows(diagnosisId)}
    next_issue_id = max(source_by_id, default=399) + 1
    saved = []

    for item in request.requirements:
        issue_id = item.issueId
        if issue_id is None:
            issue_id = next_issue_id
            next_issue_id += 1

        source = source_by_id.get(issue_id)
        status = (
            "CONTRACTED"
            if source is not None and source["status"] == "CONTRACTED"
            else "SUGGESTED"
        )
        saved.append(
            {
                "issueId": issue_id,
                "title": item.title,
                "detail": item.detail,
                "location": source["location"] if source else None,
                "isInput": source["isInput"] if source else False,
                "category": jsonable_encoder(_issue_category(item.categoryCodeId)),
                "status": status,
                "severity": item.severity,
                "fixedVersion": source["fixedVersion"] if source else None,
                "originIssueId": source["originIssueId"] if source else None,
                "qaCriteria": [
                    {
                        "qaCriterionId": qa.qaCriterionId or 290000 + order,
                        "testCase": qa.testCase,
                        "condition": None,
                        "status": "UNRESOLVED",
                        "issueId": issue_id,
                    }
                    for order, qa in enumerate(item.qaCriteria)
                ],
            }
        )

    _SAVED_REQUIREMENTS[diagnosisId] = saved
    return IssueListResponse(
        content=saved,
        page=1,
        size=max(len(saved), 1),
        totalElements=len(saved),
        totalPages=1,
    )


@router.patch(
    "/issues/{issueId}",
    response_model=IssueResponse,
    tags=[AREA2_TAG],
    responses={
        200: {"content": {"application/json": {"example": ISSUES[3]}}},
        404: {
            "model": ErrorResponse,
            "description": "요구사항 없음",
            "content": {"application/json": {"example": ISSUE_NOT_FOUND_EXAMPLE}},
        },
        409: {
            "model": ErrorResponse,
            "description": "계약에 포함되어 상태를 바꿀 수 없음",
            "content": {
                "application/json": {
                    "example": {
                        "code": "ISSUE_STATUS_CONFLICT",
                        "message": "계약에 포함된 요구사항은 상태를 바꿀 수 없습니다.",
                    }
                }
            },
        },
        **AREA1_UNAUTHORIZED,
        **AREA1_ERRORS,
    },
    summary="요구사항 해결 처리 · 되돌리기",
    description=(
        "**화면 — `2_요구사항선택` › 직접 해결했어요 / 되돌리기 · "
        "`2_클릭_직접해결했어요` › 해결 처리**\n\n"
        "이미 스스로 고친 요구사항을 해결로 내리거나, 잘못 눌렀을 때 미해결로 되돌린다. "
        "해결로 내린 항목은 목록 아래쪽으로 가고 요청서에 담기지 않는다.\n\n"
        "같은 상태를 다시 보내도 결과가 같다.\n\n"
        "- **200**: 상태를 바꿨을 때\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **404 / ISSUE_NOT_FOUND**: `issueId`가 404일 때. 없는 요구사항이거나 남의 것이다\n"
        "- **409 / ISSUE_STATUS_CONFLICT**: `issueId`가 409일 때. 이미 계약에 들어간 "
        "요구사항이라 사전점검에서 상태를 바꿀 수 없다\n"
        "- **422 / INVALID_REQUEST**: `status`가 RESOLVED도 UNRESOLVED도 아닐 때\n"
        "- **500 / INTERNAL_ERROR**: 바꾸지 못했을 때"
    ),
)
def update_issue_status(
    issueId: Annotated[
        int,
        Path(
            description=(
                "요구사항 식별자. 404는 없음, 409는 계약 포함 재현용 예약값이다."
            ),
            examples=[314],
        ),
    ],
    request: UpdateIssueStatusRequest,
) -> IssueResponse | JSONResponse:
    if issueId == NOT_FOUND_ISSUE_ID:
        return _error_response(404, ISSUE_NOT_FOUND)
    if issueId == CONTRACTED_ISSUE_ID:
        return _error_response(409, ISSUE_STATUS_CONFLICT)

    row = dict(ISSUES[3])
    row["issueId"] = issueId
    row["status"] = request.status
    row["fixedVersion"] = 2 if request.status == "RESOLVED" else None
    return IssueResponse(**row)


# =========================================================================== #
# 영역 3 — 개발자 찾기 · 상담방
#
# 개발자를 고르고, 무료 상담방에서 이야기한 뒤, 컨설팅과 개발 중 갈래를 정한다.
#
# 화면: 3_의뢰인_개발자찾기 · 3_의뢰인_개발자프로필 · 3_의뢰인_개발자추천 ·
#       3_의뢰인_상담방_대화 · 3_의뢰인_상담방_컨설팅전환 · 3_의뢰인_상담방_개발전환 ·
#       3_개발자_개발계약서요청받음
#
# 함께 쓰는 API (다른 영역에 있다):
#   GET /api/users/me            전 영역 헤더
#   GET /api/diagnoses/{id}      요청서 요약에 쓰는 진단 정보
#
# 상담 요청은 들어온 길에 따라 API가 갈린다. 추천에서 고른 개발자는 추천 회차의 상담 요청
# API로, 직접 찾은 개발자는 프로젝트 상담 요청 API로 보낸다. 뒤쪽은 회차를 만들지 않아
# 방의 recommendationItemId가 비어 있다.
# 개발자의 수락·거절은 없고, 요청하면 그 자리에서 방이 열린다.
#
# 채팅 메시지는 이 API가 다루지 않는다. 방 응답의 chatRefId로 채팅 쪽에 넘긴다.
# =========================================================================== #


# --------------------------------------------------------------------------- #
# 영역 3 — 응답 모델
# --------------------------------------------------------------------------- #


class RatingResponse(BaseModel):
    """평점 하나와 그 근거가 된 리뷰 수.

    **리뷰 하나는 개발력과 설명력을 둘 다 받는다.** 한쪽만 매기고 넘어갈 수 없으므로
    두 평점의 reviewCount는 언제나 같고, 그 값이 곧 이 개발자가 받은 리뷰 총 건수다.
    화면의 '리뷰 N개 모두 보기'가 이 수를 쓴다.
    """

    score: float | None = Field(
        description="평균 평점. 받은 리뷰가 없으면 null이다.",
        examples=[4.4],
    )
    reviewCount: int = Field(
        description=(
            "집계에 들어간 리뷰 수. 개발력과 설명력이 같은 값이며 리뷰 총 건수와도 같다."
        ),
        examples=[20],
    )


class DeveloperSummaryResponse(BaseModel):
    """목록에 한 줄로 나가는 개발자. 찾기와 추천이 같은 모양을 쓴다."""

    developerId: int = Field(
        description="개발자 식별자. 사용자 식별자가 아니다.",
        examples=[3],
    )
    name: str = Field(description="화면에 표시하는 이름.", examples=["이준"])
    profileImageFileId: int | None = Field(
        description="직접 올린 프로필 사진의 파일 식별자. 없으면 null이다.",
        examples=[None],
    )
    profileImageUrl: str | None = Field(
        description=(
            "화면에 그대로 거는 프로필 사진 주소. 계정의 `profileImageUrl`과 같은 방식으로, "
            "저장된 값이 아니라 응답을 만들 때마다 새로 만든다. 직접 올린 사진이 있으면 그 "
            "사진의 임시 주소, 없으면 카카오 프로필 사진 주소, 둘 다 없으면 null이다.\n\n"
            "임시 주소는 한동안만 쓸 수 있다. 만료되면 다시 받아야 하므로 저장해 두고 "
            "오래 쓰지 않는다."
        ),
        examples=[None],
    )
    intro: str = Field(
        description="개발자가 적은 한 줄 소개.",
        examples=["간단한 상담도 받습니다. 용어부터 풀어서 설명합니다."],
    )
    field: CodeResponse | None = Field(
        description=(
            "주로 담당하는 개발 분야. 코드 분류 `DEV_FIELD`의 항목이며, "
            "**한 명에 하나다.** 등록하지 않았으면 null이다. 카드 머리에 연차와 나란히 "
            "`DB 연결 · 5년`으로 붙는다."
        ),
    )
    yearsOfExperience: float | None = Field(
        description=(
            "경력 연차. 등록하지 않았으면 null이다. 카드 머리에서 분야 옆에 붙는다. "
            "어떤 일로 쌓은 연차인지는 상세의 `careers`가 말한다."
        ),
        examples=[5.0],
    )
    isCareerVerified: bool = Field(
        description=(
            "증빙이 확인된 경력을 하나라도 가지고 있는지. 카드의 '증빙 확인' 표시에 쓴다."
        ),
        examples=[True],
    )
    devRating: RatingResponse = Field(description="개발력 평점과 리뷰 수.")
    explanatoryRating: RatingResponse = Field(description="설명력 평점과 리뷰 수.")
    techCategories: list[CodeResponse] = Field(
        description=(
            "이 개발자가 다룰 수 있는 기술. 코드 분류 `TECH_CATEGORY`의 항목이며 "
            "여럿일 수 있다. **카드 아래 칩이 이 목록이다.** 등록 전이면 빈 배열이다."
        ),
    )
    prices: list[TaskPriceResponse] = Field(
        description="작업 유형별 최저가. 가격표를 등록하지 않은 유형은 빠진다.",
    )
    contractCount: int = Field(
        description=(
            "이 개발자가 마친 계약 수. 컨설팅과 개발을 합쳐 세며, 완료된 계약만 든다 — "
            "진행 중이거나 파기된 계약은 빠진다. 카드의 'N건 완료'와 `contract,desc` "
            "정렬이 이 값을 쓴다."
        ),
        examples=[23],
    )


class DeveloperListResponse(BaseModel):
    """개발자 찾기 목록."""

    content: list[DeveloperSummaryResponse] = Field(
        description="현재 페이지에 속한 개발자. 조건에 맞는 사람이 없으면 빈 배열이다.",
    )
    page: int = Field(description="현재 페이지 번호. 1부터 시작한다.", examples=[1])
    size: int = Field(description="한 페이지에 담긴 최대 항목 수.", examples=[20])
    totalElements: int = Field(
        description="조건에 맞는 개발자 수. 화면의 'N명 중 M명'에서 M에 해당한다.",
        examples=[5],
    )
    totalPages: int = Field(description="전체 페이지 수.", examples=[1])
    totalDevelopers: int = Field(
        description=(
            "조건을 걸지 않았을 때의 전체 개발자 수. 화면의 'N명 중 M명'에서 N에 해당한다."
        ),
        examples=[128],
    )


class CareerResponse(BaseModel):
    """개발자 경력 한 줄."""

    careerId: int = Field(description="경력 식별자.", examples=[41])
    company: str = Field(description="회사 이름.", examples=["카카오스타일"])
    role: str = Field(
        description="맡았던 일.",
        examples=["백엔드 · 주문/정산 서비스"],
    )
    startDate: str = Field(description="시작일.", examples=["2021-03-01"])
    endDate: str | None = Field(
        description="종료일. 지금도 다니는 중이면 null이다.",
        examples=["2024-08-31"],
    )
    isVerified: bool = Field(
        description="증빙이 확인된 경력인지. 거짓이면 화면에 '확인 중'으로 나온다.",
        examples=[True],
    )
    verifiedAt: str | None = Field(
        description="증빙을 확인한 시각. 확인 전이면 null이다.",
        examples=["2026-03-04T10:00:00+09:00"],
    )


class PortfolioItemResponse(BaseModel):
    """개발자가 올린 작업물 하나."""

    portfolioItemId: int = Field(description="포트폴리오 식별자.", examples=[7])
    title: str = Field(description="작업물 제목.", examples=["동네 필라테스 수업"])
    description: str | None = Field(
        description="설명. 적지 않았으면 null이다.",
        examples=[None],
    )
    linkUrl: str | None = Field(
        description=(
            "배포 사이트나 저장소 주소. 없으면 null이고, 화면은 그 자리에 '비공개'라고 "
            "적는다."
        ),
        examples=["https://example.com"],
    )
    thumbnailFileId: int | None = Field(
        description="썸네일 파일 식별자. 올리지 않았으면 null이다.",
        examples=[None],
    )
    thumbnailUrl: str | None = Field(
        description=(
            "화면에 그대로 거는 사진 주소. 내 프로필의 `thumbnailUrl`과 같은 방식으로, "
            "저장된 값이 아니라 응답을 만들 때마다 새로 만드는 임시 주소다. 사진을 "
            "올리지 않았으면 null이고, 화면은 그 자리에 빈 칸을 둔다."
        ),
        examples=[None],
    )


class ReviewResponse(BaseModel):
    """계약을 마친 의뢰인이 남긴 리뷰 하나."""

    reviewId: int = Field(description="리뷰 식별자.", examples=[88])
    clientName: str = Field(
        description=(
            "리뷰를 쓴 의뢰인의 표시 이름. **서버가 가려서 내보낸다** — 첫 글자와 끝 "
            "글자만 남기고 가운데를 `O`로 바꾸며, 두 글자면 끝 글자를 바꾼다. 개발자가 "
            "자기 프로필에서 볼 때도 같은 값이라 화면이 따로 가릴 것은 없다. 리뷰를 쓴 "
            "뒤 탈퇴한 의뢰인이면 `탈퇴한 사용자`가 들어온다."
        ),
        examples=["김O미"],
    )
    contractType: Literal["CONSULTING", "DEVELOPMENT"] = Field(
        description=(
            "이 리뷰가 나온 계약의 종류. 어떤 거래에서 받은 평가인지 구분하는 데 쓴다. "
            "어느 프로젝트였는지는 의뢰인의 사업 정보라 담지 않는다."
        ),
        examples=["DEVELOPMENT"],
    )
    devScore: int = Field(
        description="개발력 점수. 1에서 5 사이이며 비울 수 없다.",
        examples=[5],
    )
    expScore: int = Field(
        description=(
            "설명력 점수. 1에서 5 사이이며 비울 수 없다. 컨설팅 계약이든 개발 계약이든 "
            "리뷰 하나는 두 점수를 다 받는다."
        ),
        examples=[5],
    )
    content: str | None = Field(
        description="리뷰 내용. 1000자까지 받는다. 점수만 주고 글을 안 썼으면 null이다.",
        examples=["말이 잘 통했습니다."],
    )
    reply: str | None = Field(
        description="개발자가 단 답글. 1000자까지 받는다. 없으면 null이다.",
        examples=[None],
    )
    createdAt: str = Field(
        description="작성 시각.",
        examples=["2026-08-19T14:20:00+09:00"],
    )


class ReviewListResponse(BaseModel):
    """개발자가 받은 리뷰 목록."""

    content: list[ReviewResponse] = Field(
        description="현재 페이지에 속한 리뷰. 없으면 빈 배열이다.",
    )
    page: int = Field(description="현재 페이지 번호.", examples=[1])
    size: int = Field(description="한 페이지에 담긴 최대 항목 수.", examples=[20])
    totalElements: int = Field(
        description=(
            "전체 리뷰 수. 개발자 프로필의 `devRating.reviewCount`와 언제나 같은 값이다."
        ),
        examples=[20],
    )
    totalPages: int = Field(description="전체 페이지 수.", examples=[1])


class PriceCatalogItemResponse(BaseModel):
    """가격표 한 줄. 개발자가 등록한 작업마다 한 행이다."""

    priceId: int = Field(description="가격표 식별자.", examples=[61])
    taskType: Literal["CONSULT", "DEVELOP"] = Field(
        description="작업 종류. CONSULT는 컨설팅 세션, DEVELOP은 개발 작업이다.",
        examples=["DEVELOP"],
    )
    taskName: str = Field(
        description=(
            "작업 이름. 컨설팅은 '30분 · 한 가지 주제'처럼 시간을, 개발은 "
            "'로그인 붙이기'처럼 할 일을 적는다."
        ),
        examples=["로그인 붙이기"],
    )
    price: int = Field(description="가격. 원 단위 정수다.", examples=[400000])
    periodDays: int | None = Field(
        description=(
            "예상 작업 기간(일). **컨설팅 세션은 언제나 null이다.** 가격표는 세션 시간을 "
            "담지 않으므로, 몇 분짜리 세션인지는 `taskName`에 적힌 글이 전부다. 실제 "
            "세션 시간은 계약서를 쓸 때 분 단위로 따로 정한다."
        ),
        examples=[4],
    )


class DeveloperDetailResponse(DeveloperSummaryResponse):
    """개발자 프로필 상세. 목록에 쓰는 값에 소개글과 이력을 더한다."""

    description: str | None = Field(
        description="개발자 소개 글. 적지 않았으면 null이다.",
        examples=["작은 가게 서비스를 주로 맡습니다."],
    )
    activeFrom: str | None = Field(
        description="주로 활동하는 시간대의 시작. 정하지 않았으면 null이다.",
        examples=["10:00"],
    )
    activeTo: str | None = Field(
        description=(
            "주로 활동하는 시간대의 끝. 시작보다 이르면 자정을 넘기는 것으로 본다."
        ),
        examples=["19:00"],
    )
    careers: list[CareerResponse] = Field(
        description="등록한 경력. 시작일이 늦은 것, 곧 최근 경력이 먼저 내려간다.",
    )
    portfolioItems: list[PortfolioItemResponse] = Field(
        description=(
            "등록한 작업물. 등록한 순서대로 내려간다. 줄 순서를 바꾸는 기능은 없다."
        ),
    )
    priceCatalog: list[PriceCatalogItemResponse] = Field(
        description=(
            "등록한 가격표 전부. 화면의 '컨설팅'과 '개발 · 작업별 요금'을 이 목록으로 "
            "그린다. 목록 응답의 `prices`는 카드에 쓰는 유형별 최저가라 줄 단위 금액과 "
            "기간은 여기에만 있다. 컨설팅이 먼저 오고 그 안에서 가격이 싼 것부터 "
            "내려간다. 가격표를 등록하지 않았으면 빈 배열이다."
        ),
    )


class RecommendationItemResponse(BaseModel):
    """추천된 개발자 한 명."""

    recommendationItemId: int = Field(
        description="추천 항목 식별자. 상담을 요청할 때 이 값을 보낸다.",
        examples=[501],
    )
    rank: int = Field(description="노출 순위. 1부터 시작한다.", examples=[1])
    developer: DeveloperSummaryResponse = Field(
        description=(
            "추천된 개발자. **언제나 지금 값이다.** 서버는 추천 시점의 프로필을 따로 "
            "보관하지만 그것은 알고리즘 성과를 견주는 내부 자료이고, 응답에는 나가지 "
            "않는다. 옛 회차를 다시 열어도 평점과 가격은 현재 프로필을 따른다 — 카드에 "
            "적힌 금액과 상담에서 듣는 금액이 달라지면 안 되기 때문이다."
        ),
    )
    roomId: int | None = Field(
        description=(
            "상담을 요청해 열린 방 식별자. 아직 요청하지 않았으면 null이다. "
            "**이 값 하나가 요청 여부를 말한다.** 상담 요청에 개발자의 수락은 없어서 "
            "요청하면 그 자리에서 방이 열리므로, 요청했는데 방이 없는 상태는 없다. "
            "화면은 null이면 '상담 요청', 값이 있으면 '방으로 가기'를 보여준다."
        ),
        examples=[None],
    )


class RecommendationBatchResponse(BaseModel):
    """추천 회차 하나. 한 번의 상담 요청으로 뽑힌 개발자 묶음이다."""

    batchId: int = Field(description="추천 회차 식별자.", examples=[61])
    sequenceNo: int = Field(
        description="이 프로젝트에서 몇 번째 추천인지. 다시 찾으면 1씩 오른다.",
        examples=[1],
    )
    projectId: int = Field(description="추천 대상 프로젝트 식별자.", examples=[8])
    projectName: str = Field(
        description="프로젝트 이름. 화면 머리에 쓴다.",
        examples=["수제 비누 가게"],
    )
    diagnosisVersion: int = Field(
        description=(
            "이 추천을 만든 시점의 진단 회차. 재진단한 뒤 옛 추천을 열었을 때 무엇을 "
            "근거로 뽑은 결과인지 알려준다."
        ),
        examples=[2],
    )
    candidateCount: int = Field(
        description="점수를 매긴 개발자 수. 순위를 매기기 전 모집단이다.",
        examples=[128],
    )
    items: list[RecommendationItemResponse] = Field(
        description="추천된 개발자. 순위대로 내려간다.",
    )


class CreateRecommendationRequest(BaseModel):
    """추천 회차 생성 요청."""

    diagnosisId: int = Field(
        description="어느 진단을 근거로 찾을지. 이 API는 진단 없이 부를 수 없다.",
        examples=[31],
    )
    issueIds: list[int] = Field(
        min_length=1,
        description=(
            "요청서에 담을 요구사항 식별자. 한 건 이상 골라야 하며, 모두 이 진단에서 "
            "나온 것이어야 한다. 진단을 거치지 않고 개발자를 고르는 길은 프로젝트 상담 "
            "요청 API가 맡는다."
        ),
        examples=[[311, 312]],
    )


class CreateDirectConsultationRequest(BaseModel):
    """직접 찾은 개발자에게 상담을 요청한다."""

    developerIds: list[int] = Field(
        min_length=1,
        description=(
            "상담을 요청할 개발자 식별자. 여러 명에게 한 번에 보낼 수 있고, "
            "한 명 이상 골라야 한다."
        ),
        examples=[[5]],
    )


class CreateConsultationRequest(BaseModel):
    """고른 개발자들에게 상담을 요청한다."""

    recommendationItemIds: list[int] = Field(
        min_length=1,
        description=(
            "상담을 요청할 추천 항목 식별자. 여러 명에게 한 번에 보낼 수 있고, "
            "한 명 이상 골라야 한다."
        ),
        examples=[[501, 502]],
    )


class RoomSummaryResponse(BaseModel):
    """방 목록 사이드바에 한 줄로 나가는 방."""

    roomId: int = Field(description="방 식별자.", examples=[91])
    recommendationItemId: int | None = Field(
        description=(
            "이 방이 나온 추천 항목 식별자. 추천을 거치지 않고 직접 찾아 연 방이면 "
            "null이다. 여러 개발자에게 한 번에 상담을 요청하면 방이 여러 개 열리는데, "
            "어느 개발자의 방인지 이 값으로 맞춘다."
        ),
        examples=[501],
    )
    type: Literal["INQUIRY", "CONSULTING", "DEVELOPMENT"] = Field(
        description=(
            "방 용도. INQUIRY는 무료 상담, CONSULTING은 컨설팅, DEVELOPMENT는 개발이다."
        ),
        examples=["INQUIRY"],
    )
    status: Literal["OPEN", "CLOSED"] = Field(
        description="방이 열려 있는지. 갈래가 정해지면 상담방은 닫힌다.",
        examples=["OPEN"],
    )
    projectId: int = Field(description="이 방이 달린 프로젝트 식별자.", examples=[8])
    projectName: str = Field(description="프로젝트 이름.", examples=["수제 비누 가게"])
    developerId: int = Field(
        description=(
            "이 방에 참여한 **개발자 식별자**. 사용자 식별자가 아니며, 상담을 요청할 때 "
            "보내는 값과 같은 종류다. 같은 프로젝트라도 방마다 다를 수 있다.\n\n"
            "**보낸 의뢰와 받은 의뢰를 가르는 값이다.** 내 정보의 `developerId`와 같으면 "
            "내가 개발자로 들어간 방이라 '받은 의뢰', 다르면 내가 의뢰인이라 '보낸 "
            "의뢰'다. 의뢰인은 프로젝트를 등록한 사람이라 방마다 따로 적히지 않으므로 "
            "의뢰인 식별자는 이 응답에 담지 않는다."
        ),
        examples=[3],
    )
    counterpartName: str = Field(
        description=(
            "상대의 표시 이름. 의뢰인이 보면 개발자 이름, 개발자가 보면 의뢰인 이름이다. "
            "상대가 탈퇴했으면 `탈퇴한 사용자`가 들어온다."
        ),
        examples=["이준"],
    )
    chatRefId: str = Field(
        description=(
            "채팅 참조 키. 메시지 본문은 이 API가 다루지 않으므로, 마지막 메시지와 "
            "미리보기는 이 값으로 채팅 쪽에서 가져온다."
        ),
        examples=["room-91-chat"],
    )
    openedAt: str = Field(
        description="방이 열린 시각.",
        examples=["2026-08-20T09:00:00+09:00"],
    )


class RoomListResponse(BaseModel):
    """방 목록.

    **페이지 정보로 감싸지 않는다.** 사이드바가 프로젝트별로 묶어 한 번에 늘어놓는
    구조라 페이지를 넘길 자리가 없어서다. 방이 몇 개든 전부 내려가며 상한을 두지 않는다.
    """

    content: list[RoomSummaryResponse] = Field(
        description=(
            "조건에 맞는 방. 마지막으로 움직인 것이 먼저다. 없으면 빈 배열이다."
        ),
    )


class RoomRequestSummaryResponse(BaseModel):
    """방 머리에 붙는 요청서 요약.

    **요청서는 방이 아니라 프로젝트에 달린다.** 상담·컨설팅·개발 방이 모두 같은 요청서를
    보므로, 요청서를 고치면 열려 있는 모든 방의 머리가 함께 바뀐다.
    """

    diagnosisId: int = Field(description="요청서의 근거가 된 진단 식별자.", examples=[31])
    issueCount: int = Field(
        description=(
            "요청서에 담긴 요구사항 수. **이번 회차에서 나온 요구사항 가운데 직접 "
            "해결하지 않은 것**을 센다 — 해결로 내린 것은 요청서에서 빠지고, 계약에 "
            "들어간 것은 그대로 남는다. 지난 회차의 요구사항은 세지 않는다."
        ),
        examples=[3],
    )
    updatedAt: str = Field(
        description=(
            "요청서를 마지막으로 고친 시각. 요구사항을 더하거나 빼도, 확인 문장만 "
            "고쳐도 이 값이 갱신된다."
        ),
        examples=["2026-08-18T16:40:00+09:00"],
    )


class RoomDetailResponse(RoomSummaryResponse):
    """방 상세. 목록에 쓰는 값에 요청서 요약을 더한다."""

    request: RoomRequestSummaryResponse | None = Field(
        description=(
            "이 방에 딸린 요청서 요약. **아직 진단을 하지 않은 프로젝트의 방이면 "
            "null이다.** 진단이 있으면 상담을 어느 길로 요청했든 요청서가 걸린다."
        ),
    )
    contractId: int | None = Field(
        description=(
            "이 방에서 오간 계약 식별자. 아직 계약서를 쓰지 않았으면 null이고 화면은 "
            "머리에 '없음'이라 적는다. **방 하나에 계약은 하나다.** 금액과 갱신 시각은 "
            "이 식별자로 계약서를 조회해 채운다."
        ),
        examples=[None],
    )
    convertedRoomId: int | None = Field(
        description=(
            "갈래를 정해 새로 열린 방 식별자. 아직 정하지 않았으면 null이다. "
            "화면의 '컨설팅 방으로' · '개발 방으로' 버튼이 이 방으로 보낸다."
        ),
        examples=[None],
    )
    convertedRoomType: Literal["CONSULTING", "DEVELOPMENT"] | None = Field(
        description=(
            "새로 열린 방의 용도. `convertedRoomId`와 늘 짝으로 차고 빈다. 버튼에 "
            "'컨설팅 방으로'라고 적을지 '개발 방으로'라고 적을지를 이 값으로 고른다 — "
            "이것 하나 때문에 새 방을 다시 조회하지 않아도 된다."
        ),
        examples=[None],
    )


class ConvertRoomRequest(BaseModel):
    """상담방에서 갈래를 정한다."""

    targetType: Literal["CONSULTING", "DEVELOPMENT"] = Field(
        description=(
            "이어갈 갈래. CONSULTING이면 컨설팅 계약으로, DEVELOPMENT면 개발 계약으로 간다."
        ),
        examples=["DEVELOPMENT"],
    )


# --------------------------------------------------------------------------- #
# 영역 3 — 실패 응답 문구
# --------------------------------------------------------------------------- #

DEVELOPER_NOT_FOUND = ("DEVELOPER_NOT_FOUND", "개발자를 찾을 수 없습니다.")
PROJECT_NOT_FOUND = ("PROJECT_NOT_FOUND", "프로젝트를 찾을 수 없습니다.")
PROJECT_NOT_FOUND_EXAMPLE = {
    "code": PROJECT_NOT_FOUND[0],
    "message": PROJECT_NOT_FOUND[1],
}
RECOMMENDATION_NOT_FOUND = ("RECOMMENDATION_NOT_FOUND", "추천 결과를 찾을 수 없습니다.")
ROOM_NOT_FOUND = ("ROOM_NOT_FOUND", "방을 찾을 수 없습니다.")
ROOM_ALREADY_CONVERTED = (
    "ROOM_ALREADY_CONVERTED",
    "이미 갈래가 정해진 상담입니다.",
)
ROOM_NOT_CONVERTED = (
    "ROOM_NOT_CONVERTED",
    "갈래를 정하기 전에는 계약 초안을 만들 수 없습니다.",
)
CONTRACT_DRAFT_RESPONSE_INVALID = (
    "AI_RESPONSE_INVALID",
    "계약 초안을 만들지 못했습니다. 잠시 후 다시 시도해 주세요.",
)
CONTRACT_DRAFT_SERVICE_UNAVAILABLE = (
    "AI_SERVICE_UNAVAILABLE",
    "계약 초안 생성 서비스를 사용할 수 없습니다. 잠시 후 다시 시도해 주세요.",
)
CODE_GROUP_NOT_FOUND = ("CODE_GROUP_NOT_FOUND", "코드 분류를 찾을 수 없습니다.")


# --------------------------------------------------------------------------- #
# 영역 3 — 목 데이터
# --------------------------------------------------------------------------- #

AREA3_TAG = "3 개발자 찾기 · 상담방"

NOT_FOUND_DEVELOPER_ID = 404
NOT_FOUND_PROJECT_ID = 404
NOT_FOUND_BATCH_ID = 404
NOT_FOUND_ROOM_ID = 404
CONVERTED_ROOM_ID = 409

# 코드 항목의 식별자는 분류마다 번호대를 나눠 쓴다. 값만 보고 어느 분류인지 알 수 있고,
# 분류가 늘어도 이미 나간 번호와 겹치지 않는다. 새 분류는 비어 있는 번호대를 가져간다.
#
#   21~29  TECH_CATEGORY   기술
#   41~48  DEV_FIELD       분야
#   51~58  BANK_CODE       은행
#   61~68  CARD_COMPANY    카드사 (영역 8)
#   71~74  ISSUE_CATEGORY  결함 분류 (영역 2)
#
# 기술은 영역 2의 프로젝트 기술과 영역 8의 내 프로필에서도 같은 번호를 쓴다.
TECH_CODES = [
    {"codeId": 21, "codeName": "Next.js"},
    {"codeId": 22, "codeName": "Supabase"},
    {"codeId": 23, "codeName": "TypeScript"},
]

# 계좌를 등록할 때 고르는 은행. 분야·기술과 같은 코드 항목이라 코드 API 가 함께 낸다.
BANK_CODES = [
    {"codeId": 51, "codeName": "국민은행"},
    {"codeId": 52, "codeName": "신한은행"},
    {"codeId": 53, "codeName": "우리은행"},
    {"codeId": 54, "codeName": "하나은행"},
    {"codeId": 55, "codeName": "농협은행"},
    {"codeId": 56, "codeName": "카카오뱅크"},
    {"codeId": 57, "codeName": "토스뱅크"},
    {"codeId": 58, "codeName": "싸피은행"},
]

FIELD_CODES = [
    {"codeId": 41, "codeName": "인터넷에 올리기"},
    {"codeId": 42, "codeName": "DB 연결"},
    {"codeId": 43, "codeName": "도메인 연결"},
    {"codeId": 44, "codeName": "로그인 붙이기"},
    {"codeId": 45, "codeName": "알림 보내기"},
    {"codeId": 46, "codeName": "비용 진단"},
    {"codeId": 47, "codeName": "버그 잡기"},
    {"codeId": 48, "codeName": "결제 붙이기"},
]

# 카드를 등록할 때 고르는 카드사. 쓰는 곳은 영역 8이지만 코드 API 가 함께 낸다.
CARD_COMPANY_CODES = [
    {"codeId": 61, "codeName": "우리카드"},
    {"codeId": 62, "codeName": "국민카드"},
    {"codeId": 63, "codeName": "신한카드"},
    {"codeId": 64, "codeName": "삼성카드"},
    {"codeId": 65, "codeName": "현대카드"},
    {"codeId": 66, "codeName": "롯데카드"},
    {"codeId": 67, "codeName": "하나카드"},
    {"codeId": 68, "codeName": "BC카드"},
]

# 요구사항을 직접 추가할 때 고르는 결함 분류. 쓰는 곳은 영역 2다.
ISSUE_CATEGORY_CODES = [
    {"codeId": 71, "codeName": "보안"},
    {"codeId": 72, "codeName": "위법"},
    {"codeId": 73, "codeName": "결제"},
    {"codeId": 75, "codeName": "데이터"},
    {"codeId": 76, "codeName": "비용"},
    {"codeId": 77, "codeName": "인계"},
    {"codeId": 78, "codeName": "AI 잔재"},
    {"codeId": 74, "codeName": "기타"},
]


def _issue_category(code_id: int) -> CodeResponse:
    """결함 분류 식별자를 코드 항목으로 바꾼다. 아는 번호가 아니면 기타로 둔다."""
    for row in ISSUE_CATEGORY_CODES:
        if row["codeId"] == code_id:
            return CodeResponse(**row)
    return CodeResponse(codeId=code_id, codeName="기타")

DEVELOPERS = [
    {
        "developerId": 3,
        "name": "이준",
        "profileImageFileId": None,
        "profileImageUrl": None,
        "intro": "간단한 상담도 받습니다. 용어부터 풀어서 설명합니다.",
        "field": {"codeId": 42, "codeName": "DB 연결"},
        "yearsOfExperience": 5.0,
        "isCareerVerified": True,
        "devRating": {"score": 4.4, "reviewCount": 2},
        "explanatoryRating": {"score": 4.9, "reviewCount": 2},
        "techCategories": TECH_CODES,
        "prices": [
            {"taskType": "CONSULT", "price": 50000},
            {"taskType": "DEVELOP", "price": 100000},
        ],
        "contractCount": 23,
    },
    {
        "developerId": 5,
        "name": "정하",
        "profileImageFileId": None,
        "profileImageUrl": None,
        "intro": "짧게 방향만 잡아드려요.",
        "field": {"codeId": 41, "codeName": "인터넷에 올리기"},
        "yearsOfExperience": 4.0,
        "isCareerVerified": True,
        "devRating": {"score": 4.2, "reviewCount": 2},
        "explanatoryRating": {"score": 4.4, "reviewCount": 2},
        "techCategories": TECH_CODES[1:],
        "prices": [
            {"taskType": "CONSULT", "price": 40000},
            {"taskType": "DEVELOP", "price": 90000},
        ],
        "contractCount": 41,
    },
    {
        "developerId": 7,
        "name": "한서",
        "profileImageFileId": None,
        "profileImageUrl": None,
        "intro": "급한 장애 대응 잘합니다. 새벽도 괜찮습니다.",
        "field": {"codeId": 47, "codeName": "버그 잡기"},
        "yearsOfExperience": 6.0,
        "isCareerVerified": False,
        "devRating": {"score": 4.7, "reviewCount": 2},
        "explanatoryRating": {"score": 4.1, "reviewCount": 2},
        "techCategories": TECH_CODES[:1],
        "prices": [
            {"taskType": "CONSULT", "price": 60000},
            {"taskType": "DEVELOP", "price": 120000},
        ],
        "contractCount": 11,
    },
]

TOTAL_DEVELOPERS = 128


def _rating_key(rating: dict) -> tuple:
    """평점 내림차순 정렬 키. 평점이 없으면 뒤로 보낸다."""
    score = rating.get("score")
    return (score is None, -(score or 0))


def _lowest_price(row: dict) -> int:
    """가격표 전체의 최저가. 등록한 가격이 없으면 맨 뒤로 간다."""
    prices = [price["price"] for price in row["prices"]]
    return min(prices) if prices else 10**9

# 개발자 한 명의 가격표. 컨설팅은 세션 길이로, 개발은 할 일로 줄을 나눈다.
PRICE_CATALOG = [
    {
        "priceId": 60,
        "taskType": "CONSULT",
        "taskName": "30분 · 한 가지 주제",
        "price": 50000,
        "periodDays": None,
    },
    {
        "priceId": 61,
        "taskType": "CONSULT",
        "taskName": "60분 · 이어지는 두세 가지",
        "price": 90000,
        "periodDays": None,
    },
    {
        "priceId": 62,
        "taskType": "DEVELOP",
        "taskName": "인터넷에 올리기",
        "price": 200000,
        "periodDays": 1,
    },
    {
        "priceId": 63,
        "taskType": "DEVELOP",
        "taskName": "저장되게 하기",
        "price": 350000,
        "periodDays": 3,
    },
    {
        "priceId": 64,
        "taskType": "DEVELOP",
        "taskName": "사둔 주소 연결",
        "price": 100000,
        "periodDays": 1,
    },
    {
        "priceId": 65,
        "taskType": "DEVELOP",
        "taskName": "로그인 붙이기",
        "price": 400000,
        "periodDays": 4,
    },
    {
        "priceId": 66,
        "taskType": "DEVELOP",
        "taskName": "버그 하나 고치기",
        "price": 100000,
        "periodDays": 2,
    },
]

DEVELOPER_DETAIL = {
    **DEVELOPERS[0],
    "priceCatalog": PRICE_CATALOG,
    "description": "작은 가게 서비스를 주로 맡습니다.",
    "activeFrom": "10:00",
    "activeTo": "19:00",
    "careers": [
        {
            "careerId": 41,
            "company": "카카오스타일",
            "role": "백엔드 · 주문/정산 서비스",
            "startDate": "2021-03-01",
            "endDate": "2024-08-31",
            "isVerified": True,
            "verifiedAt": "2026-03-04T10:00:00+09:00",
        },
        {
            "careerId": 42,
            "company": "프리랜서",
            "role": "소상공인 웹 서비스 14건",
            "startDate": "2024-09-01",
            "endDate": None,
            "isVerified": False,
            "verifiedAt": None,
        },
    ],
    "portfolioItems": [
        {
            "portfolioItemId": 7,
            "title": "동네 필라테스 수업",
            "description": None,
            "linkUrl": "https://example.com",
            "thumbnailFileId": None,
            "thumbnailUrl": None,
        }
    ],
}

REVIEWS = [
    {
        "reviewId": 88,
        "clientName": "김O미",
        "contractType": "DEVELOPMENT",
        "devScore": 5,
        "expScore": 5,
        "content": "말이 잘 통했습니다.",
        "reply": None,
        "createdAt": "2026-08-19T14:20:00+09:00",
    },
    {
        "reviewId": 87,
        "clientName": "최O진",
        "contractType": "CONSULTING",
        "devScore": 4,
        "expScore": 5,
        "content": None,
        "reply": "감사합니다.",
        "createdAt": "2026-08-02T09:10:00+09:00",
    },
]

RECOMMENDATION_BATCH = {
    "batchId": 61,
    "sequenceNo": 1,
    "projectId": 8,
    "projectName": "수제 비누 가게",
    "diagnosisVersion": 2,
    "candidateCount": TOTAL_DEVELOPERS,
    "items": [
        {
            "recommendationItemId": 501,
            "rank": 1,
            "developer": DEVELOPERS[0],
            "roomId": None,
        },
        {
            "recommendationItemId": 502,
            "rank": 2,
            "developer": DEVELOPERS[2],
            "roomId": None,
        },
    ],
}

ROOMS = [
    {
        "roomId": 91,
        "recommendationItemId": 501,
        "type": "INQUIRY",
        "status": "OPEN",
        "projectId": 8,
        "projectName": "수제 비누 가게",
        "developerId": 3,
        "counterpartName": "이준",
        "chatRefId": "room-91-chat",
        "openedAt": "2026-08-20T09:00:00+09:00",
    },
    {
        "roomId": 92,
        "recommendationItemId": 501,
        "type": "CONSULTING",
        "status": "OPEN",
        "projectId": 8,
        "projectName": "수제 비누 가게",
        "developerId": 3,
        "counterpartName": "이준",
        "chatRefId": "room-92-chat",
        "openedAt": "2026-08-22T11:40:00+09:00",
    },
    {
        "roomId": 93,
        "recommendationItemId": 501,
        "type": "DEVELOPMENT",
        "status": "OPEN",
        "projectId": 8,
        "projectName": "수제 비누 가게",
        "developerId": 3,
        "counterpartName": "이준",
        "chatRefId": "room-93-chat",
        "openedAt": "2026-08-25T14:05:00+09:00",
    },
    {
        "roomId": 101,
        "recommendationItemId": None,
        "type": "INQUIRY",
        "status": "OPEN",
        "projectId": 21,
        "projectName": "미미헤어샵 예약",
        "developerId": 9,
        "counterpartName": "박준호",
        "chatRefId": "room-101-chat",
        "openedAt": "2026-08-24T10:10:00+09:00",
    },
    {
        "roomId": 103,
        "recommendationItemId": None,
        "type": "DEVELOPMENT",
        "status": "OPEN",
        "projectId": 21,
        "projectName": "미미헤어샵 예약",
        "developerId": 9,
        "counterpartName": "박준호",
        "chatRefId": "room-103-chat",
        "openedAt": "2026-08-28T09:20:00+09:00",
    },
    {
        "roomId": 105,
        "recommendationItemId": None,
        "type": "DEVELOPMENT",
        "status": "OPEN",
        "projectId": 21,
        "projectName": "미미헤어샵 예약",
        "developerId": 9,
        "counterpartName": "박준호",
        "chatRefId": "room-105-chat",
        "openedAt": "2026-08-22T16:10:00+09:00",
    },
    # 내가 개발자로 들어간 컨설팅 방. 계약서를 아직 쓰지 않아 작성 화면이 열린다.
    # 이 방이 없으면 `4_개발자_계약서작성`을 목으로 눌러 볼 방법이 없다 — 컨설팅 방이
    # 의뢰인으로 들어간 것 하나뿐이었다.
    {
        "roomId": 107,
        "recommendationItemId": None,
        "type": "CONSULTING",
        "status": "OPEN",
        "projectId": 21,
        "projectName": "미미헤어샵 예약",
        "developerId": 9,
        "counterpartName": "박준호",
        "chatRefId": "room-107-chat",
        "openedAt": "2026-08-25T14:00:00+09:00",
    },
    {
        "roomId": 106,
        "recommendationItemId": None,
        "type": "DEVELOPMENT",
        "status": "OPEN",
        "projectId": 8,
        "projectName": "수제 비누 가게",
        "developerId": 3,
        "counterpartName": "이준",
        "chatRefId": "room-106-chat",
        "openedAt": "2026-08-22T16:10:00+09:00",
    },
    {
        "roomId": 108,
        "recommendationItemId": None,
        "type": "DEVELOPMENT",
        "status": "OPEN",
        "projectId": 21,
        "projectName": "미미헤어샵 예약",
        "developerId": 9,
        "counterpartName": "박준호",
        "chatRefId": "room-108-chat",
        "openedAt": "2026-08-22T16:10:00+09:00",
    },
    {
        "roomId": 104,
        "recommendationItemId": None,
        "type": "DEVELOPMENT",
        "status": "CLOSED",
        "projectId": 8,
        "projectName": "수제 비누 가게",
        "developerId": 3,
        "counterpartName": "이준",
        "chatRefId": "room-104-chat",
        "openedAt": "2026-08-10T09:00:00+09:00",
    },
]

# 상담 요청으로 열리는 방. 같은 개발자에게 다시 요청해도 같은 방을 돌려주도록 고정해 둔다.
ROOM_ID_BY_ITEM = {501: 91, 502: 95}
ROOM_ID_BY_DEVELOPER = {3: 96, 5: 94, 7: 97}

# 상담방에서 갈래를 정하면 열리는 방. 고른 갈래에 따라 어느 쪽이 열리는지 갈린다.
CONVERTED_CONSULTING_ROOM_ID = 92
CONVERTED_DEVELOPMENT_ROOM_ID = 93

# 추천을 거치지 않고 직접 찾아 연 방. 추천 항목이 없어 recommendationItemId가 비어 있다.
DIRECT_ROOM = {
    "roomId": 94,
    "recommendationItemId": None,
    "type": "INQUIRY",
    "status": "OPEN",
    "projectId": 8,
    "projectName": "수제 비누 가게",
    "developerId": 5,
    "counterpartName": "정하",
    "chatRefId": "room-94-chat",
    "openedAt": "2026-08-27T10:15:00+09:00",
}

# 방 머리에 붙는 요청서 요약. 요청서는 방이 아니라 프로젝트에 달리므로 방마다 같다.
ROOM_REQUEST = {
    "diagnosisId": DIAGNOSIS_ID,
    "issueCount": 3,
    "updatedAt": "2026-08-18T16:40:00+09:00",
}


def _item_room(item_id: int) -> dict:
    """추천 항목으로 열리는 방.

    상담 요청 응답과 방 조회가 같은 방을 보도록 한 곳에서 만든다.
    """
    item = next(
        row
        for row in RECOMMENDATION_BATCH["items"]
        if row["recommendationItemId"] == item_id
    )
    room_id = ROOM_ID_BY_ITEM[item_id]
    return {
        **ROOMS[0],
        "roomId": room_id,
        "recommendationItemId": item_id,
        "developerId": item["developer"]["developerId"],
        "counterpartName": item["developer"]["name"],
        "chatRefId": f"room-{room_id}-chat",
    }


def _developer_room(developer_id: int) -> dict:
    """직접 찾은 개발자에게 요청해 열리는 방. 추천을 거치지 않아 추천 항목이 비어 있다."""
    developer = next(row for row in DEVELOPERS if row["developerId"] == developer_id)
    room_id = ROOM_ID_BY_DEVELOPER[developer_id]
    return {
        **DIRECT_ROOM,
        "roomId": room_id,
        "developerId": developer_id,
        "counterpartName": developer["name"],
        "chatRefId": f"room-{room_id}-chat",
    }


def _room(room_id: int) -> dict | None:
    """방 하나를 찾는다. 목록에 있는 방과 상담 요청으로 열리는 방을 모두 본다."""
    for room in ROOMS:
        if room["roomId"] == room_id:
            return room
    for item_id, opened_id in ROOM_ID_BY_ITEM.items():
        if opened_id == room_id:
            return _item_room(item_id)
    for developer_id, opened_id in ROOM_ID_BY_DEVELOPER.items():
        if opened_id == room_id:
            return _developer_room(developer_id)
    return None


def _room_detail(room: dict) -> dict:
    """방 상세. 목록 값에 요청서 요약과 계약 식별자를 더한다.

    방 하나에 계약은 하나이므로 계약서 예시의 roomId와 짝을 맞춘다. 그 예시는
    영역 4·5에 있어 이 자리에서는 아직 없으므로 부를 때 읽는다.
    """
    contract_ids = {
        CONSULTING_CONTRACT_EXAMPLE["roomId"]: CONTRACT_ID,
        DEV_CONTRACT_EXAMPLE["roomId"]: DEV_CONTRACT_ID,
        103: DEV_CONTRACT_ID,
        104: DONE_CONTRACT_ID,
        105: DRAFT_DEV_CONTRACT_ID,
        106: DRAFT_CLIENT_CONTRACT_ID,
        108: BEFORE_START_CONTRACT_ID,
    }
    return {
        **room,
        "request": ROOM_REQUEST,
        "contractId": contract_ids.get(room["roomId"]),
        "convertedRoomId": None,
        "convertedRoomType": None,
    }


# 방 91의 상세. 문서 예시로만 쓰며 `_room_detail(ROOMS[0])`과 같은 값이다.
ROOM_DETAIL = {
    **ROOMS[0],
    "request": ROOM_REQUEST,
    "contractId": None,
    "convertedRoomId": None,
    "convertedRoomType": None,
}

DEVELOPER_LIST_EXAMPLE = {
    "content": DEVELOPERS,
    "page": 1,
    "size": 20,
    "totalElements": len(DEVELOPERS),
    "totalPages": 1,
    "totalDevelopers": TOTAL_DEVELOPERS,
}

REVIEW_LIST_EXAMPLE = {
    "content": REVIEWS,
    "page": 1,
    "size": 20,
    "totalElements": len(REVIEWS),
    "totalPages": 1,
}

CODE_LIST_EXAMPLE = {"content": FIELD_CODES}

DEVELOPER_NOT_FOUND_EXAMPLE = {
    "code": "DEVELOPER_NOT_FOUND",
    "message": "개발자를 찾을 수 없습니다.",
}

ROOM_NOT_FOUND_EXAMPLE = {"code": "ROOM_NOT_FOUND", "message": "방을 찾을 수 없습니다."}
ROOM_NOT_CONVERTED_EXAMPLE = {
    "code": ROOM_NOT_CONVERTED[0],
    "message": ROOM_NOT_CONVERTED[1],
}



class CodeListResponse(BaseModel):
    """코드 분류 하나에 속한 항목 목록. 항목 수가 적어 content만 담는다."""

    content: list[CodeResponse] = Field(
        description="코드 항목. 화면에 보여줄 순서대로 내려간다.",
    )


# --------------------------------------------------------------------------- #
# 영역 3 — 코드
# --------------------------------------------------------------------------- #


@router.get(
    "/codes/{groupKey}",
    response_model=CodeListResponse,
    tags=[AREA3_TAG],
    responses={
        200: {"content": {"application/json": {"example": CODE_LIST_EXAMPLE}}},
        404: {
            "model": ErrorResponse,
            "description": "코드 분류 없음",
            "content": {
                "application/json": {
                    "example": {
                        "code": "CODE_GROUP_NOT_FOUND",
                        "message": "코드 분류를 찾을 수 없습니다.",
                    }
                }
            },
        },
        **AREA1_ERRORS,
    },
    summary="코드 목록 조회",
    description=(
        "**화면 — `3_의뢰인_개발자찾기` · `3_펼침_분야선택` › 분야 선택 · "
        "`8_펼침_은행선택` › 은행 선택 · `8_개발자_내프로필` › 기술 선택 · "
        "`8_마이페이지` › 카드 등록의 카드사 · `2_요구사항선택` › 요구사항 추가의 "
        "결함 분류**\n\n"
        "화면에서 고르게 할 항목을 내려준다. 분야 칩이나 은행 목록처럼 운영에서 늘고 줄 수 "
        "있는 목록이라 화면에 문구를 박지 않고 이 API로 받는다.\n\n"
        "**고르게 할 목록은 모두 이 API로 받는다.** 분류마다 API를 따로 두지 않는다.\n\n"
        "**로그인하지 않아도 부를 수 있다.** 분야 목록이 로그인 전 화면에 필요해서인데, "
        "한 API가 모든 분류를 내주므로 인증 경계를 분류마다 다르게 둘 수 없다. 로그인한 "
        "사람에게만 보여야 하는 목록은 이 API에 두지 않는다.\n\n"
        "**여기서 빠진 항목도 이름은 그대로 나간다.** 운영에서 쓰지 않기로 내린 항목은 이 "
        "목록에서 빠지지만, 이미 그 항목을 고른 개발자나 요구사항이 있으므로 상세 응답의 "
        "`codeName`에는 계속 담긴다. 고를 수 없게 하는 것과 이름을 감추는 것은 다르다.\n\n"
        "몇 달에 한 번 바뀌는 목록이라 실서버는 `Cache-Control: public, max-age=3600`을 "
        "실어 보낸다. 화면에 들어올 때마다 다시 받지 않아도 된다.\n\n"
        "아래 200 예시는 `DEV_FIELD`를 부른 결과다.\n\n"
        "- **200**: 분류가 있을 때. 쓸 수 있는 항목만 순서대로 내려간다\n"
        "- **404 / CODE_GROUP_NOT_FOUND**: 아는 분류 키가 아닐 때. 오타나 대소문자가 "
        "다른 키도 여기에 걸린다\n"
        "- **500 / INTERNAL_ERROR**: 목록을 읽지 못했을 때"
    ),
)
def get_codes(
    response: Response,
    groupKey: Annotated[
        str,
        Path(
            description=(
                "코드 분류 키. 개발 분야는 `DEV_FIELD`, 기술은 `TECH_CATEGORY`, "
                "계좌를 등록할 때 고르는 은행은 `BANK_CODE`, 카드를 등록할 때 고르는 "
                "카드사는 `CARD_COMPANY`, 요구사항을 직접 추가할 때 고르는 결함 분류는 "
                "`ISSUE_CATEGORY`다. 이 다섯이 아닌 값은 모두 분류 없음으로 본다."
            ),
            examples=["DEV_FIELD"],
        ),
    ],
) -> CodeListResponse | JSONResponse:
    rows = {
        "DEV_FIELD": FIELD_CODES,
        "TECH_CATEGORY": TECH_CODES,
        "BANK_CODE": BANK_CODES,
        "CARD_COMPANY": CARD_COMPANY_CODES,
        "ISSUE_CATEGORY": ISSUE_CATEGORY_CODES,
    }.get(groupKey)
    if rows is None:
        return _error_response(404, CODE_GROUP_NOT_FOUND)
    response.headers["Cache-Control"] = "public, max-age=3600"
    return CodeListResponse(content=[CodeResponse(**row) for row in rows])


# --------------------------------------------------------------------------- #
# 영역 3 — 개발자 찾기
# --------------------------------------------------------------------------- #


@router.get(
    "/developers",
    response_model=DeveloperListResponse,
    tags=[AREA3_TAG],
    responses={
        200: {"content": {"application/json": {"example": DEVELOPER_LIST_EXAMPLE}}},
        **AREA1_ERRORS,
    },
    summary="개발자 찾기",
    description=(
        "**화면 — `3_의뢰인_개발자찾기` › 검색 · 분야 선택 · 정렬 · 카드 목록 · "
        "`3_펼침_검색모드` · `3_펼침_정렬`**\n\n"
        "의뢰인이 직접 개발자를 찾는다. 추천을 거치지 않고 스스로 고르는 경로다. "
        "**로그인하지 않아도 부를 수 있다.** 랜딩의 '증상별로 찾기'로 바로 들어오는 "
        "경로가 있어서다.\n\n"
        "검색은 두 가지다. `problem`은 겪고 있는 문제를 문장으로 적어 맞는 개발자를 찾고, "
        "`name`은 이름으로 바로 찾는다.\n\n"
        "`totalDevelopers`는 조건을 걸지 않았을 때의 전체 수라, 화면의 "
        "'128명 중 5명'에서 앞 숫자에 해당한다.\n\n"
        "**카드 머리는 `field`와 `yearsOfExperience`, 칩은 `techCategories`다.** 분야는 "
        "한 명에 하나라 `DB 연결 · 5년`처럼 연차와 붙여 한 줄로 쓰고, 여럿일 수 있는 "
        "기술을 카드 아래 칩으로 늘어놓는다. 거르는 기준은 분야이고 칩은 기술이라 서로 "
        "다른 것을 가리킨다.\n\n"
        "**`page`는 1부터 센다.** 0이 첫 장이 아니다.\n\n"
        "- **200**: 항상. 조건에 맞는 사람이 없으면 `content`가 빈 배열이다\n"
        "- **422 / INVALID_REQUEST**: `searchMode`나 `sort`가 허용된 값이 아니거나 "
        "`size`가 범위를 벗어날 때\n"
        "- **500 / INTERNAL_ERROR**: 목록을 만들지 못했을 때\n\n"
        "⚠️ 이 목 서버는 분야 거르기, 이름 검색, 네 가지 정렬을 실제로 수행한다. "
        "`problem` 검색만은 문장의 뜻을 읽어야 하므로 거르지 않고 전체를 준다."
    ),
)
def find_developers(
    searchMode: Annotated[
        Literal["problem", "name"],
        Query(
            description=(
                "검색 방식. `problem`은 문제 문장으로 찾고, `name`은 개발자 이름으로 찾는다."
            ),
            examples=["problem"],
        ),
    ] = "problem",
    keyword: Annotated[
        str | None,
        Query(
            description="검색어. 비우면 전체에서 고른다.",
            examples=["예약 버튼이 아무 반응 없어요"],
        ),
    ] = None,
    fieldCodeIds: Annotated[
        list[int] | None,
        Query(
            description=(
                "분야 코드 식별자. 여러 개를 보내면 그중 하나라도 맞는 개발자를 찾는다. "
                "여럿을 보낼 때는 `?fieldCodeIds=41&fieldCodeIds=42`처럼 같은 이름을 "
                "거듭 적는다. **`DEV_FIELD`의 항목이 아닌 값은 조용히 무시한다** — "
                "쓰지 않기로 내린 분야가 담긴 옛 주소로 들어와도 나머지 조건으로 "
                "결과가 나오게 하기 위해서다."
            ),
            examples=[[41, 42]],
        ),
    ] = None,
    sort: Annotated[
        Literal["devRating,desc", "explanatoryRating,desc", "price,asc", "contract,desc"],
        Query(
            description=(
                "정렬 기준. 차례로 실력 높은 순, 설명력 높은 순, 비용 낮은 순, 계약 많은 "
                "순이다. **비용은 가격표 전체의 최저가로 견준다** — 컨설팅과 개발을 "
                "가리지 않고 그 개발자가 받는 가장 싼 금액이며, 카드에 나란히 적히는 "
                "두 금액 중 작은 쪽이다."
            ),
            examples=["devRating,desc"],
        ),
    ] = "devRating,desc",
    page: Annotated[int, Query(ge=1, description="페이지 번호.", examples=[1])] = 1,
    size: Annotated[
        int, Query(ge=1, le=100, description="한 페이지 항목 수.", examples=[20])
    ] = 20,
) -> DeveloperListResponse:
    rows = DEVELOPERS
    if fieldCodeIds:
        rows = [r for r in rows if r["field"] and r["field"]["codeId"] in fieldCodeIds]
    if searchMode == "name" and keyword:
        rows = [r for r in rows if keyword.strip() in r["name"]]

    # 평점이 없는 개발자는 뒤로 보낸다. 리뷰가 없다고 맨 위에 오르면 안 된다.
    sorters = {
        "devRating,desc": lambda r: _rating_key(r["devRating"]),
        "explanatoryRating,desc": lambda r: _rating_key(r["explanatoryRating"]),
        "price,asc": lambda r: (_lowest_price(r),),
        "contract,desc": lambda r: (-r["contractCount"],),
    }
    rows = sorted(rows, key=sorters[sort])

    total = len(rows)
    start = (page - 1) * size
    return DeveloperListResponse(
        content=rows[start : start + size],
        page=page,
        size=size,
        totalElements=total,
        totalPages=(total + size - 1) // size,
        totalDevelopers=TOTAL_DEVELOPERS,
    )


@router.get(
    "/developers/{developerId}",
    response_model=DeveloperDetailResponse,
    tags=[AREA3_TAG],
    responses={
        200: {"content": {"application/json": {"example": DEVELOPER_DETAIL}}},
        404: {
            "model": ErrorResponse,
            "description": "개발자 없음",
            "content": {"application/json": {"example": DEVELOPER_NOT_FOUND_EXAMPLE}},
        },
        **AREA1_ERRORS,
    },
    summary="개발자 프로필 조회",
    description=(
        "**화면 — `3_의뢰인_개발자프로필` › 전체**\n\n"
        "개발자 한 명의 소개, 경력, 작업물, 평점, 가격표를 한 번에 내려준다. 로그인하지 "
        "않아도 볼 수 있는 공개 프로필이다.\n\n"
        "경력은 `isVerified`로 갈린다. 참이면 '확인됨', 거짓이면 '확인 중'으로 보여준다.\n\n"
        "**무료 상담은 이 응답에 담기지 않는다.** 가격표에 없는 고정 정책이라 화면이 "
        "'상담 — 무료' 줄을 스스로 그린다. `priceCatalog`에서 상담 줄을 찾지 않는다.\n\n"
        "**리뷰 총 건수는 `devRating.reviewCount`다.** 리뷰 하나가 두 점수를 다 받으므로 "
        "설명력 쪽과 같은 값이고, 화면의 '리뷰 N개 모두 보기'가 이 수를 쓴다.\n\n"
        "**함께 쓰는 API** 리뷰 칸은 `GET /api/developers/{developerId}/reviews`가 채우고, "
        "아래의 '무료로 물어보기'는 `POST /api/projects/{projectId}/consultation-requests`로 "
        "간다.\n\n"
        "- **200**: 개발자가 있을 때\n"
        "- **404 / DEVELOPER_NOT_FOUND**: `developerId`가 404일 때. 없거나 탈퇴한 개발자다\n"
        "- **500 / INTERNAL_ERROR**: 프로필을 읽지 못했을 때"
    ),
)
def get_developer(
    developerId: Annotated[
        int,
        Path(
            description="개발자 식별자. 404는 없음 재현용 예약값이다.",
            examples=[3],
        ),
    ],
) -> DeveloperDetailResponse | JSONResponse:
    if developerId == NOT_FOUND_DEVELOPER_ID:
        return _error_response(404, DEVELOPER_NOT_FOUND)
    return DeveloperDetailResponse(**{**DEVELOPER_DETAIL, "developerId": developerId})


@router.get(
    "/developers/{developerId}/reviews",
    response_model=ReviewListResponse,
    tags=[AREA3_TAG],
    responses={
        200: {"content": {"application/json": {"example": REVIEW_LIST_EXAMPLE}}},
        404: {
            "model": ErrorResponse,
            "description": "개발자 없음",
            "content": {"application/json": {"example": DEVELOPER_NOT_FOUND_EXAMPLE}},
        },
        **AREA1_ERRORS,
    },
    summary="개발자 리뷰 목록 조회",
    description=(
        "**화면 — `3_의뢰인_개발자프로필` › 리뷰 · 모두 보기**\n\n"
        "이 개발자가 계약을 마친 뒤 받은 리뷰를 최신순으로 내려준다. 프로필 화면은 앞의 "
        "몇 건만 보여주고 '모두 보기'에서 페이지를 넘긴다. 프로필과 마찬가지로 "
        "로그인하지 않아도 볼 수 있다.\n\n"
        "**의뢰인 이름은 서버가 가려서 내보낸다.** 화면이 따로 가릴 것은 없고, 개발자가 "
        "자기 프로필에서 볼 때도 같은 값이다. 어느 프로젝트의 리뷰인지는 의뢰인의 사업 "
        "정보라 담지 않으며, 계약 종류만 `contractType`으로 알려준다.\n\n"
        "`totalElements`는 개발자 프로필의 `devRating.reviewCount`와 언제나 같다. 리뷰 "
        "하나가 두 점수를 다 받으므로 두 수가 갈릴 일이 없다.\n\n"
        "**`page`는 1부터 센다.** 0이 첫 장이 아니다.\n\n"
        "- **200**: 개발자가 있을 때. 받은 리뷰가 없으면 `content`가 빈 배열이다\n"
        "- **404 / DEVELOPER_NOT_FOUND**: `developerId`가 404일 때\n"
        "- **422 / INVALID_REQUEST**: `page`나 `size`가 범위를 벗어날 때\n"
        "- **500 / INTERNAL_ERROR**: 목록을 만들지 못했을 때"
    ),
)
def get_developer_reviews(
    developerId: Annotated[
        int,
        Path(
            description="개발자 식별자. 404는 없음 재현용 예약값이다.",
            examples=[3],
        ),
    ],
    page: Annotated[int, Query(ge=1, description="페이지 번호.", examples=[1])] = 1,
    size: Annotated[
        int, Query(ge=1, le=100, description="한 페이지 항목 수.", examples=[20])
    ] = 20,
) -> ReviewListResponse | JSONResponse:
    if developerId == NOT_FOUND_DEVELOPER_ID:
        return _error_response(404, DEVELOPER_NOT_FOUND)
    return ReviewListResponse(**REVIEW_LIST_EXAMPLE)


# --------------------------------------------------------------------------- #
# 영역 3 — 추천과 상담 요청
# --------------------------------------------------------------------------- #


@router.post(
    "/projects/{projectId}/recommendations",
    response_model=RecommendationBatchResponse,
    status_code=201,
    tags=[AREA3_TAG],
    responses={
        201: {"content": {"application/json": {"example": RECOMMENDATION_BATCH}}},
        404: {
            "model": ErrorResponse,
            "description": "프로젝트를 찾을 수 없음",
            "content": {"application/json": {"example": PROJECT_NOT_FOUND_EXAMPLE}},
        },
        **AREA1_UNAUTHORIZED,
        **AREA1_ERRORS,
    },
    summary="개발자 추천 받기",
    description=(
        "**화면 — `2_요구사항선택` › 상담 요청하기. 결과는 `3_의뢰인_개발자추천`에 나온다**\n\n"
        "고른 요구사항을 근거로 맞는 개발자를 찾아 추천 회차를 만든다. 이 회차의 결과가 "
        "추천 화면에 순위대로 나온다. 추천은 정해진 규칙으로 점수를 계산하며, 서버는 "
        "추천 회차의 알고리즘 판과 항목별 점수 근거를 보관한다. (R-MATCH-05)\n\n"
        "**직접 찾아 고른 개발자는 이 API를 거치지 않는다.** 그 길은 프로젝트 상담 요청 "
        "API가 맡고, 회차 없이 방이 바로 열린다.\n\n"
        "같은 프로젝트에서 다시 찾으면 회차 번호가 1 오른다. 이전 회차는 지우지 않고 "
        "남기지만, 지난 회차를 늘어놓는 화면도 API도 없다. 어떤 추천이 계약으로 "
        "이어졌는지는 서버가 집계에 쓴다.\n\n"
        "**아직 열어보지 않은 회차가 있으면 새로 만들지 않는다.** 직전 회차를 한 번도 "
        "열지 않았다면 그 회차를 그대로 돌려준다. 새로고침하거나 버튼을 두 번 눌러 빈 "
        "회차가 쌓이는 것을 막기 위해서다. 한 번이라도 열어본 뒤에 다시 부르면 그때는 "
        "새 회차가 생긴다.\n\n"
        "**추천을 무엇 때문에 만들었는지는 서버가 정한다.** 이 프로젝트의 첫 회차면 상담 "
        "요청, 이미 회차가 있으면 재매칭으로 기록하고 직전 회차를 부모로 잇는다. 요청에 "
        "담아 보낼 것은 없다.\n\n"
        "- **201**: 추천을 만들었을 때. 맞는 개발자가 없으면 `items`가 빈 배열이다\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **404 / PROJECT_NOT_FOUND**: `projectId`가 404일 때. 없거나 남의 프로젝트다\n"
        "- **422 / INVALID_REQUEST**: `diagnosisId`가 없거나, `issueIds`가 비었거나, "
        "**이 진단에서 나오지 않은 요구사항이 섞였을 때.** 고르지 않은 것이 요청서에 "
        "실리면 계약까지 따라가므로 조용히 버리지 않고 막는다\n"
        "- **500 / INTERNAL_ERROR**: 추천을 만들지 못했을 때\n\n"
        "목에서는 검사 결과를 올린 진단이면 그 결과에서 나온 요구사항만 허용하고, "
        "결과가 없는 진단이면 고정 예시 요구사항만 허용한다. 다른 진단의 요구사항이 "
        "하나라도 섞이면 422를 준다."
    ),
)
def create_recommendation(
    projectId: Annotated[
        int,
        Path(
            description="프로젝트 식별자. 404는 없음 재현용 예약값이다.",
            examples=[8],
        ),
    ],
    request: CreateRecommendationRequest,
) -> RecommendationBatchResponse | JSONResponse:
    if projectId == NOT_FOUND_PROJECT_ID:
        return _error_response(404, PROJECT_NOT_FOUND)

    known = {
        issue["issueId"]
        for issue in _issue_rows(request.diagnosisId)
        if issue["status"] in {"SUGGESTED", "CONTRACTED"}
    }
    unknown = [issue_id for issue_id in request.issueIds if issue_id not in known]
    if unknown:
        body = ValidationErrorResponse(
            code=INVALID_REQUEST[0],
            message=INVALID_REQUEST[1],
            errors=[
                FieldError(
                    field="issueIds",
                    reason="이 진단에서 나온 요구사항만 보낼 수 있습니다.",
                )
            ],
        )
        return JSONResponse(status_code=422, content=jsonable_encoder(body))

    return RecommendationBatchResponse(**RECOMMENDATION_BATCH)


@router.get(
    "/recommendations/{batchId}",
    response_model=RecommendationBatchResponse,
    tags=[AREA3_TAG],
    responses={
        200: {"content": {"application/json": {"example": RECOMMENDATION_BATCH}}},
        404: {
            "model": ErrorResponse,
            "description": "추천 결과 없음",
            "content": {
                "application/json": {
                    "example": {
                        "code": "RECOMMENDATION_NOT_FOUND",
                        "message": "추천 결과를 찾을 수 없습니다.",
                    }
                }
            },
        },
        **AREA1_UNAUTHORIZED,
        **AREA1_ERRORS,
    },
    summary="추천 결과 조회",
    description=(
        "**화면 — `3_의뢰인_개발자추천` › 추천 개발자 목록**\n\n"
        "추천 회차의 결과를 순위대로 내려준다. 개발자마다 상담을 요청했는지는 `roomId`로 "
        "안다. 비어 있으면 아직 요청하지 않은 개발자다.\n\n"
        "**이 조회가 회차를 '열어본 것'으로 표시한다.** 처음 불릴 때 열람 시각을 남기며, "
        "그 표시가 있어야 추천 받기를 다시 불렀을 때 새 회차가 만들어진다. 표시가 없는 "
        "동안에는 추천 받기가 같은 회차를 계속 돌려주므로, **이 API를 부르지 않으면 "
        "두 번째 회차가 영영 생기지 않는다.**\n\n"
        "조회인데 기록을 남기지만 몇 번을 불러도 결과는 같다. 열람 시각은 처음 한 번만 "
        "찍히고 그 뒤로는 아무것도 바뀌지 않는다.\n\n"
        "- **200**: 추천 회차가 있고 내 것일 때. 맞는 개발자가 없던 회차면 `items`가 "
        "빈 배열이다\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **404 / RECOMMENDATION_NOT_FOUND**: `batchId`가 404일 때. 없거나 남의 추천이다\n"
        "- **500 / INTERNAL_ERROR**: 결과를 읽지 못했을 때"
    ),
)
def get_recommendation(
    batchId: Annotated[
        int,
        Path(
            description="추천 회차 식별자. 404는 없음 재현용 예약값이다.",
            examples=[61],
        ),
    ],
) -> RecommendationBatchResponse | JSONResponse:
    if batchId == NOT_FOUND_BATCH_ID:
        return _error_response(404, RECOMMENDATION_NOT_FOUND)
    return RecommendationBatchResponse(**RECOMMENDATION_BATCH)


@router.post(
    "/recommendations/{batchId}/consultation-requests",
    response_model=RoomListResponse,
    status_code=201,
    tags=[AREA3_TAG],
    responses={
        201: {"content": {"application/json": {"example": {"content": ROOMS[:1]}}}},
        404: {
            "model": ErrorResponse,
            "description": "추천 결과 없음",
            "content": {
                "application/json": {
                    "example": {
                        "code": "RECOMMENDATION_NOT_FOUND",
                        "message": "추천 결과를 찾을 수 없습니다.",
                    }
                }
            },
        },
        **AREA1_UNAUTHORIZED,
        **AREA1_ERRORS,
    },
    summary="상담 요청 보내기",
    description=(
        "**화면 — `3_의뢰인_개발자추천` › N명에게 상담 요청**\n\n"
        "고른 개발자들에게 같은 요청서를 보낸다. 여러 명에게 한 번에 보낼 수 있고, "
        "개발자마다 무료 상담방이 하나씩 열린다. 상담은 무료다.\n\n"
        "**개발자의 수락을 기다리지 않는다.** 요청하면 그 자리에서 방이 열리고, 개발자는 "
        "사이드바의 '받은 의뢰'에서 그 방을 본다.\n\n"
        "열린 방은 보낸 순서가 아니라 `recommendationItemId`로 맞춘다. 그 값이 이 요청에 "
        "담아 보낸 추천 항목과 같으므로, 화면은 어느 개발자 카드에 어느 방을 붙일지 안다.\n\n"
        "**몇 번을 보내도 방은 개발자마다 하나다.** 이미 방이 있는 개발자는 그 방을 그대로 "
        "응답에 담고 새로 만들지 않는다. 같은 개발자를 두 번 담아 보내도, 새로고침해도, "
        "두 화면에서 동시에 눌러도 결과가 같다. 이미 요청했다는 이유로 막지 않으므로 "
        "**여러 명 중 한 명이 걸려 나머지가 못 나가는 일도 없다.**\n\n"
        "- **201**: 요청을 보냈을 때. 이번에 연 방과 이미 있던 방이 함께 내려온다\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **404 / RECOMMENDATION_NOT_FOUND**: `batchId`가 404일 때\n"
        "- **422 / INVALID_REQUEST**: `recommendationItemIds`가 비었거나, **이 회차의 "
        "추천 항목이 아닌 값이 섞였을 때.** 고르지 않은 개발자에게 요청이 나가면 안 되므로 "
        "조용히 버리지 않고 막는다\n"
        "- **500 / INTERNAL_ERROR**: 요청을 보내지 못했을 때\n\n"
        "목에서는 이 회차의 추천 항목(501·502)이 아닌 값을 보내면 422를 준다."
    ),
)
def request_consultations(
    batchId: Annotated[
        int,
        Path(
            description="추천 회차 식별자. 404는 없음 재현용 예약값이다.",
            examples=[61],
        ),
    ],
    request: CreateConsultationRequest,
) -> RoomListResponse | JSONResponse:
    if batchId == NOT_FOUND_BATCH_ID:
        return _error_response(404, RECOMMENDATION_NOT_FOUND)

    items = {
        item["recommendationItemId"]: item for item in RECOMMENDATION_BATCH["items"]
    }
    if any(item_id not in items for item_id in request.recommendationItemIds):
        return _field_error(
            "recommendationItemIds", "이 회차의 추천 항목만 보낼 수 있습니다."
        )

    # 같은 항목을 거듭 보내도 방은 하나다. 보낸 순서는 지킨다.
    rooms = [
        _item_room(item_id) for item_id in dict.fromkeys(request.recommendationItemIds)
    ]
    return RoomListResponse(content=[RoomSummaryResponse(**room) for room in rooms])


@router.post(
    "/projects/{projectId}/consultation-requests",
    response_model=RoomListResponse,
    status_code=201,
    tags=[AREA3_TAG],
    responses={
        201: {"content": {"application/json": {"example": {"content": [DIRECT_ROOM]}}}},
        404: {
            "model": ErrorResponse,
            "description": "프로젝트 없음",
            "content": {
                "application/json": {
                    "example": {
                        "code": "PROJECT_NOT_FOUND",
                        "message": "프로젝트를 찾을 수 없습니다.",
                    }
                }
            },
        },
        **AREA1_UNAUTHORIZED,
        **AREA1_ERRORS,
    },
    summary="직접 찾은 개발자에게 상담 요청 보내기",
    description=(
        "**화면 — `1_랜딩홈` › 추천 개발자 › 상담 시작하기 · "
        "`3_의뢰인_개발자프로필` › 무료로 물어보기**\n\n"
        "추천을 거치지 않고 직접 찾은 개발자에게 상담을 요청한다. 개발자마다 무료 "
        "상담방이 하나씩 열린다. 상담은 무료다.\n\n"
        "**부르려면 로그인과 프로젝트가 있어야 한다.** 두 진입점 모두 로그인 전에도 보이는 "
        "화면이라, 둘 중 하나라도 없으면 화면이 로그인 → 사전점검으로 보낸 뒤 프로젝트가 "
        "생긴 다음에 이 API를 부른다. 프로젝트는 진단을 시작할 때 만들어지며, 여럿이면 "
        "어느 프로젝트의 상담인지 고르게 한다.\n\n"
        "**프로젝트에 진단이 있으면 요청서가 함께 걸린다.** 개발자가 방에 들어오자마자 "
        "무엇을 고치고 싶은지 볼 수 있게 하기 위해서다. 진단 전이라면 요청서 없이 방만 "
        "열리고 방 상세의 `request`가 null이다.\n\n"
        "**추천 회차를 만들지 않는다.** 그래서 이렇게 열린 방은 "
        "`recommendationItemId`가 비어 있다. 추천 화면에서 고른 개발자는 이 API가 "
        "아니라 추천 회차의 상담 요청 API로 보낸다.\n\n"
        "**개발자의 수락을 기다리지 않는다.** 요청하면 그 자리에서 방이 열리고, "
        "개발자는 사이드바의 '받은 의뢰'에서 그 방을 본다.\n\n"
        "**몇 번을 보내도 방은 개발자마다 하나다.** 이미 방이 있는 개발자는 그 방을 그대로 "
        "응답에 담고 새로 만들지 않는다. 같은 개발자를 두 번 담아 보내도, 새로고침해도 "
        "결과가 같다.\n\n"
        "- **201**: 요청을 보냈을 때. 이번에 연 방과 이미 있던 방이 함께 내려온다\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **404 / PROJECT_NOT_FOUND**: `projectId`가 404일 때. 없거나 남의 프로젝트다\n"
        "- **422 / INVALID_REQUEST**: `developerIds`가 비었거나, 없는 개발자가 섞였거나, "
        "**자기 자신에게 요청했을 때.** 개발자이면서 의뢰인인 사용자가 자기 프로필에서 "
        "누를 수 있어 서버가 막는다\n"
        "- **500 / INTERNAL_ERROR**: 요청을 보내지 못했을 때\n\n"
        "목에서는 등록된 개발자(3·5·7)가 아닌 값을 보내면 422를 준다. 자기 자신인지는 "
        "토큰을 봐야 알 수 있어 목이 가려내지 않는다."
    ),
)
def request_direct_consultations(
    projectId: Annotated[
        int,
        Path(
            description="프로젝트 식별자. 404는 없음 재현용 예약값이다.",
            examples=[8],
        ),
    ],
    request: CreateDirectConsultationRequest,
) -> RoomListResponse | JSONResponse:
    if projectId == NOT_FOUND_PROJECT_ID:
        return _error_response(404, PROJECT_NOT_FOUND)

    developers = {row["developerId"]: row for row in DEVELOPERS}
    if any(dev_id not in developers for dev_id in request.developerIds):
        return _field_error("developerIds", "등록된 개발자만 보낼 수 있습니다.")

    # 같은 개발자를 거듭 보내도 방은 하나다. 보낸 순서는 지킨다.
    rooms = [
        _developer_room(dev_id) for dev_id in dict.fromkeys(request.developerIds)
    ]
    return RoomListResponse(content=[RoomSummaryResponse(**room) for room in rooms])


# --------------------------------------------------------------------------- #
# 영역 3 — 상담방
# --------------------------------------------------------------------------- #


@router.get(
    "/rooms",
    response_model=RoomListResponse,
    tags=[AREA3_TAG],
    responses={
        200: {"content": {"application/json": {"example": {"content": ROOMS}}}},
        **AREA1_UNAUTHORIZED,
        **AREA1_ERRORS,
    },
    summary="방 목록 조회",
    description=(
        "**화면 — 영역 3~5의 모든 방 화면 › 왼쪽 사이드바의 보낸 의뢰 · 받은 의뢰**\n\n"
        "내가 들어가 있는 방을 내려준다. 의뢰인이 보면 '보낸 의뢰', 개발자가 보면 "
        "'받은 의뢰'가 된다.\n\n"
        "**가르는 값은 `developerId` 하나다.** 방의 `developerId`가 내 개발자 식별자와 "
        "같으면 받은 의뢰, 아니면 보낸 의뢰다. 이 목록에는 내가 들어가 있는 방만 오고 "
        "한 방에서 나는 의뢰인이거나 개발자 둘 중 하나이므로 비교는 한 번으로 끝난다. "
        "의뢰인 식별자를 따로 내려주지 않는 것도 그래서다.\n\n"
        "**한 사람이 양쪽일 수 있다.** 개발자이면서 의뢰인인 사용자는 보낸 의뢰와 받은 "
        "의뢰에 방이 모두 있다. 역할별로 따로 부르는 API는 두지 않으며, 이 API가 둘 다 "
        "내려주고 화면이 가른다.\n\n"
        "**마지막으로 움직인 방이 먼저 온다.** 메시지가 오거나 계약이 바뀌면 그 방이 "
        "위로 올라온다. 프로젝트로 묶어 보여주는 화면은 그 안에서 가장 최근에 움직인 "
        "방을 따라 그룹 순서를 정하면 된다.\n\n"
        "**닫힌 방도 내려간다.** 갈래가 정해져 닫힌 상담방은 나눈 이야기를 계속 볼 수 "
        "있어야 하므로 목록에 남는다. 거르려면 `status`를 준다.\n\n"
        "**마지막 메시지와 미리보기는 이 응답에 없다.** 메시지는 채팅 쪽이 다루므로 "
        "`chatRefId`로 그쪽에서 가져온다. 순서는 이 응답이 이미 정해 주므로 채팅 쪽 "
        "데이터를 기다리지 않아도 된다.\n\n"
        "- **200**: 항상. 들어가 있는 방이 없으면 `content`가 빈 배열이다\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **422 / INVALID_REQUEST**: `type`이나 `status`가 허용된 값이 아닐 때\n"
        "- **500 / INTERNAL_ERROR**: 목록을 만들지 못했을 때"
    ),
)
def get_rooms(
    type: Annotated[
        Literal["INQUIRY", "CONSULTING", "DEVELOPMENT"] | None,
        Query(
            description=(
                "방 용도로 거른다. 비우면 전부 내려간다. 사이드바는 모든 방을 한 번에 "
                "보여주므로 거르지 않는다."
            ),
            examples=["INQUIRY"],
        ),
    ] = None,
    status: Annotated[
        Literal["OPEN", "CLOSED"] | None,
        Query(
            description="열림 여부로 거른다. 비우면 전부 내려간다.",
            examples=["OPEN"],
        ),
    ] = None,
) -> RoomListResponse:
    rows = ROOMS
    if type is not None:
        rows = [r for r in rows if r["type"] == type]
    if status is not None:
        rows = [r for r in rows if r["status"] == status]

    # 실서버는 마지막으로 움직인 시각으로 줄 세운다. 목에는 그 값이 없어 개설 시각을 쓴다.
    rows = sorted(rows, key=lambda r: r["openedAt"], reverse=True)
    return RoomListResponse(content=[RoomSummaryResponse(**r) for r in rows])


@router.get(
    "/rooms/{roomId}",
    response_model=RoomDetailResponse,
    tags=[AREA3_TAG],
    responses={
        200: {"content": {"application/json": {"example": ROOM_DETAIL}}},
        404: {
            "model": ErrorResponse,
            "description": "방 없음",
            "content": {"application/json": {"example": ROOM_NOT_FOUND_EXAMPLE}},
        },
        **AREA1_UNAUTHORIZED,
        **AREA1_ERRORS,
    },
    summary="방 상세 조회",
    description=(
        "**화면 — `3_의뢰인_상담방_대화` · `4_의뢰인_대화` · `5_의뢰인_대화` · "
        "`5_개발자_대화` › 머리와 요청서 요약**\n\n"
        "방 머리에 붙는 상대 이름, 방 용도, 요청서 요약을 내려준다. 갈래가 이미 정해진 "
        "방이면 `convertedRoomId`에 이어진 방이 담겨서, 화면이 '컨설팅 방으로' 같은 "
        "버튼을 보여줄 수 있다.\n\n"
        "**메시지는 이 응답에 없다.** `chatRefId`로 채팅 쪽에서 가져온다. 대화 화면이 "
        "이 API로 받는 것은 머리와 옆 칸까지이고, 말풍선은 채팅 쪽에서 붙는다.\n\n"
        "- **200**: 방이 있고 내가 들어가 있을 때\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **404 / ROOM_NOT_FOUND**: `roomId`가 404일 때. 없거나 내가 들어가 있지 않은 "
        "방이다. 남의 방도 존재를 감추기 위해 같은 응답을 준다\n"
        "- **500 / INTERNAL_ERROR**: 방을 읽지 못했을 때"
    ),
)
def get_room(
    roomId: Annotated[
        int,
        Path(
            description=(
                "방 식별자. 맡긴 의뢰는 91·92·93·104·106, 맡은 의뢰는 101·103·105·108이다. "
                "상담 요청으로 열리는 94~97도 있다. 그 밖의 값은 모두 없는 방이며 "
                "404는 없음 재현용 예약값이다."
            ),
            examples=[91],
        ),
    ],
) -> RoomDetailResponse | JSONResponse:
    room = _room(roomId)
    if room is None:
        return _error_response(404, ROOM_NOT_FOUND)
    return RoomDetailResponse(**_room_detail(room))


@router.post(
    "/rooms/{roomId}/conversion",
    response_model=RoomDetailResponse,
    status_code=201,
    tags=[AREA3_TAG],
    responses={
        201: {
            "content": {
                "application/json": {
                    "example": {
                        **ROOM_DETAIL,
                        "status": "CLOSED",
                        "convertedRoomId": CONVERTED_CONSULTING_ROOM_ID,
                        "convertedRoomType": "CONSULTING",
                    }
                }
            }
        },
        403: {
            "model": ErrorResponse,
            "description": "이 방의 의뢰인이 아님",
            "content": {"application/json": {"example": FORBIDDEN_EXAMPLE}},
        },
        404: {
            "model": ErrorResponse,
            "description": "방 없음",
            "content": {"application/json": {"example": ROOM_NOT_FOUND_EXAMPLE}},
        },
        409: {
            "model": ErrorResponse,
            "description": "이미 갈래가 정해짐",
            "content": {
                "application/json": {
                    "example": {
                        "code": "ROOM_ALREADY_CONVERTED",
                        "message": "이미 갈래가 정해진 상담입니다.",
                    }
                }
            },
        },
        **AREA1_UNAUTHORIZED,
        **AREA1_ERRORS,
    },
    summary="상담에서 갈래 정하기",
    description=(
        "**화면 — `3_의뢰인_상담방_대화` › 컨설팅 계약 진행 · 개발 계약 진행**\n\n"
        "무료 상담을 마치고 컨설팅과 개발 중 하나로 이어간다. 상담방은 닫히고 고른 갈래의 "
        "방이 새로 열린다. 닫힌 상담방도 지우지 않고 남겨서 나눈 이야기를 계속 볼 수 있다.\n\n"
        "응답의 `convertedRoomId`가 새로 열린 방이고 `convertedRoomType`이 그 방의 "
        "용도다. 화면은 둘로 '컨설팅 방으로' 또는 '개발 방으로' 버튼을 그린다. 개발자 "
        "화면에는 대신 '개발 계약서 작성'이 뜬다.\n\n"
        "**갈래는 의뢰인이 정한다.** 개발자는 이 API를 부를 수 없다 — 상담을 어디로 "
        "이어갈지는 돈을 내는 쪽이 고르기 때문이다. 개발자는 그 방의 당사자라 방이 있다는 "
        "것을 이미 아는 상태이므로, 없는 척 감추지 않고 권한이 없다고 답한다.\n\n"
        "- **201**: 갈래를 정해 새 방이 열렸을 때\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **403 / FORBIDDEN**: 이 방의 의뢰인이 아닐 때. 갈래는 의뢰인이 정한다\n"
        "- **404 / ROOM_NOT_FOUND**: `roomId`가 404일 때\n"
        "- **409 / ROOM_ALREADY_CONVERTED**: `roomId`가 409일 때. 이미 갈래가 정해졌다\n"
        "- **422 / INVALID_REQUEST**: `targetType`이 CONSULTING도 DEVELOPMENT도 아닐 때\n"
        "- **500 / INTERNAL_ERROR**: 갈래를 정하지 못했을 때"
    ),
)
def convert_room(
    roomId: Annotated[
        int,
        Path(
            description=(
                "상담방 식별자. 404는 없음, 409는 이미 전환됨 재현용 예약값이다."
            ),
            examples=[91],
        ),
    ],
    request: ConvertRoomRequest,
) -> RoomDetailResponse | JSONResponse:
    if roomId == CONVERTED_ROOM_ID:
        return _error_response(409, ROOM_ALREADY_CONVERTED)
    room = _room(roomId)
    if room is None:
        return _error_response(404, ROOM_NOT_FOUND)
    converted_room_id = (
        CONVERTED_CONSULTING_ROOM_ID
        if request.targetType == "CONSULTING"
        else CONVERTED_DEVELOPMENT_ROOM_ID
    )
    return RoomDetailResponse(
        **{
            **_room_detail(room),
            "status": "CLOSED",
            "convertedRoomId": converted_room_id,
            "convertedRoomType": request.targetType,
        }
    )



# =========================================================================== #
# 영역 4 — 컨설팅
#
# 개발자가 컨설팅 계약서를 쓰고, 양쪽이 서명한 뒤, 정해진 시각에 세션을 하고,
# 끝나면 요약을 받아 요청서에 반영한다.
#
# 화면: 4_의뢰인_대화 · 4_개발자_계약서작성 · 4_개발자_계약서검토 ·
#       4_의뢰인_계약서상세 · 4_의뢰인_계약서수정제안 ·
#       4_의뢰인_입장전 · 4_의뢰인_세션중 · 4_의뢰인_요약
#
# 함께 쓰는 API (다른 영역에 있다):
#   GET /api/users/me            전 영역 헤더
#   GET /api/rooms               방 화면 사이드바
#   GET /api/rooms/{roomId}      방 머리와 요청서 요약
#
# 세션 중의 화상과 채팅은 이 API가 다루지 않는다. chatRefId로 그쪽에 넘긴다.
# =========================================================================== #


# --------------------------------------------------------------------------- #
# 영역 4 — 응답 모델
# --------------------------------------------------------------------------- #


class ReservationResponse(BaseModel):
    """세션 예약. 한 세션에 하나만 있다."""

    reservationId: int = Field(description="예약 식별자.", examples=[71])
    scheduledAt: str = Field(
        description="예약한 일시.",
        examples=["2026-08-29T14:00:00+09:00"],
    )
    status: Literal["PROPOSED", "CONFIRMED", "DISABLED"] = Field(
        description=(
            "예약 처리. 계약서를 주고받는 동안은 PROPOSED이고, 양쪽이 서명하면 "
            "CONFIRMED가 된다. **서명한 뒤에는 일정을 바꾸지 않는다.** DISABLED는 계약이 "
            "파기되었을 때만 쓴다."
        ),
        examples=["CONFIRMED"],
    )


class SessionRecordingResponse(BaseModel):
    """세션 녹음 한 건.

    참가자마다 자기 마이크만 녹음해 각자 올린다. 두 사람 소리를 한 파일로 합치지
    않는다. 파일이 곧 화자라 나중에 누가 말했는지 가리는 데 따로 손이 들지 않는다.
    """

    recordingId: int = Field(description="녹음 식별자.", examples=[71])
    speaker: Literal["CLIENT", "DEVELOPER"] = Field(
        description="이 녹음을 올린 사람의 이 계약에서의 역할. 인증 정보로 정한다.",
        examples=["CLIENT"],
    )
    fileId: int = Field(description="올린 파일 식별자.", examples=[612])
    uploadedAt: str = Field(
        description="올린 시각.",
        examples=["2026-08-29T15:12:00+09:00"],
    )


class CreateSessionRecordingRequest(BaseModel):
    """세션 녹음 올리기 요청."""

    fileId: int = Field(
        description=(
            "`POST /api/uploads`에 `purpose`를 `SESSION_RECORDING`으로 올려 받은 "
            "파일 식별자."
        ),
        examples=[612],
    )


class ConsultingSessionResponse(BaseModel):
    """컨설팅 세션 하나."""

    sessionId: int = Field(description="컨설팅 세션 식별자.", examples=[55])
    contractId: int = Field(
        description=(
            "이 세션이 달린 계약 식별자.\n\n"
            "세션 화면은 방 밖에 있는 별도 화면이라 세션 식별자만으로 열린다. 끝낸 뒤 "
            "어느 방으로 돌아갈지, 요약을 어디서 볼지가 이 값에서 나온다."
        ),
        examples=[120],
    )
    price: int = Field(description="세션 금액. 원 단위 정수다.", examples=[50000])
    durationMinutes: int = Field(
        description="계약서에 적힌 세션 시간(분).",
        examples=[30],
    )
    reservation: ReservationResponse = Field(
        description="이 세션의 예약. 한 세션에 하나뿐이며 이 시각에 진행한다.",
    )
    startedAt: str | None = Field(
        description="세션을 시작한 시각. 시작 전이면 null이다.",
        examples=[None],
    )
    endedAt: str | None = Field(
        description="세션을 마친 시각. 끝나기 전이면 null이다.",
        examples=[None],
    )
    chatRefId: str = Field(
        description=(
            "세션 중 실시간 채팅 참조 키. 메시지와 화상은 이 API가 다루지 않으므로 "
            "이 값으로 그쪽에서 잇는다."
        ),
        examples=["session-55-chat"],
    )


class SessionParticipationResponse(BaseModel):
    """세션 참가자 한 사람의 기록."""

    userId: int = Field(description="참가자 사용자 식별자.", examples=[12])
    consentedAt: str = Field(
        description="녹음·전사·요약에 동의한 시각.",
        examples=["2026-08-29T13:55:00+09:00"],
    )
    joinedAt: str | None = Field(
        description="실제로 들어간 시각. 상대 동의를 기다리는 중이면 null이다.",
        examples=[None],
    )


class IceServerResponse(BaseModel):
    """브라우저가 그대로 RTCPeerConnection에 넣는 ICE 서버 한 줄."""

    urls: list[str] = Field(
        description="같은 자격으로 쓰는 주소들.",
        examples=[["turn:turn.komit.site:3478?transport=udp"]],
    )
    username: str | None = Field(
        default=None,
        description="시한부 자격의 아이디. STUN에는 없다.",
        examples=["1790238175:12"],
    )
    credential: str | None = Field(
        default=None,
        description="시한부 자격의 비밀번호. STUN에는 없다.",
        examples=["8mCPiF2R1qkHrDbY0dzXqA1sZ0w="],
    )


class IceServersResponse(BaseModel):
    """통화를 열 때 화면이 받아 가는 ICE 서버 목록."""

    iceServers: list[IceServerResponse] = Field(
        description="브라우저에 그대로 넣는 목록.",
    )
    expiresAt: str | None = Field(
        default=None,
        description="중계 자격이 죽는 시각. 중계 서버를 두지 않은 환경에서는 없다.",
        examples=["2026-08-29T16:00:00+09:00"],
    )


class SignatureResponse(BaseModel):
    """계약서에 남긴 서명 하나."""

    userId: int = Field(description="서명한 사용자 식별자.", examples=[12])
    isSigned: bool = Field(description="서명을 마쳤는지.", examples=[True])
    signedAt: str | None = Field(
        description="서명한 시각. 서명 전이면 null이다.",
        examples=["2026-08-22T16:10:00+09:00"],
    )
    signatureImageFileId: int | None = Field(
        description="서명판에 그린 서명 이미지 파일 식별자. 서명 전이면 null이다.",
        examples=[41],
    )


class TempMessageResponse(BaseModel):
    """계약 전에 서로 남기는 전달 메시지 하나."""

    messageId: int = Field(description="전달 메시지 식별자.", examples=[31])
    content: str = Field(
        description="남긴 내용.",
        examples=["세션 전에 결제 실패가 언제 나는지 알려주세요."],
    )
    isClient: bool = Field(
        description="남긴 쪽이 의뢰인인지. 거짓이면 개발자가 남긴 것이다.",
        examples=[False],
    )
    createdAt: str = Field(
        description="남긴 시각.",
        examples=["2026-08-22T11:44:00+09:00"],
    )


class ConsultingAgreementResponse(BaseModel):
    """컨설팅 계약에만 있는 내용. 개발 계약이면 이 자리가 null이다."""

    status: Literal[
        "SIGNED", "DEPOSITED", "IN_PROGRESS", "COMPLETED", "TERMINATED"
    ] = Field(
        description=(
            "컨설팅 진행. 계약서를 처음 쓸 때부터 SIGNED다. AI가 만든 version 1도 같다. "
            "예치가 끝나면 DEPOSITED, 세션이 시작되면 IN_PROGRESS가 된다. COMPLETED는 "
            "완료됨, TERMINATED는 파기됨이다. 개발 계약과 같은 순서다.\n\n"
            "**체결 여부는 바깥 계약의 `status`로만 판단한다.** 이 값이 SIGNED여도 "
            "체결된 것이 아니다. 서명 두 개가 모두 찍혀야 계약이 성립한다. "
            "(`R-CON-01`)"
        ),
        examples=["SIGNED"],
    )
    totalAmount: int = Field(
        description="이 계약으로 오가는 전체 금액. 속한 세션 금액의 합이다.",
        examples=[50000],
    )
    topic: str = Field(
        description=(
            "이 세션에서 다룰 것. 개발자가 한 줄로 적는다.\n\n"
            "**요구사항 식별자를 담지 않는다.** 컨설팅은 요구사항 하나를 작업으로 만드는 "
            "것이 아니라 한 번 만나 여러 갈래를 함께 보는 자리라, 요청서의 어느 줄에 "
            "매이지 않는다. 요청서에 없는 것을 다루기로 할 수도 있다."
        ),
        examples=["회원탈퇴 경로와 결제 간헐 실패를 함께 봅니다"],
    )
    detail: str | None = Field(
        default=None,
        description="합의한 상담 내용의 상세 설명. 적지 않았으면 null이다.",
        examples=["회원탈퇴 처리 흐름과 결제 실패 재현 방법을 화면 공유로 봅니다."],
    )
    sessions: list[ConsultingSessionResponse] = Field(
        description="이 계약에 속한 세션.",
    )


class ContractResponse(BaseModel):
    """계약서 한 건.

    컨설팅과 개발이 계약 공통 부분(서명·전달 메시지·원문)을 함께 쓰고, 갈래별 내용만
    `contractType`에 따라 `consulting` 또는 `development` 한쪽에 담긴다. 나머지 한쪽은
    항상 null이다.
    """

    contractId: int = Field(description="계약 식별자.", examples=[120])
    roomId: int = Field(description="이 계약이 달린 방 식별자.", examples=[92])
    contractType: Literal["CONSULTING", "DEVELOPMENT"] = Field(
        description=(
            "계약 종류. CONSULTING이면 `consulting`이, DEVELOPMENT면 `development`가 "
            "채워진다."
        ),
        examples=["CONSULTING"],
    )
    status: Literal["NEGOTIATING", "AGREED", "SIGNED", "CANCELLED"] = Field(
        description=(
            "계약 진행. NEGOTIATING은 협의중, AGREED는 합의됨, SIGNED는 양쪽이 서명해 "
            "체결된 것이다. CANCELLED는 개발자가 서명하기 전에 의뢰인이 취소한 것이다 "
            "(R-CON-21)."
        ),
        examples=["NEGOTIATING"],
    )
    consulting: ConsultingAgreementResponse | None = Field(
        default=None,
        description="컨설팅 계약일 때의 내용. 개발 계약이면 null이다.",
    )
    development: "DevelopmentAgreementResponse | None" = Field(
        default=None,
        description="개발 계약일 때의 내용. 컨설팅 계약이면 null이다.",
    )
    clientSignature: SignatureResponse | None = Field(
        description="의뢰인 서명. 아직 없으면 null이다.",
    )
    developerSignature: SignatureResponse | None = Field(
        description="개발자 서명. 아직 없으면 null이다.",
    )
    messages: list[TempMessageResponse] = Field(
        description="계약 전에 주고받은 전달 메시지.",
    )
    contractText: str | None = Field(
        description=(
            "체결된 계약서 원문. 체결 시점 문구를 그대로 보관하며 이후 바뀌지 않는다. "
            "체결 전이면 null이다."
        ),
        examples=[None],
    )
    updatedAt: str = Field(
        description="계약서를 마지막으로 고친 시각.",
        examples=["2026-08-22T11:40:00+09:00"],
    )


class WriteConsultingContractRequest(BaseModel):
    """컨설팅 계약서 작성 요청."""

    topic: str = Field(
        min_length=1,
        max_length=255,
        description="이 세션에서 다룰 것. 한 줄로 적는다.",
        examples=["회원탈퇴 경로와 결제 간헐 실패를 함께 봅니다"],
    )
    scheduledAt: str = Field(
        description="세션을 할 일시.",
        examples=["2026-08-29T14:00:00+09:00"],
    )
    durationMinutes: int = Field(
        description="세션 시간(분).",
        examples=[30],
    )
    price: int = Field(description="세션 금액. 원 단위 정수다.", examples=[50000])
    detail: str | None = Field(
        default=None,
        description="상세 설명. 적지 않아도 된다.",
        examples=["회원탈퇴 처리 흐름과 결제 실패 재현 방법을 화면 공유로 봅니다."],
    )
    message: str | None = Field(
        default=None,
        max_length=300,
        description="계약서와 함께 상대에게 남길 전달 메시지.",
        examples=["세션 전에 확인하고 싶은 게 있으면 적어주세요."],
    )


class AmendConsultingContractRequest(BaseModel):
    """컨설팅 계약서 수정 제안. 보낸 항목만 바뀐다."""

    topic: str | None = Field(
        default=None,
        max_length=255,
        description="바꿀 「다룰 항목」.",
        examples=["회원탈퇴 경로와 결제 간헐 실패를 함께 봅니다"],
    )
    scheduledAt: str | None = Field(
        default=None,
        description="바꿀 세션 일시.",
        examples=["2026-08-30T15:00:00+09:00"],
    )
    durationMinutes: int | None = Field(
        default=None,
        description="바꿀 세션 시간(분).",
        examples=[45],
    )
    price: int | None = Field(
        default=None,
        description="바꿀 세션 금액.",
        examples=[70000],
    )
    detail: str | None = Field(
        default=None,
        description="바꿀 상세 설명. 보내지 않으면 그대로 두고, 빈 문자열이면 지운다.",
        examples=[None],
    )
    message: str | None = Field(
        default=None,
        max_length=300,
        description="수정 제안과 함께 남길 전달 메시지.",
        examples=["시간을 45분으로 늘리고 싶습니다."],
    )


class SignContractRequest(BaseModel):
    """계약서 서명 요청."""

    signatureImageFileId: int = Field(
        description="서명판에 그린 서명을 올린 파일 식별자.",
        examples=[41],
    )


class WriteMessageRequest(BaseModel):
    """전달 메시지 작성 요청."""

    content: str = Field(
        description="남길 내용.",
        examples=["세션 전에 결제 실패가 언제 나는지 알려주세요."],
    )


class SummaryItemResponse(BaseModel):
    """세션 요약의 항목 하나."""

    title: str = Field(
        description="항목 제목.",
        examples=["회원탈퇴 데이터는 30일 보관 후 완전 삭제"],
    )
    detail: str | None = Field(
        description="덧붙인 설명. 없으면 null이다.",
        examples=["개인정보보호법상 즉시 삭제 의무는 없습니다."],
    )


class SummaryChangeResponse(BaseModel):
    """요청서에 반영할 수 있는 변경 후보 하나."""

    changeId: int = Field(
        description="변경 후보 식별자. 요청서에 반영할 때 이 값을 보낸다.",
        examples=[1],
    )
    kind: Literal["UPDATE", "ADD"] = Field(
        description=(
            "반영 방식. UPDATE는 이미 있는 요구사항을 고치는 것, ADD는 새로 넣는 것이다."
        ),
        examples=["UPDATE"],
    )
    issueId: int | None = Field(
        description="고칠 요구사항 식별자. 새로 넣는 것이면 null이다.",
        examples=[312],
    )
    title: str = Field(
        min_length=1,
        description="반영할 요구사항 문장.",
        examples=["검수 기준 갱신 · 회원탈퇴 경로 구현"],
    )
    detail: str | None = Field(
        description="왜 바꾸는지에 대한 설명. 없으면 null이다.",
        examples=["결제 실패는 코드 수정 전에 재현 로그부터 모아야 합니다."],
    )
    isApplied: bool = Field(
        description="이미 요청서에 반영했는지.",
        examples=[False],
    )

    @model_validator(mode="after")
    def validate_issue_reference(self):
        if self.kind == "UPDATE" and self.issueId is None:
            raise ValueError("UPDATE 변경에는 기존 요구사항 식별자가 필요합니다.")
        if self.kind == "ADD" and self.issueId is not None:
            raise ValueError("ADD 변경에는 기존 요구사항 식별자를 넣을 수 없습니다.")
        return self


class SessionSummaryResponse(BaseModel):
    """세션이 끝난 뒤 만들어지는 요약."""

    sessionId: int = Field(description="컨설팅 세션 식별자.", examples=[55])
    discussed: list[SummaryItemResponse] = Field(
        description="논의한 것. 요청서에는 반영되지 않는다.",
    )
    decided: list[SummaryItemResponse] = Field(description="결정된 것.")
    nextActions: list[SummaryItemResponse] = Field(description="다음에 할 것.")
    changes: list[SummaryChangeResponse] = Field(
        description="요청서에 반영할 수 있는 변경 후보.",
    )
    updatedAt: str = Field(
        description="요약을 마지막으로 고친 시각.",
        examples=["2026-08-29T14:35:00+09:00"],
    )


class ApplySummaryChangesRequest(BaseModel):
    """요약의 변경 후보를 요청서에 반영한다."""

    changeIds: list[int] = Field(
        min_length=1,
        description="반영할 변경 후보 식별자.",
        examples=[[1, 2]],
    )


# --------------------------------------------------------------------------- #
# 영역 4 — 실패 응답 문구
# --------------------------------------------------------------------------- #

CONTRACT_NOT_FOUND = ("CONTRACT_NOT_FOUND", "계약서를 찾을 수 없습니다.")
DELIVERABLE_NOT_FOUND = ("DELIVERABLE_NOT_FOUND", "인계물을 찾을 수 없습니다.")
DELIVERABLE_NOT_UPLOADED = (
    "DELIVERABLE_NOT_UPLOADED",
    "아직 올라온 파일이 없습니다.",
)
DELIVERABLE_NOT_UPLOADED_EXAMPLE = {
    "code": DELIVERABLE_NOT_UPLOADED[0],
    "message": DELIVERABLE_NOT_UPLOADED[1],
}
DELIVERABLE_FILE_NAME = "komit-deliverable.zip"
DELIVERABLE_FILE_BODY = b"komit-mock-deliverable"
DELIVERABLE_NOT_FOUND_EXAMPLE = {
    "code": DELIVERABLE_NOT_FOUND[0],
    "message": DELIVERABLE_NOT_FOUND[1],
}
SESSION_NOT_FOUND = ("SESSION_NOT_FOUND", "세션을 찾을 수 없습니다.")
CONTRACT_ALREADY_SIGNED = (
    "CONTRACT_ALREADY_SIGNED",
    "이미 체결된 계약서는 고칠 수 없습니다.",
)
ALREADY_SIGNED_BY_ME = ("ALREADY_SIGNED_BY_ME", "이미 서명하셨습니다.")
CONTRACT_CANCELLED = ("CONTRACT_CANCELLED", "취소된 계약입니다.")
CONTRACT_CONCLUDED = (
    "CONTRACT_CONCLUDED",
    "양쪽이 서명해 성립한 계약은 취소할 수 없습니다.",
)
REFUND_PENDING = (
    "REFUND_PENDING",
    "계약을 취소했습니다. 환불을 확인하는 중이니 잠시 뒤 다시 눌러 주세요.",
)
SESSION_CANCELLED = ("SESSION_CANCELLED", "취소된 계약이라 세션을 열 수 없습니다.")
SESSION_NOT_OPEN = (
    "SESSION_NOT_OPEN",
    "아직 입장할 수 없습니다. 시작 10분 전부터 들어갈 수 있습니다.",
)
SUMMARY_NOT_READY = ("SUMMARY_NOT_READY", "요약을 아직 만드는 중입니다.")
SESSION_NOT_ENDED = ("SESSION_NOT_ENDED", "아직 끝나지 않은 세션입니다.")
RECORDING_ALREADY_UPLOADED = (
    "RECORDING_ALREADY_UPLOADED",
    "이미 녹음을 올렸습니다.",
)


# --------------------------------------------------------------------------- #
# 영역 4 — 목 데이터
# --------------------------------------------------------------------------- #

AREA4_TAG = "4 컨설팅"

CONTRACT_ID = 120
SESSION_ID = 55
NOT_FOUND_CONTRACT_ID = 404
NOT_FOUND_DELIVERABLE_ID = 404
NOT_UPLOADED_DELIVERABLE_ID = 409
REVIEWED_CONTRACT_ID = 409
NOT_COMPLETED_CONTRACT_ID = 400
SIGNED_CONTRACT_ID = 409
# 개발자가 서명하기 전에 의뢰인이 취소한 계약. 서명·수정·예치가 모두 409 다. (R-CON-21)
CANCELLED_CONTRACT_ID = 133
# 취소는 됐지만 예치금 환불 결과를 아직 모르는 계약. 같은 요청을 다시 보내면 환불을 잇는다.
REFUND_PENDING_CONTRACT_ID = 503
NOT_FOUND_SESSION_ID = 404
# 취소된 계약에 잡혀 있던 세션. 입장을 받지 않는다. (R-CON-21)
CANCELLED_SESSION_ID = 133
NOT_OPEN_SESSION_ID = 409
PENDING_CONSENT_SESSION_ID = 203
# 아직 아무도 동의하지 않은 세션. 입장 안내 화면을 재현한다. (R-CON-17)
NO_CONSENT_SESSION_ID = 205
NO_SUMMARY_SESSION_ID = 202
NOT_ENDED_SESSION_ID = 400
RECORDED_SESSION_ID = 410

SESSION_EXAMPLE = {
    "sessionId": SESSION_ID,
    "contractId": CONTRACT_ID,
    "price": 50000,
    "durationMinutes": 30,
    "reservation": {
        "reservationId": 71,
        "scheduledAt": "2026-08-29T14:00:00+09:00",
        "status": "CONFIRMED",
    },
    "startedAt": None,
    "endedAt": None,
    "chatRefId": "session-55-chat",
}

# 양쪽이 동의하고 들어간 세션. 기본 흐름이다.
SESSION_PARTICIPATIONS_EXAMPLE = [
    {
        "userId": 12,
        "consentedAt": "2026-08-29T13:55:00+09:00",
        "joinedAt": "2026-08-29T14:00:00+09:00",
    },
    {
        "userId": 30,
        "consentedAt": "2026-08-29T13:58:00+09:00",
        "joinedAt": "2026-08-29T14:00:00+09:00",
    },
]

# 나만 동의하고 상대를 기다리는 중. 들어간 시각이 아직 없다. (R-CON-13)
SESSION_PARTICIPATIONS_PENDING_EXAMPLE = [
    {
        "userId": 12,
        "consentedAt": "2026-08-29T13:55:00+09:00",
        "joinedAt": None,
    },
]

# 중계 서버를 둔 환경의 응답. 자격은 사람마다 다르고 시한부다. (R-CON-19)
ICE_SERVERS_EXAMPLE = {
    "iceServers": [
        {"urls": ["stun:turn.komit.site:3478"]},
        {
            "urls": [
                "turn:turn.komit.site:3478?transport=udp",
                "turn:turn.komit.site:3478?transport=tcp",
            ],
            "username": "1790238175:12",
            "credential": "8mCPiF2R1qkHrDbY0dzXqA1sZ0w=",
        },
    ],
    "expiresAt": "2026-08-29T16:00:00+09:00",
}

CONSULTING_CONTRACT_EXAMPLE = {
    "contractId": CONTRACT_ID,
    "roomId": 92,
    "contractType": "CONSULTING",
    "status": "NEGOTIATING",
    "consulting": {
        # 바깥이 NEGOTIATING 이므로 이 값은 아직 의미가 없다. 체결 여부는
        # 바깥 status 로만 판단한다.
        "status": "SIGNED",
        "totalAmount": 50000,
        "topic": "회원탈퇴 경로와 결제 간헐 실패를 함께 봅니다",
        "detail": "회원탈퇴 처리 흐름과 결제 실패 재현 방법을 화면 공유로 봅니다.",
        "sessions": [SESSION_EXAMPLE],
    },
    "development": None,
    "clientSignature": None,
    "developerSignature": None,
    "messages": [
        {
            "messageId": 31,
            "content": "세션 전에 확인하고 싶은 게 있으면 적어주세요.",
            "isClient": False,
            "createdAt": "2026-08-22T11:40:00+09:00",
        }
    ],
    "contractText": None,
    "updatedAt": "2026-08-22T11:40:00+09:00",
}

SESSION_SUMMARY_EXAMPLE = {
    "sessionId": SESSION_ID,
    "discussed": [
        {
            "title": "회원탈퇴 데이터를 얼마나 보관할지",
            "detail": "법상 최소 요건과 분쟁 대응 사이에서 정했습니다.",
        },
        {
            "title": "결제가 가끔 실패하는 건이 코드 문제인지 결제사 응답 문제인지",
            "detail": None,
        },
    ],
    "decided": [
        {
            "title": "회원탈퇴 데이터는 30일 보관 후 완전 삭제",
            "detail": "개인정보보호법상 즉시 삭제 의무는 없습니다.",
        }
    ],
    "nextActions": [
        {
            "title": "Vercel 로그에서 결제 실패 건 3개 이상 수집",
            "detail": "8월 31일까지 김미미님이 합니다.",
        }
    ],
    "changes": [
        {
            "changeId": 1,
            "kind": "UPDATE",
            "issueId": 312,
            "title": "검수 기준 갱신 · 회원탈퇴 경로 구현",
            "detail": "30일 보관 뒤 삭제로 확인 문장을 고칩니다.",
            "isApplied": False,
        },
        {
            "changeId": 2,
            "kind": "ADD",
            "issueId": None,
            "title": "결제 실패 로그 수집",
            "detail": "코드 수정 전에 재현 로그부터 모읍니다.",
            "isApplied": False,
        },
    ],
    "updatedAt": "2026-08-29T14:35:00+09:00",
}

SESSION_RECORDING_EXAMPLE = {
    "recordingId": 71,
    "speaker": "CLIENT",
    "fileId": 612,
    "uploadedAt": "2026-08-29T15:12:00+09:00",
}

CONTRACT_NOT_FOUND_EXAMPLE = {
    "code": "CONTRACT_NOT_FOUND",
    "message": "계약서를 찾을 수 없습니다.",
}

SESSION_NOT_FOUND_EXAMPLE = {
    "code": "SESSION_NOT_FOUND",
    "message": "세션을 찾을 수 없습니다.",
}

CONTRACT_ERRORS: dict = {
    404: {
        "model": ErrorResponse,
        "description": "계약서 없음",
        "content": {"application/json": {"example": CONTRACT_NOT_FOUND_EXAMPLE}},
    },
    **AREA1_UNAUTHORIZED,
    **AREA1_ERRORS,
}

SESSION_ERRORS: dict = {
    404: {
        "model": ErrorResponse,
        "description": "세션 없음",
        "content": {"application/json": {"example": SESSION_NOT_FOUND_EXAMPLE}},
    },
    **AREA1_UNAUTHORIZED,
    **AREA1_ERRORS,
}


# --------------------------------------------------------------------------- #
# 영역 4 — 계약서
# --------------------------------------------------------------------------- #


@router.post(
    "/rooms/{roomId}/consulting-contracts",
    response_model=ContractResponse,
    status_code=201,
    tags=[AREA4_TAG],
    responses={
        201: {
            "content": {"application/json": {"example": CONSULTING_CONTRACT_EXAMPLE}}
        },
        404: {
            "model": ErrorResponse,
            "description": "방 없음",
            "content": {"application/json": {"example": ROOM_NOT_FOUND_EXAMPLE}},
        },
        **AREA1_UNAUTHORIZED,
        **AREA1_ERRORS,
    },
    summary="컨설팅 계약서 작성",
    description=(
        "**화면 — `4_개발자_계약서작성` › 컨설팅 구성 · 날짜와 시간 · 보내기**\n\n"
        "개발자가 컨설팅 계약서를 써서 의뢰인에게 보낸다. 다룰 항목, 세션 일시, 세션 "
        "시간, 금액을 담는다. 보내면 의뢰인 화면에 협의중으로 뜬다.\n\n"
        "- **201**: 계약서를 보냈을 때. 아직 아무도 서명하지 않아 서명 두 개가 모두 null이다\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **403 / FORBIDDEN**: 이 방의 개발자가 아닐 때. 계약서는 개발자만 쓴다\n"
        "- **404 / ROOM_NOT_FOUND**: `roomId`가 404일 때\n"
        "- **422 / INVALID_REQUEST**: `scheduledAt`이 지난 시각이거나 `price`가 음수일 때\n"
        "- **500 / INTERNAL_ERROR**: 계약서를 만들지 못했을 때"
    ),
)
def write_consulting_contract(
    roomId: Annotated[
        int,
        Path(
            description="컨설팅 방 식별자. 404는 없음 재현용 예약값이다.",
            examples=[92],
        ),
    ],
    request: WriteConsultingContractRequest,
) -> ContractResponse | JSONResponse:
    if roomId == NOT_FOUND_ROOM_ID:
        return _error_response(404, ROOM_NOT_FOUND)
    # 보낸 값을 그대로 담아 돌려준다. 저장하는 것이 아니라 방금 무엇을 보냈는지
    # 화면이 확인할 수 있게 하는 것이라 호출 순서와는 무관하다.
    session = {
        **SESSION_EXAMPLE,
        "price": request.price,
        "durationMinutes": request.durationMinutes,
        "reservation": {
            **SESSION_EXAMPLE["reservation"],
            "scheduledAt": request.scheduledAt,
        },
    }
    return ContractResponse(
        **{
            **CONSULTING_CONTRACT_EXAMPLE,
            "consulting": {
                **CONSULTING_CONTRACT_EXAMPLE["consulting"],
                "topic": request.topic,
                "detail": request.detail,
                "totalAmount": request.price,
                "sessions": [session],
            },
        }
    )


@router.get(
    "/contracts/{contractId}",
    response_model=ContractResponse,
    tags=[AREA4_TAG],
    responses={
        200: {
            "content": {"application/json": {"example": CONSULTING_CONTRACT_EXAMPLE}}
        },
        **CONTRACT_ERRORS,
    },
    summary="계약서 조회",
    description=(
        "**화면 — `4_의뢰인_계약서상세` · `4_개발자_계약서검토` › 전체**\n\n"
        "계약서 내용과 서명 상태를 내려준다. 의뢰인과 개발자가 같은 API를 쓰고, 화면이 "
        "자기 서명 여부를 보고 '서명하고 결제' 또는 '서명하고 계약하기'를 보여준다.\n\n"
        "체결 전에는 `contractText`가 null이다. 체결되는 순간 그때의 문구가 담기고 이후 "
        "바뀌지 않는다.\n\n"
        "**갈래는 `contractId`로 갈린다.** 목에서는 120이 컨설팅, 130이 개발 계약서다. "
        "방 상세의 `contractId`를 그대로 넣으면 그 방의 갈래에 맞는 계약서가 온다.\n\n"
        "- **200**: 계약서가 있고 내가 당사자일 때\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **404 / CONTRACT_NOT_FOUND**: `contractId`가 404일 때. 없거나 남의 계약이다\n"
        "- **500 / INTERNAL_ERROR**: 계약서를 읽지 못했을 때"
    ),
)
def get_contract(
    contractId: Annotated[
        int,
        Path(
            description="계약 식별자. 404는 없음 재현용 예약값이다.",
            examples=[CONTRACT_ID],
        ),
    ],
) -> ContractResponse | JSONResponse:
    if contractId == NOT_FOUND_CONTRACT_ID:
        return _error_response(404, CONTRACT_NOT_FOUND)
    if contractId == DEV_CONTRACT_ID:
        return ContractResponse(**DEV_CONTRACT_EXAMPLE)
    if contractId == DONE_CONTRACT_ID:
        return ContractResponse(**DONE_CONTRACT_EXAMPLE)
    if contractId == DRAFT_DEV_CONTRACT_ID:
        return ContractResponse(**DRAFT_DEV_CONTRACT_EXAMPLE)
    if contractId == DRAFT_CLIENT_CONTRACT_ID:
        return ContractResponse(**DRAFT_CLIENT_CONTRACT_EXAMPLE)
    if contractId == BEFORE_START_CONTRACT_ID:
        return ContractResponse(**BEFORE_START_CONTRACT_EXAMPLE)
    if contractId == CANCELLED_CONTRACT_ID:
        return ContractResponse(**CANCELLED_CONTRACT_EXAMPLE)
    return ContractResponse(**CONSULTING_CONTRACT_EXAMPLE)


@router.patch(
    "/contracts/{contractId}",
    response_model=ContractResponse,
    tags=[AREA4_TAG],
    responses={
        200: {
            "content": {"application/json": {"example": CONSULTING_CONTRACT_EXAMPLE}}
        },
        409: {
            "model": ErrorResponse,
            "description": "이미 체결되어 고칠 수 없음 · 취소된 계약",
            "content": {
                "application/json": {
                    "examples": {
                        "CONTRACT_ALREADY_SIGNED": {
                            "summary": "이미 서명함",
                            "value": {
                                "code": CONTRACT_ALREADY_SIGNED[0],
                                "message": CONTRACT_ALREADY_SIGNED[1],
                            },
                        },
                        "CONTRACT_CANCELLED": {
                            "summary": "취소된 계약",
                            "value": {
                                "code": CONTRACT_CANCELLED[0],
                                "message": CONTRACT_CANCELLED[1],
                            },
                        },
                    }
                }
            },
        },
        **CONTRACT_ERRORS,
    },
    summary="계약서 수정 제안",
    description=(
        "**화면 — `4_의뢰인_계약서수정제안` › 수정 제안 보내기**\n\n"
        "의뢰인이 시간·금액·설명을 고쳐 다시 제안한다. 보낸 항목만 바뀌고 나머지는 그대로 "
        "남는다. 계약서는 판을 나누지 않고 이 계약서 자체가 바뀐다.\n\n"
        "**한쪽이라도 서명한 뒤에는 고칠 수 없다.** 그때는 채팅으로 이야기한 뒤 다시 "
        "계약서를 쓴다.\n\n"
        "- **200**: 고쳤을 때. 상태는 협의중으로 남는다\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **404 / CONTRACT_NOT_FOUND**: `contractId`가 404일 때\n"
        "- **409 / CONTRACT_ALREADY_SIGNED**: `contractId`가 409일 때\n"
        "- **409 / CONTRACT_CANCELLED**: 의뢰인이 취소한 계약일 때 (R-CON-21). 목에서는 "
        f"`contractId`={CANCELLED_CONTRACT_ID}이다\n"
        "- **422 / INVALID_REQUEST**: `scheduledAt`이 지난 시각이거나 `price`가 음수일 때\n"
        "- **500 / INTERNAL_ERROR**: 고치지 못했을 때"
    ),
)
def amend_contract(
    contractId: Annotated[
        int,
        Path(
            description=(
                "계약 식별자. 404는 없음, 409는 이미 체결됨 재현용 예약값이다. "
                f"{CANCELLED_CONTRACT_ID}은 취소된 계약이다."
            ),
            examples=[CONTRACT_ID],
        ),
    ],
    request: AmendConsultingContractRequest,
) -> ContractResponse | JSONResponse:
    if contractId == NOT_FOUND_CONTRACT_ID:
        return _error_response(404, CONTRACT_NOT_FOUND)
    if contractId == SIGNED_CONTRACT_ID:
        return _error_response(409, CONTRACT_ALREADY_SIGNED)
    if contractId == CANCELLED_CONTRACT_ID:
        return _error_response(409, CONTRACT_CANCELLED)
    return ContractResponse(**CONSULTING_CONTRACT_EXAMPLE)


@router.post(
    "/contracts/{contractId}/signatures",
    response_model=ContractResponse,
    status_code=201,
    tags=[AREA4_TAG],
    responses={
        201: {
            "content": {
                "application/json": {
                    "example": {
                        **CONSULTING_CONTRACT_EXAMPLE,
                        # 의뢰인만 서명한 상태다. 개발자 서명이 남아 있으므로
                        # 계약은 아직 체결되지 않았다. (R-CON-01)
                        "status": "NEGOTIATING",
                        "clientSignature": {
                            "userId": 12,
                            "isSigned": True,
                            "signedAt": "2026-08-22T16:10:00+09:00",
                            "signatureImageFileId": 41,
                        },
                    }
                }
            }
        },
        409: {
            "model": ErrorResponse,
            "description": "이미 서명함 · 취소된 계약",
            "content": {
                "application/json": {
                    "examples": {
                        "ALREADY_SIGNED_BY_ME": {
                            "summary": "이미 서명함",
                            "value": {
                                "code": ALREADY_SIGNED_BY_ME[0],
                                "message": ALREADY_SIGNED_BY_ME[1],
                            },
                        },
                        "CONTRACT_CANCELLED": {
                            "summary": "취소된 계약",
                            "value": {
                                "code": CONTRACT_CANCELLED[0],
                                "message": CONTRACT_CANCELLED[1],
                            },
                        },
                    }
                }
            },
        },
        **CONTRACT_ERRORS,
    },
    summary="계약서 서명",
    description=(
        "**화면 — `4_의뢰인_계약서상세` › 서명하고 결제 · "
        "`4_개발자_계약서검토` › 서명하고 계약하기 · `5_의뢰인_계약서상세` › "
        "서명하고 예치하기 · `5_개발자_계약서검토` › 서명하고 계약하기 · "
        "`5_서명판`**\n\n"
        "계약서의 서명 버튼을 누르면 `5_서명판`이 열리고, 거기서 그린 서명을 이 API로 "
        "올린다. 서명판은 컨설팅과 개발이 같은 것을 쓴다.\n\n"
        "양쪽이 모두 서명하면 계약이 체결되고 그 시점 문구가 `contractText`에 그대로 "
        "보관된다.\n\n"
        "서명한 뒤에는 계약서를 고칠 수 없다.\n\n"
        "- **201**: 서명했을 때. 상대가 아직이면 계약은 협의중으로 남는다\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **404 / CONTRACT_NOT_FOUND**: `contractId`가 404일 때\n"
        "- **409 / ALREADY_SIGNED_BY_ME**: `contractId`가 409일 때. 이미 내가 서명했다\n"
        "- **409 / CONTRACT_CANCELLED**: 의뢰인이 취소한 계약일 때. 개발자가 뒤늦게 서명하려 "
        "해도 받지 않는다 (R-CON-21). 목에서는 "
        f"`contractId`={CANCELLED_CONTRACT_ID}이다\n"
        "- **422 / INVALID_REQUEST**: `signatureImageFileId`가 없을 때\n"
        "- **500 / INTERNAL_ERROR**: 서명하지 못했을 때"
    ),
)
def sign_contract(
    contractId: Annotated[
        int,
        Path(
            description=(
                "계약 식별자. 404는 없음, 409는 이미 서명함 재현용 예약값이다. "
                f"{CANCELLED_CONTRACT_ID}은 취소된 계약이다."
            ),
            examples=[CONTRACT_ID],
        ),
    ],
    request: SignContractRequest,
) -> ContractResponse | JSONResponse:
    if contractId == NOT_FOUND_CONTRACT_ID:
        return _error_response(404, CONTRACT_NOT_FOUND)
    if contractId == SIGNED_CONTRACT_ID:
        return _error_response(409, ALREADY_SIGNED_BY_ME)
    if contractId == CANCELLED_CONTRACT_ID:
        return _error_response(409, CONTRACT_CANCELLED)
    return ContractResponse(
        **{
            **CONSULTING_CONTRACT_EXAMPLE,
            # 의뢰인만 서명했으므로 아직 협의중이다. (R-CON-01)
            "status": "NEGOTIATING",
            "clientSignature": {
                "userId": 12,
                "isSigned": True,
                "signedAt": "2026-08-22T16:10:00+09:00",
                "signatureImageFileId": request.signatureImageFileId,
            },
        }
    )


# 의뢰인이 서명하고 결제한 뒤, 개발자가 서명하기 전에 취소한 계약. (R-CON-21)
CANCELLED_CONTRACT_EXAMPLE = {
    **CONSULTING_CONTRACT_EXAMPLE,
    "contractId": CANCELLED_CONTRACT_ID,
    "status": "CANCELLED",
    "clientSignature": {
        "userId": 12,
        "isSigned": True,
        "signedAt": "2026-08-22T16:10:00+09:00",
        "signatureImageFileId": 41,
    },
}


@router.post(
    "/contracts/{contractId}/cancellation",
    response_model=ContractResponse,
    tags=[AREA4_TAG],
    responses={
        200: {"content": {"application/json": {"example": CANCELLED_CONTRACT_EXAMPLE}}},
        409: {
            "model": ErrorResponse,
            "description": "이미 성립한 계약",
            "content": {
                "application/json": {
                    "example": {
                        "code": CONTRACT_CONCLUDED[0],
                        "message": CONTRACT_CONCLUDED[1],
                    }
                }
            },
        },
        503: {
            "model": ErrorResponse,
            "description": "취소는 됐고 환불 결과를 아직 모름",
            "content": {
                "application/json": {
                    "example": {
                        "code": REFUND_PENDING[0],
                        "message": REFUND_PENDING[1],
                    }
                }
            },
        },
        **CONTRACT_ERRORS,
    },
    summary="계약 취소",
    description=(
        "**화면 — `4_의뢰인_계약서상세` › 계약 취소**\n\n"
        "개발자가 서명하기 전에 의뢰인이 계약을 취소한다. 예치금이 있으면 전액을 예치할 때 "
        "쓴 회원가입 발급 계좌로 돌려준다 (R-CON-21, R-PAY-04).\n\n"
        "취소된 계약은 되살릴 수 없다. 개발자가 뒤늦게 서명하려 해도, 누가 계약서를 고치거나 "
        "예치하려 해도 409 `CONTRACT_CANCELLED`다. 그 계약에 잡혀 있던 세션에는 들어갈 수 "
        "없고, 대금도 개발자에게 나가지 않는다.\n\n"
        "**같은 요청을 다시 보내도 된다.** 이미 취소된 계약이면 그대로 200을 주고, 환불이 "
        "끝나지 않았으면 이어서 진행한다. 돈은 두 번 나가지 않는다.\n\n"
        "- **200**: 취소했을 때. 예치금이 있었으면 환불까지 끝났다. `status`가 `CANCELLED`다\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **403 / FORBIDDEN**: 이 계약의 의뢰인이 아닐 때. 취소는 의뢰인만 한다\n"
        "- **404 / CONTRACT_NOT_FOUND**: `contractId`가 404일 때. 없거나 남의 계약이다\n"
        "- **409 / CONTRACT_CONCLUDED**: `contractId`가 409일 때. 양쪽이 서명해 이미 "
        "성립한 계약이다\n"
        "- **503 / REFUND_PENDING**: `contractId`가 503일 때. 계약은 취소됐지만 환불 결과를 "
        "아직 확인하지 못했다. 잠시 뒤 같은 요청을 다시 보낸다\n"
        "- **500 / INTERNAL_ERROR**: 취소하지 못했을 때"
    ),
)
def cancel_contract(
    contractId: Annotated[
        int,
        Path(
            description=(
                "계약 식별자. 404는 없음, 409는 이미 성립, 503은 환불 확인 중 재현용 "
                "예약값이다."
            ),
            examples=[CONTRACT_ID],
        ),
    ],
) -> ContractResponse | JSONResponse:
    if contractId == NOT_FOUND_CONTRACT_ID:
        return _error_response(404, CONTRACT_NOT_FOUND)
    if contractId == SIGNED_CONTRACT_ID:
        return _error_response(409, CONTRACT_CONCLUDED)
    if contractId == REFUND_PENDING_CONTRACT_ID:
        return _error_response(503, REFUND_PENDING)
    return ContractResponse(**{**CANCELLED_CONTRACT_EXAMPLE, "contractId": contractId})


@router.post(
    "/contracts/{contractId}/messages",
    response_model=TempMessageResponse,
    status_code=201,
    tags=[AREA4_TAG],
    responses={
        201: {
            "content": {
                "application/json": {
                    "example": CONSULTING_CONTRACT_EXAMPLE["messages"][0]
                }
            }
        },
        **CONTRACT_ERRORS,
    },
    summary="전달 메시지 남기기",
    description=(
        "**화면 — `4_개발자_계약서작성` › 전달 메시지 · "
        "`4_의뢰인_계약서상세` › 무엇을 물어볼지 미리 적어주세요**\n\n"
        "계약서에 딸려 상대에게 남기는 짧은 메모다. 채팅이 아니라 계약서에 붙는 한 줄이라 "
        "세션 전에 상대가 읽는다.\n\n"
        "- **201**: 남겼을 때. `isClient`로 누가 남겼는지 구분한다\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **404 / CONTRACT_NOT_FOUND**: `contractId`가 404일 때\n"
        "- **422 / INVALID_REQUEST**: `content`가 비었거나 300자를 넘을 때\n"
        "- **500 / INTERNAL_ERROR**: 남기지 못했을 때"
    ),
)
def write_contract_message(
    contractId: Annotated[
        int,
        Path(
            description="계약 식별자. 404는 없음 재현용 예약값이다.",
            examples=[CONTRACT_ID],
        ),
    ],
    request: WriteMessageRequest,
) -> TempMessageResponse | JSONResponse:
    if contractId == NOT_FOUND_CONTRACT_ID:
        return _error_response(404, CONTRACT_NOT_FOUND)
    return TempMessageResponse(
        messageId=32,
        content=request.content,
        isClient=True,
        createdAt="2026-08-22T11:44:00+09:00",
    )


# --------------------------------------------------------------------------- #
# 영역 4 — 세션
# --------------------------------------------------------------------------- #


@router.get(
    "/consulting-sessions/{sessionId}",
    response_model=ConsultingSessionResponse,
    tags=[AREA4_TAG],
    responses={
        200: {"content": {"application/json": {"example": SESSION_EXAMPLE}}},
        **SESSION_ERRORS,
    },
    summary="컨설팅 세션 조회",
    description=(
        "**화면 — `4_의뢰인_입장전` › 세션 안내와 입장 버튼**\n\n"
        "세션 일시와 시간, 진행 상태를 내려준다. 화면은 `reservation.scheduledAt`과 지금 "
        "시각을 견주어 입장 버튼을 열지 정한다. 시작 전 정해진 시간부터 들어갈 수 있다. (R-CON-08)\n\n"
        "예약은 한 세션에 하나뿐이고, 서명한 뒤에는 일정을 바꾸지 않는다.\n\n"
        "**세션 중의 화상과 채팅은 이 응답에 없다.** `chatRefId`로 그쪽에서 잇는다.\n\n"
        "- **200**: 세션이 있고 내가 당사자일 때. 시작 전이면 `startedAt`이 null이다\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **404 / SESSION_NOT_FOUND**: `sessionId`가 404일 때\n"
        "- **500 / INTERNAL_ERROR**: 세션을 읽지 못했을 때"
    ),
)
def get_consulting_session(
    sessionId: Annotated[
        int,
        Path(
            description="컨설팅 세션 식별자. 404는 없음 재현용 예약값이다.",
            examples=[SESSION_ID],
        ),
    ],
) -> ConsultingSessionResponse | JSONResponse:
    if sessionId == NOT_FOUND_SESSION_ID:
        return _error_response(404, SESSION_NOT_FOUND)
    # 물어본 식별자를 그대로 돌려준다. 화면은 이 값으로 입장·종료를 부르므로, 고정값을
    # 주면 203 같은 재현용 식별자로 들어와도 55의 흐름이 나와 재현이 되지 않는다.
    return ConsultingSessionResponse(**{**SESSION_EXAMPLE, "sessionId": sessionId})


@router.get(
    "/consulting-sessions/{sessionId}/participations",
    response_model=list[SessionParticipationResponse],
    tags=[AREA4_TAG],
    responses={
        200: {
            "content": {
                "application/json": {"example": SESSION_PARTICIPATIONS_EXAMPLE}
            }
        },
        **SESSION_ERRORS,
    },
    summary="세션 참가자 기록",
    description=(
        "**화면 — 세션 입장 안내**\n\n"
        "이 세션에 동의한 사람들의 기록을 돌려준다. **목록에 있다는 것이 곧 동의했다는 "
        "뜻이다.** (R-CON-17)\n\n"
        "화면은 이것으로 자기가 이미 동의했는지 가린다. 없으면 입장 안내를 보여 주고, "
        "있으면 곧장 세션으로 보낸다. 통화 중에 새로고침해도 안내부터 다시 묻지 않는 "
        "근거가 이 목록이다.\n\n"
        "상대의 기록도 함께 준다. 「상대방은 이미 동의했습니다」를 안내에 적으려면 "
        "필요하다. (R-CON-13)\n\n"
        "- **200**: 받았을 때. `205`는 빈 목록, `203`은 나 하나만 돌아온다\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **404 / SESSION_NOT_FOUND**: `sessionId`가 404일 때\n"
        "- **500 / INTERNAL_ERROR**: 기록을 읽지 못했을 때"
    ),
)
def get_session_participations(
    sessionId: Annotated[
        int,
        Path(
            description=(
                "컨설팅 세션 식별자. 404는 없음, 205는 아무도 동의하지 않음, "
                "203은 나만 동의함 재현용 예약값이다."
            ),
            examples=[SESSION_ID],
        ),
    ],
) -> list[SessionParticipationResponse] | JSONResponse:
    if sessionId == NOT_FOUND_SESSION_ID:
        return _error_response(404, SESSION_NOT_FOUND)
    if sessionId == NO_CONSENT_SESSION_ID:
        return []
    if sessionId == PENDING_CONSENT_SESSION_ID:
        return [
            SessionParticipationResponse(**record)
            for record in SESSION_PARTICIPATIONS_PENDING_EXAMPLE
        ]
    return [
        SessionParticipationResponse(**record)
        for record in SESSION_PARTICIPATIONS_EXAMPLE
    ]


@router.get(
    "/consulting-sessions/{sessionId}/ice-servers",
    response_model=IceServersResponse,
    tags=[AREA4_TAG],
    responses={
        200: {"content": {"application/json": {"example": ICE_SERVERS_EXAMPLE}}},
        **SESSION_ERRORS,
    },
    summary="통화에 쓸 주소와 자격",
    description=(
        "**화면 — 세션 통화**\n\n"
        "브라우저가 상대와 연결할 때 쓰는 주소와 자격을 돌려준다. 그대로 "
        "`RTCPeerConnection`에 넣는다.\n\n"
        "**중계 자격은 한 통화보다 오래 산다.** (R-CON-19) 통화 도중에 죽으면 중계를 "
        "다시 얻을 길이 없어서다. 화면은 통화를 열 때 한 번 부르면 된다.\n\n"
        "자격은 발급한 뒤 취소할 수 없다. 그래서 세션 당사자에게만 준다 — 우리 서버로 "
        "남의 트래픽을 흘릴 수 있는 열쇠다.\n\n"
        "**이 값들은 어디에도 저장하지 않는다.** 공유 비밀로 계산해 발급하고 중계 서버가 "
        "같은 계산으로 맞춰 보므로 ERD에 자리가 없다. 속성 이름은 브라우저 WebRTC 규격이 "
        "정한 것이라 바꿀 수 없다. (`mock/AGENTS.md` — 발급만 하고 저장하지 않는 자격)\n\n"
        "- **200**: 받았을 때\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **404 / SESSION_NOT_FOUND**: `sessionId`가 404일 때\n"
        "- **500 / INTERNAL_ERROR**: 자격을 만들지 못했을 때"
    ),
)
def get_session_ice_servers(
    sessionId: Annotated[
        int,
        Path(
            description="컨설팅 세션 식별자. 404는 없음 재현용 예약값이다.",
            examples=[SESSION_ID],
        ),
    ],
) -> IceServersResponse | JSONResponse:
    if sessionId == NOT_FOUND_SESSION_ID:
        return _error_response(404, SESSION_NOT_FOUND)
    return IceServersResponse(**ICE_SERVERS_EXAMPLE)


@router.post(
    "/consulting-sessions/{sessionId}/participations",
    response_model=ConsultingSessionResponse,
    status_code=201,
    tags=[AREA4_TAG],
    responses={
        201: {
            "content": {
                "application/json": {
                    "example": {
                        **SESSION_EXAMPLE,
                        "startedAt": "2026-08-29T14:00:00+09:00",
                    }
                }
            }
        },
        202: {
            "description": "내 동의는 기록됐고 상대방 동의를 기다리는 중",
            "content": {"application/json": {"example": SESSION_EXAMPLE}},
        },
        409: {
            "model": ErrorResponse,
            "description": "아직 입장할 수 없음 · 취소된 계약",
            "content": {
                "application/json": {
                    "examples": {
                        "SESSION_NOT_OPEN": {
                            "summary": "입장 시각 전이거나 끝났거나 계약 성립 전인 세션",
                            "value": {
                                "code": SESSION_NOT_OPEN[0],
                                "message": SESSION_NOT_OPEN[1],
                            },
                        },
                        "SESSION_CANCELLED": {
                            "summary": "취소된 계약의 세션",
                            "value": {
                                "code": SESSION_CANCELLED[0],
                                "message": SESSION_CANCELLED[1],
                            },
                        },
                    }
                }
            },
        },
        **SESSION_ERRORS,
    },
    summary="세션 입장",
    description=(
        "**화면 — `4_의뢰인_입장전` › 입장**\n\n"
        "이 요청 자체가 녹음·전사·요약 동의와 입장 요청을 겸한다. 화면은 요청 전에 그 "
        "내용을 안내해야 한다. 의뢰인과 개발자 양쪽의 요청이 모두 모이기 전에는 실제 "
        "입장시키지 않는다. 먼저 요청한 참가자에게는 202를 돌려 상대 동의를 기다리게 "
        "한다. 같은 참가자가 기다리는 동안 다시 요청해도 동의는 한 번만 기록한다. "
        "(R-CON-13)\n\n"
        "양쪽 동의가 모인 뒤 실제로 들어간 시각이 참여 기록으로 남고, 최초 입장 시각을 "
        "세션 시작 시각으로 기록한다. (R-CON-09)\n\n"
        "화상 연결 자체는 이 API가 하지 않는다. 들어갈 수 있는지 판정하고 기록만 남긴다.\n\n"
        "- **201**: 양쪽이 동의했고 실제로 들어갔을 때. 최초 입장이면 `startedAt`이 채워진다\n"
        "- **202**: 내 동의는 기록됐지만 상대방 동의를 기다릴 때\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **404 / SESSION_NOT_FOUND**: `sessionId`가 404일 때\n"
        "- **409 / SESSION_NOT_OPEN**: `sessionId`가 409일 때. 아직 이른 시각이거나, 이미 "
        "끝났거나, 계약이 성립하지 않은 세션이다\n"
        "- **409 / SESSION_CANCELLED**: 의뢰인이 취소한 계약의 세션일 때. 동의도 기록하지 "
        f"않는다 (R-CON-21). 목에서는 `sessionId`={CANCELLED_SESSION_ID}이다\n"
        "- **500 / INTERNAL_ERROR**: 들어가지 못했을 때"
    ),
)
def join_consulting_session(
    sessionId: Annotated[
        int,
        Path(
            description=(
                "컨설팅 세션 식별자. 404는 없음, 409는 입장 불가, 203은 상대 동의 "
                f"대기 재현용 예약값이다. {CANCELLED_SESSION_ID}은 취소된 계약의 세션이다."
            ),
            examples=[SESSION_ID],
        ),
    ],
) -> ConsultingSessionResponse | JSONResponse:
    if sessionId == NOT_FOUND_SESSION_ID:
        return _error_response(404, SESSION_NOT_FOUND)
    if sessionId == NOT_OPEN_SESSION_ID:
        return _error_response(409, SESSION_NOT_OPEN)
    if sessionId == CANCELLED_SESSION_ID:
        return _error_response(409, SESSION_CANCELLED)
    if sessionId == PENDING_CONSENT_SESSION_ID:
        return JSONResponse(status_code=202, content=SESSION_EXAMPLE)
    return ConsultingSessionResponse(
        **{**SESSION_EXAMPLE, "startedAt": "2026-08-29T14:00:00+09:00"}
    )


@router.post(
    "/consulting-sessions/{sessionId}/end",
    response_model=ConsultingSessionResponse,
    tags=[AREA4_TAG],
    responses={
        200: {
            "content": {
                "application/json": {
                    "example": {
                        **SESSION_EXAMPLE,
                        "startedAt": "2026-08-29T14:00:00+09:00",
                        "endedAt": "2026-08-29T14:32:00+09:00",
                    }
                }
            }
        },
        **SESSION_ERRORS,
    },
    summary="세션 종료",
    description=(
        "**화면 — `4_의뢰인_세션중` › 세션 종료**\n\n"
        "세션을 마치고 끝난 시각을 기록한다. 같은 요청을 다시 보내도 최초 종료 시각과 "
        "같은 결과가 돌아온다. (R-CON-11)\n\n"
        "종료 뒤 참가자들이 각자 녹음을 올린다. 전사와 요약은 양쪽 녹음이 준비되거나 "
        "녹음 누락 대기 조건을 충족한 뒤 시작하므로 종료 응답에 단일 녹음 파일을 싣지 "
        "않는다. (R-CON-10, R-CON-12)\n\n"
        "- **200**: 세션 종료가 확정됐을 때\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **404 / SESSION_NOT_FOUND**: `sessionId`가 404일 때\n"
        "- **500 / INTERNAL_ERROR**: 마치지 못했을 때"
    ),
)
def end_consulting_session(
    sessionId: Annotated[
        int,
        Path(
            description="컨설팅 세션 식별자. 404는 없음 재현용 예약값이다.",
            examples=[SESSION_ID],
        ),
    ],
) -> ConsultingSessionResponse | JSONResponse:
    if sessionId == NOT_FOUND_SESSION_ID:
        return _error_response(404, SESSION_NOT_FOUND)
    return ConsultingSessionResponse(
        **{
            **SESSION_EXAMPLE,
            "startedAt": "2026-08-29T14:00:00+09:00",
            "endedAt": "2026-08-29T14:32:00+09:00",
        }
    )


@router.get(
    "/consulting-sessions/{sessionId}/summary",
    response_model=SessionSummaryResponse,
    tags=[AREA4_TAG],
    responses={
        200: {"content": {"application/json": {"example": SESSION_SUMMARY_EXAMPLE}}},
        409: {
            "model": ErrorResponse,
            "description": "요약을 아직 만드는 중",
            "content": {
                "application/json": {
                    "example": {
                        "code": "SUMMARY_NOT_READY",
                        "message": "요약을 아직 만드는 중입니다.",
                    }
                }
            },
        },
        **SESSION_ERRORS,
    },
    summary="세션 요약 조회",
    description=(
        "**화면 — `4_의뢰인_요약` › 논의한 것 · 결정된 것 · 다음에 할 것 · 변경 후보**\n\n"
        "세션이 끝난 뒤 만들어진 요약을 내려준다. 녹음을 글로 옮겨 정리한 뒤 개발자가 "
        "검토한 결과다.\n\n"
        "`changes`는 요청서에 반영할 수 있는 후보다. 화면의 'N건 요청서에 반영' 버튼이 "
        "이 목록을 쓴다. `discussed`는 반영 대상이 아니다.\n\n"
        "- **200**: 요약이 만들어졌을 때\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **404 / SESSION_NOT_FOUND**: `sessionId`가 404일 때\n"
        "- **409 / SUMMARY_NOT_READY**: `sessionId`가 202일 때. 세션은 끝났지만 요약이 "
        "아직 준비되지 않았다\n"
        "- **500 / INTERNAL_ERROR**: 요약을 읽지 못했을 때"
    ),
)
def get_session_summary(
    sessionId: Annotated[
        int,
        Path(
            description=(
                "컨설팅 세션 식별자. 404는 없음, 202는 요약 준비 전 재현용 예약값이다."
            ),
            examples=[SESSION_ID],
        ),
    ],
) -> SessionSummaryResponse | JSONResponse:
    if sessionId == NOT_FOUND_SESSION_ID:
        return _error_response(404, SESSION_NOT_FOUND)
    if sessionId == NO_SUMMARY_SESSION_ID:
        return _error_response(409, SUMMARY_NOT_READY)
    return SessionSummaryResponse(**SESSION_SUMMARY_EXAMPLE)


@router.post(
    "/consulting-sessions/{sessionId}/summary/applied-changes",
    response_model=SessionSummaryResponse,
    status_code=201,
    tags=[AREA4_TAG],
    responses={
        201: {"content": {"application/json": {"example": SESSION_SUMMARY_EXAMPLE}}},
        **SESSION_ERRORS,
    },
    summary="변경 후보를 요청서에 반영",
    description=(
        "**화면 — `4_의뢰인_요약` › N건 요청서에 반영**\n\n"
        "요약에서 고른 변경 후보를 요청서에 넣는다. `UPDATE`는 이미 있는 요구사항을 "
        "고치고, `ADD`는 새 요구사항으로 넣는다. 반영한 뒤 개발 계약으로 이어갈 때 이 "
        "요청서가 쓰인다.\n\n"
        "이미 반영한 후보를 다시 보내도 두 번 들어가지 않는다.\n\n"
        "- **201**: 반영했을 때. 반영된 후보는 `isApplied`가 참으로 바뀐다\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **404 / SESSION_NOT_FOUND**: `sessionId`가 404일 때\n"
        "- **422 / INVALID_REQUEST**: `changeIds`가 비었을 때\n"
        "- **500 / INTERNAL_ERROR**: 반영하지 못했을 때"
    ),
)
def apply_summary_changes(
    sessionId: Annotated[
        int,
        Path(
            description="컨설팅 세션 식별자. 404는 없음 재현용 예약값이다.",
            examples=[SESSION_ID],
        ),
    ],
    request: ApplySummaryChangesRequest,
) -> SessionSummaryResponse | JSONResponse:
    if sessionId == NOT_FOUND_SESSION_ID:
        return _error_response(404, SESSION_NOT_FOUND)
    if not request.changeIds:
        body = ValidationErrorResponse(
            code=INVALID_REQUEST[0],
            message=INVALID_REQUEST[1],
            errors=[FieldError(field="changeIds", reason="한 건 이상 골라야 합니다.")],
        )
        return JSONResponse(status_code=422, content=jsonable_encoder(body))

    applied = [
        {**c, "isApplied": c["changeId"] in request.changeIds}
        for c in SESSION_SUMMARY_EXAMPLE["changes"]
    ]
    return SessionSummaryResponse(**{**SESSION_SUMMARY_EXAMPLE, "changes": applied})


@router.post(
    "/consulting-sessions/{sessionId}/recordings",
    response_model=SessionRecordingResponse,
    status_code=201,
    tags=[AREA4_TAG],
    responses={
        201: {
            "content": {"application/json": {"example": SESSION_RECORDING_EXAMPLE}}
        },
        400: {
            "model": ErrorResponse,
            "description": "아직 끝나지 않은 세션",
            "content": {
                "application/json": {
                    "example": {
                        "code": SESSION_NOT_ENDED[0],
                        "message": SESSION_NOT_ENDED[1],
                    }
                }
            },
        },
        409: {
            "model": ErrorResponse,
            "description": "이미 올린 녹음",
            "content": {
                "application/json": {
                    "example": {
                        "code": RECORDING_ALREADY_UPLOADED[0],
                        "message": RECORDING_ALREADY_UPLOADED[1],
                    }
                }
            },
        },
        **SESSION_ERRORS,
    },
    summary="세션 녹음 올리기",
    description=(
        "세션이 끝난 뒤 자기 마이크로 녹음한 파일을 올린다.\n\n"
        "**참가자마다 자기 트랙만 올린다.** 두 사람 소리를 한 파일로 합치지 않는다. "
        "파일이 곧 화자라서 나중에 누가 말했는지 가리는 데 따로 손이 들지 않는다. "
        "누가 올렸는지는 인증 정보로 정하므로 요청에 담지 않는다. (R-CON-14)\n\n"
        "파일은 `POST /api/uploads`에 `purpose`를 `SESSION_RECORDING`으로 올려 받은 "
        "`fileId`로 넘긴다. 파일 자체는 이 API를 지나가지 않는다.\n\n"
        "두 트랙이 모두 올라오면 전사와 요약이 시작된다. 한쪽이 창을 그냥 닫아 올라오지 "
        "않을 수 있으므로, 정해진 시간이 지나면 올라온 트랙만으로 진행한다. 기다리기만 "
        "하면 요약이 영영 만들어지지 않는다. (R-CON-16)\n\n"
        "**같은 파일을 다시 보내면 붙어 있는 것을 그대로 돌려준다.** 올리고 나서 응답을 "
        "받지 못해 다시 보내는 일이 있어, 그 되풀이를 실패로 돌리면 올라간 녹음을 두고도 "
        "화면이 실패를 알린다. 다른 파일로 바꾸려는 요청만 거절한다.\n\n"
        "- **201**: 올렸을 때. 같은 파일을 다시 보낸 경우도 여기다\n"
        "- **400 / SESSION_NOT_ENDED**: `sessionId`가 400일 때. 아직 끝나지 않은 "
        "세션이다\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **404 / SESSION_NOT_FOUND**: `sessionId`가 404일 때\n"
        "- **409 / RECORDING_ALREADY_UPLOADED**: `sessionId`가 410이고 이미 붙어 있는 "
        "것과 다른 `fileId`일 때. 같은 `fileId`면 201로 기존 녹음이 돌아온다\n"
        "- **500 / INTERNAL_ERROR**: 저장하지 못했을 때"
    ),
)
def upload_session_recording(
    sessionId: Annotated[
        int,
        Path(
            description=(
                "컨설팅 세션 식별자. 404는 없음, 400은 종료 전, 410은 이미 올림 "
                "재현용 예약값이다."
            ),
            examples=[SESSION_ID],
        ),
    ],
    request: CreateSessionRecordingRequest,
) -> SessionRecordingResponse | JSONResponse:
    if sessionId == NOT_FOUND_SESSION_ID:
        return _error_response(404, SESSION_NOT_FOUND)
    if sessionId == NOT_ENDED_SESSION_ID:
        return _error_response(400, SESSION_NOT_ENDED)
    if sessionId == RECORDED_SESSION_ID:
        if request.fileId != SESSION_RECORDING_EXAMPLE["fileId"]:
            return _error_response(409, RECORDING_ALREADY_UPLOADED)
        return SessionRecordingResponse(**SESSION_RECORDING_EXAMPLE)
    return SessionRecordingResponse(
        **{**SESSION_RECORDING_EXAMPLE, "fileId": request.fileId}
    )


# =========================================================================== #
# 영역 5 — 개발
#
# 개발자가 계약서를 쓰고, 의뢰인이 서명하며 전액을 예치하고, 개발자가 착수한 뒤
# 작업을 하나씩 확인해 전부 통과하면 대금이 한 번에 나간다.
#
# 예치와 착수는 다른 단계다. 예치는 의뢰인이 돈을 맡기는 것이고, 착수는 개발자가
# 그것을 확인하고 일을 시작한다고 알리는 것이다. 착수 전까지는 개발자가 계약을
# 물릴 수 있다.
#
# 화면: 5_개발자_계약서작성 · 5_개발자_계약서수정 · 5_의뢰인_계약서상세 ·
#       5_개발자_계약서검토 · 5_서명판 · 5_의뢰인_계약예치 ·
#       5_개발자_착수전 · 5_개발자_착수전_예치대기 ·
#       5_의뢰인_진행 · 5_의뢰인_확인 · 5_개발자_확인 · 5_의뢰인_리뷰작성
#
# 함께 쓰는 API (다른 영역에 있다):
#   GET   /api/users/me                      전 영역 헤더
#   GET   /api/rooms · /api/rooms/{roomId}   방 사이드바와 머리
#   GET   /api/contracts/{contractId}        계약서 조회 (contractType으로 갈린다)
#   POST  /api/contracts/{contractId}/signatures · /messages   서명과 전달 메시지
#
# 돈의 원장은 Escrow 서버가 가진다. 여기서는 예치를 걸고 상태를 확인만 한다.
# =========================================================================== #


# --------------------------------------------------------------------------- #
# 영역 5 — 응답 모델
# --------------------------------------------------------------------------- #


class MilestoneResponse(BaseModel):
    """작업 하나. 화면에서는 '작업'으로 부르고 ERD에서는 마일스톤이다."""

    milestoneId: int = Field(description="작업 식별자.", examples=[301])
    issueId: int | None = Field(
        description=(
            "붙은 요구사항 식별자. 계약서를 다시 쓸 때 이 값을 그대로 보낸다. "
            "요구사항에 붙지 않은 작업이면 null이다."
        ),
        examples=[312],
    )
    title: str = Field(
        description=(
            "작업 이름. 계약서에서 바꾼 이름이 있으면 그것, 없으면 붙은 요구사항의 이름이다. "
            "계약서에서 바꿔도 요청서는 바뀌지 않는다."
        ),
        examples=["1차 · 관리자 API에 인증 추가"],
    )
    detail: str | None = Field(
        description=(
            "작업 설명. 계약서에서 바꾼 설명이 있으면 그것, 없으면 붙은 요구사항의 설명이다. "
            "둘 다 없으면 null이다."
        ),
        examples=["로그인하지 않아도 관리자 주소로 회원 목록이 열립니다."],
    )
    price: int = Field(
        description=(
            "작업 가격. 원 단위 정수다. 인계 작업은 0원이며 화면에 가격을 보여주지 않는다."
        ),
        examples=[400000],
    )
    location: str | None = Field(
        description=(
            "이 작업이 다루는 요구사항의 위치. 진단에서 나온 요구사항이면 채워지고, "
            "개발자가 직접 만든 작업이면 null이다. ERD issues.location에 대응한다."
        ),
        examples=["app/api/admin/* · 4개 라우트"],
    )
    workDate: int | None = Field(
        description=(
            "이 작업에 잡은 기간(일). 최종 납기일이 어떻게 나왔는지 보여주는 참고값이라 "
            "**계약 전에만 화면에 보여준다.** 정하지 않았으면 null이다."
        ),
        examples=[10],
    )
    status: Literal[
        "PROPOSED", "REJECTED", "SCHEDULED", "IN_PROGRESS", "SUBMITTED", "CONFIRMED"
    ] = Field(
        description=(
            "작업 진행. PROPOSED는 제안됨, REJECTED는 거부됨, SCHEDULED는 예정됨, "
            "IN_PROGRESS는 진행중, SUBMITTED는 개발자가 확인을 요청한 상태, "
            "CONFIRMED는 의뢰인이 통과시킨 상태다."
        ),
        examples=["SUBMITTED"],
    )
    rejectMessage: str | None = Field(
        description="거부 사유. 거부되지 않았으면 null이다.",
        examples=[None],
    )
    submittedAt: str | None = Field(
        description=(
            "개발자가 확인 요청을 보낸 시각. 자동 통과 기한 3일을 여기서부터 센다. "
            "보내기 전이면 null이다."
        ),
        examples=["2026-08-29T11:20:00+09:00"],
    )
    confirmedAt: str | None = Field(
        description=(
            "의뢰인이 통과시킨 시각. 자동으로 통과된 경우에도 채워진다. 아직이면 null이다."
        ),
        examples=[None],
    )
    autoPassAt: str | None = Field(
        description=(
            "답이 없을 때 자동으로 통과되는 시각. 확인 요청을 보낸 뒤에만 있다."
        ),
        examples=["2026-09-01T11:20:00+09:00"],
    )
    qaCriteria: list[QaCriterionResponse] = Field(
        description="이 작업의 확인 문장. 전부 체크되어야 통과시킬 수 있다.",
    )


class AttachDeliverableRequest(BaseModel):
    """인계물에 붙일 파일."""

    fileId: int = Field(
        description="업로드 3콜을 마친 파일 식별자.",
        examples=[71],
    )


class DeliverableResponse(BaseModel):
    """인계물 하나. 소스·계정·설명서처럼 마지막에 넘겨받는 것들이다."""

    deliverableId: int = Field(description="인계물 식별자.", examples=[401])
    title: str = Field(description="인계물 이름.", examples=["소스 파일 전체"])
    fileId: int | None = Field(
        description="올린 파일 식별자. 아직 안 올렸으면 null이다.",
        examples=[None],
    )
    deliveredAt: str | None = Field(
        description="넘긴 시각. 아직이면 null이다.",
        examples=[None],
    )


class DevelopmentAgreementResponse(BaseModel):
    """개발 계약에만 있는 내용. 컨설팅 계약이면 이 자리가 null이다."""

    status: Literal[
        "SIGNED",
        "DEPOSITED",
        "IN_PROGRESS",
        "AFTER_SERVICE",
        "COMPLETED",
        "TERMINATED",
    ] = Field(
        description=(
            "개발 진행. 계약서를 처음 쓸 때부터 SIGNED다. AI가 만든 version 1도 같다. "
            "예치가 끝나면 DEPOSITED, 착수하면 IN_PROGRESS가 된다. AFTER_SERVICE는 "
            "하자보수, COMPLETED는 완료됨, TERMINATED는 파기됨이며 이 셋은 운영 "
            "콘솔에서 다룬다.\n\n"
            "**체결 여부는 바깥 계약의 `status`로만 판단한다.** 이 값이 SIGNED여도 "
            "체결된 것이 아니다. 서명 두 개가 모두 찍혀야 계약이 성립한다. "
            "(`R-CON-01`)"
        ),
        examples=["IN_PROGRESS"],
    )
    totalAmount: int = Field(
        description="이 계약으로 오가는 전체 금액. 속한 작업 가격의 합이다.",
        examples=[650000],
    )
    issueIds: list[int] = Field(
        description=(
            "이 계약에서 다룰 요구사항 식별자. 개발 계약에만 있다 — 요구사항 하나가 "
            "작업 하나가 되므로 어느 줄을 맡기로 했는지가 계약의 내용이다. 컨설팅은 "
            "요청서의 줄에 매이지 않아 대신 `topic` 한 줄을 둔다."
        ),
        examples=[[312, 313, 311, 320]],
    )
    startDate: str | None = Field(
        description="착수일. 정하기 전이면 null이다.",
        examples=["2026-08-26"],
    )
    dueDate: str | None = Field(
        description=(
            "최종 납기일. 작업마다 따로 납기를 두지 않고 이 날짜 하나만 쓴다."
        ),
        examples=["2026-09-16"],
    )
    freeRepairDays: int = Field(
        description="무상 하자보수 기간(일).",
        examples=[14],
    )
    milestones: list[MilestoneResponse] = Field(
        description="이 계약에 속한 작업. 화면에 보여줄 순서대로 내려간다.",
    )
    deliverables: list[DeliverableResponse] = Field(
        description="넘겨받을 인계물.",
    )


class WriteDevelopmentContractRequest(BaseModel):
    """개발 계약서 작성 요청.

    계약에서 다룰 요구사항은 작업이 가리키는 것이다. 목록을 따로 받지 않는다. 따로 받으면
    작업과 어긋난 목록이 올 수 있다.
    """

    startDate: str = Field(description="착수일.", examples=["2026-08-26"])
    dueDate: str = Field(description="최종 납기일.", examples=["2026-09-16"])
    freeRepairDays: int = Field(
        ge=0,
        description="무상 하자보수 기간(일).",
        examples=[14],
    )
    milestones: list["WriteMilestoneRequest"] = Field(
        min_length=1,
        description=(
            "맡기로 한 작업. 가격의 합이 계약 금액이 된다. 하나 이상이어야 한다. "
            "한 요구사항에는 작업이 하나라 같은 `issueId`를 두 작업에 담을 수 없다."
        ),
    )
    deliverableTitles: list[Annotated[str, Field(min_length=1, max_length=100)]] = Field(
        default_factory=list,
        description="넘겨받을 인계물 이름. 이름마다 100자까지 받는다.",
        examples=[["소스 파일 전체", "관리자 계정과 비밀번호", "운영 설명서"]],
    )
    message: str | None = Field(
        default=None,
        max_length=300,
        description="계약서와 함께 남길 전달 메시지. 300자까지 받는다.",
        examples=[None],
    )

    @field_validator("milestones")
    @classmethod
    def validate_distinct_issues(
        cls, value: list["WriteMilestoneRequest"]
    ) -> list["WriteMilestoneRequest"]:
        # 새 작업은 아직 요구사항이 없어 겹칠 것이 없다.
        issue_ids = [milestone.issueId for milestone in value if milestone.issueId is not None]
        if len(issue_ids) != len(set(issue_ids)):
            raise PydanticCustomError("duplicate_issue", "same issue in two milestones")
        return value


class WriteMilestoneRequest(BaseModel):
    """계약서에 담을 작업 하나.

    작업은 언제나 요구사항 하나에 붙는다. 요청서에 있던 요구사항이면 `issueId`를
    보내고, 개발방에서 새로 합의한 작업이면 `issueId` 없이 `title`을 보낸다. 새 작업은
    서버가 계약용 요구사항을 만들어 붙인다.

    `title`·`detail`은 계약서에서 쓰는 이름·설명이다. 기존 요구사항에서 보내지 않으면
    그 요구사항의 이름·설명을 그대로 쓴다. 계약서에서 바꿔도 요청서는 바뀌지 않는다.
    """

    issueId: int | None = Field(
        default=None,
        description="붙일 요구사항 식별자. 개발방에서 새로 합의한 작업이면 보내지 않는다.",
        examples=[312],
    )
    title: str | None = Field(
        default=None,
        min_length=1,
        max_length=200,
        validate_default=True,
        description=(
            "작업 이름. 새 작업이면 반드시 보낸다. 기존 요구사항이면 계약서에서 바꾼 "
            "이름만 보내고, 보내지 않으면 요구사항 이름을 쓴다."
        ),
        examples=["관리자 API에 인증 추가"],
    )
    detail: str | None = Field(
        default=None,
        min_length=1,
        max_length=1000,
        description=(
            "작업 설명. 적지 않아도 된다. 기존 요구사항이면 보내지 않았을 때 요구사항 "
            "설명을 쓴다."
        ),
        examples=[None],
    )
    price: int = Field(
        ge=0,
        description="작업 가격. 인계 작업은 0으로 보낸다.",
        examples=[400000],
    )
    workDate: int | None = Field(
        default=None,
        ge=0,
        description=(
            "이 작업에 잡은 기간(일). 최종 납기일이 어떻게 나왔는지 보여주는 참고값이라 "
            "정하지 않아도 된다."
        ),
        examples=[10],
    )
    qaCriteria: list[Annotated[str, Field(min_length=1, max_length=500)]] = Field(
        min_length=1,
        description="이 작업의 확인 문장. 하나 이상이어야 하고 문장마다 500자까지 받는다.",
        examples=[["관리자 주소로 들어가면 로그인 화면이 뜬다"]],
    )

    @field_validator("title")
    @classmethod
    def validate_new_work_title(cls, value: str | None, info: ValidationInfo) -> str | None:
        # 새 작업은 붙일 요구사항이 없어 이름을 여기서 받아야 한다.
        if value is None and info.data.get("issueId") is None:
            raise PydanticCustomError("missing", "title is required for new work")
        return value


class EscrowDepositResponse(BaseModel):
    """예치 상태. 돈의 원장은 Escrow 서버가 가지고 여기서는 확인만 한다."""

    contractId: int = Field(description="계약 식별자.", examples=[130])
    amount: int = Field(description="예치할 금액. 원 단위 정수다.", examples=[650000])
    status: Literal["PENDING", "DEPOSITED", "FAILED"] = Field(
        description=(
            "예치 진행. PENDING은 입금 검증과 보관계좌 정산을 기다리는 상태, "
            "DEPOSITED는 예치가 확인된 상태, FAILED는 실패한 상태다."
        ),
        examples=["DEPOSITED"],
    )
    depositedAt: str | None = Field(
        description="예치가 확인된 시각. 확인 전이면 null이다.",
        examples=["2026-08-21T09:40:00+09:00"],
    )


class DepositRequest(BaseModel):
    """예치 요청."""

    paymentMethodId: int = Field(
        description=(
            "회원가입 때 발급한 SSAFY 출금 계좌의 결제 수단 식별자. "
            "결제 수단 조회에서 받은 값을 보낸다. 별도 계좌 선택은 없다. (R-PAY-09)"
        ),
        examples=[9],
        gt=0,
    )
    idempotencyKey: str = Field(
        description=(
            "같은 결제가 두 번 나가지 않게 하는 키. 재시도할 때 같은 값을 보낸다."
        ),
        examples=["dep-130-9f2c"],
    )


class SubmitMilestoneRequest(BaseModel):
    """작업 확인 요청."""

    message: str | None = Field(
        default=None,
        max_length=300,
        description="의뢰인에게 함께 남길 말. 적지 않아도 된다. 300자까지 받는다.",
        examples=[None],
    )


class SaveQaResultsRequest(BaseModel):
    """확인 결과 저장. 다 체크하지 않아도 보낼 수 있다."""

    qaCriterionIds: list[int] = Field(
        min_length=1,
        description=(
            "이번에 체크한 확인 문장 식별자. 이미 통과한 것에 더하기만 한다. "
            "**체크는 되돌릴 수 없으므로** 빼고 보내도 풀리지 않는다. 이미 통과한 "
            "값을 다시 보내도 결과가 같다."
        ),
        examples=[[201]],
    )


class ConfirmMilestoneRequest(BaseModel):
    """작업 통과 처리."""

    qaCriterionIds: list[int] = Field(
        description="체크한 확인 문장 식별자. 전부 체크해야 통과시킬 수 있다.",
        examples=[[201, 202]],
    )


class WriteReviewRequest(BaseModel):
    """계약을 마친 뒤 남기는 리뷰."""

    devScore: int = Field(
        ge=1, le=5, description="개발력 점수. 1에서 5 사이다.", examples=[5]
    )
    expScore: int = Field(
        ge=1,
        le=5,
        description="설명력 점수. 1에서 5 사이다. ERD reviews.exp_score에 대응한다.",
        examples=[5],
    )
    content: str | None = Field(
        default=None,
        max_length=1000,
        description="리뷰 내용. 1,000자까지 받는다(R-REV-04). 점수만 주고 넘어가도 된다.",
        examples=["말이 잘 통했습니다."],
    )


# --------------------------------------------------------------------------- #
# 영역 5 — 실패 응답 문구
# --------------------------------------------------------------------------- #

MILESTONE_NOT_FOUND = ("MILESTONE_NOT_FOUND", "작업을 찾을 수 없습니다.")
DEPOSIT_REQUIRED = ("DEPOSIT_REQUIRED", "예치가 끝나야 착수할 수 있습니다.")
ALREADY_DEPOSITED = ("ALREADY_DEPOSITED", "이미 예치가 끝났습니다.")
CONTRACT_NOT_SIGNED = ("CONTRACT_NOT_SIGNED", "계약서에 서명한 뒤에 예치할 수 있습니다.")
QA_NOT_ALL_CHECKED = (
    "QA_NOT_ALL_CHECKED",
    "확인 문장을 모두 체크해야 통과시킬 수 있습니다.",
)
MILESTONE_STATE_CONFLICT = (
    "MILESTONE_STATE_CONFLICT",
    "지금 상태에서는 처리할 수 없습니다.",
)
REVIEW_ALREADY_WRITTEN = ("REVIEW_ALREADY_WRITTEN", "이미 리뷰를 남기셨습니다.")
CONTRACT_NOT_COMPLETED = (
    "CONTRACT_NOT_COMPLETED",
    "계약이 끝나야 리뷰를 남길 수 있습니다.",
)
CONTRACT_NOT_COMPLETED_EXAMPLE = {
    "code": CONTRACT_NOT_COMPLETED[0],
    "message": CONTRACT_NOT_COMPLETED[1],
}
DUE_DATE_BEFORE_START = (
    "DUE_DATE_BEFORE_START",
    "납기일은 착수일보다 늦어야 합니다.",
)
# 같은 요구사항을 두 작업에 담았을 때. 어느 작업의 통과가 그 요구사항의 통과인지 가를 수 없다.
DUPLICATE_ISSUE_REASON = "같은 요구사항을 두 작업에 담을 수 없습니다."

# 개발방이 아닌 곳에서 개발 계약서를 쓰려 했을 때. 화면에는 그런 길이 없어 API를 직접 부를 때만 난다.
NOT_DEVELOPMENT_ROOM = (
    "NOT_DEVELOPMENT_ROOM",
    "개발방에서만 개발 계약서를 쓸 수 있습니다.",
)

DUE_DATE_BEFORE_START_EXAMPLE = {
    "code": DUE_DATE_BEFORE_START[0],
    "message": DUE_DATE_BEFORE_START[1],
}


# --------------------------------------------------------------------------- #
# 영역 5 — 목 데이터
# --------------------------------------------------------------------------- #

AREA5_TAG = "5 개발"

DEV_CONTRACT_ID = 130
DONE_CONTRACT_ID = 140
# 아직 아무도 서명하지 않은 계약서. 서명판(5_서명판)이 열리는 시점이 이때뿐이라
# 두 벌을 둔다 — 개발자 시점(105)과 의뢰인 시점(106)은 방이 달라 계약도 갈린다.
DRAFT_DEV_CONTRACT_ID = 131
DRAFT_CLIENT_CONTRACT_ID = 132
# 예치까지 끝나고 아직 착수하지 않은 계약. 개발자의 착수 화면이 이때만 나온다.
BEFORE_START_CONTRACT_ID = 134
MILESTONE_ID = 301
NOT_FOUND_MILESTONE_ID = 404
CONFLICT_MILESTONE_ID = 409
NOT_DEPOSITED_CONTRACT_ID = 402
DEPOSITED_CONTRACT_ID = 409

MILESTONES = [
    {
        "milestoneId": 301,
        "issueId": 312,
        "title": "1차 · 관리자 API에 인증 추가",
        "detail": "로그인하지 않아도 관리자 주소로 회원 목록이 열립니다.",
        "location": "app/api/admin/* · 4개 라우트",
        "workDate": 10,
        "price": 400000,
        "status": "CONFIRMED",
        "rejectMessage": None,
        "submittedAt": "2026-08-24T10:00:00+09:00",
        "confirmedAt": "2026-08-26T16:40:00+09:00",
        "autoPassAt": None,
        "qaCriteria": [
            {
                "qaCriterionId": 201,
                "testCase": "관리자 주소로 들어가면 로그인 화면이 뜬다",
                "condition": None,
                "status": "RESOLVED",
                "issueId": 312,
            },
            {
                "qaCriterionId": 202,
                "testCase": "로그인 없이 열면 막힌다",
                "condition": None,
                "status": "RESOLVED",
                "issueId": 312,
            },
        ],
    },
    {
        "milestoneId": 302,
        "issueId": 313,
        "title": "2차 · 회원탈퇴 경로 구현",
        "detail": None,
        "location": "신규 작성 · 데이터는 30일 보관 후 삭제",
        "workDate": 6,
        "price": 200000,
        "status": "SUBMITTED",
        "rejectMessage": None,
        "submittedAt": "2026-08-29T11:20:00+09:00",
        "confirmedAt": None,
        "autoPassAt": "2026-09-01T11:20:00+09:00",
        "qaCriteria": [
            {
                "qaCriterionId": 203,
                "testCase": "탈퇴를 누르면 계정으로 다시 들어갈 수 없다",
                "condition": None,
                "status": "RESOLVED",
                "issueId": 313,
            },
            {
                "qaCriterionId": 204,
                "testCase": "30일 뒤 데이터가 지워진다",
                "condition": None,
                "status": "RESOLVED",
                "issueId": 313,
            },
            {
                "qaCriterionId": 205,
                "testCase": "관리자 화면에서 탈퇴한 회원이 목록에서 빠진다",
                "condition": None,
                "status": "UNRESOLVED",
                "issueId": 313,
            },
        ],
    },
    {
        "milestoneId": 303,
        "issueId": 311,
        "title": "3차 · 결제 간헐 실패 수정",
        "detail": None,
        "location": "app/api/checkout/route.ts:47",
        "workDate": 4,
        "price": 50000,
        "status": "IN_PROGRESS",
        "rejectMessage": None,
        "submittedAt": None,
        "confirmedAt": None,
        "autoPassAt": None,
        "qaCriteria": [
            {
                "qaCriterionId": 206,
                "testCase": "결제를 50번 연속으로 넣어도 모두 성공한다",
                "condition": None,
                "status": "UNRESOLVED",
                "issueId": 311,
            }
        ],
    },
    {
        "milestoneId": 304,
        "issueId": 320,
        "title": "인계 · 소스 · 계정 · 설명서",
        "detail": None,
        "location": None,
        "workDate": None,
        "price": 0,
        "status": "IN_PROGRESS",
        "rejectMessage": None,
        "submittedAt": None,
        "confirmedAt": None,
        "autoPassAt": None,
        "qaCriteria": [
            {
                "qaCriterionId": 207,
                "testCase": "소스 파일 전체를 받았다",
                "condition": None,
                "status": "UNRESOLVED",
                "issueId": 320,
            },
            {
                "qaCriterionId": 208,
                "testCase": "관리자 계정과 비밀번호를 받았다",
                "condition": None,
                "status": "UNRESOLVED",
                "issueId": 320,
            },
            {
                "qaCriterionId": 209,
                "testCase": "운영 설명서를 받았다",
                "condition": None,
                "status": "UNRESOLVED",
                "issueId": 320,
            },
        ],
    },
]

DELIVERABLES = [
    {"deliverableId": 401, "title": "소스 파일 전체", "fileId": None, "deliveredAt": None},
    {
        "deliverableId": 402,
        "title": "관리자 계정과 비밀번호",
        "fileId": None,
        "deliveredAt": None,
    },
    {"deliverableId": 403, "title": "운영 설명서", "fileId": None, "deliveredAt": None},
]

DEV_CONTRACT_EXAMPLE = {
    "contractId": DEV_CONTRACT_ID,
    "roomId": 93,
    "contractType": "DEVELOPMENT",
    "status": "SIGNED",
    "consulting": None,
    "development": {
        "status": "IN_PROGRESS",
        "totalAmount": 650000,
        "issueIds": [312, 313, 311, 320],
        "startDate": "2026-08-26",
        "dueDate": "2026-09-16",
        "freeRepairDays": 14,
        "milestones": MILESTONES,
        "deliverables": DELIVERABLES,
    },
    "clientSignature": {
        "userId": 12,
        "isSigned": True,
        "signedAt": "2026-08-21T09:38:00+09:00",
        "signatureImageFileId": 41,
    },
    "developerSignature": {
        "userId": 30,
        "isSigned": True,
        "signedAt": "2026-08-22T17:40:00+09:00",
        "signatureImageFileId": 42,
    },
    "messages": [],
    "contractText": "<h2>개발 외주 계약서</h2>",
    "updatedAt": "2026-08-23T10:20:00+09:00",
}


# 작업이 모두 통과 확정된 계약. 리뷰 화면(R-REV-02)을 보려면 끝난 거래가 있어야 한다.
DONE_MILESTONES = [
    {
        **milestone,
        "status": "CONFIRMED",
        "confirmedAt": "2026-09-04T17:00:00+09:00",
        "autoPassAt": None,
        "qaCriteria": [
            {**qa, "status": "RESOLVED"} for qa in milestone["qaCriteria"]
        ],
    }
    for milestone in MILESTONES
]

DONE_CONTRACT_EXAMPLE = {
    **DEV_CONTRACT_EXAMPLE,
    "contractId": DONE_CONTRACT_ID,
    "roomId": 104,
    "development": {
        **DEV_CONTRACT_EXAMPLE["development"],
        "status": "COMPLETED",
        "milestones": DONE_MILESTONES,
    },
    "updatedAt": "2026-09-04T17:00:00+09:00",
}

# 협의 중인 계약서. 개발자가 보냈고 아직 양쪽 다 서명하지 않았다. 여기서만 계약서
# 화면의 서명 버튼과 수정 버튼이 보인다. 값을 따로 적지 않고 복사해 상태만 바꾼다.
def _draft_contract(contract_id: int, room_id: int) -> dict:
    draft = copy.deepcopy(DEV_CONTRACT_EXAMPLE)
    draft["contractId"] = contract_id
    draft["roomId"] = room_id
    draft["status"] = "NEGOTIATING"
    draft["contractText"] = None
    draft["updatedAt"] = "2026-08-22T16:10:00+09:00"
    draft["development"]["status"] = "SIGNED"
    for milestone in draft["development"]["milestones"]:
        milestone["status"] = "PROPOSED"
        milestone["submittedAt"] = None
        milestone["confirmedAt"] = None
        milestone["autoPassAt"] = None
        for criterion in milestone["qaCriteria"]:
            criterion["status"] = "UNRESOLVED"
    for side in ("clientSignature", "developerSignature"):
        draft[side]["isSigned"] = False
        draft[side]["signedAt"] = None
        draft[side]["signatureImageFileId"] = None
    return draft


DRAFT_DEV_CONTRACT_EXAMPLE = _draft_contract(DRAFT_DEV_CONTRACT_ID, 105)
DRAFT_CLIENT_CONTRACT_EXAMPLE = _draft_contract(DRAFT_CLIENT_CONTRACT_ID, 106)


# --------------------------------------------------------------------------- #
# 무료 상담 계약 초안
#
# 방 자원이라 영역 3에 속하지만, 응답이 계약서 한 건이라 계약 예시가 다 만들어진
# 이 자리에 둔다. 초안 전용 응답을 따로 만들지 않는다 — 초안은 계약서 version 1이다.
# --------------------------------------------------------------------------- #

def _blank_developer_input(draft: dict) -> dict:
    """AI가 채우지 않는 칸을 비운다.

    일정·기간·금액·인계물·전달 메시지는 합의와 검수가 필요해 개발자가 직접 넣는다.
    기존 계약 예시를 그대로 쓰면 이 값들이 차 있어 「AI가 금액까지 정했다」로 읽힌다.
    남기는 것은 AI가 만드는 것뿐이다 — 작업 이름, 확인 문장, 맡기로 한 요구사항, 다룰 항목.
    """
    draft["messages"] = []
    development = draft.get("development")
    if development is not None:
        development["startDate"] = None
        development["dueDate"] = None
        development["freeRepairDays"] = 0
        development["totalAmount"] = 0
        development["deliverables"] = []
        for milestone in development["milestones"]:
            milestone["price"] = 0
            milestone["workDate"] = None
    consulting = draft.get("consulting")
    if consulting is not None:
        consulting["sessions"] = []
        consulting["totalAmount"] = 0
    return draft


DRAFT_CONSULTING_CONTRACT_EXAMPLE = _blank_developer_input(
    copy.deepcopy(CONSULTING_CONTRACT_EXAMPLE)
)
DRAFT_CONSULTING_CONTRACT_EXAMPLE["roomId"] = CONVERTED_CONSULTING_ROOM_ID
DRAFT_CONSULTING_CONTRACT_EXAMPLE["consulting"]["status"] = "SIGNED"

# 협의 중 예시(DRAFT_DEV_CONTRACT_EXAMPLE)는 개발자가 써서 보낸 계약서라 금액과 일정이
# 차 있다. 초안은 그 앞 단계이므로 같은 예시를 쓰지 않고 비운 판을 따로 둔다.
DRAFT_DEVELOPMENT_CONTRACT_EXAMPLE = _blank_developer_input(
    copy.deepcopy(DRAFT_DEV_CONTRACT_EXAMPLE)
)
DRAFT_DEVELOPMENT_CONTRACT_EXAMPLE["roomId"] = CONVERTED_DEVELOPMENT_ROOM_ID
DRAFT_DEVELOPMENT_CONTRACT_EXAMPLE["development"]["status"] = "SIGNED"


@router.post(
    "/rooms/{roomId}/contract-draft",
    response_model=ContractResponse,
    tags=[AREA3_TAG],
    responses={
        200: {
            "content": {
                "application/json": {"example": DRAFT_DEVELOPMENT_CONTRACT_EXAMPLE}
            }
        },
        403: {
            "model": ErrorResponse,
            "description": "이 방의 개발자가 아님",
            "content": {"application/json": {"example": FORBIDDEN_EXAMPLE}},
        },
        404: {
            "model": ErrorResponse,
            "description": "방 없음",
            "content": {"application/json": {"example": ROOM_NOT_FOUND_EXAMPLE}},
        },
        409: {
            "model": ErrorResponse,
            "description": "아직 갈래가 정해지지 않은 상담방",
            "content": {"application/json": {"example": ROOM_NOT_CONVERTED_EXAMPLE}},
        },
        422: {
            "model": ValidationErrorResponse,
            "description": "방 식별자 형식 오류",
        },
        502: {
            "model": ErrorResponse,
            "description": "AI가 계약에 맞는 초안을 만들지 못함",
            "content": {
                "application/json": {
                    "example": {
                        "code": CONTRACT_DRAFT_RESPONSE_INVALID[0],
                        "message": CONTRACT_DRAFT_RESPONSE_INVALID[1],
                    }
                }
            },
        },
        503: {
            "model": ErrorResponse,
            "description": "초안 생성 서비스에 연결할 수 없음",
            "content": {
                "application/json": {
                    "example": {
                        "code": CONTRACT_DRAFT_SERVICE_UNAVAILABLE[0],
                        "message": CONTRACT_DRAFT_SERVICE_UNAVAILABLE[1],
                    }
                }
            },
        },
        **AREA1_UNAUTHORIZED,
        **AREA1_ERRORS,
    },
    summary="계약서 초안 만들기",
    description=(
        "**화면 — `4_개발자_계약서작성` · 개발 계약서 작성**\n\n"
        "무료 상담에서 나눈 이야기를 계약서 초안으로 정리한다. 의뢰인이 갈래를 정해 새 "
        "방이 열린 뒤, 개발자가 그 방에서 계약서를 쓰려고 들어올 때 부른다. 갈래를 "
        "정하지 않으면 초안도 만들지 않는다.\n\n"
        "**초안은 계약서 version 1이다.** 응답은 계약서 조회와 같은 형식이고 갈래별 `status`는 "
        "`SIGNED`다. 실제 체결 전까지 공통 계약 `status`는 `NEGOTIATING`이다. 초안 전용 "
        "응답 형식을 따로 두지 않는다.\n\n"
        "**방마다 한 번만 만든다.** 처음 부르면 만들어 두고, 같은 방에서 다시 부르면 그 "
        "계약서를 그대로 준다. 몇 번을 불러도 문장이 달라지지 않는다.\n\n"
        "**개발자가 고친 내용은 서버가 들고 있지 않는다.** 고친 내용은 계약서 작성 화면이 "
        "들고 있다가 기존 계약서 작성·수정 API로 보낸다. 서명 두 개가 모두 찍혀야 계약이 "
        "성립하므로(`R-CON-01`) 초안을 만드는 것만으로는 아무것도 확정되지 않는다. AI는 "
        "계약서를 보내지도 서명하지도 않는다.\n\n"
        "**AI가 채우지 않는 칸이 있다.** 일정·기간·금액·인계물·전달 메시지는 비어 있다. "
        "합의와 검수가 필요한 값이라 개발자가 직접 입력한다.\n\n"
        "**개발 초안은 요청서와 채팅을 종합한다.** 요청서의 요구사항은 모두 작업으로 들어가고 "
        "요청서의 확인 문장이 그대로 옮겨진다. 채팅에서 새로 합의한 확인 문장은 해당 작업에 "
        "더해지고, 요청서 밖에 더 하기로 합의한 작업은 새 작업으로 들어간다. 새 작업은 "
        "합의한 확인 문장이 없으면 `qaCriteria`가 비어 있다.\n\n"
        "- **200**: 계약에 맞는 초안을 만들었을 때\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **403 / FORBIDDEN**: 이 방의 개발자가 아닐 때\n"
        "- **404 / ROOM_NOT_FOUND**: `roomId`가 404일 때\n"
        "- **409 / ROOM_NOT_CONVERTED**: `roomId`가 91일 때. 아직 갈래를 정하지 않았다\n"
        "- **422 / INVALID_REQUEST**: `roomId`가 정수가 아닐 때\n"
        "- **502 / AI_RESPONSE_INVALID**: 생성 결과의 필드·길이·식별자가 계약에 맞지 "
        "않을 때\n"
        "- **503 / AI_SERVICE_UNAVAILABLE**: 생성 서비스에 연결할 수 없을 때\n"
        "- **500 / INTERNAL_ERROR**: 그 밖의 서버 오류가 발생했을 때\n\n"
        "Mock은 방의 갈래에 따라 고정된 200 응답을 반환한다. 502·503은 후속 Service "
        "구현을 위한 응답 계약 예시이며 Mock 전용 실패 트리거를 만들지 않는다."
    ),
)
def draft_contract(
    roomId: Annotated[
        int,
        Path(
            description=(
                "갈래가 정해져 새로 열린 방 식별자. 404는 없음, 91은 갈래를 정하기 "
                "전인 상담방 재현용 예약값이다."
            ),
            examples=[CONVERTED_DEVELOPMENT_ROOM_ID],
        ),
    ],
) -> ContractResponse | JSONResponse:
    room = _room(roomId)
    if room is None:
        return _error_response(404, ROOM_NOT_FOUND)
    if room["type"] == "INQUIRY":
        return _error_response(409, ROOM_NOT_CONVERTED)
    example = (
        DRAFT_CONSULTING_CONTRACT_EXAMPLE
        if room["type"] == "CONSULTING"
        else DRAFT_DEVELOPMENT_CONTRACT_EXAMPLE
    )
    return ContractResponse(**{**example, "roomId": roomId})

# 착수 직후의 계약서. DEV_CONTRACT_EXAMPLE을 복사해 상태만 바꾼다 — 값을 따로 적으면
# 작업 이름·가격·확인 문장을 고칠 때 두 예시가 어긋나므로 반드시 복사로 만든다.
# DEV_CONTRACT_EXAMPLE은 1차 통과·2차 확인 대기인 진행 중 시점이라 착수 응답으로 쓰면
# "착수하면 1차가 이미 통과돼 있다"고 읽힌다. 착수 직후는 1차만 IN_PROGRESS, 나머지는
# SCHEDULED이고 확인 요청·통과 시각은 아직 없다.
STARTED_CONTRACT_EXAMPLE = copy.deepcopy(DEV_CONTRACT_EXAMPLE)
for _index, _milestone in enumerate(STARTED_CONTRACT_EXAMPLE["development"]["milestones"]):
    _milestone["status"] = "IN_PROGRESS" if _index == 0 else "SCHEDULED"
    _milestone["submittedAt"] = None
    _milestone["confirmedAt"] = None
    _milestone["autoPassAt"] = None
    for _criterion in _milestone["qaCriteria"]:
        _criterion["status"] = "UNRESOLVED"
del _index, _milestone, _criterion

def _before_start_contract(contract_id: int, room_id: int, status: str) -> dict:
    before = copy.deepcopy(DEV_CONTRACT_EXAMPLE)
    before["contractId"] = contract_id
    before["roomId"] = room_id
    before["development"]["status"] = status
    for milestone in before["development"]["milestones"]:
        milestone["status"] = "SCHEDULED"
        milestone["submittedAt"] = None
        milestone["confirmedAt"] = None
        milestone["autoPassAt"] = None
        for criterion in milestone["qaCriteria"]:
            criterion["status"] = "UNRESOLVED"
    return before


BEFORE_START_CONTRACT_EXAMPLE = _before_start_contract(
    BEFORE_START_CONTRACT_ID, 108, "DEPOSITED"
)

DEPOSIT_EXAMPLE = {
    "contractId": DEV_CONTRACT_ID,
    "amount": 650000,
    "status": "DEPOSITED",
    "depositedAt": "2026-08-21T09:40:00+09:00",
}

MILESTONE_NOT_FOUND_EXAMPLE = {
    "code": "MILESTONE_NOT_FOUND",
    "message": "작업을 찾을 수 없습니다.",
}

MILESTONE_ERRORS: dict = {
    404: {
        "model": ErrorResponse,
        "description": "작업 없음",
        "content": {"application/json": {"example": MILESTONE_NOT_FOUND_EXAMPLE}},
    },
    **AREA1_UNAUTHORIZED,
    **AREA1_ERRORS,
}


ALREADY_STARTED = ("ALREADY_STARTED", "이미 착수한 계약입니다.")

ALREADY_STARTED_EXAMPLE = {
    "code": ALREADY_STARTED[0],
    "message": ALREADY_STARTED[1],
}
DEPOSIT_REQUIRED_EXAMPLE = {
    "code": DEPOSIT_REQUIRED[0],
    "message": DEPOSIT_REQUIRED[1],
}

# 착수는 바디가 없어 검증 실패는 경로 변수 contractId가 정수가 아닐 때만 난다.
# FastAPI는 파라미터가 있는 API에 422를 자동으로 문서화하므로, 빼는 대신 이 앱 형식으로 선언한다.
CONTRACT_ID_ERROR_EXAMPLE = {
    "code": "INVALID_REQUEST",
    "message": "요청 형식이 올바르지 않습니다.",
    "errors": [{"field": "contractId", "reason": "정수여야 합니다."}],
}

# 계약서 작성 바디에서 스키마로 걸리는 건 필수 항목 누락과 빈 배열(milestones, 작업의 qaCriteria)이다.
# 배열 안 항목의 필드는 `milestones.0.qaCriteria`처럼 위치가 붙어 나온다.
# 납기가 착수일보다 이른 것은 필드 사이 규칙이라 422가 아니라 400이다.
WRITE_CONTRACT_ERROR_EXAMPLE = {
    "code": "INVALID_REQUEST",
    "message": "요청 형식이 올바르지 않습니다.",
    "errors": [
        {"field": "dueDate", "reason": "필수 항목입니다."},
        {"field": "milestones.0.qaCriteria", "reason": "1개 이상이어야 합니다."},
    ],
}

# 확인 요청 바디는 message 하나뿐이고 300자 제한이 유일한 검증 조건이다.
SUBMIT_MESSAGE_ERROR_EXAMPLE = {
    "code": "INVALID_REQUEST",
    "message": "요청 형식이 올바르지 않습니다.",
    "errors": [{"field": "message", "reason": "300자 이하여야 합니다."}],
}

# 통과시키기 바디는 qaCriterionIds 하나뿐이고 필수다. 빈 배열은 422가 아니라 400(QA_NOT_ALL_CHECKED)이다.
CONFIRM_QA_ERROR_EXAMPLE = {
    "code": "INVALID_REQUEST",
    "message": "요청 형식이 올바르지 않습니다.",
    "errors": [{"field": "qaCriterionIds", "reason": "필수 항목입니다."}],
}

# 파일 용량 초과. 영역 5(인계물 올리기)와 영역 8(파일 올리기)이 함께 쓴다.
# 파일 순서상 먼저 쓰는 영역 5에 둔다. 허용 용량은 아직 정해지지 않아 목은 재현하지 않는다.
FILE_TOO_LARGE = ("FILE_TOO_LARGE", "파일이 너무 큽니다.")
FILE_TOO_LARGE_EXAMPLE = {"code": FILE_TOO_LARGE[0], "message": FILE_TOO_LARGE[1]}

# 인계물 올리기는 multipart의 file 파트 하나만 받고, 그것이 필수다.
DELIVERABLE_FILE_ERROR_EXAMPLE = {
    "code": "INVALID_REQUEST",
    "message": "요청 형식이 올바르지 않습니다.",
    "errors": [{"field": "fileId", "reason": "필수 항목입니다."}],
}

# 리뷰는 점수 1~5와 content 1000자가 검증 조건이다. 둘을 함께 어긴 경우를 예시로 든다.
REVIEW_ERROR_EXAMPLE = {
    "code": "INVALID_REQUEST",
    "message": "요청 형식이 올바르지 않습니다.",
    "errors": [
        {"field": "devScore", "reason": "5 이하여야 합니다."},
        {"field": "content", "reason": "1000자 이하여야 합니다."},
    ],
}

# 인계물 내려받기는 바디가 없어 검증 실패는 경로 변수 deliverableId가 정수가 아닐 때만 난다.
DELIVERABLE_ID_ERROR_EXAMPLE = {
    "code": "INVALID_REQUEST",
    "message": "요청 형식이 올바르지 않습니다.",
    "errors": [{"field": "deliverableId", "reason": "정수여야 합니다."}],
}

STARTED_CONTRACT_ID = 409


# --------------------------------------------------------------------------- #
# 영역 5 — 계약과 예치
# --------------------------------------------------------------------------- #


@router.post(
    "/contracts/{contractId}/start",
    response_model=ContractResponse,
    tags=[AREA5_TAG],
    responses={
        200: {"content": {"application/json": {"example": STARTED_CONTRACT_EXAMPLE}}},
        403: {
            "model": ErrorResponse,
            "description": "이 계약의 개발자가 아님",
            "content": {"application/json": {"example": FORBIDDEN_EXAMPLE}},
        },
        409: {
            "model": ErrorResponse,
            "description": "지금 상태에서는 착수할 수 없음",
            "content": {
                "application/json": {
                    "examples": {
                        "deposit_required": {
                            "summary": "예치가 끝나지 않음",
                            "value": DEPOSIT_REQUIRED_EXAMPLE,
                        },
                        "already_started": {
                            "summary": "이미 착수함",
                            "value": ALREADY_STARTED_EXAMPLE,
                        },
                    }
                }
            },
        },
        404: CONTRACT_ERRORS[404],
        **AREA1_UNAUTHORIZED,
        422: {
            "model": ValidationErrorResponse,
            "description": "요청 스키마 유효성 검증 실패",
            "content": {"application/json": {"example": CONTRACT_ID_ERROR_EXAMPLE}},
        },
        500: AREA1_ERRORS[500],
    },
    summary="개발 착수",
    description=(
        "**화면 — `5_개발자_착수전` › 착수하고 김미미님에게 알리기**\n\n"
        "개발자가 일을 시작한다고 알린다. 계약이 `DEPOSITED`에서 `IN_PROGRESS`가 되고 "
        "1차 작업이 `SCHEDULED`에서 `IN_PROGRESS`로 넘어간다. 의뢰인 화면에 착수 알림이 "
        "가고 진행 기록에도 남는다.\n\n"
        "**예치가 끝나야 착수할 수 있다.** 돈이 들어온 것을 확인하기 전에는 막는다. "
        "착수 전까지는 개발자가 계약을 물릴 수 있지만, 착수한 뒤로는 이 API로 되돌리지 "
        "않는다.\n\n"
        "착수일은 계약서에 적힌 날짜를 그대로 쓴다. 이 요청이 날짜를 바꾸지 않는다.\n\n"
        "컨설팅 계약에는 착수가 없다. 세션 입장이 그 자리를 대신한다.\n\n"
        "- **200**: 착수했을 때. `development.status`가 `IN_PROGRESS`가 된다\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **403 / FORBIDDEN**: 이 계약의 개발자가 아닐 때. 착수는 개발자만 한다\n"
        "- **404 / CONTRACT_NOT_FOUND**: `contractId`가 404일 때. 없는 계약이거나 이 계약의 "
        "당사자가 아닌 사람이 부른 경우다. 존재를 알리지 않기 위해 403 대신 404를 준다\n"
        "- **409 / DEPOSIT_REQUIRED**: `contractId`가 402일 때. 예치가 끝나지 않아 아직 "
        "착수할 상태가 아니다\n"
        "- **409 / ALREADY_STARTED**: `contractId`가 409일 때\n"
        "- **422 / INVALID_REQUEST**: `contractId`가 정수가 아닐 때\n"
        "- **500 / INTERNAL_ERROR**: 착수를 처리하지 못했을 때"
    ),
)
def start_development(
    contractId: Annotated[
        int,
        Path(
            description=(
                "계약 식별자. 404는 없음, 402는 예치 전, 409는 이미 착수함 재현용 "
                "예약값이다."
            ),
            examples=[DEV_CONTRACT_ID],
        ),
    ],
) -> ContractResponse | JSONResponse:
    if contractId == NOT_FOUND_CONTRACT_ID:
        return _error_response(404, CONTRACT_NOT_FOUND)
    if contractId == NOT_DEPOSITED_CONTRACT_ID:
        return _error_response(409, DEPOSIT_REQUIRED)
    if contractId == STARTED_CONTRACT_ID:
        return _error_response(409, ALREADY_STARTED)
    return ContractResponse(**STARTED_CONTRACT_EXAMPLE)


@router.post(
    "/rooms/{roomId}/development-contracts",
    response_model=ContractResponse,
    status_code=201,
    tags=[AREA5_TAG],
    responses={
        201: {"content": {"application/json": {"example": DEV_CONTRACT_EXAMPLE}}},
        400: {
            "model": ErrorResponse,
            "description": "납기일이 착수일보다 이름",
            "content": {"application/json": {"example": DUE_DATE_BEFORE_START_EXAMPLE}},
        },
        403: {
            "model": ErrorResponse,
            "description": "이 방의 개발자가 아님",
            "content": {"application/json": {"example": FORBIDDEN_EXAMPLE}},
        },
        404: {
            "model": ErrorResponse,
            "description": "방 없음",
            "content": {"application/json": {"example": ROOM_NOT_FOUND_EXAMPLE}},
        },
        409: {
            "model": ErrorResponse,
            "description": "개발방이 아님 · 이미 서명함 · 취소된 계약",
            "content": {
                "application/json": {
                    "examples": {
                        "NOT_DEVELOPMENT_ROOM": {
                            "summary": "개발방이 아님",
                            "value": {
                                "code": NOT_DEVELOPMENT_ROOM[0],
                                "message": NOT_DEVELOPMENT_ROOM[1],
                            },
                        },
                        "CONTRACT_ALREADY_SIGNED": {
                            "summary": "이미 서명함",
                            "value": {
                                "code": CONTRACT_ALREADY_SIGNED[0],
                                "message": CONTRACT_ALREADY_SIGNED[1],
                            },
                        },
                        "CONTRACT_CANCELLED": {
                            "summary": "취소된 계약",
                            "value": {
                                "code": CONTRACT_CANCELLED[0],
                                "message": CONTRACT_CANCELLED[1],
                            },
                        },
                    }
                }
            },
        },
        **AREA1_UNAUTHORIZED,
        422: {
            "model": ValidationErrorResponse,
            "description": "요청 스키마 유효성 검증 실패 · 계약서에 넣을 수 없는 요구사항",
            "content": {"application/json": {"example": WRITE_CONTRACT_ERROR_EXAMPLE}},
        },
        500: AREA1_ERRORS[500],
    },
    summary="개발 계약서 작성",
    description=(
        "**화면 — `5_개발자_계약서작성` › 요구사항 고르기 · 작업 나누기 · 확인 문장 · 납기 · 보내기**\n\n"
        "개발자가 개발 계약서를 써서 의뢰인에게 보낸다. 작업마다 가격과 확인 문장을 붙이고, "
        "작업 가격의 합이 계약 금액이 된다.\n\n"
        "**작업은 언제나 요구사항 하나에 붙는다.** 요청서에 있던 요구사항이면 `issueId`를 "
        "보내고, 개발방에서 새로 합의한 작업이면 `issueId` 없이 `title`을 보낸다. 새 작업은 "
        "서버가 계약용 요구사항을 만들어 붙인다. 계약에서 다룰 요구사항 목록은 작업이 "
        "가리키는 것이라 따로 받지 않는다.\n\n"
        "**계약서는 요청서를 바꾸지 않는다.** 이름·설명·확인 문장을 계약서에서 바꿔도 "
        "계약서에만 남는다.\n\n"
        "**받는 요구사항은 요청서에 담긴 것과 이 계약서에 이미 들어 있는 것이다.** 개발자는 "
        "요청서에 담긴 것만 볼 수 있다(R-REQ-05). 다시 쓸 때는 앞서 새로 만든 작업이 그 "
        "요구사항 번호를 달고 돌아온다.\n\n"
        "보낸 계약서는 양쪽이 서명할 때까지 **협의 중**이다. 공통 계약 `status`는 "
        "`NEGOTIATING`이고 개발 쪽 `status`는 처음부터 `SIGNED`다. 서명 두 개가 모두 "
        "찍혀야 체결된다(`R-CON-01`). 협의 중에 다시 보내면 그 계약서의 내용을 이 "
        "요청으로 덮어쓴다. 계약서는 방에 하나이고, 양쪽이 서명한 뒤에 내용을 바꾸려면 "
        "그때 새 계약서를 쓴다.\n\n"
        "**작업마다 따로 납기를 두지 않는다.** 최종 납기일 하나만 있고, 대금도 모든 작업이 "
        "통과된 뒤 한 번에 나간다.\n\n"
        "인계물은 가격 0원짜리 작업 하나로 함께 들어간다. 0원인 작업은 화면에 가격을 "
        "보여주지 않는다.\n\n"
        "- **201**: 계약서를 보냈을 때. 협의 중인 계약서가 있었으면 그 내용이 이걸로 바뀐다\n"
        "- **400 / DUE_DATE_BEFORE_START**: `dueDate`가 `startDate`보다 이를 때\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **403 / FORBIDDEN**: 이 방의 개발자가 아닐 때. 계약서는 개발자만 쓴다\n"
        "- **404 / ROOM_NOT_FOUND**: `roomId`가 404일 때. 없는 방이거나 이 방의 당사자가 "
        "아닌 사람이 부른 경우다. 존재를 알리지 않기 위해 403 대신 404를 준다\n"
        "- **409 / NOT_DEVELOPMENT_ROOM**: 개발방이 아닐 때. 화면에는 그런 길이 없어 API를 "
        "직접 부를 때만 난다\n"
        "- **409 / CONTRACT_ALREADY_SIGNED**: 한쪽이라도 이미 서명했을 때 (R-CON-02)\n"
        "- **409 / CONTRACT_CANCELLED**: 취소된 계약일 때 (R-CON-21)\n"
        "- **422 / INVALID_REQUEST**: `milestones`가 비었거나, 작업의 `qaCriteria`가 비었거나, "
        "새 작업에 `title`이 없거나, 같은 `issueId`를 두 작업에 담았거나, "
        "`startDate`·`dueDate`·`freeRepairDays`·`milestones` 중 빠진 것이 있을 때. "
        "요청서에도 이 계약서에도 없는 요구사항을 넣었을 때도 422다\n"
        "- **500 / INTERNAL_ERROR**: 계약서를 만들지 못했을 때"
    ),
)
def write_development_contract(
    roomId: Annotated[
        int,
        Path(
            description="개발 방 식별자. 404는 없음 재현용 예약값이다.",
            examples=[93],
        ),
    ],
    request: WriteDevelopmentContractRequest,
) -> ContractResponse | JSONResponse:
    if roomId == NOT_FOUND_ROOM_ID:
        return _error_response(404, ROOM_NOT_FOUND)
    if request.dueDate < request.startDate:
        return _error_response(400, DUE_DATE_BEFORE_START)
    return ContractResponse(
        **{
            **DEV_CONTRACT_EXAMPLE,
            "status": "NEGOTIATING",
            "clientSignature": None,
            "developerSignature": None,
            "contractText": None,
            "development": {
                **DEV_CONTRACT_EXAMPLE["development"],
                "status": "SIGNED",
            },
        }
    )


@router.post(
    "/rooms/{roomId}/contract-qa-suggestions",
    response_model=QaSuggestionListResponse,
    tags=[AREA5_TAG],
    responses={
        200: {
            "content": {"application/json": {"example": QA_SUGGESTION_LIST_EXAMPLE}}
        },
        403: {
            "model": ErrorResponse,
            "description": "이 방의 개발자가 아님",
            "content": {"application/json": {"example": FORBIDDEN_EXAMPLE}},
        },
        404: {
            "model": ErrorResponse,
            "description": "방 없음",
            "content": {"application/json": {"example": ROOM_NOT_FOUND_EXAMPLE}},
        },
        409: {
            "model": ErrorResponse,
            "description": "개발방이 아님 · 한쪽이라도 이미 서명함",
            "content": {
                "application/json": {
                    "examples": {
                        "NOT_DEVELOPMENT_ROOM": {
                            "summary": "개발방이 아님",
                            "value": {
                                "code": NOT_DEVELOPMENT_ROOM[0],
                                "message": NOT_DEVELOPMENT_ROOM[1],
                            },
                        },
                        "CONTRACT_ALREADY_SIGNED": {
                            "summary": "이미 서명함",
                            "value": {
                                "code": CONTRACT_ALREADY_SIGNED[0],
                                "message": CONTRACT_ALREADY_SIGNED[1],
                            },
                        },
                    }
                }
            },
        },
        422: {
            "model": ValidationErrorResponse,
            "description": "요청 스키마 유효성 검증 실패 · 계약서에 넣을 수 없는 요구사항",
        },
        502: {
            "model": ErrorResponse,
            "description": "AI가 계약에 맞는 후보를 만들지 못함",
            "content": {
                "application/json": {
                    "example": {
                        "code": AI_RESPONSE_INVALID[0],
                        "message": AI_RESPONSE_INVALID[1],
                    }
                }
            },
        },
        503: {
            "model": ErrorResponse,
            "description": "확인 문장 생성 서비스에 연결할 수 없음",
            "content": {
                "application/json": {
                    "example": {
                        "code": AI_SERVICE_UNAVAILABLE[0],
                        "message": AI_SERVICE_UNAVAILABLE[1],
                    }
                }
            },
        },
        **AREA1_UNAUTHORIZED,
        500: AREA1_ERRORS[500],
    },
    summary="계약 작업 확인 문장 후보 생성",
    description=(
        "**화면 — `5_개발자_계약서작성` › 작업별 확인 문장 추천**\n\n"
        "개발자가 계약서를 편집하면서 작업 하나의 확인 문장 후보를 세 건 요청한다. "
        "기존 요구사항에 붙은 작업은 `issueId`를 보내고, 아직 저장하지 않은 새 작업은 "
        "`issueId`를 null로 보낸다. 두 경우 모두 현재 편집기에 적힌 `title`과 `detail`을 "
        "생성 기준으로 사용한다.\n\n"
        "후보를 받거나 고르는 것만으로 계약서·요구사항·확인 문장은 저장되지 않는다. "
        "개발자가 고르거나 고쳐 넣은 문장은 기존 개발 계약서 작성 API를 호출할 때 함께 "
        "저장된다. 최초 AI 계약 초안은 별도 초안 API가 한 번 만들고, 이후 편집·재전송·저장은 "
        "이 API를 자동 호출하지 않는다. (R-CON-22)\n\n"
        "기존 `issueId`는 이 방의 요청서에 있거나 현재 계약서에 이미 들어 있는 요구사항만 "
        "허용한다. 계약서가 아직 없어도 직접 작성 중인 내용으로 후보를 요청할 수 있다.\n\n"
        "- **200**: 계약 작업에 맞는 후보 세 건을 만들었을 때\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **403 / FORBIDDEN**: 이 방의 개발자가 아닐 때\n"
        "- **404 / ROOM_NOT_FOUND**: `roomId`가 404일 때. 없는 방이거나 이 방의 당사자가 "
        "아닌 사람이 부른 경우다\n"
        "- **409 / NOT_DEVELOPMENT_ROOM**: 개발방이 아닐 때\n"
        "- **409 / CONTRACT_ALREADY_SIGNED**: 한쪽이라도 이미 서명했을 때 (R-CON-02)\n"
        "- **422 / INVALID_REQUEST**: 요청 값이 형식에 맞지 않거나, 기존 `issueId`가 이 방의 "
        "요청서와 현재 계약서 어느 쪽에도 없을 때\n"
        "- **502 / AI_RESPONSE_INVALID**: 생성 결과의 개수·길이·형식이 계약에 맞지 않을 때\n"
        "- **503 / AI_SERVICE_UNAVAILABLE**: 생성 서비스에 연결할 수 없을 때\n"
        "- **500 / INTERNAL_ERROR**: 그 밖의 서버 오류가 발생했을 때\n\n"
        "Mock은 고정된 200 응답만 반환한다. 권한·서명·502·503은 후속 Service 구현을 위한 "
        "응답 계약 예시이며 Mock 전용 실패 트리거를 만들지 않는다."
    ),
)
def suggest_contract_qa_criteria(
    roomId: Annotated[
        int,
        Path(
            description="개발 방 식별자. 404는 없음 재현용 예약값이다.",
            examples=[CONVERTED_DEVELOPMENT_ROOM_ID],
        ),
    ],
    request: ContractQaSuggestionRequest,
) -> QaSuggestionListResponse | JSONResponse:
    if roomId == NOT_FOUND_ROOM_ID:
        return _error_response(404, ROOM_NOT_FOUND)
    return QaSuggestionListResponse(**QA_SUGGESTION_LIST_EXAMPLE)


@router.post(
    "/contracts/{contractId}/deposit",
    response_model=EscrowDepositResponse,
    status_code=201,
    tags=[AREA4_TAG],
    responses={
        201: {"content": {"application/json": {"example": DEPOSIT_EXAMPLE}}},
        409: {
            "model": ErrorResponse,
            "description": "이미 예치됨 · 서명 전",
            "content": {
                "application/json": {
                    "examples": {
                        "ALREADY_DEPOSITED": {
                            "summary": "이미 예치됨",
                            "value": {
                                "code": ALREADY_DEPOSITED[0],
                                "message": ALREADY_DEPOSITED[1],
                            },
                        },
                        "CONTRACT_NOT_SIGNED": {
                            "summary": "의뢰인 서명 전",
                            "value": {
                                "code": CONTRACT_NOT_SIGNED[0],
                                "message": CONTRACT_NOT_SIGNED[1],
                            },
                        },
                        "CONTRACT_CANCELLED": {
                            "summary": "취소된 계약",
                            "value": {
                                "code": CONTRACT_CANCELLED[0],
                                "message": CONTRACT_CANCELLED[1],
                            },
                        },
                    }
                }
            },
        },
        **CONTRACT_ERRORS,
    },
    summary="계약 금액 예치",
    description=(
        "**화면 — `4_의뢰인_계약서상세` › 서명하고 N원 결제 · "
        "`5_의뢰인_계약예치` › N원 예치하기**\n\n"
        "계약 금액 전액을 한 번에 맡긴다. **컨설팅과 개발이 같은 API를 쓴다.** 화면 문구는 "
        "컨설팅이 '결제', 개발이 '예치'로 다르지만 돈이 오가는 방식은 같다 — 맡겨 두었다가 "
        "일이 끝나면 개발자에게 나간다.\n\n"
        "컨설팅은 서명과 결제를 한 버튼에서 하므로, 화면이 서명 API를 먼저 부르고 이어서 "
        "이 API를 부른다.\n\n"
        "예치가 확인되면 계약 상태가 `DEPOSITED`가 되고 개발자에게 착수 알림이 간다.\n\n"
        "**돈의 원장은 이 서버가 갖지 않는다.** 결제와 보관은 Escrow 서버가 맡고, 여기서는 "
        "예치를 걸고 그 결과를 확인만 한다. 같은 `idempotencyKey`로 다시 보내면 결제가 "
        "두 번 나가지 않는다.\n\n"
        "**돈은 회원가입 때 발급한 의뢰인의 SSAFY 계좌에서 빠진다.** Komit이 보관한 "
        "`userKey`·은행코드·계좌번호를 Escrow의 `createEscrow` 요청에 전달한다. "
        "`paymentMethodId`는 이 계좌를 가리키며 다른 계좌나 결제창은 제공하지 않는다. "
        "(`R-PAY-09`)\n\n"
        "- **201**: 예치를 걸었을 때. 결제가 바로 확인되면 `DEPOSITED`, 결과를 기다리는 "
        "중이면 `PENDING`이다\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **403 / FORBIDDEN**: 이 계약의 의뢰인이 아닐 때. 예치는 의뢰인만 한다\n"
        "- **404 / CONTRACT_NOT_FOUND**: `contractId`가 404일 때\n"
        "- **409 / ALREADY_DEPOSITED**: `contractId`가 409일 때\n"
        "- **409 / CONTRACT_NOT_SIGNED**: 의뢰인이 아직 서명하지 않았을 때. 의뢰인은 자기가 서명한 "
        "계약서에만 돈을 맡긴다. 개발자 서명은 기다리지 않으며, 대금은 양쪽이 모두 서명한 뒤에만 "
        "개발자에게 나간다(R-CON-20). 목에서는 아직 아무도 서명하지 않은 계약서 "
        f"`contractId`={DRAFT_CLIENT_CONTRACT_ID}이 이 응답을 준다\n"
        "- **409 / CONTRACT_CANCELLED**: 의뢰인이 취소한 계약일 때 (R-CON-21). 목에서는 "
        f"`contractId`={CANCELLED_CONTRACT_ID}이다\n"
        "- **422 / INVALID_REQUEST**: `paymentMethodId`나 `idempotencyKey`가 없을 때\n"
        "- **500 / INTERNAL_ERROR**: 예치를 걸지 못했을 때"
    ),
)
def deposit_contract(
    contractId: Annotated[
        int,
        Path(
            description=(
                "계약 식별자. 404는 없음, 409는 이미 예치됨 재현용 예약값이다. "
                f"{DRAFT_CLIENT_CONTRACT_ID}은 서명 전 계약서다."
            ),
            examples=[DEV_CONTRACT_ID],
        ),
    ],
    request: DepositRequest,
) -> EscrowDepositResponse | JSONResponse:
    if contractId == NOT_FOUND_CONTRACT_ID:
        return _error_response(404, CONTRACT_NOT_FOUND)
    if contractId == DEPOSITED_CONTRACT_ID:
        return _error_response(409, ALREADY_DEPOSITED)
    if contractId == DRAFT_CLIENT_CONTRACT_ID:
        return _error_response(409, CONTRACT_NOT_SIGNED)
    if contractId == CANCELLED_CONTRACT_ID:
        return _error_response(409, CONTRACT_CANCELLED)
    return EscrowDepositResponse(**DEPOSIT_EXAMPLE)


@router.get(
    "/contracts/{contractId}/deposit",
    response_model=EscrowDepositResponse,
    tags=[AREA4_TAG],
    responses={
        200: {"content": {"application/json": {"example": DEPOSIT_EXAMPLE}}},
        **CONTRACT_ERRORS,
    },
    summary="예치 상태 조회",
    description=(
        "**화면 — `5_개발자_착수전` › 예치 확인 · `5_의뢰인_진행` › 예치 상태**\n\n"
        "컨설팅과 개발이 같은 API를 쓴다.\n\n"
        "이 계약의 예치가 어디까지 왔는지 확인한다. 개발자는 착수 전에 이 값으로 돈이 "
        "실제로 걸려 있는지 본다.\n\n"
        "- **200**: 예치를 건 적이 있을 때\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **404 / CONTRACT_NOT_FOUND**: `contractId`가 404이거나 아직 예치를 걸지 않았을 때\n"
        "- **500 / INTERNAL_ERROR**: 상태를 읽지 못했을 때"
    ),
)
def get_deposit(
    contractId: Annotated[
        int,
        Path(
            description="계약 식별자. 404는 없음 재현용 예약값이다.",
            examples=[DEV_CONTRACT_ID],
        ),
    ],
) -> EscrowDepositResponse | JSONResponse:
    if contractId == NOT_FOUND_CONTRACT_ID:
        return _error_response(404, CONTRACT_NOT_FOUND)
    return EscrowDepositResponse(**DEPOSIT_EXAMPLE)


# --------------------------------------------------------------------------- #
# 영역 5 — 작업 진행과 확인
# --------------------------------------------------------------------------- #


@router.post(
    "/milestones/{milestoneId}/submission",
    response_model=MilestoneResponse,
    status_code=201,
    tags=[AREA5_TAG],
    responses={
        201: {"content": {"application/json": {"example": MILESTONES[1]}}},
        403: {
            "model": ErrorResponse,
            "description": "이 계약의 개발자가 아님",
            "content": {"application/json": {"example": FORBIDDEN_EXAMPLE}},
        },
        409: {
            "model": ErrorResponse,
            "description": "지금 상태에서는 처리할 수 없음",
            "content": {
                "application/json": {
                    "example": {
                        "code": "MILESTONE_STATE_CONFLICT",
                        "message": "지금 상태에서는 처리할 수 없습니다.",
                    }
                }
            },
        },
        404: MILESTONE_ERRORS[404],
        **AREA1_UNAUTHORIZED,
        422: {
            "model": ValidationErrorResponse,
            "description": "요청 스키마 유효성 검증 실패",
            "content": {"application/json": {"example": SUBMIT_MESSAGE_ERROR_EXAMPLE}},
        },
        500: AREA1_ERRORS[500],
    },
    summary="작업 확인 요청",
    description=(
        "**화면 — `5_개발자_확인` › 확인 요청 보내기 · 확인 요청 다시 보내기**\n\n"
        "착수한 작업을 마치고 의뢰인에게 확인을 요청한다. 이때부터 **3일**을 세고, "
        "의뢰인이 답하지 않으면 자동으로 통과된다. 다시 보내면 기한도 다시 시작한다.\n\n"
        "남길 말이 없으면 바디를 생략해도 된다. 빈 바디는 `{}`와 같게 처리한다.\n\n"
        "응답의 `autoPassAt`이 자동 통과 시각이다. 화면은 이 값으로 '9월 1일 자동 통과'를 "
        "보여준다.\n\n"
        "- **201**: 확인 요청을 보냈을 때. 상태가 SUBMITTED가 되고 `submittedAt`이 채워진다\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **403 / FORBIDDEN**: 이 계약의 개발자가 아닐 때. 확인 요청은 개발자만 보낸다\n"
        "- **404 / MILESTONE_NOT_FOUND**: `milestoneId`가 404일 때. 없는 작업이거나 이 계약의 "
        "당사자가 아닌 사람이 부른 경우다. 존재를 알리지 않기 위해 403 대신 404를 준다\n"
        "- **409 / MILESTONE_STATE_CONFLICT**: `milestoneId`가 409일 때. 이미 통과된 "
        "작업이다\n"
        "- **422 / INVALID_REQUEST**: `message`가 300자를 넘을 때\n"
        "- **500 / INTERNAL_ERROR**: 보내지 못했을 때"
    ),
)
def submit_milestone(
    milestoneId: Annotated[
        int,
        Path(
            description=(
                "작업 식별자. 404는 없음, 409는 상태 충돌 재현용 예약값이다."
            ),
            examples=[302],
        ),
    ],
    # message가 선택이라 보낼 것이 없으면 바디를 생략할 수 있다. 빈 바디는 {}와 같게 본다.
    request: SubmitMilestoneRequest | None = None,
) -> MilestoneResponse | JSONResponse:
    if milestoneId == NOT_FOUND_MILESTONE_ID:
        return _error_response(404, MILESTONE_NOT_FOUND)
    if milestoneId == CONFLICT_MILESTONE_ID:
        return _error_response(409, MILESTONE_STATE_CONFLICT)
    return MilestoneResponse(**MILESTONES[1])


@router.put(
    "/milestones/{milestoneId}/qa-results",
    response_model=MilestoneResponse,
    tags=[AREA5_TAG],
    responses={
        200: {"content": {"application/json": {"example": MILESTONES[1]}}},
        403: {
            "model": ErrorResponse,
            "description": "이 계약의 의뢰인이 아님",
            "content": {"application/json": {"example": FORBIDDEN_EXAMPLE}},
        },
        409: {
            "model": ErrorResponse,
            "description": "지금 상태에서는 처리할 수 없음",
            "content": {
                "application/json": {
                    "example": {
                        "code": MILESTONE_STATE_CONFLICT[0],
                        "message": MILESTONE_STATE_CONFLICT[1],
                    }
                }
            },
        },
        **MILESTONE_ERRORS,
    },
    summary="확인 결과 저장",
    description=(
        "**화면 — `5_의뢰인_확인` › 확인된 기능 제출**\n\n"
        "의뢰인이 확인 문장을 직접 해보고 그 결과를 저장한다. **다 체크하지 않아도 "
        "보낼 수 있다.** 3줄 중 2줄만 됐다면 그대로 보내고, 개발자는 무엇이 남았는지 "
        "`5_개발자_확인`에서 본다. 고쳐서 다시 확인 요청을 보내는 흐름이다.\n\n"
        "**작업 상태는 바뀌지 않는다.** 이 API는 완료 처리가 아니다. 확인 문장을 모두 "
        "통과한 작업만 완료가 되며(R-QA-01) 그건 작업 통과시키기가 맡는다. 화면은 "
        "전부 체크됐을 때 이 API 대신 통과시키기를 부른다.\n\n"
        "**체크는 되돌릴 수 없다.** 이번에 체크한 것을 보내면 이미 통과한 것에 더한다. "
        "빼고 보내도 풀리지 않고, 이미 통과한 값을 다시 보내도 결과가 같다.\n\n"
        "- **200**: 저장했을 때. 저장된 뒤의 작업 전체를 준다\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **403 / FORBIDDEN**: 이 계약의 의뢰인이 아닐 때. 확인은 의뢰인만 한다\n"
        "- **404 / MILESTONE_NOT_FOUND**: `milestoneId`가 404일 때. 없는 작업이거나 이 "
        "계약의 당사자가 아닌 사람이 부른 경우다. 존재를 알리지 않기 위해 403 대신 404를 "
        "준다\n"
        "- **409 / MILESTONE_STATE_CONFLICT**: `milestoneId`가 409일 때. 이미 통과된 "
        "작업이라 결과를 바꿀 수 없다(R-QA-03)\n"
        "- **422 / INVALID_REQUEST**: `qaCriterionIds`가 비었거나 이 작업의 확인 "
        "문장이 아닌 값이 섞였을 때\n"
        "- **500 / INTERNAL_ERROR**: 저장하지 못했을 때"
    ),
)
def save_qa_results(
    milestoneId: Annotated[
        int,
        Path(
            description=(
                "작업 식별자. 404는 없음, 409는 상태 충돌 재현용 예약값이다."
            ),
            examples=[MILESTONE_ID],
        ),
    ],
    request: SaveQaResultsRequest,
) -> MilestoneResponse | JSONResponse:
    if milestoneId == NOT_FOUND_MILESTONE_ID:
        return _error_response(404, MILESTONE_NOT_FOUND)
    if milestoneId == CONFLICT_MILESTONE_ID:
        return _error_response(409, MILESTONE_STATE_CONFLICT)
    # 그 작업에 없는 확인 문장은 막는다.
    target = next(
        (row for row in MILESTONES if row["milestoneId"] == milestoneId), MILESTONES[1]
    )
    known = {qa["qaCriterionId"] for qa in target["qaCriteria"]}
    if not set(request.qaCriterionIds) <= known:
        return _field_error("qaCriterionIds", "이 작업의 확인 문장만 보낼 수 있습니다.")

    # 체크는 되돌릴 수 없으므로 이미 통과한 것에 더하기만 한다.
    passed = {
        qa["qaCriterionId"]
        for qa in target["qaCriteria"]
        if qa["status"] == "RESOLVED"
    } | set(request.qaCriterionIds)
    milestone = {
        **target,
        "qaCriteria": [
            {
                **qa,
                "status": "RESOLVED" if qa["qaCriterionId"] in passed else "UNRESOLVED",
            }
            for qa in target["qaCriteria"]
        ],
    }
    return MilestoneResponse(**milestone)


@router.post(
    "/milestones/{milestoneId}/confirmation",
    response_model=MilestoneResponse,
    status_code=201,
    tags=[AREA5_TAG],
    responses={
        201: {"content": {"application/json": {"example": MILESTONES[0]}}},
        400: {
            "model": ErrorResponse,
            "description": "확인 문장을 다 체크하지 않음",
            "content": {
                "application/json": {
                    "example": {
                        "code": "QA_NOT_ALL_CHECKED",
                        "message": "확인 문장을 모두 체크해야 통과시킬 수 있습니다.",
                    }
                }
            },
        },
        403: {
            "model": ErrorResponse,
            "description": "이 계약의 의뢰인이 아님",
            "content": {"application/json": {"example": FORBIDDEN_EXAMPLE}},
        },
        409: {
            "model": ErrorResponse,
            "description": "지금 상태에서는 처리할 수 없음",
            "content": {
                "application/json": {
                    "example": {
                        "code": "MILESTONE_STATE_CONFLICT",
                        "message": "지금 상태에서는 처리할 수 없습니다.",
                    }
                }
            },
        },
        404: MILESTONE_ERRORS[404],
        **AREA1_UNAUTHORIZED,
        422: {
            "model": ValidationErrorResponse,
            "description": "요청 스키마 유효성 검증 실패",
            "content": {"application/json": {"example": CONFIRM_QA_ERROR_EXAMPLE}},
        },
        500: AREA1_ERRORS[500],
    },
    summary="작업 통과시키기",
    description=(
        "**화면 — `5_의뢰인_확인` › 확인된 기능 제출**\n\n"
        "의뢰인이 확인 문장을 모두 체크하고 작업을 통과시킨다. **통과시키면 되돌릴 수 "
        "없다.** 확인 문장이 하나라도 남아 있으면 통과시킬 수 없다.\n\n"
        "**같은 버튼이 두 API로 갈린다.** 다 체크하지 않은 채로 누르면 확인 결과 저장이 "
        "개발자에게 거기까지의 결과를 알리고, 다 체크하고 누르면 이 API가 완료로 "
        "확정한다(R-QA-01·R-QA-03).\n\n"
        "대금은 여기서 나가지 않는다. 모든 작업이 통과된 뒤 한 번에 지급된다.\n\n"
        "- **201**: 통과시켰을 때. 상태가 CONFIRMED가 되고 `confirmedAt`이 채워진다\n"
        "- **400 / QA_NOT_ALL_CHECKED**: `qaCriterionIds`에 이 작업의 확인 문장이 모두 들어 있지 "
        "않을 때. 목에서는 201·202가 빠지면 이 응답을 준다\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **403 / FORBIDDEN**: 이 계약의 의뢰인이 아닐 때. 통과 판정은 의뢰인만 한다\n"
        "- **404 / MILESTONE_NOT_FOUND**: `milestoneId`가 404일 때. 없는 작업이거나 이 계약의 "
        "당사자가 아닌 사람이 부른 경우다. 존재를 알리지 않기 위해 403 대신 404를 준다\n"
        "- **409 / MILESTONE_STATE_CONFLICT**: `milestoneId`가 409일 때. 이미 통과됐거나 "
        "아직 확인 요청이 오지 않은 작업이다\n"
        "- **422 / INVALID_REQUEST**: `qaCriterionIds`가 없을 때. 빈 배열은 400이다\n"
        "- **500 / INTERNAL_ERROR**: 통과시키지 못했을 때"
    ),
)
def confirm_milestone(
    milestoneId: Annotated[
        int,
        Path(
            description=(
                "작업 식별자. 404는 없음, 409는 상태 충돌 재현용 예약값이다."
            ),
            examples=[MILESTONE_ID],
        ),
    ],
    request: ConfirmMilestoneRequest,
) -> MilestoneResponse | JSONResponse:
    if milestoneId == NOT_FOUND_MILESTONE_ID:
        return _error_response(404, MILESTONE_NOT_FOUND)
    if milestoneId == CONFLICT_MILESTONE_ID:
        return _error_response(409, MILESTONE_STATE_CONFLICT)

    # 그 작업의 확인 문장이 하나라도 빠지면 규칙 위반이다 (R-QA-01).
    milestone = next(
        (row for row in MILESTONES if row["milestoneId"] == milestoneId), MILESTONES[0]
    )
    needed = {qa["qaCriterionId"] for qa in milestone["qaCriteria"]}
    if not needed <= set(request.qaCriterionIds):
        return _error_response(400, QA_NOT_ALL_CHECKED)

    passed = copy.deepcopy(milestone)
    passed["status"] = "CONFIRMED"
    passed["confirmedAt"] = "2026-08-30T10:00:00+09:00"
    passed["autoPassAt"] = None
    for criterion in passed["qaCriteria"]:
        criterion["status"] = "RESOLVED"
    return MilestoneResponse(**passed)


@router.post(
    "/deliverables/{deliverableId}/file",
    response_model=DeliverableResponse,
    status_code=201,
    tags=[AREA5_TAG],
    responses={
        201: {
            "content": {
                "application/json": {
                    "example": {
                        **DELIVERABLES[0],
                        "fileId": 71,
                        "deliveredAt": "2026-09-10T15:00:00+09:00",
                    }
                }
            }
        },
        403: {
            "model": ErrorResponse,
            "description": "이 계약의 개발자가 아님",
            "content": {"application/json": {"example": FORBIDDEN_EXAMPLE}},
        },
        404: {
            "model": ErrorResponse,
            "description": "인계물 없음",
            "content": {
                "application/json": {
                    "example": {
                        "code": DELIVERABLE_NOT_FOUND[0],
                        "message": DELIVERABLE_NOT_FOUND[1],
                    }
                }
            },
        },
        **AREA1_UNAUTHORIZED,
        422: {
            "model": ValidationErrorResponse,
            "description": "요청 스키마 유효성 검증 실패",
            "content": {"application/json": {"example": DELIVERABLE_FILE_ERROR_EXAMPLE}},
        },
        500: AREA1_ERRORS[500],
    },
    summary="인계물 붙이기",
    description=(
        "**화면 — `5_개발자_확인` › 인계 작업의 파일 첨부**\n\n"
        "**파일은 이 요청으로 보내지 않는다.** 다른 파일과 같이 업로드 3콜(`POST /uploads` → "
        "저장소로 `PUT` → `POST /uploads/{uploadId}/complete`)을 먼저 마치고, 거기서 받은 "
        "`fileId`를 이 요청에 담아 인계물에 붙인다. 소스 파일 묶음은 크기가 커서 이 서버를 "
        "지나가지 않게 한다.\n\n"
        "붙인 시각이 인계 시각으로 남고, 의뢰인 화면에서는 '받기'로 내려받을 수 있게 된다.\n\n"
        "이미 붙인 인계물에 다시 붙이면 새 파일로 바뀌고 `deliveredAt`도 그 시각으로 갱신된다. "
        "재첨부를 막지 않으므로 409는 없다.\n\n"
        "형식과 크기는 `POST /uploads`에서 `purpose`가 `DELIVERABLE`일 때의 한도로 거른다. "
        "그래서 이 요청에는 용량 오류가 없다.\n\n"
        "- **201**: 붙였을 때\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **403 / FORBIDDEN**: 이 계약의 개발자가 아닐 때\n"
        "- **404 / DELIVERABLE_NOT_FOUND**: `deliverableId`가 404일 때. 없는 인계물이거나 이 "
        "계약의 당사자가 아닌 사람이 부른 경우다. 존재를 알리지 않기 위해 403 대신 404를 준다\n"
        "- **422 / INVALID_REQUEST**: `fileId`가 없을 때\n"
        "- **500 / INTERNAL_ERROR**: 붙이지 못했을 때"
    ),
)
def upload_deliverable(
    deliverableId: Annotated[
        int,
        Path(
            description="인계물 식별자. 404는 없음 재현용 예약값이다.",
            examples=[401],
        ),
    ],
    request: AttachDeliverableRequest,
) -> DeliverableResponse | JSONResponse:
    if deliverableId == NOT_FOUND_DELIVERABLE_ID:
        return _error_response(404, DELIVERABLE_NOT_FOUND)
    return DeliverableResponse(
        **{
            **DELIVERABLES[0],
            "deliverableId": deliverableId,
            "fileId": 71,
            "deliveredAt": "2026-09-10T15:00:00+09:00",
        }
    )


# --------------------------------------------------------------------------- #
# 영역 5 — 리뷰
# --------------------------------------------------------------------------- #


@router.post(
    "/contracts/{contractId}/review",
    response_model=ReviewResponse,
    status_code=201,
    tags=[AREA5_TAG],
    responses={
        201: {"content": {"application/json": {"example": REVIEWS[0]}}},
        400: {
            "model": ErrorResponse,
            "description": "계약이 아직 끝나지 않음",
            "content": {
                "application/json": {"example": CONTRACT_NOT_COMPLETED_EXAMPLE}
            },
        },
        403: {
            "model": ErrorResponse,
            "description": "이 계약의 의뢰인이 아님",
            "content": {"application/json": {"example": FORBIDDEN_EXAMPLE}},
        },
        409: {
            "model": ErrorResponse,
            "description": "이미 리뷰를 남김",
            "content": {
                "application/json": {
                    "example": {
                        "code": "REVIEW_ALREADY_WRITTEN",
                        "message": "이미 리뷰를 남기셨습니다.",
                    }
                }
            },
        },
        404: CONTRACT_ERRORS[404],
        **AREA1_UNAUTHORIZED,
        422: {
            "model": ValidationErrorResponse,
            "description": "요청 스키마 유효성 검증 실패",
            "content": {"application/json": {"example": REVIEW_ERROR_EXAMPLE}},
        },
        500: AREA1_ERRORS[500],
    },
    summary="리뷰 남기기",
    description=(
        "**화면 — `5_의뢰인_리뷰작성` › 리뷰 남기기**\n\n"
        "계약을 마친 의뢰인이 개발자를 평가한다. 개발력과 설명력을 따로 매기고, 이 점수가 "
        "개발자 프로필의 평점에 들어간다.\n\n"
        "한 계약에 리뷰는 한 번만 남길 수 있다.\n\n"
        "- **201**: 남겼을 때\n"
        "- **400 / CONTRACT_NOT_COMPLETED**: `contractId`가 400일 때. 아직 끝나지 않은 "
        "계약이다\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **403 / FORBIDDEN**: 이 계약의 의뢰인이 아닐 때\n"
        "- **404 / CONTRACT_NOT_FOUND**: `contractId`가 404일 때. 없는 계약이거나 이 계약의 "
        "당사자가 아닌 사람이 부른 경우다. 존재를 알리지 않기 위해 403 대신 404를 준다\n"
        "- **409 / REVIEW_ALREADY_WRITTEN**: `contractId`가 409일 때\n"
        "- **422 / INVALID_REQUEST**: 점수가 1에서 5 사이가 아니거나 `content`가 "
        "1000자를 넘을 때\n"
        "- **500 / INTERNAL_ERROR**: 남기지 못했을 때"
    ),
)
def write_review(
    contractId: Annotated[
        int,
        Path(
            description=(
                "계약 식별자. 404는 없음, 409는 중복 리뷰, 400은 미완료 재현용 예약값이다."
            ),
            examples=[DEV_CONTRACT_ID],
        ),
    ],
    request: WriteReviewRequest,
) -> ReviewResponse | JSONResponse:
    if contractId == NOT_FOUND_CONTRACT_ID:
        return _error_response(404, CONTRACT_NOT_FOUND)
    if contractId == REVIEWED_CONTRACT_ID:
        return _error_response(409, REVIEW_ALREADY_WRITTEN)
    if contractId == NOT_COMPLETED_CONTRACT_ID:
        return _error_response(400, CONTRACT_NOT_COMPLETED)
    return ReviewResponse(
        **{
            **REVIEWS[0],
            "devScore": request.devScore,
            "expScore": request.expScore,
            "content": request.content,
        }
    )


# =========================================================================== #
# 영역 6 — 문제 신고 · 이의 제기
#
# 계약이 도는 동안 말이 통하지 않을 때 운영자에게 알린다. 의뢰인이 내는 것을 문제 신고,
# 개발자가 내는 것을 이의 제기라 부르지만 접수되는 내용은 같다.
#
# 화면: 6_의뢰인_문제신고 · 6_개발자_이의제기
#
# 함께 쓰는 API (다른 영역에 있다):
#   GET /api/users/me            전 영역 헤더
#   GET /api/rooms/{roomId}      방 머리
#   GET /api/contracts/{id}      계약 내용
#
# 접수된 신고를 확인하고 종결하는 것은 운영자 몫이라 9 운영 콘솔에서 다룬다.
# 종결되면 신고한 사람에게 알림이 가고, 신고 상태를 따로 보는 화면은 두지 않는다.
# 신고를 저장한 뒤 Service가 연결된 Escrow의 자금을 동결한다. 클라이언트는 Service만
# 부르며, 저장과 동결이 모두 끝나야 접수 성공이다.
#
# ERD 확인 필요: notifications.target_type(NOTIFICATION_TARGET_TYPE)에 신고를 가리키는
# 값이 없다. ANNOUNCEMENT / CONTRACT / ISSUE 뿐이라 지금은 CONTRACT 로 계약을 가리켜
# 알림에서 그 계약으로 들어가게 한다. 신고를 바로 열어야 하면 값을 하나 늘려야 한다.
# =========================================================================== #


class CreateReportRequest(BaseModel):
    """문제 신고 접수 요청."""

    reason: str = Field(
        min_length=1,
        max_length=255,
        description="적어 낼 내용. 255자까지 받는다.",
        examples=["8월 31일에 작업 진행 상황을 문의했지만 답변을 받지 못했습니다."],
    )
    fileId: int | None = Field(
        default=None,
        description=(
            "증거 파일 식별자. `POST /api/uploads`에 `purpose`를 `REPORT_EVIDENCE`로 "
            "올려 받는다. 없으면 null이다."
        ),
        examples=[81],
    )


class ReportResponse(BaseModel):
    """접수된 신고 한 건. 문제 신고와 이의 제기가 같은 모양을 쓴다."""

    reportId: int = Field(description="신고 식별자.", examples=[77])
    contractId: int = Field(
        description="문제가 생긴 계약 식별자.",
        examples=[DEV_CONTRACT_ID],
    )
    reporterUserId: int = Field(
        description="신고한 사람의 사용자 식별자.",
        examples=[12],
    )
    targetUserId: int = Field(
        description="신고 대상이 된 사람의 사용자 식별자.",
        examples=[30],
    )
    reason: str = Field(
        description="적어 낸 내용. 255자까지 받는다.",
        examples=["8월 31일에 작업 진행 상황을 문의했지만 답변을 받지 못했습니다."],
    )
    fileId: int | None = Field(
        description="증거로 올린 파일 식별자. 올리지 않았으면 null이다.",
        examples=[None],
    )
    status: Literal["OPEN", "RESOLVED"] = Field(
        description=(
            "신고 처리. OPEN은 접수되어 운영자 확인을 기다리는 상태, RESOLVED는 운영자가 "
            "종결한 상태다."
        ),
        examples=["OPEN"],
    )
    createdAt: str = Field(
        description="접수된 시각.",
        examples=["2026-09-02T16:04:00+09:00"],
    )
    resolvedAt: str | None = Field(
        description="운영자가 종결한 시각. 아직이면 null이다.",
        examples=[None],
    )


REPORT_ID = 77
NOT_FOUND_REPORT_CONTRACT_ID = 404
OPEN_REPORT_CONTRACT_ID = 409

REPORT_EXAMPLE = {
    "reportId": REPORT_ID,
    "contractId": DEV_CONTRACT_ID,
    "reporterUserId": 12,
    "targetUserId": 30,
    "reason": "8월 31일에 작업 진행 상황을 문의했지만 답변을 받지 못했습니다.",
    "fileId": None,
    "status": "OPEN",
    "createdAt": "2026-09-02T16:04:00+09:00",
    "resolvedAt": None,
}

REPORT_ALREADY_OPEN = (
    "REPORT_ALREADY_OPEN",
    "이미 접수되어 확인 중인 신고가 있습니다.",
)

AREA6_TAG = "6 문제 신고 · 이의 제기"


@router.post(
    "/contracts/{contractId}/reports",
    response_model=ReportResponse,
    status_code=201,
    tags=[AREA6_TAG],
    responses={
        201: {"content": {"application/json": {"example": REPORT_EXAMPLE}}},
        **CONTRACT_ERRORS,
        403: {
            "model": ErrorResponse,
            "description": "계약 당사자가 아님",
            "content": {"application/json": {"example": FORBIDDEN_EXAMPLE}},
        },
        409: {
            "model": ErrorResponse,
            "description": "이미 확인 중인 신고가 있음",
            "content": {
                "application/json": {
                    "example": {
                        "code": "REPORT_ALREADY_OPEN",
                        "message": "이미 접수되어 확인 중인 신고가 있습니다.",
                    }
                }
            },
        },
        500: {
            "model": ErrorResponse,
            "description": "신고 저장 또는 Escrow 동결을 완료하지 못함",
            "content": {"application/json": {"example": INTERNAL_ERROR_EXAMPLE}},
        },
    },
    summary="문제 신고 · 이의 제기 접수",
    description=(
        "**화면 — `6_의뢰인_문제신고` › 신고하기 · `6_개발자_이의제기` › 이의 제기 보내기**\n\n"
        "계약이 도는 동안 상대와 말이 통하지 않을 때 운영자에게 알린다. 의뢰인이 내면 "
        "문제 신고, 개발자가 내면 이의 제기라고 부르지만 **접수되는 내용과 처리 방식은 "
        "같다.** 누가 냈는지는 신고자로 구분한다.\n\n"
        "**접수는 계약이나 작업 상태를 바꾸지 않는다.** Komit이 누가 옳은지 정하지도 "
        "않는다. 대신 Service가 신고를 저장한 뒤 연결된 Escrow의 지급과 일반 환불을 "
        "동결하며, 운영자가 내용을 확인한 뒤 종결한다. 클라이언트는 Service API만 "
        "호출하고 Escrow를 직접 호출하지 않는다.\n\n"
        "**201은 신고 저장과 Escrow 동결이 모두 완료됐다는 뜻이다.** 둘 중 하나라도 "
        "완료하지 못하면 성공을 반환하지 않는다.\n\n"
        "**종결되면 신고한 사람에게 알림이 간다.** 신고 상태를 따로 보는 화면은 두지 "
        "않고, 알림에서 해당 계약으로 들어간다.\n\n"
        "확인 중인 신고가 있는 계약은 **작업이 3일 만에 자동 통과되지 않는다.** 답이 "
        "없어 자동으로 넘어가는 일을 막기 위해서다. 종결되면 연결된 Escrow의 동결을 "
        "해제한다.\n\n"
        "증거 파일은 하나까지 올릴 수 있고 없어도 된다. **다른 첨부와 같은 업로드 "
        "3단계를 쓴다.** `POST /api/uploads`에 `purpose`를 `REPORT_EVIDENCE`로 올려 "
        "받은 `fileId`를 담아 보낸다. 파일 자체는 이 API를 지나가지 않으므로 형식과 "
        "크기는 업로드 단계에서 걸러진다.\n\n"
        "- **201**: 신고 저장과 Escrow 동결이 끝났을 때. 상태는 `OPEN`이고 종결 시각은 "
        "null이다\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **403 / FORBIDDEN**: 이 계약의 당사자가 아닐 때\n"
        "- **404 / CONTRACT_NOT_FOUND**: `contractId`가 404일 때\n"
        "- **409 / REPORT_ALREADY_OPEN**: `contractId`가 409일 때. 아직 확인 중인 신고가 "
        "있어 새로 낼 수 없다\n"
        "- **422 / INVALID_REQUEST**: `reason`이 비었거나 255자를 넘을 때\n"
        "- **500 / INTERNAL_ERROR**: 신고 저장 또는 Escrow 동결을 완료하지 못했을 때"
    ),
)
def create_report(
    contractId: Annotated[
        int,
        Path(
            description=(
                "계약 식별자. 404는 없음, 409는 확인 중인 신고 있음 재현용 예약값이다."
            ),
            examples=[DEV_CONTRACT_ID],
        ),
    ],
    request: CreateReportRequest,
) -> ReportResponse | JSONResponse:
    if contractId == NOT_FOUND_REPORT_CONTRACT_ID:
        return _error_response(404, CONTRACT_NOT_FOUND)
    if contractId == OPEN_REPORT_CONTRACT_ID:
        return _error_response(409, REPORT_ALREADY_OPEN)
    if not request.reason.strip():
        return _field_error("reason", "내용을 입력해 주세요.")
    return ReportResponse(
        **{
            **REPORT_EXAMPLE,
            "contractId": contractId,
            "reason": request.reason,
            "fileId": request.fileId,
        }
    )


# =========================================================================== #
# 영역 7 — 정산
#
# 의뢰인은 쓴 돈과 맡긴 돈을, 개발자는 받을 돈을 본다.
#
# 화면: 7_의뢰인_소비내역 · 7_의뢰인_예치금 · 7_의뢰인_예치금원장 · 7_개발자_소득내역
#       7_개발자_소득내역_계좌없음
#
# 소득 내역의 계좌 칸은 보여주기만 한다. 대금을 받을 계좌를 등록하고 바꾸는 화면은
# 마이페이지 하나뿐이므로, 이 영역에는 계좌를 고치는 API 가 없다. 영역 8을 쓴다.
#
# 돈의 원본은 Escrow 서버가 가진다. escrows 가 (service_client_id, external_contract_id)
# 로 Komit 계약과 이어지고, 예치·지급·환불 금액과 원장 전표도 그쪽에 있다.
# 여기 API 는 화면에 필요한 만큼만 Escrow 에서 받아와 Komit 의 계약·작업 정보와 엮는다.
# 서버 간 호출은 BACKEND_CONVENTIONS 6.5 를 따른다.
#
# 함께 쓰는 API (다른 영역에 있다):
#   GET /api/users/me                     전 영역 헤더
#   GET /api/contracts/{contractId}       계약과 작업 내용
# =========================================================================== #


class MoneyFlowItemResponse(BaseModel):
    """정산 화면의 거래 한 줄. 소비·예치·소득이 같은 모양을 쓴다."""

    contractId: int = Field(description="관련 계약 식별자.", examples=[130])
    title: str = Field(
        description="무엇에 대한 돈인지.",
        examples=["1차 · 관리자 API에 인증 추가"],
    )
    counterpartName: str = Field(
        description="상대 이름. 의뢰인이 보면 개발자, 개발자가 보면 의뢰인이다.",
        examples=["이준"],
    )
    amount: int = Field(
        description="금액. 원 단위 정수이며 환불처럼 되돌아온 돈은 음수다.",
        examples=[400000],
    )
    occurredAt: str = Field(
        description="이 일이 있었던 시각.",
        examples=["2026-08-30T10:00:00+09:00"],
    )
    state: Literal["DEPOSITED", "CONFIRMED", "AWAITING", "PAID", "REFUNDED"] = Field(
        description=(
            "이 줄의 상태. DEPOSITED는 예치됨, CONFIRMED는 통과됨, AWAITING은 확인 대기, "
            "PAID는 지급 완료, REFUNDED는 환불됨이다."
        ),
        examples=["CONFIRMED"],
    )


class SpendingResponse(BaseModel):
    """의뢰인이 쓴 돈."""

    monthlyTotal: int = Field(
        description="이번 달에 쓴 돈의 합. 환불을 뺀 값이다.",
        examples=[670000],
    )
    items: list[MoneyFlowItemResponse] = Field(
        description="이번 달 결제와 환불. 최근 것이 먼저다.",
    )


class ContractDepositResponse(BaseModel):
    """계약 하나의 예치 현황."""

    contractId: int = Field(description="계약 식별자.", examples=[130])
    projectName: str = Field(description="프로젝트 이름.", examples=["수제 비누 가게"])
    counterpartName: str = Field(description="개발자 이름.", examples=["이준"])
    depositedAmount: int = Field(description="맡긴 돈.", examples=[650000])
    releasedAmount: int = Field(description="이미 나간 돈.", examples=[0])
    remainingAmount: int = Field(
        description="예치금에 남은 돈. 맡긴 돈에서 나간 돈과 환불을 뺀 값이다.",
        examples=[650000],
    )
    items: list[MoneyFlowItemResponse] = Field(
        description="이 계약의 작업별 상태와 금액.",
    )


class DepositsResponse(BaseModel):
    """의뢰인이 맡긴 돈 전체."""

    totalRemaining: int = Field(
        description="모든 계약을 합쳐 예치금에 남은 돈.",
        examples=[650000],
    )
    awaitingCount: int = Field(
        description="아직 확인하지 않은 작업 수. 헤더 배지에도 쓴다.",
        examples=[2],
    )
    contracts: list[ContractDepositResponse] = Field(
        description="예치금이 걸려 있는 계약.",
    )


class LedgerEntryResponse(BaseModel):
    """원장 한 줄. 돈이 들어오고 나간 기록이다."""

    occurredAt: str = Field(
        description="기록된 시각.",
        examples=["2026-08-23T09:40:00+09:00"],
    )
    description: str = Field(
        description="무슨 거래인지.",
        examples=["예치 — 신한은행 ···· 4412"],
    )
    amount: int = Field(
        description="드나든 금액. 들어오면 양수, 나가면 음수다.",
        examples=[650000],
    )
    balance: int = Field(
        description="이 줄이 반영된 뒤의 예치 잔액.",
        examples=[650000],
    )


class ContractLedgerResponse(BaseModel):
    """계약 하나의 예치금 원장."""

    contractId: int = Field(description="계약 식별자.", examples=[130])
    projectName: str = Field(description="프로젝트 이름.", examples=["수제 비누 가게"])
    counterpartName: str = Field(description="개발자 이름.", examples=["이준"])
    depositedAmount: int = Field(description="맡긴 돈.", examples=[650000])
    confirmedAmount: int = Field(
        description="통과가 확정된 작업의 금액 합.",
        examples=[400000],
    )
    remainingAmount: int = Field(
        description="아직 확인이 남은 작업의 금액 합.",
        examples=[250000],
    )
    entries: list[LedgerEntryResponse] = Field(
        description="입금과 지급 기록. 오래된 것이 먼저다.",
    )
    upcoming: list[MoneyFlowItemResponse] = Field(
        description="앞으로 나갈 돈. 작업별로 통과 여부와 금액을 담는다.",
    )


class AccountResponse(BaseModel):
    """1원 인증으로 등록한 계좌. 예치 출금은 회원가입 때 발급한 SSAFY 계좌를 쓴다."""

    accountId: int = Field(description="계좌 식별자.", examples=[21])
    bankName: str = Field(description="은행 이름.", examples=["국민은행"])
    maskedNumber: str = Field(
        description="가려진 계좌번호. 전체 번호는 내려보내지 않는다.",
        examples=["···· 8842"],
    )
    accountHolder: str = Field(description="예금주.", examples=["이준"])


class IncomeResponse(BaseModel):
    """개발자가 받을 돈."""

    expectedAmount: int = Field(
        description="계약이 끝나면 수수료를 빼고 받을 돈의 합. (R-PAY-07)",
        examples=[643500],
    )
    awaitingAmount: int = Field(
        description="아직 확인이 남은 작업의 금액 합.",
        examples=[250000],
    )
    awaitingCount: int = Field(description="확인이 남은 작업 수.", examples=[2])
    items: list[MoneyFlowItemResponse] = Field(
        description="이번 달 거래. 통과·지급 완료·확인 대기가 섞인다.",
    )
    settlementAccount: AccountResponse | None = Field(
        description="돈을 받을 계좌. 등록하지 않았으면 null이다.",
    )



AREA7_TAG = "7 정산"


SPENDING_EXAMPLE = {
    "monthlyTotal": 670000,
    "items": [
        {
            "contractId": 130,
            "title": "개발 대금",
            "counterpartName": "이준",
            "amount": 650000,
            "occurredAt": "2026-08-23T09:40:00+09:00",
            "state": "DEPOSITED",
        },
        {
            "contractId": 120,
            "title": "컨설팅 30분",
            "counterpartName": "이준",
            "amount": 50000,
            "occurredAt": "2026-08-29T14:00:00+09:00",
            "state": "PAID",
        },
        {
            "contractId": 121,
            "title": "컨설팅 30분",
            "counterpartName": "정하",
            "amount": -30000,
            "occurredAt": "2026-08-26T11:00:00+09:00",
            "state": "REFUNDED",
        },
    ],
}

DEPOSIT_ITEMS = [
    {
        "contractId": 130,
        "title": "1차 · 관리자 API에 인증 추가",
        "counterpartName": "이준",
        "amount": 400000,
        "occurredAt": "2026-08-30T10:00:00+09:00",
        "state": "CONFIRMED",
    },
    {
        "contractId": 130,
        "title": "2차 · 회원탈퇴 경로 구현",
        "counterpartName": "이준",
        "amount": 200000,
        "occurredAt": "2026-08-29T11:20:00+09:00",
        "state": "AWAITING",
    },
    {
        "contractId": 130,
        "title": "3차 · 결제 간헐 실패 수정",
        "counterpartName": "이준",
        "amount": 50000,
        "occurredAt": "2026-08-29T11:20:00+09:00",
        "state": "AWAITING",
    },
]

DEPOSITS_EXAMPLE = {
    "totalRemaining": 650000,
    "awaitingCount": 2,
    "contracts": [
        {
            "contractId": 130,
            "projectName": "수제 비누 가게",
            "counterpartName": "이준",
            "depositedAmount": 650000,
            "releasedAmount": 0,
            "remainingAmount": 650000,
            "items": DEPOSIT_ITEMS,
        }
    ],
}

LEDGER_EXAMPLE = {
    "contractId": 130,
    "projectName": "수제 비누 가게",
    "counterpartName": "이준",
    "depositedAmount": 650000,
    "confirmedAmount": 400000,
    "remainingAmount": 250000,
    "entries": [
        {
            "occurredAt": "2026-08-23T09:40:00+09:00",
            "description": "예치 — 신한은행 ···· 4412",
            "amount": 650000,
            "balance": 650000,
        }
    ],
    "upcoming": DEPOSIT_ITEMS,
}

INCOME_EXAMPLE = {
    "expectedAmount": 643500,
    "awaitingAmount": 250000,
    "awaitingCount": 2,
    "items": [
        DEPOSIT_ITEMS[0],
        {
            "contractId": 120,
            "title": "컨설팅 45분",
            "counterpartName": "김미미",
            "amount": 70000,
            "occurredAt": "2026-09-01T10:00:00+09:00",
            "state": "PAID",
        },
        DEPOSIT_ITEMS[1],
        DEPOSIT_ITEMS[2],
    ],
    "settlementAccount": {
        "accountId": 21,
        "bankName": "국민은행",
        "maskedNumber": "···· 8842",
        "accountHolder": "이준",
    },
}


@router.get(
    "/settlements/spending",
    response_model=SpendingResponse,
    tags=[AREA7_TAG],
    responses={
        200: {"content": {"application/json": {"example": SPENDING_EXAMPLE}}},
        **AREA1_UNAUTHORIZED,
        **AREA1_ERRORS,
    },
    summary="소비 내역 조회",
    description=(
        "**화면 — `7_의뢰인_소비내역` › 이번 달 합계와 결제 목록**\n\n"
        "의뢰인이 이번 달에 쓴 돈을 내려준다. 개발 대금 예치, 컨설팅 결제, 환불이 한 "
        "목록에 섞이고 환불은 금액이 음수로 들어간다.\n\n"
        "- **200**: 항상. 이번 달 거래가 없으면 합계가 0이고 `items`가 빈 배열이다\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **500 / INTERNAL_ERROR**: 내역을 만들지 못했을 때"
    ),
)
def get_spending(
    month: Annotated[
        str | None,
        Query(
            description="조회할 달. `YYYY-MM` 형식이며 비우면 이번 달이다.",
            examples=["2026-08"],
        ),
    ] = None,
) -> SpendingResponse:
    return SpendingResponse(**SPENDING_EXAMPLE)


@router.get(
    "/settlements/deposits",
    response_model=DepositsResponse,
    tags=[AREA7_TAG],
    responses={
        200: {"content": {"application/json": {"example": DEPOSITS_EXAMPLE}}},
        **AREA1_UNAUTHORIZED,
        **AREA1_ERRORS,
    },
    summary="예치금 현황 조회",
    description=(
        "**화면 — `7_의뢰인_예치금` › 남은 돈과 계약별 흐름**\n\n"
        "맡긴 돈이 지금 어디에 얼마나 걸려 있는지 계약별로 내려준다. 작업마다 통과됐는지 "
        "확인이 남았는지가 함께 나온다.\n\n"
        "**통과시킨 작업의 대금도 계약이 전부 끝날 때까지 예치금에 남는다.** 일괄정산이라 "
        "통과가 곧 지급이 아니다.\n\n"
        "- **200**: 항상. 걸려 있는 예치금이 없으면 `contracts`가 빈 배열이다\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **500 / INTERNAL_ERROR**: 현황을 만들지 못했을 때"
    ),
)
def get_deposits() -> DepositsResponse:
    return DepositsResponse(**DEPOSITS_EXAMPLE)


@router.get(
    "/contracts/{contractId}/ledger",
    response_model=ContractLedgerResponse,
    tags=[AREA7_TAG],
    responses={
        200: {"content": {"application/json": {"example": LEDGER_EXAMPLE}}},
        **CONTRACT_ERRORS,
    },
    summary="예치금 원장 조회",
    description=(
        "**화면 — `7_의뢰인_예치금원장` › 원장과 앞으로 나갈 돈**\n\n"
        "한 계약에서 돈이 들어오고 나간 기록을 시간 순으로 내려준다. `entries`는 이미 "
        "일어난 일이고, `upcoming`은 앞으로 나갈 작업별 금액이다.\n\n"
        "`depositedAmount`는 `confirmedAmount`와 `remainingAmount`의 합이다. 화면의 "
        "`650,000 = 400,000 + 250,000` 줄이 이 관계를 보여준다.\n\n"
        "- **200**: 계약이 있고 내가 당사자일 때\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **404 / CONTRACT_NOT_FOUND**: `contractId`가 404이거나 아직 예치가 없을 때\n"
        "- **500 / INTERNAL_ERROR**: 원장을 만들지 못했을 때"
    ),
)
def get_contract_ledger(
    contractId: Annotated[
        int,
        Path(
            description="계약 식별자. 404는 없음 재현용 예약값이다.",
            examples=[DEV_CONTRACT_ID],
        ),
    ],
) -> ContractLedgerResponse | JSONResponse:
    if contractId == NOT_FOUND_CONTRACT_ID:
        return _error_response(404, CONTRACT_NOT_FOUND)
    return ContractLedgerResponse(**LEDGER_EXAMPLE)


@router.get(
    "/settlements/income",
    response_model=IncomeResponse,
    tags=[AREA7_TAG],
    responses={
        200: {"content": {"application/json": {"example": INCOME_EXAMPLE}}},
        **AREA1_UNAUTHORIZED,
        **AREA1_ERRORS,
    },
    summary="소득 내역 조회",
    description=(
        "**화면 — `7_개발자_소득내역` · `7_개발자_소득내역_계좌없음` › 받을 돈 · "
        "거래 목록 · 정산 계좌**\n\n"
        "개발자가 계약이 끝나면 받을 돈과, 아직 확인이 남은 작업을 내려준다. 돈을 받을 "
        "계좌도 함께 담아 화면이 한 번에 그린다.\n\n"
        "`settlementAccount`가 null이면 계좌를 아직 등록하지 않은 것이다. 화면은 계좌 "
        "칸을 등록 안내로 바꾸고 버튼 문구를 **마이페이지에서 변경** 대신 "
        "**마이페이지에서 등록**으로 낸다. 같은 API이며 화면만 갈린다.\n\n"
        "**계좌 칸은 보여주기만 한다.** 등록과 변경은 마이페이지에서 하고, 이 화면의 "
        "버튼은 그리로 보낼 뿐이다.\n\n"
        "개발 대금은 작업을 모두 통과한 뒤 한 번에 나간다. 컨설팅 대금은 예약된 세션 "
        "종료 뒤 이의 제기 기간 동안 보관하며, 이의가 없을 때 지급한다. 이의나 세션 예외가 "
        "있으면 해결될 때까지 지급하지 않는다. (R-CON-09~R-CON-12) "
        "`state`가 `PAID`인 줄이 이미 받은 돈이다.\n\n"
        "- **200**: 항상. 개발자 프로필이 없으면 금액이 0이고 목록이 빈 배열이다\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **500 / INTERNAL_ERROR**: 내역을 만들지 못했을 때"
    ),
)
def get_income() -> IncomeResponse:
    return IncomeResponse(**INCOME_EXAMPLE)



# =========================================================================== #
# 영역 8 — 계정 · 공통
#
# 마이페이지에서 계정과 돈 쓰는 수단을 관리한다. 흐름 밖 화면이다.
#
# 화면: 8_마이페이지 · 8_마이페이지_등록전 · 8_계좌등록 · 8_계좌인증
#       8_펼침_은행선택 · 8_아바타메뉴 · 8_회원탈퇴 · 8_알림 · 8_약관
#       8_개발자_내프로필 · 8_내일정
#
# 8_약관 문서와 8_아바타메뉴에는 별도 API가 없다. 약관 문서는 화면의 정적 문서이고,
# 필수 약관 동의 상태는 가입 영역의 /api/settings/terms에서 관리한다. 판매자 약관
# 동의는 개발자 프로필을 만드는 것에 묶여 있다.
#
# 내 프로필은 줄마다 저장하지 않는다. 화면의 저장 버튼 하나가 소개·기술·경력·작업물·
# 가격표를 통째로 보내고, 보낸 내용으로 맞춘다. 파일만 먼저 올려 식별자를 받는다.
#
# 개발자 프로필을 만드는 경로는 그 저장 하나뿐이다. 없으면 만들고 있으면 고친다.
# 만드는 것은 계정을 개발자로 바꾸는 것이 아니라 역할을 하나 더하는 것이며(R-ACC-08),
# 판매자 약관 동의는 만드는 것으로 갈음하고 그 문서는 마이페이지 약관 칸에서 본다.
#
# 파일은 이 서버를 거치지 않는다. uploads 로 보낼 주소를 받고, 화면이 그 주소로 직접
# 보낸 뒤 complete 로 확인받아 fileId 를 받는다. 올리기와 붙이기는 별개이며, 프로필
# 사진은 전용 API 로 즉시 붙고 증빙·작업물은 프로필 저장 요청에 실려 붙는다.
#
# 회원 탈퇴는 지우는 것이 아니다. 사용자 정보는 그대로 두고 삭제 여부 값만 참으로
# 바꾼 뒤 삭제 시각을 적는다. 계약·정산 이력과 메시지, 리뷰가 상대 화면에도 있는
# 기록이라 남겨야 하기 때문이다. 그래서 DELETE 가 아니라 POST 를 쓴다.
#
# 예치 출금 계좌는 회원가입 때 발급한 SSAFY 계좌로 고정한다(R-PAY-09).
# 환불도 그 계좌로 나간다. 별도로 등록·교체한 계좌로 환불하지 않는다(R-PAY-04).
# 이 영역의 accounts는 1원 인증으로 등록하는 계좌이고 사람마다 하나다.
# 개발자가 대금을 받는 데에만 쓴다.
#
# 계좌는 1원 인증으로만 등록한다. 계좌를 넣는 요청과 입금자명 뒤 네 자리를 내는
# 요청이 나뉘며, 확인이 끝나는 순간에만 accounts 에 계좌가 남는다. 1원 이체와
# 확인 호출은 Escrow 서버가 금융망에 하고, Komit 은 확인 건과 계좌 원본을 안전하게
# 보관한다. Escrow는 요청받은 userKey와 계좌번호를 금융망 호출 뒤 보관하지 않는다.
#
# Escrow 서버는 SSAFY 금융망과 Komit 사이의 프록시로 동작한다. 클라이언트에서
# Komit 까지는 아래 API 들의 간소화된 형식을 쓰고, Komit 에서 Escrow 까지는 SSAFY
# 금융망 형식(Header/REC)을 쓴다. 클라이언트는 SSAFY 형식을 알 필요가 없다.
#
# 이 영역은 전부 내 것을 다루므로 인가를 반드시 검증한다. 액세스 토큰을 검증해
# sub 를 꺼내고, 다루려는 자원의 주인이 그 sub 와 같은지 확인한 뒤에 처리한다.
# 대상을 요청에 담지 않는 API 는 sub 가 곧 대상이고, 경로나 본문에 식별자가 있는
# API 는 그 자원의 주인이 sub 와 같은지 확인해 다르면 403 FORBIDDEN 으로 막는다.
#
# 그래서 대상을 담지 않는 API 에는 403 FORBIDDEN 을 두지 않는다. 남의 것을 가리킬
# 자리가 없어 나올 수가 없기 때문이다. 토큰을 고쳐 남의 sub 를 넣는 경우도 서명이
# 깨져 401 에서 끝난다.
#
# 403 은 두 뜻으로 쓴다. FORBIDDEN 은 남의 자원, NO_DEVELOPER_PROFILE 은 개발자
# 프로필이 반드시 필요한 API 에서 프로필이 없는 것이다. 화면 분기는 상태코드가
# 아니라 code 로 가른다.
#
# 함께 쓰는 API (다른 영역에 있다):
#   GET /api/users/me                 전 영역 헤더
#   GET /api/settlements/income       개발자 소득 내역의 계좌 칸
# =========================================================================== #


class RequestAccountVerificationRequest(BaseModel):
    """1원 보내기 요청."""

    bankCodeId: int = Field(
        description=(
            "은행 코드 식별자. 금융망 1원 송금에는 쓰이지 않고, 계좌번호만으로는 은행을 "
            "알 수 없어 화면 표시와 저장에 쓴다."
        ),
        examples=[4],
    )
    accountNumber: str = Field(
        description=(
            "계좌번호. 하이픈 없는 숫자 16자리다. Komit은 원문을 암호화해 보관하고 "
            "화면에는 가린 번호만 보낸다."
        ),
        pattern=r"^\d{16}$",
        examples=["0011012345678842"],
    )
    accountHolder: str = Field(
        description="예금주.", min_length=1, examples=["이준"]
    )


class AccountVerificationResponse(BaseModel):
    """1원을 보낸 뒤의 확인 상태."""

    verificationId: int = Field(
        description=(
            "이번 확인 건의 식별자. 사용자가 입력하는 값이 아니며 화면에도 보이지 "
            "않는다. 확인 API의 경로에 그대로 실어 어느 확인 건인지 가리키는 데만 쓴다. "
            "사용자가 입력하는 것은 입금자명 뒤 네 자리인 `authCode` 하나뿐이다."
        ),
        examples=[77301],
    )
    bankName: str = Field(description="은행 이름.", examples=["국민은행"])
    maskedNumber: str = Field(
        description="가려진 계좌번호. 전체 번호는 내려보내지 않는다.",
        examples=["···· 8842"],
    )
    accountHolder: str = Field(description="예금주.", examples=["이준"])
    expiresAt: str = Field(
        description="이 시각이 지나면 확인이 만료된다. 화면의 남은 시간이 이 값에서 나온다.",
        examples=["2026-09-02T14:12:00+09:00"],
    )


class ConfirmAccountVerificationRequest(BaseModel):
    """입금자명 뒤 4자리 확인 요청."""

    authCode: str = Field(
        description="입금자명 뒤에 붙은 숫자 네 자리. 앞자리 0을 보존하는 문자열이다.",
        pattern=r"^\d{4}$",
        examples=["1234"],
    )

AREA8_TAG = "8 계정 · 공통"

# 영역 8의 모든 엔드포인트가 함께 쓰는 인가 실패 응답.
# 토큰의 주인 것이 아닌 자원을 다루려 했을 때다.
AREA8_FORBIDDEN: dict = {
    403: {
        "model": ErrorResponse,
        "description": "토큰의 주인 것이 아님",
        "content": {"application/json": {"example": FORBIDDEN_EXAMPLE}},
    },
}

ACCOUNT_REJECTED = (
    "ACCOUNT_REJECTED",
    "은행과 계좌번호를 다시 확인해 주세요.",
)
ESCROW_UNAVAILABLE = (
    "ESCROW_UNAVAILABLE",
    "지금은 계좌를 확인할 수 없습니다. 잠시 뒤 다시 시도해 주세요.",
)

VERIFICATION_NOT_FOUND = (
    "VERIFICATION_NOT_FOUND",
    "계좌 확인 건을 찾을 수 없습니다.",
)
NOTIFICATION_NOT_FOUND = ("NOTIFICATION_NOT_FOUND", "알림을 찾을 수 없습니다.")
REVIEW_NOT_FOUND = ("REVIEW_NOT_FOUND", "리뷰를 찾을 수 없습니다.")

VERIFICATION_NOT_FOUND_EXAMPLE = {
    "code": VERIFICATION_NOT_FOUND[0],
    "message": VERIFICATION_NOT_FOUND[1],
}
NOTIFICATION_NOT_FOUND_EXAMPLE = {
    "code": NOTIFICATION_NOT_FOUND[0],
    "message": NOTIFICATION_NOT_FOUND[1],
}
REVIEW_NOT_FOUND_EXAMPLE = {
    "code": REVIEW_NOT_FOUND[0],
    "message": REVIEW_NOT_FOUND[1],
}

# 목에서 실패 응답을 보고 싶을 때 쓰는 값.
REJECTED_ACCOUNT_NUMBER = "0000000000000000"
# R-ACC-09: 회원가입 때 그 회원 몫으로 발급된 계좌번호.
MEMBER_ACCOUNT_NO = "0011012345678842"
ACCOUNT_NO_PATTERN = r"^\d{16}$"
NOT_FOUND_VERIFICATION_ID = 404
NOT_FOUND_NOTIFICATION_ID = 404

ACCOUNT_REJECTED_EXAMPLE = {
    "code": ACCOUNT_REJECTED[0],
    "message": ACCOUNT_REJECTED[1],
}
ESCROW_UNAVAILABLE_EXAMPLE = {
    "code": ESCROW_UNAVAILABLE[0],
    "message": ESCROW_UNAVAILABLE[1],
}

VERIFICATION_EXAMPLE = {
    "verificationId": 77301,
    "bankName": "국민은행",
    "maskedNumber": "···· 8842",
    "accountHolder": "이준",
    "expiresAt": "2026-09-02T14:12:00+09:00",
}


class PaymentMethodResponse(BaseModel):
    """예치에 쓸 결제 수단 한 건."""

    paymentMethodId: int = Field(description="결제 수단 식별자.", examples=[9])
    methodType: Literal["CARD", "REALTIME_TRANSFER"] = Field(
        description=(
            "수단 종류. 지금 서비스는 실시간 계좌이체만 쓰므로 REALTIME_TRANSFER만 "
            "내려간다. CARD는 ERD 코드에 있어 자리만 남겨 둔다."
        ),
        examples=["REALTIME_TRANSFER"],
    )
    maskedNumber: str | None = Field(
        description="가린 계좌번호. 화면에 그대로 보여준다.",
        examples=["한국은행 ···· 0123"],
    )
    isDefault: bool = Field(
        description="기본 수단인지. 예치 화면이 처음에 고르는 것이다.",
        examples=[True],
    )


class PaymentMethodListResponse(BaseModel):
    """회원가입 때 발급한 출금 계좌 한 건을 content에 담는다."""

    content: list[PaymentMethodResponse] = Field(
        description="회원가입 때 발급한 SSAFY 출금 계좌 한 건이다. (R-PAY-09)",
    )


PAYMENT_METHODS = [
    {
        "paymentMethodId": 9,
        "methodType": "REALTIME_TRANSFER",
        "maskedNumber": "한국은행 ···· 0123",
        "isDefault": True,
    }
]


@router.get(
    "/payment-methods",
    response_model=PaymentMethodListResponse,
    tags=[AREA8_TAG],
    responses={
        200: {"content": {"application/json": {"example": {"content": PAYMENT_METHODS}}}},
        **AREA1_UNAUTHORIZED,
        **AREA1_ERRORS,
    },
    summary="결제 수단 조회",
    description=(
        "**화면 — `5_의뢰인_계약예치` › 결제 수단**\n\n"
        "회원가입 때 발급한 SSAFY 입출금 계좌를 유일한 예치 출금 수단으로 내려준다. "
        "`paymentMethodId`는 예치 API에서 같은 계좌를 가리킨다. 다른 계좌 선택이나 "
        "별도 결제창은 없다. (`R-PAY-09`)\n\n"
        "- **200**: 발급 계좌 한 건을 `content`에 담아 반환\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **500 / INTERNAL_ERROR**: 목록을 읽지 못했을 때"
    ),
)
def get_payment_methods() -> PaymentMethodListResponse:
    return PaymentMethodListResponse(content=PAYMENT_METHODS)


class MemberAccountResponse(BaseModel):
    """회원가입 때 발급한 데모 계좌."""

    bankCode: str = Field(
        description="에스크로가 계좌를 연 은행코드. 데모는 001 한 곳뿐이다. (`R-ACC-10`)",
        examples=["001"],
    )
    accountNumber: str = Field(
        description=(
            "발급된 계좌번호 16자리. 은행 앱이 없는 데모라 본인에게는 가리지 않고 준다. "
            "(`R-ACC-14`) 등록이 끝난 계좌를 보여줄 때는 가린 번호를 쓴다."
        ),
        pattern=ACCOUNT_NO_PATTERN,
        examples=[MEMBER_ACCOUNT_NO],
    )


MEMBER_ACCOUNT_EXAMPLE = {"bankCode": "001", "accountNumber": MEMBER_ACCOUNT_NO}


@router.post(
    "/accounts/member-account",
    response_model=MemberAccountResponse,
    tags=[AREA8_TAG],
    responses={
        200: {"content": {"application/json": {"example": MEMBER_ACCOUNT_EXAMPLE}}},
        503: {
            "model": ErrorResponse,
            "description": "에스크로 서버에 연결하지 못함",
            "content": {"application/json": {"example": ESCROW_UNAVAILABLE_EXAMPLE}},
        },
        **AREA1_UNAUTHORIZED,
        **AREA1_ERRORS,
    },
    summary="내 발급 계좌 연동",
    description=(
        "**화면 — `8_계좌등록` › 계좌 연동하기**\n\n"
        "회원가입 때 그 회원 몫으로 발급한 계좌번호를 돌려준다. 은행 앱이 없는 데모에서는 "
        "회원이 자기 계좌번호를 확인할 방법이 없어, 화면이 이 번호로 계좌번호 칸을 채운다. "
        "(`R-ACC-14`)\n\n"
        "**대상 회원을 요청에 담지 않는다.** 액세스 토큰을 검증해 `sub`를 꺼내고 그 사람의 "
        "계좌만 돌려준다. 남의 번호를 가리킬 자리가 없어 403도 나오지 않는다.\n\n"
        "**가입 진행 중에도 부를 수 있다.** 계좌 연동은 가입 절차의 일부다. (`R-ACC-13`)\n\n"
        "**멱등하다.** 아직 발급되지 않았으면 이때 에스크로에 발급을 요청하고, 이미 발급된 "
        "회원에게는 보관 중인 같은 번호를 그대로 준다. 가입을 중간에 그만둔 회원이 다시 "
        "들어와도 계좌를 새로 만들지 않는다. (`R-ACC-13`)\n\n"
        "- **200**: 발급된 계좌번호\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **500 / INTERNAL_ERROR**: 계좌를 읽지 못했을 때\n"
        "- **503 / ESCROW_UNAVAILABLE**: 에스크로 서버에 연결하지 못했을 때"
    ),
)
def issue_member_account() -> MemberAccountResponse:
    return MemberAccountResponse(**MEMBER_ACCOUNT_EXAMPLE)


@router.post(
    "/accounts/verifications",
    response_model=AccountVerificationResponse,
    status_code=201,
    tags=[AREA8_TAG],
    responses={
        201: {"content": {"application/json": {"example": VERIFICATION_EXAMPLE}}},
        400: {
            "model": ErrorResponse,
            "description": "1원을 보내지 못함",
            "content": {"application/json": {"example": ACCOUNT_REJECTED_EXAMPLE}},
        },
        503: {
            "model": ErrorResponse,
            "description": "에스크로 서버에 연결하지 못함",
            "content": {"application/json": {"example": ESCROW_UNAVAILABLE_EXAMPLE}},
        },
        **AREA1_UNAUTHORIZED,
        **AREA1_ERRORS,
    },
    summary="계좌 1원 보내기",
    description=(
        "**화면 — `8_계좌등록` › 1원 보내기**\n\n"
        "**의뢰인과 개발자가 같은 API를 쓴다.** 의뢰인은 `8_마이페이지` › 계좌에서, "
        "개발자는 `7_개발자_소득내역` › 계좌 등록하기·계좌 변경하기에서 들어온다. "
        "사람마다 등록 계좌는 하나이고 개발자가 대금을 받는 데에 쓴다. 예치 출금과 "
        "환불에는 회원가입 때 발급한 SSAFY 계좌를 쓰며, 여기서 등록·교체한 계좌로는 "
        "환불하지 않는다. (`R-PAY-09`, `R-PAY-04`)\n\n"
        "넣은 계좌로 1원을 보내 확인 건을 연다. 실제 이체는 Escrow 서버가 "
        "금융망에 요청한다. Komit Service는 확인을 끝낼 때 계좌를 저장할 수 있도록 "
        "확인 건과 계좌번호를 안전하게 보관하고, Escrow는 요청받은 `userKey`와 "
        "계좌번호를 금융망 호출 뒤 보관하지 않는다.\n\n"
        "이 요청만으로는 계좌가 등록되지 않는다. 입금자명 뒤 4자리를 확인 API에 "
        "내야 등록이 끝난다. `expiresAt`이 지나면 이 확인 건은 쓸 수 없고, 같은 "
        "요청을 다시 보내 새 확인 건을 연다. 화면의 **다시 보내기**가 이 경로다.\n\n"
        "이미 등록한 계좌가 있어도 여기서 막지 않는다. 확인이 끝나는 순간 새 계좌가 "
        "이전 계좌를 대신한다.\n\n"
        "**확인 건은 토큰의 주인에게 달린다.** 액세스 토큰을 검증해 `sub`를 꺼내고 그 "
        "사용자의 확인 건으로 연다. 누구의 계좌인지는 요청에 담지 않는다.\n\n"
        "**403은 나오지 않는다.** 대상을 요청에 담지 않아 남의 것을 가리킬 자리가 "
        "없고, 토큰을 고쳐 남의 `sub`를 넣으면 서명이 깨져 401이 된다.\n\n"
        "- **201**: 1원을 보냈고 확인 건이 열렸을 때\n"
        "- **400 / ACCOUNT_REJECTED**: 은행이 계좌를 받지 않았을 때. 계좌번호나 "
        "은행이 틀렸거나 거래할 수 없는 계좌다\n"
        "- **400 / VERIFICATION_TOO_MANY**: 같은 계좌로 너무 자주 요청했을 때\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **422 / INVALID_REQUEST**: 은행·계좌번호·예금주가 비었거나 계좌번호에 "
        "숫자가 아닌 글자가 섞였을 때\n"
        "- **500 / INTERNAL_ERROR**: 확인 건을 열지 못했을 때\n"
        "- **503 / ESCROW_UNAVAILABLE**: 에스크로 서버에 연결하지 못했을 때\n\n"
        "`accountNumber`가 `0000000000000000`이면 400 `ACCOUNT_REJECTED`를 준다."
    ),
)
def request_account_verification(
    request: RequestAccountVerificationRequest,
) -> AccountVerificationResponse | JSONResponse:
    if request.accountNumber == REJECTED_ACCOUNT_NUMBER:
        return _error_response(400, ACCOUNT_REJECTED)

    return AccountVerificationResponse(
        verificationId=77301,
        bankName="국민은행",
        maskedNumber="···· " + request.accountNumber[-4:],
        accountHolder=request.accountHolder,
        # R-ACC-02: 확인 유효 시간은 요청 시점부터 10분이다.
        expiresAt=(
            datetime.now(timezone(timedelta(hours=9))) + timedelta(minutes=10)
        ).isoformat(timespec="seconds"),
    )


@router.post(
    "/accounts/verifications/{verificationId}/confirm",
    response_model=AccountResponse,
    tags=[AREA8_TAG],
    responses={
        200: {
            "content": {
                "application/json": {"example": INCOME_EXAMPLE["settlementAccount"]}
            }
        },
        **AREA8_FORBIDDEN,
        404: {
            "model": ErrorResponse,
            "description": "확인 건을 찾지 못함",
            "content": {
                "application/json": {"example": VERIFICATION_NOT_FOUND_EXAMPLE}
            },
        },
        **AREA1_UNAUTHORIZED,
        **AREA1_ERRORS,
    },
    summary="계좌 확인 · 등록",
    description=(
        "**화면 — `8_계좌인증` › 계좌 등록하기**\n\n"
        "숫자 네 자리를 내면 확인 건이 끝나고 그 계좌가 등록된다. 등록한 계좌가 "
        "이미 있으면 이 시점에 대체된다.\n\n"
        "계좌번호는 암호화해 보관하고 응답에는 언제나 가려서 내보낸다.\n\n"
        "**R-ACC-02:** 데모에서는 은행 앱이 없어 실제 인증번호를 확인할 수 없다. "
        "Komit이 네 자리 숫자를 에스크로의 `checkAuthCode` API에 보내면, "
        "에스크로는 숫자의 값과 관계없이 확인 성공을 돌려준다.\n\n"
        "**확인 건의 주인이 토큰의 주인인지 검증한다.** `verificationId`의 확인 건을 "
        "연 사람이 `sub`와 같은지 확인한 뒤에 처리하고, 다르면 403으로 막는다. 남의 "
        "확인 건을 끝내 그 계좌를 내 계좌로 등록하는 일이 없어야 한다. 등록되는 계좌는 "
        "언제나 `sub`의 계좌다.\n\n"
        "- **200**: 네 자리 숫자로 계좌가 등록됐을 때\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **403 / FORBIDDEN**: `sub`가 연 확인 건이 아닐 때\n"
        "- **404 / VERIFICATION_NOT_FOUND**: `verificationId`에 해당하는 확인 건이 "
        "없을 때\n"
        "- **422 / INVALID_REQUEST**: `authCode`가 네 자리 숫자가 아닐 때\n"
        "- **500 / INTERNAL_ERROR**: 계좌를 저장하지 못했을 때\n\n"
        "목 서버에서는 확인 건 상태를 저장하지 않는다. `verificationId`가 `404`인 "
        "경우를 제외하면 유효한 네 자리 숫자는 모두 성공한다."
    ),
)
def confirm_account_verification(
    request: ConfirmAccountVerificationRequest,
    verificationId: Annotated[
        int,
        Path(description="1원 보내기에서 받은 확인 건 식별자.", examples=[9012]),
    ],
) -> AccountResponse | JSONResponse:
    if verificationId == NOT_FOUND_VERIFICATION_ID:
        return _error_response(404, VERIFICATION_NOT_FOUND)
    return AccountResponse(**INCOME_EXAMPLE["settlementAccount"])


# --------------------------------------------------------------------------- #
# 마이페이지
# --------------------------------------------------------------------------- #


class MyAccountResponse(BaseModel):
    """마이페이지의 계정 칸."""

    name: str = Field(description="화면에 표시하는 이름.", examples=["김미미"])
    email: str | None = Field(
        description="카카오 계정 이메일. 동의하지 않았으면 null이다.",
        examples=["mimi@kakao.com"],
    )
    profileImageUrl: str | None = Field(
        description=(
            "화면에 그대로 거는 프로필 사진 주소. 저장된 값이 아니라 응답을 만들 때마다 "
            "새로 만든다. 직접 올린 사진이 있으면 그 사진의 주소이고, 없으면 카카오 "
            "사진 주소다. 둘 다 없으면 null이며 이때 화면은 이름 첫 글자를 동그라미에 "
            "넣어 보여준다.\n\n"
            "직접 올린 사진의 주소는 한동안만 쓸 수 있는 임시 주소다. 만료되면 다시 "
            "받아야 하므로 저장해 두고 오래 쓰지 않는다."
        ),
        examples=["https://cdn.komit.example.com/profiles/512.png"],
    )


class MyProjectResponse(BaseModel):
    """마이페이지에 한 줄로 나가는, 내가 등록한 프로젝트."""

    projectId: int = Field(description="프로젝트 식별자.", examples=[12])
    name: str = Field(description="프로젝트 이름.", examples=["수제 비누 가게"])
    latestDiagnosisId: int | None = Field(
        description=(
            "가장 최근 진단서 식별자. 진단 기록에서 골라내는 값이며 아직 진단이 없으면 "
            "null이다."
        ),
        examples=[31],
    )
    latestDiagnosedAt: str | None = Field(
        description=(
            "가장 최근에 진단한 시각. 진단 기록에서 골라내는 값이며 진단이 없으면 "
            "null이다."
        ),
        examples=["2026-08-17T09:12:00+09:00"],
    )
    roomCount: int = Field(
        description="이 프로젝트로 연 방 수. 세어서 만드는 값이다.",
        examples=[4],
    )


class MyDeveloperResponse(BaseModel):
    """마이페이지의 개발자 프로필 칸. 개발자 프로필이 있는 사람에게만 내려간다."""

    developerId: int = Field(
        description="개발자 식별자. 사용자 식별자가 아니다.",
        examples=[3],
    )
    reviewCount: int = Field(
        description="받은 리뷰 수. 세어서 만드는 값이다.",
        examples=[12],
    )
    upcomingSessionCount: int = Field(
        description=(
            "아직 하지 않은 컨설팅 세션 수. 화면의 '잡힌 세션 N건'이며 세어서 만드는 "
            "값이다."
        ),
        examples=[1],
    )


class NotificationSettingResponse(BaseModel):
    """알림 수신 설정."""

    notificationEnabled: bool = Field(
        description=(
            "카카오 알림톡을 받을지. 거짓이면 방 소식과 확인 요청을 보내지 않는다."
        ),
        examples=[True],
    )


class MyPageResponse(BaseModel):
    """마이페이지 한 판."""

    account: MyAccountResponse = Field(description="계정 칸.")
    settlementAccount: AccountResponse | None = Field(
        description=(
            "개발자가 대금을 받는 계좌. 등록하지 않았으면 null이다. 환불은 회원가입 "
            "때 발급한 SSAFY 계좌로 나가므로 이 계좌를 쓰지 않는다. (`R-PAY-04`)"
        ),
    )
    projects: list[MyProjectResponse] = Field(
        description="내가 등록한 프로젝트. 없으면 빈 배열이다.",
    )
    developer: MyDeveloperResponse | None = Field(
        description=(
            "개발자 프로필 칸. 개발자 프로필이 없으면 null이고 화면에서 빠진다."
        ),
    )
    notificationSetting: NotificationSettingResponse = Field(description="알림 설정.")


class UpdateMyNameRequest(BaseModel):
    """이름 바꾸기 요청."""

    name: str = Field(
        min_length=1,
        max_length=20,
        description="새로 쓸 표시 이름. 20자까지 받는다. 카카오 닉네임과 달라도 된다.",
        examples=["김미미"],
    )


class UpdateNotificationSettingRequest(BaseModel):
    """알림 켜고 끄기 요청."""

    notificationEnabled: bool = Field(
        description="참이면 알림톡을 받고, 거짓이면 받지 않는다.",
        examples=[False],
    )


# --------------------------------------------------------------------------- #
# 알림
# --------------------------------------------------------------------------- #


class NotificationResponse(BaseModel):
    """받은 알림 한 줄."""

    notificationId: int = Field(description="알림 식별자.", examples=[901])
    type: Literal["공지", "착수알림", "승인알림", "예약알림톡"] = Field(
        description=(
            "알림 종류. 코드 분류 `NOTIFICATION_TYPE`의 코드값이다. 공지는 운영이 "
            "모두에게 보내는 것이고 나머지는 내 계약에서 생긴 일이다."
        ),
        examples=["승인알림"],
    )
    title: str = Field(
        description="알림 제목. 목록에서 굵게 보이는 줄이다.",
        examples=["이준 개발자가 3차를 끝냈습니다"],
    )
    content: str = Field(
        description=(
            "제목 아래에 붙는 줄. 공지는 본문이 들어가고, 그 밖에는 어느 건인지 "
            "가리키는 줄이 들어간다."
        ),
        examples=["수제 비누 가게 · 회원탈퇴 경로 구현 · 확인 문장 세 줄"],
    )
    targetType: Literal["ANNOUNCEMENT", "CONTRACT", "ISSUE"] = Field(
        description=(
            "'확인하기'를 눌렀을 때 갈 곳의 종류. 코드 분류 "
            "`NOTIFICATION_TARGET_TYPE`의 코드값이다."
        ),
        examples=["CONTRACT"],
    )
    targetId: int | None = Field(
        description=(
            "갈 곳의 식별자. `targetType`에 따라 공지·계약·이슈 중 하나를 가리키며 "
            "갈 곳이 없으면 null이다. 공지 본문은 이 식별자로 공지 화면에서 본다."
        ),
        examples=[130],
    )
    roomId: int | None = Field(
        description=(
            "**방**의 식별자다. 바로 위 `targetId`는 계약·공지·이슈의 식별자이므로 "
            "서로 다른 값이다.\n\n"
            "화면이 계약을 방 안에서 열기 때문에 함께 내려준다. `targetType`이 "
            "`CONTRACT`일 때 그 계약이 달린 방이며(`contracts.room_id`), 그 밖에는 "
            "null이다. 공지는 갈 방이 없고, 이슈는 프로젝트에 방이 여럿이라 하나로 "
            "좁혀지지 않는다."
        ),
        examples=[93],
    )
    isRead: bool = Field(description="읽었는지.", examples=[False])
    createdAt: str = Field(
        description="알림이 생긴 시각.",
        examples=["2026-08-30T14:30:00+09:00"],
    )


class NotificationListResponse(BaseModel):
    """알림 목록."""

    content: list[NotificationResponse] = Field(
        description="알림. 새로운 것이 먼저다. 공지도 이 목록에 섞여 있다.",
    )
    unreadCount: int = Field(
        description=(
            "안 읽은 알림 수. 헤더 종 옆 숫자와 '안 읽음' 뱃지에 쓰며 세어서 만드는 "
            "값이다."
        ),
        examples=[3],
    )
    page: int = Field(description="현재 페이지 번호. 1부터 시작한다.", examples=[1])
    size: int = Field(description="한 페이지에 담긴 최대 항목 수.", examples=[20])
    totalElements: int = Field(description="조건에 맞는 알림 수.", examples=[6])
    totalPages: int = Field(description="전체 페이지 수.", examples=[1])


# --------------------------------------------------------------------------- #
# 회원 탈퇴
# --------------------------------------------------------------------------- #


class WithdrawalBlockerResponse(BaseModel):
    """탈퇴 전에 정리해야 할 것 한 줄."""

    type: Literal["CONTRACT", "DEPOSIT", "INCOME"] = Field(
        description=(
            "정리 대상. CONTRACT는 진행 중인 의뢰, DEPOSIT은 남은 예치금, INCOME은 "
            "아직 받지 못한 소득이다."
        ),
        examples=["CONTRACT"],
    )
    title: str = Field(
        description=(
            "화면에 보여줄 줄. 갈래마다 모양이 정해져 있다.\n\n"
            "- CONTRACT: `{프로젝트 이름} · 진행 중인 의뢰`\n"
            "- DEPOSIT: `예치금 {금액}원` — 내가 의뢰인인 계약의 에스크로 잔액\n"
            "- INCOME: `못 받은 소득 {금액}원` — 내가 개발자인 계약의 에스크로 잔액\n\n"
            "금액은 세 자리마다 쉼표를 찍는다."
        ),
        examples=["수제 비누 가게 · 진행 중인 의뢰"],
    )
    amount: int | None = Field(
        description="금액. 돈이 아닌 항목이면 null이다.",
        examples=[None],
    )
    targetId: int | None = Field(
        description="'이동'을 눌렀을 때 갈 **계약**의 식별자. 갈 곳이 없으면 null이다.",
        examples=[130],
    )
    roomId: int | None = Field(
        description=(
            "**방**의 식별자다. 바로 위 `targetId`는 계약의 식별자이므로 서로 다른 "
            "값이다.\n\n"
            "화면이 계약을 방 안에서 열기 때문에 함께 내려준다. `targetId`가 가리키는 "
            "계약이 달린 방이며(`contracts.room_id`), `targetId`가 null이면 함께 "
            "null이다. 세 갈래 모두 정리하려면 결국 그 계약을 끝내야 하므로 DEPOSIT과 "
            "INCOME도 같은 방을 가리킨다."
        ),
        examples=[93],
    )


class WithdrawalResultResponse(BaseModel):
    """탈퇴 처리 결과.

    계정을 지우는 것이 아니라 삭제 표시를 켜는 것이므로, 무엇이 바뀌었는지를
    돌려준다.
    """

    isDeleted: bool = Field(
        description=(
            "삭제 표시. 탈퇴로 켜진 값이며, 사용자 정보는 지우지 않고 이 표시만 바꾼다."
        ),
        examples=[True],
    )
    deletedAt: str = Field(
        description="삭제 표시를 켠 시각.",
        examples=["2026-09-14T10:05:00+09:00"],
    )


class WithdrawalCheckResponse(BaseModel):
    """탈퇴할 수 있는지."""

    canWithdraw: bool = Field(
        description=(
            "지금 탈퇴할 수 있는지. 저장된 값이 아니라 아래 `blockers`가 비었는지로 "
            "판정해 만드는 값이며, 거짓이면 화면이 탈퇴 버튼을 잠근다."
        ),
        examples=[False],
    )
    blockers: list[WithdrawalBlockerResponse] = Field(
        description=(
            "정리해야 할 것. 계약·예치금·소득을 훑어 그때그때 만드는 목록이라 저장되지 "
            "않는다. 없으면 빈 배열이고 이때 `canWithdraw`가 참이다.\n\n"
            "계약 식별자 순으로 늘어놓고, 한 계약 안에서는 CONTRACT, DEPOSIT, INCOME "
            "순이다. 잔액이 0인 계약은 DEPOSIT·INCOME 줄을 만들지 않는다."
        ),
    )


# --------------------------------------------------------------------------- #
# 개발자 프로필 · 일정
# --------------------------------------------------------------------------- #


class MyPortfolioItemResponse(BaseModel):
    """내 프로필 편집 화면이 쓰는 작업물 하나."""

    portfolioItemId: int = Field(description="작업물 식별자.", examples=[7])
    title: str = Field(description="작업물 제목.", examples=["미미헤어샵 예약"])
    description: str | None = Field(
        description="설명. 적지 않았으면 null이다.",
        examples=["예약이 저장되지 않던 문제를 고쳤습니다."],
    )
    linkUrl: str | None = Field(
        description="배포 사이트나 저장소 주소. 없으면 null이다.",
        examples=["https://hairshop-mimi.com"],
    )
    thumbnailFileId: int | None = Field(
        description=(
            "현재 붙어 있는 썸네일 파일 식별자. 사진이 없으면 null이다. 편집 화면은 "
            "사진을 바꾸지 않을 때 이 값을 저장 요청에 다시 보낸다."
        ),
        examples=[512],
    )
    thumbnailUrl: str | None = Field(
        description=(
            "화면에 그대로 거는 사진 주소. 계정의 `profileImageUrl`과 같은 방식으로, "
            "저장된 값이 아니라 응답을 만들 때마다 새로 만든다. 사진을 올리지 않았으면 "
            "null이다.\n\n"
            "한동안만 쓸 수 있는 임시 주소다. 만료되면 다시 받아야 하므로 저장해 두고 "
            "오래 쓰지 않는다."
        ),
        examples=["https://cdn.komit.example.com/portfolio/512.png"],
    )


class MyCareerResponse(CareerResponse):
    """내 프로필 편집 화면이 쓰는 경력 한 줄."""

    evidenceFileId: int | None = Field(
        description=(
            "확인 전 증빙 파일 식별자. 편집 화면은 증빙을 바꾸지 않을 때 이 값을 저장 "
            "요청에 다시 보낸다. 확인을 마치면 원본을 지우므로 null이다."
        ),
        examples=[512],
    )
    evidenceFileName: str | None = Field(
        description=(
            "확인 전 증빙의 원래 파일명. `evidenceFileId`와 함께 값이 있거나 함께 "
            "null이다. 확인을 마치면 원본을 지우므로 null이다."
        ),
        examples=["재직증명서.pdf"],
    )


class MyDeveloperProfileResponse(BaseModel):
    """내 프로필 편집 화면이 쓰는 값.

    남에게 보이는 개발자 상세와 필드가 겹치지만 유형별 최저가(`prices`)는 담지
    않는다. 편집 화면은 줄 단위 가격표를 고치는 곳이고, 최저가는 그 줄들에서
    계산되는 값이라 함께 내려주면 서로 어긋날 수 있다. 줄 단위 가격표는 상세와
    같은 `priceCatalog`를 그대로 쓴다.
    """

    developerId: int = Field(
        description="개발자 식별자. 사용자 식별자가 아니다.",
        examples=[3],
    )
    name: str = Field(description="화면에 표시하는 이름.", examples=["이준"])
    profileImageUrl: str | None = Field(
        description=(
            "화면에 그대로 거는 프로필 사진 주소. 계정의 `profileImageUrl`과 같은 "
            "값이라 직접 올린 사진이 있으면 그 사진의 주소이고, 없으면 카카오 사진 "
            "주소다. 둘 다 없으면 null이다.\n\n"
            "직접 올린 사진의 주소는 한동안만 쓸 수 있는 임시 주소다. 만료되면 다시 "
            "받아야 하므로 저장해 두고 오래 쓰지 않는다."
        ),
        examples=["https://cdn.komit.example.com/profiles/512.png"],
    )
    intro: str = Field(
        description="개발자가 적은 한 줄 소개.",
        examples=["간단한 상담도 받습니다. 용어부터 풀어서 설명합니다."],
    )
    description: str | None = Field(
        description="개발자 소개 글. 적지 않았으면 null이다.",
        examples=["작은 가게 서비스를 주로 맡습니다."],
    )
    yearsOfExperience: float | None = Field(
        description="경력 연차. 등록하지 않았으면 null이다.",
        examples=[5.0],
    )
    field: CodeResponse | None = Field(
        description="주로 담당하는 개발 분야. 등록하지 않았으면 null이다.",
    )
    activeFrom: str | None = Field(
        description="주로 활동하는 시간대의 시작. 정하지 않았으면 null이다.",
        examples=["10:00"],
    )
    activeTo: str | None = Field(
        description=(
            "주로 활동하는 시간대의 끝. 시작보다 이르면 자정을 넘기는 것으로 본다."
        ),
        examples=["19:00"],
    )
    isCareerVerified: bool = Field(
        description="증빙이 확인된 경력을 하나라도 가지고 있는지.",
        examples=[True],
    )
    devRating: RatingResponse = Field(description="개발력 평점과 리뷰 수.")
    explanatoryRating: RatingResponse = Field(description="설명력 평점과 리뷰 수.")
    techCategories: list[CodeResponse] = Field(
        description="다룰 수 있는 기술. 등록 전이면 빈 배열이다.",
    )
    careers: list[MyCareerResponse] = Field(
        description=(
            "등록한 경력. 확인 전 증빙은 파일 식별자와 이름을 함께 내려주고, 확인을 "
            "마쳐 원본을 지운 경력은 두 값이 모두 null이다."
        ),
    )
    portfolioItems: list[MyPortfolioItemResponse] = Field(
        description="등록한 작업물. 보낸 순서가 화면에 보이는 순서다.",
    )
    priceCatalog: list[PriceCatalogItemResponse] = Field(
        description=(
            "등록한 가격표 전부. 개발자 상세가 내려주는 것과 같은 목록이라 모델도 "
            "그대로 쓴다. 편집 화면은 줄마다 고쳐야 하므로 유형별 최저가가 아니라 "
            "전부 내려준다."
        ),
    )
    reviews: list[ReviewResponse] = Field(
        description="받은 리뷰. 새로운 것이 먼저다. 답글을 여기서 단다.",
    )
    replyCount: int = Field(
        description="답글을 단 리뷰 수. 세어서 만드는 값이다.",
        examples=[1],
    )
    updatedAt: str = Field(
        description="마지막으로 저장한 시각. 화면의 '마지막 저장'에 쓴다.",
        examples=["2026-08-14T18:20:00+09:00"],
    )


DATE_PATTERN = r"^\d{4}-\d{2}-\d{2}$"
TIME_PATTERN = r"^([01]\d|2[0-3]):[0-5]\d$"


class WriteCareerRequest(BaseModel):
    """저장할 경력 한 줄."""

    careerId: int | None = Field(
        default=None,
        description="이미 있는 경력이면 그 식별자, 새로 추가하는 줄이면 null이다.",
        examples=[41],
    )
    company: str = Field(
        max_length=50, description="회사 이름. 50자까지.", examples=["카카오스타일"]
    )
    role: str = Field(
        max_length=100, description="맡았던 일. 100자까지.", examples=["백엔드 · 주문/정산 서비스"]
    )
    startDate: str = Field(
        pattern=DATE_PATTERN, description="시작일. `YYYY-MM-DD`.", examples=["2021-03-01"]
    )
    endDate: str | None = Field(
        pattern=DATE_PATTERN,
        description=(
            "종료일. `YYYY-MM-DD`. 지금도 다니는 중이면 null이다. 시작일보다 빠르면 "
            "422다."
        ),
        examples=["2024-08-31"],
    )
    evidenceFileId: int | None = Field(
        default=None,
        description=(
            "증빙 서류 파일 식별자. 기존 식별자를 보내면 증빙을 유지하고, 새 식별자를 "
            "보내면 교체하며, null이면 증빙을 붙이지 않는다. 확인을 마친 경력은 원본이 "
            "이미 삭제되어 null을 보내도 확인 상태와 확인 시각이 그대로 유지된다."
        ),
        examples=[512],
    )

    @field_validator("startDate", "endDate")
    @classmethod
    def validate_calendar_date(cls, value: str | None) -> str | None:
        if value is None:
            return None
        try:
            datetime.strptime(value, "%Y-%m-%d")
        except ValueError as exception:
            raise PydanticCustomError(
                "invalid_calendar_date", "invalid calendar date"
            ) from exception
        return value

    @field_validator("endDate")
    @classmethod
    def validate_period(cls, value: str | None, info: ValidationInfo) -> str | None:
        start = info.data.get("startDate")
        if value is not None and start is not None and value < start:
            raise PydanticCustomError(
                "career_period", "career end date precedes start date"
            )
        return value


class WritePortfolioItemRequest(BaseModel):
    """저장할 작업물 하나."""

    portfolioItemId: int | None = Field(
        default=None,
        description="이미 있는 작업물이면 그 식별자, 새로 추가하면 null이다.",
        examples=[7],
    )
    title: str = Field(
        max_length=200, description="작업물 제목. 200자까지.", examples=["미미헤어샵 예약"]
    )
    description: str | None = Field(
        max_length=500,
        description="설명. 500자까지 적을 수 있고, 적지 않으면 null이다.",
        examples=["예약이 저장되지 않던 문제를 고쳤습니다."],
    )
    linkUrl: str | None = Field(
        max_length=2048,
        description="배포 사이트나 저장소 주소. 2048자까지. 없으면 null이다.",
        examples=["https://hairshop-mimi.com"],
    )
    thumbnailFileId: int | None = Field(
        description=(
            "올려 둔 사진의 파일 식별자. 조회에서 받은 기존 식별자를 보내면 사진을 "
            "유지하고, 새 식별자를 보내면 교체하며, null을 보내면 사진을 제거한다. "
            "응답에는 이 식별자와 바로 걸 수 있는 `thumbnailUrl`이 함께 담긴다. "
            "올리기를 마치지 않았거나 남이 올린 파일이면 400 `FILE_NOT_ATTACHABLE`이다."
        ),
        examples=[512],
    )


class WritePriceRequest(BaseModel):
    """저장할 가격표 한 줄."""

    priceId: int | None = Field(
        default=None,
        description="이미 있는 줄이면 그 식별자, 새로 추가하면 null이다.",
        examples=[61],
    )
    taskType: Literal["CONSULT", "DEVELOP"] = Field(
        description="작업 종류. 코드 분류 `TASK_TYPE`의 코드값이다.",
        examples=["DEVELOP"],
    )
    taskName: str = Field(
        max_length=50, description="작업 이름. 50자까지.", examples=["로그인 붙이기"]
    )
    price: int = Field(ge=1, description="가격. 원 단위 정수다. 1 이상.", examples=[400000])
    periodDays: int | None = Field(
        ge=1,
        le=32767,
        description="예상 작업 기간(일). 1 이상 32767 이하. 컨설팅 세션이면 비운다.",
        examples=[4],
    )


class WriteDeveloperProfileRequest(BaseModel):
    """내 프로필 저장 요청. 화면의 저장 버튼 하나가 이 요청 하나를 보낸다."""

    intro: str = Field(
        min_length=1,
        max_length=100,
        description="한 줄 소개. 1자 이상 100자까지.",
        examples=["간단한 상담도 받습니다. 용어부터 풀어서 설명합니다."],
    )
    description: str | None = Field(
        max_length=1000,
        description="소개 글. 1000자까지.",
        examples=["AI로 만든 걸 실제로 돌아가게 만드는 일을 주로 합니다."],
    )
    fieldCodeId: int | None = Field(
        description=(
            "주로 담당하는 개발 분야 코드 식별자. 코드 분류 `DEV_FIELD`의 항목이다. "
            "없는 코드면 404 `CODE_NOT_FOUND`다."
        ),
        examples=[42],
    )
    yearsOfExperience: float | None = Field(
        ge=0,
        le=99.9,
        description="경력 연차. 0 이상 99.9 이하, 소수점 한 자리.",
        examples=[5.0],
    )
    activeFrom: str | None = Field(
        pattern=TIME_PATTERN,
        description="주로 활동하는 시간대의 시작. `HH:MM`.",
        examples=["10:00"],
    )
    activeTo: str | None = Field(
        pattern=TIME_PATTERN,
        description="주로 활동하는 시간대의 끝. `HH:MM`.",
        examples=["19:00"],
    )
    techCodeIds: list[int] = Field(
        description=(
            "다룰 수 있는 기술의 코드 식별자. 코드 분류 `TECH_CATEGORY`의 항목이며 보낸 "
            "목록이 그대로 저장된다. 없는 코드가 섞여 있으면 404 `CODE_NOT_FOUND`다."
        ),
        examples=[[21, 22, 23]],
    )
    careers: list[WriteCareerRequest] = Field(
        description=(
            "경력 전부. 보낸 목록에 없는 기존 경력은 지운 것으로 본다. 확인이 끝난 "
            "경력은 증빙과 표시 내용이 어긋나지 않도록 삭제 외의 수정을 반영하지 않는다."
        ),
    )
    portfolioItems: list[WritePortfolioItemRequest] = Field(
        description=(
            "작업물 전부. 보낸 목록에 없는 기존 작업물은 지운 것으로 본다. 새로 추가한 "
            "줄은 뒤에 붙으며, 이미 등록된 줄의 순서는 보낸 순서로 바뀌지 않는다."
        ),
    )
    prices: list[WritePriceRequest] = Field(
        description="가격표 전부. 보낸 목록에 없는 기존 줄은 지운 것으로 본다.",
    )


class WriteReviewReplyRequest(BaseModel):
    """리뷰 답글 요청."""

    reply: str = Field(
        min_length=1,
        max_length=1000,
        description="답글 내용. 1000자까지 받는다.",
        examples=["감사합니다. 도메인 만료 전에 미리 알려드리겠습니다."],
    )


class MySessionResponse(BaseModel):
    """내 일정에 한 줄로 나가는 컨설팅 세션."""

    sessionId: int = Field(description="컨설팅 세션 식별자.", examples=[55])
    contractId: int = Field(description="이 세션이 달린 **계약**의 식별자.", examples=[120])
    roomId: int = Field(
        description=(
            "**방**의 식별자다. 바로 위 `contractId`는 계약의 식별자이므로 서로 다른 "
            "값이다.\n\n"
            "화면이 세션 방과 대화를 방 안에서 열기 때문에 함께 내려준다. "
            "`contractId`가 가리키는 계약이 달린 방이다(`contracts.room_id`). "
            "세션은 반드시 계약에 달리고 계약은 반드시 방에 달리므로 null이 없다."
        ),
        examples=[92],
    )
    role: Literal["CLIENT", "DEVELOPER"] = Field(
        description=(
            "이 계약에서 내가 맡은 쪽. CLIENT는 의뢰인, DEVELOPER는 개발자다. "
            "한 사용자가 계약마다 다른 역할로 참여할 수 있으므로 세션마다 내려준다."
        ),
        examples=["CLIENT"],
    )
    counterpartName: str = Field(
        description=(
            "이 세션에서 상대방의 이름. 내가 의뢰인이면 개발자 이름이고, 내가 "
            "개발자면 의뢰인 이름이다."
        ),
        examples=["이준"],
    )
    scheduledAt: str = Field(
        description=(
            "확정된 예약의 일시. 세션이 아니라 그 세션에 잡힌 CONFIRMED 예약이 "
            "가진 값이다."
        ),
        examples=["2026-08-22T15:00:00+09:00"],
    )
    durationMinutes: int = Field(description="세션 시간(분).", examples=[30])
    price: int = Field(description="세션 금액. 원 단위 정수다.", examples=[50000])
    endedAt: str | None = Field(
        description="세션을 마친 시각. 아직 안 했으면 null이다.",
        examples=[None],
    )
    followUpUntil: str | None = Field(
        description="세션이 끝난 뒤 질문을 받는 기한. 아직 안 한 세션이면 null이다.",
        examples=[None],
    )


class MySessionListResponse(BaseModel):
    """내 일정."""

    upcoming: list[MySessionResponse] = Field(
        description="아직 하지 않은 세션. 가까운 것이 먼저다.",
    )
    past: list[MySessionResponse] = Field(
        description="이미 마친 세션. 최근 것이 먼저다.",
    )


class CreateUploadRequest(BaseModel):
    """올릴 파일을 미리 알리는 요청. 파일 자체는 아직 보내지 않는다."""

    originalName: str = Field(
        min_length=1,
        max_length=255,
        description="올릴 파일 이름. 화면에 보여줄 이름으로 그대로 남는다.",
        examples=["재직증명서.pdf"],
    )
    contentType: str = Field(
        min_length=1,
        max_length=100,
        description=(
            "파일 종류. 올릴 때 같은 값을 `Content-Type`으로 보내야 하며, 다르면 "
            "완료 요청에서 거절된다."
        ),
        examples=["application/pdf"],
    )
    sizeBytes: int = Field(
        ge=1,
        description="파일 크기(바이트). 완료할 때 실제 크기와 맞는지 본다.",
        examples=[284193],
    )
    purpose: Literal[
        "PROFILE_IMAGE",
        "CAREER_EVIDENCE",
        "PORTFOLIO_IMAGE",
        "SIGNATURE",
        "DELIVERABLE",
        "SESSION_RECORDING",
        "REPORT_EVIDENCE",
    ] = Field(
        description=(
            "이 파일을 어디에 쓸지. 쓰임에 따라 받는 형식과 크기가 다르고 보관 기간도 "
            "다르다.\n\n"
            "**이 값은 보관하지 않는다.** 형식·크기 한도를 고르고 파일을 둘 자리를 "
            "정하는 데까지만 쓴다. 그래서 올린 뒤에는 어느 쓰임으로 올렸는지 따지지 "
            "않으며, 증빙 서류로 올린 파일을 작업물 사진에 붙여도 막지 않는다."
        ),
        examples=["CAREER_EVIDENCE"],
    )


class UploadTargetResponse(BaseModel):
    """파일을 올릴 곳. 이 주소로 파일을 직접 보낸다."""

    uploadId: int = Field(
        description=(
            "이번 업로드 건의 식별자. 다 올린 뒤 완료 요청의 경로에 그대로 쓴다. "
            "완료되면 이 값이 그대로 파일 식별자가 된다."
        ),
        examples=[512],
    )
    uploadUrl: str = Field(
        description=(
            "파일을 보낼 주소. 이 주소로 `PUT` 한 번을 보내며 본문이 파일 내용이다. "
            "한동안만 쓸 수 있고 이 서버를 거치지 않는다."
        ),
        examples=["https://storage.komit.example.com/komit/evidence/9f3c1a.pdf?..."],
    )
    expiresAt: str = Field(
        description=(
            "이 주소를 쓸 수 있는 기한. 지나면 올릴 수 없고 처음부터 다시 받아야 한다."
        ),
        examples=["2026-08-14T18:17:00+09:00"],
    )
    headers: dict[str, str] = Field(
        description=(
            "올릴 때 함께 보내야 하는 헤더. 여기 적힌 `Content-Type`을 그대로 보내지 "
            "않으면 완료 요청에서 거절된다."
        ),
        examples=[{"Content-Type": "application/pdf"}],
    )


class UploadedFileResponse(BaseModel):
    """올리기를 마친 파일 하나."""

    fileId: int = Field(description="파일 식별자.", examples=[512])
    originalName: str = Field(
        description="올린 파일 이름.",
        examples=["재직증명서.pdf"],
    )
    contentType: str | None = Field(
        description="파일 종류.",
        examples=["application/pdf"],
    )
    sizeBytes: int = Field(description="파일 크기(바이트).", examples=[284193])
    verifiedAt: str = Field(
        description="올라온 파일을 확인한 시각.",
        examples=["2026-08-14T18:12:00+09:00"],
    )


class UpdateProfileImageRequest(BaseModel):
    """프로필 사진 바꾸기 요청. 파일이 아니라 올려 둔 파일의 식별자를 보낸다."""

    fileId: int = Field(
        description=(
            "프로필 사진으로 걸 파일 식별자. 올리기 완료까지 마친 내 파일이어야 한다."
        ),
        examples=[512],
    )


# 목에서 실패 응답을 보고 싶을 때 쓰는 값.
BLOCKED_WITHDRAWAL = 409
NOT_FOUND_REVIEW_ID = 404

WITHDRAWAL_BLOCKED = (
    "WITHDRAWAL_BLOCKED",
    "진행 중인 의뢰나 남은 돈이 있어 탈퇴할 수 없습니다.",
)
NO_DEVELOPER_PROFILE = (
    "NO_DEVELOPER_PROFILE",
    "개발자 프로필이 없습니다.",
)
UNSUPPORTED_FILE_TYPE = ("UNSUPPORTED_FILE_TYPE", "올릴 수 없는 형식입니다.")
UPLOAD_NOT_FOUND = ("UPLOAD_NOT_FOUND", "업로드 건을 찾을 수 없습니다.")
UPLOAD_NOT_COMPLETED = (
    "UPLOAD_NOT_COMPLETED",
    "아직 파일이 올라오지 않았습니다. 올리기를 마친 뒤 다시 시도해 주세요.",
)
UPLOAD_SIZE_MISMATCH = (
    "UPLOAD_SIZE_MISMATCH",
    "올린 파일이 알린 것과 다릅니다. 처음부터 다시 올려 주세요.",
)
UPLOAD_TYPE_MISMATCH = (
    "UPLOAD_TYPE_MISMATCH",
    "올린 파일의 형식이 알린 것과 다릅니다. 처음부터 다시 올려 주세요.",
)
UPLOAD_EXPIRED = (
    "UPLOAD_EXPIRED",
    "올릴 수 있는 시간이 지났습니다. 처음부터 다시 올려 주세요.",
)
FILE_NOT_ATTACHABLE = ("FILE_NOT_ATTACHABLE", "쓸 수 없는 파일입니다.")
CODE_NOT_FOUND = ("CODE_NOT_FOUND", "없는 코드입니다.")

WITHDRAWAL_BLOCKED_EXAMPLE = {
    "code": WITHDRAWAL_BLOCKED[0],
    "message": WITHDRAWAL_BLOCKED[1],
}
NO_DEVELOPER_PROFILE_EXAMPLE = {
    "code": NO_DEVELOPER_PROFILE[0],
    "message": NO_DEVELOPER_PROFILE[1],
}
UPLOAD_NOT_FOUND_EXAMPLE = {
    "code": UPLOAD_NOT_FOUND[0],
    "message": UPLOAD_NOT_FOUND[1],
}
UNSUPPORTED_FILE_TYPE_EXAMPLE = {
    "code": UNSUPPORTED_FILE_TYPE[0],
    "message": UNSUPPORTED_FILE_TYPE[1],
}
UPLOAD_NOT_COMPLETED_EXAMPLE = {
    "code": UPLOAD_NOT_COMPLETED[0],
    "message": UPLOAD_NOT_COMPLETED[1],
}
UPLOAD_SIZE_MISMATCH_EXAMPLE = {
    "code": UPLOAD_SIZE_MISMATCH[0],
    "message": UPLOAD_SIZE_MISMATCH[1],
}
UPLOAD_TYPE_MISMATCH_EXAMPLE = {
    "code": UPLOAD_TYPE_MISMATCH[0],
    "message": UPLOAD_TYPE_MISMATCH[1],
}
UPLOAD_EXPIRED_EXAMPLE = {
    "code": UPLOAD_EXPIRED[0],
    "message": UPLOAD_EXPIRED[1],
}

# R-FILE-01, R-FILE-02. 목과 실서버가 같은 정책 판정을 하도록 한 곳에 둔다.
# 가장 큰 파일인 세션 녹음에 맞춘다. 24kbps 고정에 90분이면 14MiB 안팎이다. (R-CON-18)
MAX_UPLOAD_BYTES = 30 * 1024 * 1024
UPLOAD_CONTENT_TYPES = {
    "PROFILE_IMAGE": {"image/png", "image/jpeg", "image/webp"},
    "CAREER_EVIDENCE": {"application/pdf", "image/png", "image/jpeg"},
    "PORTFOLIO_IMAGE": {"image/png", "image/jpeg", "image/webp"},
    "SIGNATURE": {"image/png"},
    "DELIVERABLE": {
        "application/zip",
        "application/x-zip-compressed",
        "application/pdf",
        "application/octet-stream",
    },
    "SESSION_RECORDING": {"audio/webm", "audio/mp4"},
    "REPORT_EVIDENCE": {"application/pdf", "image/png", "image/jpeg", "image/webp"},
}
FILE_NOT_ATTACHABLE_EXAMPLE = {
    "code": FILE_NOT_ATTACHABLE[0],
    "message": FILE_NOT_ATTACHABLE[1],
}
CODE_NOT_FOUND_EXAMPLE = {
    "code": CODE_NOT_FOUND[0],
    "message": CODE_NOT_FOUND[1],
}

# 목에서 실패 응답을 보고 싶을 때 쓰는 값.
NOT_FOUND_UPLOAD_ID = 404
NOT_COMPLETED_UPLOAD_ID = 400
NOT_ATTACHABLE_FILE_ID = 400
NOT_FOUND_CODE_ID = 404

# 스키마로 거를 수 없는 검증 실패 문구. 실서버 messages.properties 의 komit.careerPeriod 와 같다.
CAREER_PERIOD_REASON = "시작일보다 빠를 수 없습니다."

# 개발자 프로필이 있어야 부를 수 있는 API 가 함께 쓰는 실패 응답.
# 대상을 요청에 담지 않아 남의 것을 가리킬 자리가 없으므로 FORBIDDEN 은 싣지 않는다.
AREA8_NO_DEVELOPER_PROFILE: dict = {
    403: {
        "model": ErrorResponse,
        "description": "개발자 프로필이 없음",
        "content": {
            "application/json": {"example": NO_DEVELOPER_PROFILE_EXAMPLE}
        },
    },
}

MY_PAGE_EXAMPLE = {
    "account": {
        "name": "김미미",
        "email": "mimi@kakao.com",
        "profileImageUrl": "https://k.kakaocdn.net/dn/profile/mimi.jpg",
    },
    "settlementAccount": {
        "accountId": 22,
        "bankName": "신한은행",
        "maskedNumber": "···· 4412",
        "accountHolder": "김미미",
    },
    "projects": [
        {
            "projectId": 12,
            "name": "수제 비누 가게",
            "latestDiagnosisId": 31,
            "latestDiagnosedAt": "2026-08-17T09:12:00+09:00",
            "roomCount": 4,
        },
        {
            "projectId": 11,
            "name": "미미헤어샵 예약",
            "latestDiagnosisId": 18,
            "latestDiagnosedAt": "2026-07-02T11:40:00+09:00",
            "roomCount": 2,
        },
        {
            "projectId": 14,
            "name": "사내 재고 관리",
            "latestDiagnosisId": None,
            "latestDiagnosedAt": None,
            "roomCount": 1,
        },
    ],
    "developer": None,
    "notificationSetting": {"notificationEnabled": True},
}

MY_PAGE_EMPTY_EXAMPLE = {
    **MY_PAGE_EXAMPLE,
    "settlementAccount": None,
}

NAME_INVALID_EXAMPLE = {
    "code": INVALID_REQUEST[0],
    "message": INVALID_REQUEST[1],
    "errors": [{"field": "name", "reason": "20자 이하여야 합니다."}],
}

NOTIFICATION_LIST_EXAMPLE = {
    "content": [
        {
            "notificationId": 940,
            "type": "공지",
            "title": "9월 1일부터 정산 지급 주기가 바뀝니다",
            # 본문에 적는 영업일 수는 R-PAY-02 를 따른다. 단축안은 아직 적용
            # 시점이 정해지지 않아 공지로 내보내지 않는다.
            "content": (
                "작업 전 건이 통과 확정되면 3영업일 이내에 지급합니다. 통과한 작업의 "
                "대금도 계약이 끝날 때까지 예치금에 남아 있다가 한 번에 나갑니다."
            ),
            "targetType": "ANNOUNCEMENT",
            "targetId": 7,
            "roomId": None,
            "isRead": False,
            "createdAt": "2026-08-28T10:00:00+09:00",
        },
        {
            "notificationId": 951,
            "type": "승인알림",
            "title": "이준 개발자가 3차를 끝냈습니다",
            "content": "수제 비누 가게 · 회원탈퇴 경로 구현 · 확인 문장 세 줄",
            "targetType": "CONTRACT",
            "targetId": 130,
            "roomId": 93,
            "isRead": False,
            "createdAt": "2026-08-30T14:30:00+09:00",
        },
        {
            "notificationId": 950,
            "type": "착수알림",
            "title": "한서 개발자가 계약서를 보냈습니다",
            "content": "수제 비누 가게 · 650,000원",
            "targetType": "CONTRACT",
            "targetId": 131,
            "roomId": 105,
            "isRead": False,
            "createdAt": "2026-08-30T11:02:00+09:00",
        },
        {
            "notificationId": 930,
            "type": "예약알림톡",
            "title": "컨설팅 세션이 30분 뒤에 시작합니다",
            "content": "이준 개발자 · 30분 화상",
            "targetType": "CONTRACT",
            "targetId": 120,
            "roomId": 92,
            "isRead": True,
            "createdAt": "2026-08-29T13:30:00+09:00",
        },
        {
            "notificationId": 910,
            "type": "승인알림",
            "title": "'관리자 API에 인증 추가'가 통과 확정됐습니다",
            "content": "수제 비누 가게 · 이준 개발자",
            "targetType": "CONTRACT",
            "targetId": 130,
            "roomId": 93,
            "isRead": True,
            "createdAt": "2026-08-19T09:00:00+09:00",
        },
    ],
    "unreadCount": 3,
    "page": 1,
    "size": 20,
    "totalElements": 5,
    "totalPages": 1,
}

WITHDRAWAL_CHECK_EXAMPLE = {
    "canWithdraw": False,
    "blockers": [
        {
            "type": "CONTRACT",
            "title": "수제 비누 가게 · 진행 중인 의뢰",
            "amount": None,
            "targetId": 130,
            "roomId": 93,
        },
        {
            "type": "DEPOSIT",
            "title": "예치금 650,000원",
            "amount": 650000,
            "targetId": 130,
            "roomId": 93,
        },
    ],
}

WITHDRAWAL_RESULT_EXAMPLE = {
    "isDeleted": True,
    "deletedAt": "2026-09-14T10:05:00+09:00",
}

MY_DEVELOPER_PROFILE_EXAMPLE = {
    "developerId": 3,
    "name": "이준",
    "profileImageUrl": None,
    "intro": "간단한 상담도 받습니다. 용어부터 풀어서 설명합니다.",
    "yearsOfExperience": 5.0,
    "field": {"codeId": 42, "codeName": "DB 연결"},
    "isCareerVerified": True,
    "devRating": {"score": 4.8, "reviewCount": 2},
    "explanatoryRating": {"score": 4.9, "reviewCount": 2},
    "techCategories": [
        {"codeId": 23, "codeName": "TypeScript"},
        {"codeId": 21, "codeName": "Next.js"},
        {"codeId": 22, "codeName": "Supabase"},
    ],
    "description": (
        "AI로 만든 걸 실제로 돌아가게 만드는 일을 주로 합니다. 지난 1년 동안 소상공인 "
        "웹 서비스 14건을 올렸고, 그중 13건이 Komit에서 만난 분들이었습니다."
    ),
    "activeFrom": "10:00",
    "activeTo": "19:00",
    "careers": [
        {
            "careerId": 41,
            "company": "카카오스타일",
            "role": "백엔드 · 주문/정산 서비스",
            "startDate": "2021-03-01",
            "endDate": "2024-08-31",
            "isVerified": True,
            "verifiedAt": "2026-03-04T10:00:00+09:00",
            "evidenceFileId": None,
            "evidenceFileName": None,
        },
        {
            "careerId": 42,
            "company": "당근마켓",
            "role": "백엔드 인턴 · 검색 색인",
            "startDate": "2020-06-01",
            "endDate": "2020-12-31",
            "isVerified": True,
            "verifiedAt": "2026-03-04T10:00:00+09:00",
            "evidenceFileId": None,
            "evidenceFileName": None,
        },
        {
            "careerId": 43,
            "company": "프리랜서",
            "role": "소상공인 웹 서비스 14건 · Komit 13건",
            "startDate": "2024-09-01",
            "endDate": None,
            "isVerified": False,
            "verifiedAt": None,
            "evidenceFileId": 514,
            "evidenceFileName": "프리랜서_경력증명서.pdf",
        },
    ],
    "portfolioItems": [
        {
            "portfolioItemId": 7,
            "title": "미미헤어샵 예약",
            "description": "예약이 저장되지 않던 문제를 고쳤습니다.",
            "linkUrl": "https://hairshop-mimi.com",
            "thumbnailFileId": 512,
            "thumbnailUrl": "https://cdn.komit.example.com/portfolio/512.png",
        },
        {
            "portfolioItemId": 8,
            "title": "동네 필라테스 수업",
            "description": "회원가입 없이 수업을 예약할 수 있게 했습니다.",
            "linkUrl": "https://pilates-onnuri.kr",
            "thumbnailFileId": 513,
            "thumbnailUrl": "https://cdn.komit.example.com/portfolio/513.png",
        },
        {
            "portfolioItemId": 9,
            "title": "사내 재고 관리",
            "description": "엑셀로 관리하던 재고를 DB로 옮겼습니다.",
            "linkUrl": None,
            "thumbnailFileId": None,
            "thumbnailUrl": None,
        },
    ],
    "priceCatalog": PRICE_CATALOG,
    "reviews": [
        {
            "reviewId": 88,
            "clientName": "김O미",
            "contractType": "DEVELOPMENT",
            "devScore": 5,
            "expScore": 5,
            "content": (
                "예약이 저장되지 않던 문제를 고쳐주셨고, 왜 그랬는지도 제가 알아듣게 "
                "설명해주셨습니다."
            ),
            "reply": "감사합니다. 도메인 만료 전에 미리 알려드리겠습니다.",
            "createdAt": "2026-08-19T14:20:00+09:00",
        },
        {
            "reviewId": 84,
            "clientName": "최O진",
            "contractType": "CONSULTING",
            "devScore": 4,
            "expScore": 5,
            "content": "일정이 하루 밀렸지만 미리 알려주셔서 괜찮았습니다.",
            "reply": None,
            "createdAt": "2026-08-02T09:10:00+09:00",
        },
    ],
    "replyCount": 1,
    "updatedAt": "2026-08-14T18:20:00+09:00",
}

MY_SESSION_LIST_EXAMPLE = {
    "upcoming": [
        {
            "sessionId": 56,
            "contractId": 120,
            "roomId": 92,
            "role": "CLIENT",
            "counterpartName": "이준",
            "scheduledAt": "2026-08-22T15:00:00+09:00",
            "durationMinutes": 30,
            "price": 50000,
            "endedAt": None,
            "followUpUntil": None,
        }
    ],
    "past": [
        {
            "sessionId": 55,
            "contractId": 120,
            "roomId": 92,
            "role": "CLIENT",
            "counterpartName": "이준",
            "scheduledAt": "2026-08-15T14:00:00+09:00",
            "durationMinutes": 30,
            "price": 50000,
            "endedAt": "2026-08-15T14:31:00+09:00",
            "followUpUntil": "2026-08-22T23:59:59+09:00",
        }
    ],
}

UPLOADED_FILE_EXAMPLE = {
    "fileId": 512,
    "originalName": "재직증명서.pdf",
    "contentType": "application/pdf",
    "sizeBytes": 284193,
    "verifiedAt": "2026-08-14T18:12:00+09:00",
}

UPLOAD_TARGET_EXAMPLE = {
    "uploadId": 512,
    "uploadUrl": (
        "https://storage.komit.example.com/komit/evidence/9f3c1a2b.pdf"
        "?X-Amz-Expires=300&X-Amz-Signature=6f1d..."
    ),
    "expiresAt": "2026-08-14T18:17:00+09:00",
    "headers": {"Content-Type": "application/pdf"},
}


@router.get(
    "/mypage",
    response_model=MyPageResponse,
    tags=[AREA8_TAG],
    responses={
        200: {
            "content": {
                "application/json": {
                    "examples": {
                        "registered": {
                            "summary": "계좌를 등록한 사람",
                            "value": MY_PAGE_EXAMPLE,
                        },
                        "empty": {
                            "summary": "아직 계좌를 등록하지 않은 사람",
                            "value": MY_PAGE_EMPTY_EXAMPLE,
                        },
                    }
                }
            }
        },
        **AREA1_UNAUTHORIZED,
        **AREA1_ERRORS,
    },
    summary="마이페이지 조회",
    description=(
        "**화면 — `8_마이페이지` · `8_마이페이지_등록전`**\n\n"
        "마이페이지 한 판을 한 번에 내려준다. 계정, 계좌, 등록한 프로젝트, 개발자 "
        "프로필 요약, 알림 설정이 들어간다.\n\n"
        "`settlementAccount`가 null이면 화면이 그 칸을 등록 안내로 바꾼다. 같은 "
        "API이며 화면만 갈린다.\n\n"
        "`developer`는 개발자 프로필이 없는 사람에게 null로 나간다. 이때 화면은 개발자 "
        "프로필 칸을 감추지 않고 「프로필 생성」을 보여준다. 일정은 의뢰인으로 참여한 "
        "컨설팅도 포함하므로 개발자 프로필 여부와 관계없이 내 일정 화면으로 갈 수 있다. "
        "일정 자체는 `GET /api/users/me/sessions`에서 조회한다.\n\n"
        "약관 칸은 이 응답에 없다. 약관 문서와 개정일은 서버가 들고 있지 않고 화면이 "
        "가진 정적 문서이며, 동의 여부도 이 응답에 담지 않는다.\n\n"
        "**403은 나오지 않는다.** 대상을 요청에 담지 않아 남의 것을 가리킬 자리가 "
        "없고, 토큰을 고쳐 남의 `sub`를 넣으면 서명이 깨져 401이 된다.\n\n"
        "- **200**: 항상\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **500 / INTERNAL_ERROR**: 마이페이지를 만들지 못했을 때"
    ),
)
def get_my_page() -> MyPageResponse:
    return MyPageResponse(**MY_PAGE_EXAMPLE)


@router.patch(
    "/users/me",
    response_model=MyAccountResponse,
    tags=[AREA8_TAG],
    responses={
        200: {
            "content": {"application/json": {"example": MY_PAGE_EXAMPLE["account"]}}
        },
        **AREA1_UNAUTHORIZED,
        **AREA1_ERRORS,
        422: {
            "model": ValidationErrorResponse,
            "description": "요청 스키마 유효성 검증 실패",
            "content": {"application/json": {"example": NAME_INVALID_EXAMPLE}},
        },
    },
    summary="이름 바꾸기",
    description=(
        "**화면 — `8_마이페이지` › 계정 › 이름 바꾸기**\n\n"
        "화면에 표시하는 이름을 바꾼다. 카카오 닉네임으로 시작하지만 이후에는 따로 "
        "간다 — 카카오에서 닉네임을 바꿔도 여기 이름은 그대로다.\n\n"
        "이미 남긴 리뷰나 주고받은 메시지에 붙은 이름도 이 이름을 따라 바뀐다.\n\n"
        "**바꾸려는 계정이 토큰의 주인인지 검증한다.** 액세스 토큰을 검증해 `sub`를 "
        "꺼내고, 바꾸려는 계정이 그 `sub`와 같은지 확인한 뒤에 바꾼다. 다르면 403으로 "
        "막는다. 로그인했다는 것만으로 남의 이름을 바꿀 수 없다.\n\n"
        "**403은 나오지 않는다.** 대상을 요청에 담지 않아 남의 것을 가리킬 자리가 "
        "없고, 토큰을 고쳐 남의 `sub`를 넣으면 서명이 깨져 401이 된다.\n\n"
        "- **200**: 바꿨을 때\n"
        "- **401 / UNAUTHORIZED**: 토큰이 없거나 만료·위조됐을 때, `sub`의 사용자가 "
        "없거나 탈퇴한 계정일 때\n"
        "- **422 / INVALID_REQUEST**: `name`이 비었거나 20자를 넘었을 때\n"
        "- **500 / INTERNAL_ERROR**: 저장하지 못했을 때"
    ),
)
def update_my_name(request: UpdateMyNameRequest) -> MyAccountResponse:
    return MyAccountResponse(
        name=request.name,
        email=MY_PAGE_EXAMPLE["account"]["email"],
        profileImageUrl=MY_PAGE_EXAMPLE["account"]["profileImageUrl"],
    )


@router.put(
    "/users/me/profile-image",
    response_model=MyAccountResponse,
    tags=[AREA8_TAG],
    responses={
        200: {
            "content": {"application/json": {"example": MY_PAGE_EXAMPLE["account"]}}
        },
        400: {
            "model": ErrorResponse,
            "description": "쓸 수 없는 파일",
            "content": {"application/json": {"example": FILE_NOT_ATTACHABLE_EXAMPLE}},
        },
        **AREA8_FORBIDDEN,
        **AREA1_UNAUTHORIZED,
        **AREA1_ERRORS,
    },
    summary="프로필 사진 바꾸기",
    description=(
        "**화면 — `8_마이페이지` › 계정 › 사진 바꾸기**\n\n"
        "올려 둔 사진을 프로필 사진으로 건다. 올리기 전에는 카카오 프로필 사진을 쓰고, "
        "한 번 걸면 그 뒤로는 올린 사진을 쓴다.\n\n"
        "**파일 자체는 이 API로 보내지 않는다.** 업로드 시작과 완료를 먼저 거쳐 "
        "`fileId`를 받고, 그 값만 여기로 보낸다. 이 화면은 저장 버튼이 따로 없어 "
        "고르는 즉시 바뀐다.\n\n"
        "**바뀌는 것은 토큰의 주인의 사진이다.** 액세스 토큰을 검증해 `sub`를 꺼내 그 "
        "계정에 걸며, 대상을 요청에 담지 않는다. `fileId`가 `sub`가 올린 파일인지도 "
        "확인하고, 남의 파일이면 걸지 않는다.\n\n"
        "- **200**: 바꿨을 때. `profileImageUrl`이 새 사진을 가리킨다\n"
        "- **400 / FILE_NOT_ATTACHABLE**: `fileId`가 400일 때. 아직 올리기를 마치지 "
        "않았거나 남이 올린 파일일 때다\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **403 / FORBIDDEN**: `sub`가 올린 파일이 아니거나 남의 계정에 걸려 할 때\n"
        "- **422 / INVALID_REQUEST**: `fileId`가 없거나 정수가 아닐 때\n"
        "- **500 / INTERNAL_ERROR**: 저장하지 못했을 때"
    ),
)
def update_my_profile_image(
    request: UpdateProfileImageRequest,
) -> MyAccountResponse | JSONResponse:
    if request.fileId == NOT_ATTACHABLE_FILE_ID:
        return _error_response(400, FILE_NOT_ATTACHABLE)
    return MyAccountResponse(
        name=MY_PAGE_EXAMPLE["account"]["name"],
        email=MY_PAGE_EXAMPLE["account"]["email"],
        profileImageUrl=(
            f"https://cdn.komit.example.com/profiles/{request.fileId}.png"
        ),
    )


@router.patch(
    "/settings/notification",
    response_model=NotificationSettingResponse,
    tags=[AREA8_TAG],
    responses={
        200: {"content": {"application/json": {"example": {"notificationEnabled": False}}}},
        **AREA1_UNAUTHORIZED,
        **AREA1_ERRORS,
    },
    summary="알림 켜고 끄기",
    description=(
        "**화면 — `8_마이페이지` › 알림 › 끄기 · 켜기**\n\n"
        "카카오 알림톡을 받을지 정한다. 끄면 방 소식과 확인 요청 알림톡이 나가지 않고, "
        "알림 화면에는 그대로 쌓인다.\n\n"
        "카카오 이메일을 인증하지 않은 계정에는 켜도 알림톡이 나가지 않는다.\n\n"
        "**바뀌는 것은 토큰의 주인의 설정이다.** 액세스 토큰을 검증해 `sub`를 꺼내 그 "
        "계정의 설정만 바꾸며, 대상을 요청에 담지 않는다.\n\n"
        "**403은 나오지 않는다.** 대상을 요청에 담지 않아 남의 것을 가리킬 자리가 "
        "없고, 토큰을 고쳐 남의 `sub`를 넣으면 서명이 깨져 401이 된다.\n\n"
        "- **200**: 바꿨을 때\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **422 / INVALID_REQUEST**: `notificationEnabled`가 없을 때\n"
        "- **500 / INTERNAL_ERROR**: 저장하지 못했을 때"
    ),
)
def update_notification_setting(
    request: UpdateNotificationSettingRequest,
) -> NotificationSettingResponse:
    return NotificationSettingResponse(
        notificationEnabled=request.notificationEnabled
    )


@router.get(
    "/notifications",
    response_model=NotificationListResponse,
    tags=[AREA8_TAG],
    responses={
        200: {
            "content": {"application/json": {"example": NOTIFICATION_LIST_EXAMPLE}}
        },
        **AREA1_UNAUTHORIZED,
        **AREA1_ERRORS,
    },
    summary="알림 목록 조회",
    description=(
        "**화면 — `8_알림` › 전체 · 안 읽음**\n\n"
        "받은 알림을 새로운 것부터 내려준다. 공지도 같은 목록에 섞이며 `type`이 "
        "`공지`인 줄이 그것이다.\n\n"
        "한 줄은 `title`과 `content` 두 단이다. 공지는 `content`에 본문이 들어가 "
        "화면이 목록에서 바로 펼쳐 보여주고, 그 밖의 알림은 어느 건인지 가리키는 "
        "줄이 들어간다.\n\n"
        "`unreadCount`는 `unreadOnly`와 페이지에 상관없이 언제나 안 읽은 알림 전체 수다. "
        "헤더 종 옆 숫자가 이 값이다.\n\n"
        "**403은 나오지 않는다.** 대상을 요청에 담지 않아 남의 것을 가리킬 자리가 "
        "없고, 토큰을 고쳐 남의 `sub`를 넣으면 서명이 깨져 401이 된다.\n\n"
        "- **200**: 항상. 알림이 없으면 빈 배열이다\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **422 / INVALID_REQUEST**: `page`가 1보다 작거나 `size`가 범위를 벗어났을 때\n"
        "- **500 / INTERNAL_ERROR**: 목록을 만들지 못했을 때"
    ),
)
def get_notifications(
    unreadOnly: Annotated[
        bool,
        Query(description="참이면 안 읽은 알림만 준다. 화면의 '안 읽음' 탭이 쓴다."),
    ] = False,
    page: Annotated[int, Query(description="페이지 번호. 1부터 시작한다.", ge=1)] = 1,
    size: Annotated[
        int, Query(description="한 페이지에 담을 최대 항목 수.", ge=1, le=50)
    ] = 20,
) -> NotificationListResponse:
    items = NOTIFICATION_LIST_EXAMPLE["content"]
    if unreadOnly:
        items = [item for item in items if not item["isRead"]]
    return NotificationListResponse(
        **{
            **NOTIFICATION_LIST_EXAMPLE,
            "content": items,
            "page": page,
            "size": size,
            "totalElements": len(items),
        }
    )


@router.post(
    "/notifications/read-all",
    response_model=NotificationListResponse,
    tags=[AREA8_TAG],
    responses={
        200: {
            "content": {"application/json": {"example": NOTIFICATION_LIST_EXAMPLE}}
        },
        **AREA1_UNAUTHORIZED,
        **AREA1_ERRORS,
    },
    summary="알림 모두 읽음으로",
    description=(
        "**화면 — `8_알림` › 모두 읽음으로**\n\n"
        "안 읽은 알림을 전부 읽음으로 바꾸고, 바뀐 목록을 그대로 돌려준다. 화면이 "
        "다시 조회하지 않아도 되게 하기 위해서다.\n\n"
        "이미 다 읽었으면 아무것도 바꾸지 않고 같은 목록을 준다.\n\n"
        "**403은 나오지 않는다.** 대상을 요청에 담지 않아 남의 것을 가리킬 자리가 "
        "없고, 토큰을 고쳐 남의 `sub`를 넣으면 서명이 깨져 401이 된다.\n\n"
        "- **200**: 처리했을 때. `unreadCount`가 0이다\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **500 / INTERNAL_ERROR**: 처리하지 못했을 때"
    ),
)
def read_all_notifications() -> NotificationListResponse:
    items = [{**item, "isRead": True} for item in NOTIFICATION_LIST_EXAMPLE["content"]]
    return NotificationListResponse(
        **{**NOTIFICATION_LIST_EXAMPLE, "content": items, "unreadCount": 0}
    )


@router.patch(
    "/notifications/{notificationId}/read",
    response_model=NotificationResponse,
    tags=[AREA8_TAG],
    responses={
        200: {
            "content": {
                "application/json": {
                    "example": {
                        **NOTIFICATION_LIST_EXAMPLE["content"][1],
                        "isRead": True,
                    }
                }
            }
        },
        **AREA8_FORBIDDEN,
        404: {
            "model": ErrorResponse,
            "description": "알림을 찾지 못함",
            "content": {
                "application/json": {"example": NOTIFICATION_NOT_FOUND_EXAMPLE}
            },
        },
        **AREA1_UNAUTHORIZED,
        **AREA1_ERRORS,
    },
    summary="알림 하나 읽음으로",
    description=(
        "**화면 — `8_알림` › 확인하기**\n\n"
        "알림 하나를 읽음으로 바꾼다. 화면은 '확인하기'를 누를 때 이 API를 부르고 "
        "`targetType`과 `targetId`가 가리키는 곳으로 옮겨 간다.\n\n"
        "이미 읽은 알림에 다시 보내도 200이다.\n\n"
        "**알림을 받은 사람이 토큰의 주인인지 검증한다.** `notificationId`의 알림이 "
        "`sub`에게 온 것인지 확인한 뒤에 처리하고, 다르면 403으로 막는다. 남의 알림을 "
        "읽음으로 바꾸거나 그 내용을 응답으로 받아 갈 수 없어야 한다.\n\n"
        "- **200**: 처리했을 때\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **403 / FORBIDDEN**: `sub`에게 온 알림이 아닐 때\n"
        "- **404 / NOTIFICATION_NOT_FOUND**: `notificationId`가 404일 때\n"
        "- **500 / INTERNAL_ERROR**: 처리하지 못했을 때"
    ),
)
def read_notification(
    notificationId: Annotated[
        int,
        Path(description="알림 식별자. 404는 없음 재현용 예약값이다.", examples=[951]),
    ],
) -> NotificationResponse | JSONResponse:
    if notificationId == NOT_FOUND_NOTIFICATION_ID:
        return _error_response(404, NOTIFICATION_NOT_FOUND)
    return NotificationResponse(
        **{**NOTIFICATION_LIST_EXAMPLE["content"][1], "isRead": True}
    )


@router.get(
    "/users/me/withdrawal",
    response_model=WithdrawalCheckResponse,
    tags=[AREA8_TAG],
    responses={
        200: {
            "content": {"application/json": {"example": WITHDRAWAL_CHECK_EXAMPLE}}
        },
        **AREA1_UNAUTHORIZED,
        **AREA1_ERRORS,
    },
    summary="탈퇴 전 확인",
    description=(
        "**화면 — `8_회원탈퇴` › 정리해야 할 것**\n\n"
        "지금 탈퇴할 수 있는지와, 막고 있는 것이 무엇인지 내려준다. 화면은 "
        "`canWithdraw`가 거짓이면 탈퇴 버튼을 잠그고 `blockers`를 줄로 늘어놓는다.\n\n"
        "진행 중인 의뢰, 남은 예치금, 아직 받지 못한 소득이 막는다. 예치금과 소득은 "
        "Escrow 서버에서 받아와 센다.\n\n"
        "**403은 나오지 않는다.** 대상을 요청에 담지 않아 남의 것을 가리킬 자리가 "
        "없고, 토큰을 고쳐 남의 `sub`를 넣으면 서명이 깨져 401이 된다.\n\n"
        "- **200**: 항상\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **500 / INTERNAL_ERROR**: 확인하지 못했을 때"
    ),
)
def check_withdrawal() -> WithdrawalCheckResponse:
    return WithdrawalCheckResponse(**WITHDRAWAL_CHECK_EXAMPLE)


@router.post(
    "/users/me/withdrawal",
    response_model=WithdrawalResultResponse,
    tags=[AREA8_TAG],
    responses={
        200: {"content": {"application/json": {"example": WITHDRAWAL_RESULT_EXAMPLE}}},
        409: {
            "model": ErrorResponse,
            "description": "정리하지 않은 것이 남아 탈퇴할 수 없음",
            "content": {"application/json": {"example": WITHDRAWAL_BLOCKED_EXAMPLE}},
        },
        **AREA1_UNAUTHORIZED,
        **AREA1_ERRORS,
    },
    summary="회원 탈퇴",
    description=(
        "**화면 — `8_회원탈퇴` › 탈퇴하기**\n\n"
        "**행을 지우지 않는다.** 사용자 정보는 그대로 두고 삭제 표시만 켠 뒤 삭제 시각을 "
        "적는다. 그래서 이 API는 `DELETE`가 아니라 `POST`다 — 지우는 요청으로 읽히면 "
        "안 되기 때문이다.\n\n"
        "표시가 켜지면 그 계정으로는 로그인할 수 없고 카카오 연결도 끊긴다. 같은 카카오 "
        "계정으로 30일 동안 다시 가입할 수 없다.\n\n"
        "**정보를 남기는 이유는 상대가 있기 때문이다.** 계약과 정산 이력, 주고받은 "
        "메시지, 남긴 리뷰는 상대 화면에도 있는 기록이라 그대로 두고, 표시 이름만 "
        "'탈퇴한 사용자'로 바뀐다. 개인정보는 30일 보관 후 지우되 법으로 보관해야 하는 "
        "자료는 법정 기간 동안 남는다.\n\n"
        "탈퇴 전 확인과 같은 조건을 여기서 다시 본다. 화면이 버튼을 잠갔더라도 서버가 "
        "한 번 더 막는다.\n\n"
        "**탈퇴하는 계정은 토큰의 주인이다.** 액세스 토큰을 검증해 `sub`를 꺼내 그 "
        "계정만 탈퇴시키며, 대상을 요청에 담지 않는다. 되돌릴 수 없는 요청이므로 "
        "로그인했다는 것만으로 통과시키지 않는다.\n\n"
        "**403은 나오지 않는다.** 대상을 요청에 담지 않아 남의 것을 가리킬 자리가 "
        "없고, 토큰을 고쳐 남의 `sub`를 넣으면 서명이 깨져 401이 된다.\n\n"
        "- **200**: 탈퇴했을 때\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **409 / WITHDRAWAL_BLOCKED**: 진행 중인 의뢰나 남은 돈이 있을 때\n"
        "- **500 / INTERNAL_ERROR**: 처리하지 못했을 때"
    ),
)
def withdraw() -> WithdrawalResultResponse | JSONResponse:
    return WithdrawalResultResponse(**WITHDRAWAL_RESULT_EXAMPLE)


@router.get(
    "/users/me/developer-profile",
    response_model=MyDeveloperProfileResponse,
    tags=[AREA8_TAG],
    responses={
        200: {
            "content": {
                "application/json": {"example": MY_DEVELOPER_PROFILE_EXAMPLE}
            }
        },
        **AREA8_NO_DEVELOPER_PROFILE,
        **AREA1_UNAUTHORIZED,
        **AREA1_ERRORS,
    },
    summary="내 개발자 프로필 조회",
    description=(
        "**화면 — `8_개발자_내프로필`**\n\n"
        "편집 화면이 쓰는 값을 한 번에 내려준다. 남에게 보이는 개발자 상세에 가격표 "
        "전부와 받은 리뷰를 더한 모양이다.\n\n"
        "줄 단위 가격표는 개발자 상세에서 물려받은 `priceCatalog`다. 같은 데이터를 "
        "이름만 바꿔 두 번 내려보내지 않으려고 모양을 맞췄다.\n\n"
        "편집한 다른 내용을 저장할 때 기존 첨부가 사라지지 않도록 작업물에는 "
        "`thumbnailFileId`와 `thumbnailUrl`을 함께 내려준다. 확인 전 경력 증빙에는 "
        "`evidenceFileId`와 `evidenceFileName`을 함께 내려준다. 확인을 마친 증빙은 원본을 "
        "삭제하므로 두 값이 모두 null이다.\n\n"
        "평점은 읽기 전용이다. 유료 거래를 마친 의뢰인의 리뷰로만 매겨지므로 저장 "
        "요청으로 바꿀 수 없다.\n\n"
        "**403은 개발자 프로필이 없을 때만 나온다.** 대상을 요청에 담지 않아 남의 "
        "것을 가리킬 자리가 없고, 토큰을 고치면 서명이 깨져 401이 된다.\n\n"
        "- **200**: 항상\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **403 / NO_DEVELOPER_PROFILE**: 개발자 프로필이 없을 때\n"
        "- **500 / INTERNAL_ERROR**: 프로필을 만들지 못했을 때"
    ),
)
def get_my_developer_profile() -> MyDeveloperProfileResponse:
    return MyDeveloperProfileResponse(**MY_DEVELOPER_PROFILE_EXAMPLE)


@router.put(
    "/users/me/developer-profile",
    response_model=MyDeveloperProfileResponse,
    tags=[AREA8_TAG],
    responses={
        200: {
            "content": {
                "application/json": {"example": MY_DEVELOPER_PROFILE_EXAMPLE}
            }
        },
        201: {
            "description": "프로필이 없어 새로 만들었음",
            "content": {
                "application/json": {"example": MY_DEVELOPER_PROFILE_EXAMPLE}
            },
        },
        400: {
            "model": ErrorResponse,
            "description": "붙일 수 없는 파일",
            "content": {"application/json": {"example": FILE_NOT_ATTACHABLE_EXAMPLE}},
        },
        **AREA8_FORBIDDEN,
        404: {
            "model": ErrorResponse,
            "description": "없는 코드",
            "content": {"application/json": {"example": CODE_NOT_FOUND_EXAMPLE}},
        },
        **AREA1_UNAUTHORIZED,
        **AREA1_ERRORS,
    },
    summary="내 개발자 프로필 저장",
    description=(
        "**화면 — `8_개발자_내프로필` › 저장**\n\n"
        "화면의 저장 버튼 하나가 이 요청 하나를 보낸다. 소개, 기술, 경력, 작업물, "
        "가격표를 통째로 받아 **보낸 내용으로 맞춘다** — 목록에서 빠진 줄은 지운 것으로 "
        "본다. 줄마다 따로 부르는 API는 두지 않는다.\n\n"
        "**프로필이 없으면 만든다.** 개발자 프로필을 만드는 경로는 이 하나뿐이며 따로 "
        "두지 않는다. 화면도 만들 때와 고칠 때가 같고, 마이페이지의 「프로필 생성」과 "
        "「프로필 편집」이 같은 화면으로 온다. 만들었을 때는 201, 이미 있던 것을 "
        "고쳤을 때는 200이다.\n\n"
        "**계정이 개발자로 바뀌는 것이 아니라 역할이 하나 늘어난다.** (`R-ACC-08`) "
        "의뢰인으로 하던 일은 그대로 두고 개발자로도 활동할 수 있게 된다. 만들 때 "
        "`developers` 행과 프로필이 생기고 `users.has_dev_profile`이 참이 된다.\n\n"
        "**판매자 약관 동의는 만드는 것으로 갈음한다.** 요청에 동의 필드를 두지 않고, "
        "만드는 시점에 서버가 `settings.is_seller_terms_agreed`를 켠다. 필수 약관을 "
        "로그인으로 처리하는 것과 같은 방식이며, 화면이 '만들면 판매자 약관에 동의한 "
        "것으로 간주합니다'라고 안내한다. 동의한 문서는 마이페이지 약관 칸에서 본다.\n\n"
        "새로 추가하는 줄은 식별자를 null로 보내고, 서버가 만들어 응답에 담아 준다.\n\n"
        "작업물 썸네일은 기존 파일 식별자를 다시 보내면 유지되고, 새 식별자를 보내면 "
        "교체되며, null을 보내면 제거된다. 확인 전 경력 증빙도 기존 식별자를 다시 "
        "보내면 유지되고 새 식별자를 보내면 교체된다. 확인을 마친 경력은 증빙 원본이 "
        "이미 삭제되어 null을 보내도 확인 상태와 확인 시각이 유지된다. "
        "확인된 경력의 회사·역할·기간은 증빙과 어긋나지 않도록 저장 요청으로 바뀌지 않는다.\n\n"
        "작업물 공개 여부와 순서를 따로 저장하는 값은 받지 않는다. 작업물은 등록한 "
        "순서로 고정이며 새 줄은 뒤에 붙는다. 개발자 목록 카드에는 작업물을 보여주지 "
        "않는다.\n\n"
        "**저장되는 곳은 토큰의 주인의 프로필이다.** 액세스 토큰을 검증해 `sub`를 꺼내 "
        "그 사용자의 개발자 프로필에 저장하며, 대상을 요청에 담지 않는다. 요청에 실린 "
        "`careerId`·`portfolioItemId`·`priceId`가 그 프로필의 것인지도 확인하고, 남의 "
        "줄을 가리키면 저장하지 않는다.\n\n"
        "**저장해도 바뀌지 않는 것이 있다.** 경력의 증빙 확인 표시는 운영에서 확인한 "
        "값이라 그대로 남고, 평점과 리뷰 수는 의뢰인이 남긴 것이라 손대지 않는다.\n\n"
        "**공개·비공개를 나누지 않는다.** 개발자 프로필이 있으면 개발자 찾기에 나온다. "
        "저장이 곧 공개이며 따로 감출 수 없다.\n\n"
        "- **200**: 이미 있던 프로필을 고쳤을 때. 저장된 뒤의 프로필을 그대로 준다\n"
        "- **201**: 프로필이 없어 새로 만들었을 때. 만들어진 프로필을 준다\n"
        "- **400 / FILE_NOT_ATTACHABLE**: `thumbnailFileId`·`evidenceFileId`가 400일 "
        "때. 올리기를 마치지 않았거나 남이 올린 파일이다\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **403 / FORBIDDEN**: 요청의 `careerId`·`portfolioItemId`·`priceId`가 내 "
        "프로필의 줄이 아닐 때\n"
        "- **404 / CODE_NOT_FOUND**: `fieldCodeId`·`techCodeIds`에 404가 있을 때. "
        "없는 코드거나 그 분류의 코드가 아니다\n"
        "- **422 / INVALID_REQUEST**: `intro`가 비었거나 길이·형식 제약을 어겼거나, "
        "경력 날짜가 실제 달력에 없거나, 종료일이 시작일보다 빠르거나"
        "(`careers.N.endDate`), 가격이 0 이하이거나 작업 기간이 32767일을 넘을 때\n"
        "- **500 / INTERNAL_ERROR**: 저장하지 못했을 때"
    ),
)
def write_my_developer_profile(
    request: WriteDeveloperProfileRequest,
) -> MyDeveloperProfileResponse | JSONResponse:
    file_ids = [item.thumbnailFileId for item in request.portfolioItems] + [
        career.evidenceFileId for career in request.careers
    ]
    if NOT_ATTACHABLE_FILE_ID in file_ids:
        return _error_response(400, FILE_NOT_ATTACHABLE)
    if request.fieldCodeId == NOT_FOUND_CODE_ID or NOT_FOUND_CODE_ID in request.techCodeIds:
        return _error_response(404, CODE_NOT_FOUND)
    return MyDeveloperProfileResponse(
        **{
            **MY_DEVELOPER_PROFILE_EXAMPLE,
            "intro": request.intro,
            "description": request.description,
        }
    )


@router.put(
    "/reviews/{reviewId}/reply",
    response_model=ReviewResponse,
    tags=[AREA8_TAG],
    responses={
        200: {
            "content": {
                "application/json": {
                    "example": MY_DEVELOPER_PROFILE_EXAMPLE["reviews"][0]
                }
            }
        },
        **AREA8_FORBIDDEN,
        404: {
            "model": ErrorResponse,
            "description": "리뷰를 찾지 못함",
            "content": {"application/json": {"example": REVIEW_NOT_FOUND_EXAMPLE}},
        },
        **AREA1_UNAUTHORIZED,
        **AREA1_ERRORS,
    },
    summary="리뷰 답글 쓰기 · 고치기",
    description=(
        "**화면 — `8_개발자_내프로필` › 받은 리뷰 › 답글 쓰기 · 답글 수정**\n\n"
        "받은 리뷰에 답글을 단다. 리뷰 하나에 답글은 하나뿐이라 다시 보내면 덮어쓴다. "
        "프로필 저장과 따로 움직이며 보내는 즉시 남는다.\n\n"
        "답글은 의뢰인에게 보이는 프로필에도 함께 나간다.\n\n"
        "**리뷰를 받은 개발자가 토큰의 주인인지 검증한다.** `reviewId`의 리뷰가 `sub`의 "
        "개발자 프로필에 달린 것인지 확인한 뒤에 저장하고, 다르면 403으로 막는다. 남의 "
        "프로필에 내가 답글을 남기는 일이 없어야 한다.\n\n"
        "- **200**: 달았거나 고쳤을 때\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **403 / FORBIDDEN**: `sub`가 받은 리뷰가 아닐 때\n"
        "- **404 / REVIEW_NOT_FOUND**: `reviewId`가 404일 때\n"
        "- **422 / INVALID_REQUEST**: `reply`가 비었거나 1000자를 넘을 때\n"
        "- **500 / INTERNAL_ERROR**: 저장하지 못했을 때"
    ),
)
def write_review_reply(
    reviewId: Annotated[
        int,
        Path(description="리뷰 식별자. 404는 없음 재현용 예약값이다.", examples=[88]),
    ],
    request: WriteReviewReplyRequest,
) -> ReviewResponse | JSONResponse:
    if reviewId == NOT_FOUND_REVIEW_ID:
        return _error_response(404, REVIEW_NOT_FOUND)
    return ReviewResponse(
        **{**MY_DEVELOPER_PROFILE_EXAMPLE["reviews"][0], "reply": request.reply}
    )


@router.get(
    "/users/me/sessions",
    response_model=MySessionListResponse,
    tags=[AREA8_TAG],
    responses={
        200: {
            "content": {"application/json": {"example": MY_SESSION_LIST_EXAMPLE}}
        },
        **AREA1_UNAUTHORIZED,
        **AREA1_ERRORS,
    },
    summary="내 일정 조회",
    description=(
        "**화면 — `8_내일정`**\n\n"
        "의뢰인이나 개발자로 참여한 컨설팅 세션을 잡힌 세션과 지난 세션으로 나눠 "
        "내려준다. 한 사용자가 두 역할로 모두 활동할 수 있으므로 각 세션의 `role`로 "
        "그 계약에서 맡은 쪽을 구분하고, `counterpartName`에는 반대쪽 이름을 담는다.\n\n"
        "한 세션에 예약 제안이 여러 번 생길 수 있지만 일정에는 확정된 예약만 쓴다. "
        "양쪽이 서명한 뒤에는 시각을 바꾸지 않으므로, 여기 `scheduledAt`이 곧 확정된 "
        "시각이다.\n\n"
        "화면의 '시작 N분 전'은 `scheduledAt`에서, '언제까지 질문 받습니다'는 "
        "`followUpUntil`에서 만든다.\n\n"
        "개발 계약에는 예약이 없으므로 이 목록에 나오지 않는다.\n\n"
        "개발자 프로필이 없어도 의뢰인으로 참여한 일정을 조회할 수 있다. 개발자 "
        "프로필이 반드시 필요한 `GET /api/users/me/developer-profile`과 달리 이 API에는 "
        "`NO_DEVELOPER_PROFILE` 응답이 없다.\n\n"
        "- **200**: 항상. 역할별로 참여한 세션이 없으면 빈 배열이다\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **500 / INTERNAL_ERROR**: 일정을 만들지 못했을 때"
    ),
)
def get_my_sessions() -> MySessionListResponse:
    return MySessionListResponse(**MY_SESSION_LIST_EXAMPLE)


@router.post(
    "/uploads",
    response_model=UploadTargetResponse,
    status_code=201,
    tags=[AREA8_TAG],
    responses={
        201: {"content": {"application/json": {"example": UPLOAD_TARGET_EXAMPLE}}},
        400: {
            "model": ErrorResponse,
            "description": "파일이 너무 크거나 형식이 맞지 않음",
            "content": {
                "application/json": {
                    "examples": {
                        "FILE_TOO_LARGE": {"value": FILE_TOO_LARGE_EXAMPLE},
                        "UNSUPPORTED_FILE_TYPE": {
                            "value": UNSUPPORTED_FILE_TYPE_EXAMPLE
                        },
                    }
                }
            },
        },
        **AREA1_UNAUTHORIZED,
        **AREA1_ERRORS,
    },
    summary="파일 올리기 시작",
    description=(
        "**화면 — `8_마이페이지` › 계정 › 사진 바꾸기, "
        "`8_개발자_내프로필` › 증빙 서류 파일 올리기 · 작업물 사진 추가, "
        "`5_서명판` › 서명하기, `5_개발자_확인` › 인계 작업의 파일 첨부**\n\n"
        "**파일은 이 서버로 보내지 않는다.** 올릴 파일을 먼저 알리면 파일을 보낼 주소를 "
        "돌려주고, 화면이 그 주소로 파일을 직접 보낸다. 다 보낸 뒤 완료 API를 불러야 "
        "파일이 쓸 수 있는 상태가 된다.\n\n"
        "형식과 크기는 이 단계에서 본다(`R-FILE-01`, `R-FILE-02`). 여기서 걸리면 주소를 내주지 않으므로 파일이 "
        "올라가지도 않는다. `contentType`과 `sizeBytes`는 완료할 때 실제로 올라온 "
        "파일과 맞는지 다시 본다.\n\n"
        "**올리기만 해서는 어디에도 붙지 않는다.** 완료까지 마친 뒤 `fileId`를 프로필 "
        "사진 바꾸기나 프로필 저장 요청에 담아야 붙는다. 어디에도 담기지 않은 파일은 "
        "붙지 않은 채로 남다가 정리된다.\n\n"
        "**증빙 서류는 경력 확인에만 쓴다.** 운영에서 확인이 끝나면 원본을 지우고 "
        "프로필에는 확인 표시만 남는다.\n\n"
        "**403은 나오지 않는다.** 대상을 요청에 담지 않아 남의 것을 가리킬 자리가 "
        "없고, 토큰을 고쳐 남의 `sub`를 넣으면 서명이 깨져 401이 된다.\n\n"
        "- **201**: 보낼 주소를 내줬을 때\n"
        "- **400 / FILE_TOO_LARGE**: `sizeBytes`가 그 쓰임의 허용 크기를 넘었을 때\n"
        "- **400 / UNSUPPORTED_FILE_TYPE**: `contentType`이 그 쓰임에서 받지 않는 "
        "형식일 때\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **422 / INVALID_REQUEST**: `originalName`·`contentType`·`sizeBytes`·"
        "`purpose`가 비었거나 `purpose`가 허용된 값이 아닐 때\n"
        "- **500 / INTERNAL_ERROR**: 주소를 만들지 못했을 때\n\n"
        "**실서버의 `uploadUrl`은 저장소가 내주는 주소다.** 이 목 서버는 그 자리에 "
        "자기 주소를 넣어 주고 받은 본문을 버린다. 주소 만료 기준은 `R-FILE-03`이다. 화면이 3콜 구조를 그대로 돌려볼 "
        "수 있게 하려는 것이며, 올린 파일이 남지는 않는다."
    ),
)
def create_upload(
    request: CreateUploadRequest, http: Request
) -> UploadTargetResponse | JSONResponse:
    if request.sizeBytes > MAX_UPLOAD_BYTES:
        return _error_response(400, FILE_TOO_LARGE)
    base_content_type = request.contentType.partition(";")[0].strip().lower()
    if base_content_type not in UPLOAD_CONTENT_TYPES[request.purpose]:
        return _error_response(400, UNSUPPORTED_FILE_TYPE)
    upload_id = UPLOAD_TARGET_EXAMPLE["uploadId"]
    return UploadTargetResponse(
        **{
            **UPLOAD_TARGET_EXAMPLE,
            "uploadUrl": str(http.url_for("put_upload_binary", uploadId=upload_id)),
            "headers": {"Content-Type": request.contentType},
        }
    )


# 저장소 자리를 대신하는 자리다. 실서버에서는 이 경로가 없고 저장소가 직접 받으므로
# 명세에 넣지 않는다. 받은 본문은 읽고 버린다.
@router.put("/uploads/{uploadId}/binary", status_code=204, include_in_schema=False)
async def put_upload_binary(uploadId: int, http: Request) -> Response:
    await http.body()
    return Response(status_code=204)


@router.post(
    "/uploads/{uploadId}/complete",
    response_model=UploadedFileResponse,
    tags=[AREA8_TAG],
    responses={
        200: {"content": {"application/json": {"example": UPLOADED_FILE_EXAMPLE}}},
        400: {
            "model": ErrorResponse,
            "description": "올라오지 않았거나 알린 것과 다름",
            "content": {
                "application/json": {
                    "examples": {
                        "UPLOAD_NOT_COMPLETED": {
                            "value": UPLOAD_NOT_COMPLETED_EXAMPLE
                        },
                        "UPLOAD_SIZE_MISMATCH": {
                            "value": UPLOAD_SIZE_MISMATCH_EXAMPLE
                        },
                        "UPLOAD_TYPE_MISMATCH": {
                            "value": UPLOAD_TYPE_MISMATCH_EXAMPLE
                        },
                        "UPLOAD_EXPIRED": {"value": UPLOAD_EXPIRED_EXAMPLE},
                    }
                }
            },
        },
        **AREA8_FORBIDDEN,
        404: {
            "model": ErrorResponse,
            "description": "업로드 건을 찾지 못함",
            "content": {"application/json": {"example": UPLOAD_NOT_FOUND_EXAMPLE}},
        },
        **AREA1_UNAUTHORIZED,
        **AREA1_ERRORS,
    },
    summary="파일 올리기 완료",
    description=(
        "**화면 — 파일 올리기 시작으로 받은 주소에 파일을 다 보낸 뒤**\n\n"
        "파일이 제대로 올라왔는지 확인하고 쓸 수 있는 상태로 바꾼다(`R-FILE-04`). 이 API를 부르기 "
        "전까지 `fileId`는 어디에도 붙일 수 없다.\n\n"
        "화면이 '다 보냈다'고 알려도 그대로 믿지 않는다. 저장소에 실제로 그 파일이 "
        "있는지, 크기와 형식이 시작할 때 알린 것과 같은지 서버가 직접 확인한다. 하나라도 "
        "어긋나면 올라온 것을 지우고 실패로 남긴다.\n\n"
        "**업로드를 시작한 사람이 토큰의 주인인지 검증한다.** 남이 시작한 업로드 건을 "
        "가로채 자기 파일로 만들 수 없다.\n\n"
        "같은 `uploadId`로 다시 불러도 된다. 이미 끝난 건이면 같은 응답을 그대로 준다. "
        "보내는 도중 연결이 끊겨 화면이 다시 보내는 경우가 있기 때문이다.\n\n"
        "- **200**: 확인을 마쳐 쓸 수 있게 됐을 때\n"
        "- **400 / UPLOAD_NOT_COMPLETED**: `uploadId`가 400일 때. 저장소에 파일이 "
        "없는 경우다\n"
        "- **400 / UPLOAD_SIZE_MISMATCH**: 올라온 크기가 알린 것과 다를 때\n"
        "- **400 / UPLOAD_TYPE_MISMATCH**: 올라온 형식이 알린 것과 다를 때. 보낼 때 "
        "`headers`의 `Content-Type`을 그대로 쓰지 않으면 여기서 걸린다\n"
        "- **400 / UPLOAD_EXPIRED**: `expiresAt`이 지난 뒤 불렀을 때\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **403 / FORBIDDEN**: 남이 시작한 업로드 건일 때\n"
        "- **404 / UPLOAD_NOT_FOUND**: `uploadId`가 404일 때\n"
        "- **500 / INTERNAL_ERROR**: 확인하지 못했을 때\n\n"
        "**이 목 서버는 저장소를 확인하지 않는다.** 위 예약값이 아니면 언제나 성공한다."
    ),
)
def complete_upload(
    uploadId: Annotated[
        int,
        Path(
            description=(
                "올리기 시작에서 받은 업로드 건 식별자. 404는 없음, 400은 파일이 "
                "올라오지 않음 재현용 예약값이다."
            ),
            examples=[512],
        ),
    ],
) -> UploadedFileResponse | JSONResponse:
    if uploadId == NOT_FOUND_UPLOAD_ID:
        return _error_response(404, UPLOAD_NOT_FOUND)
    if uploadId == NOT_COMPLETED_UPLOAD_ID:
        return _error_response(400, UPLOAD_NOT_COMPLETED)
    return UploadedFileResponse(**{**UPLOADED_FILE_EXAMPLE, "fileId": uploadId})


# =========================================================================== #
# 영역 9 — 운영 콘솔
#
# Komit 내부 직원만 쓴다. 흐름 밖 화면이고 의뢰인·개발자에게는 보이지 않는다.
#
# 화면: 9_로그인 · 9_홈 · 9_신고처리 · 9_정산대사
#       9_계약양식 · 9_양식편집 · 9_양식등록 · 9_펼침_양식유형
#
# 운영자 계정은 사용자와 완전히 별개다. 사용자는 카카오로 들어오고 운영자는 아이디와
# 비밀번호로 들어온다. 토큰도 따로 발급하며 사용자 토큰으로는 이 영역을 부를 수 없다.
#
# 정산 대사는 Escrow 서버가 매일 돌린 결과를 받아 화면에 맞게 엮는다. 대사 자체는
# 여기서 실행하지 않는다. 거래 이름과 대기 건수는 Komit 의 계약·작업에서 나오고,
# 금액과 잔고는 Escrow 에서 온다.
#
# 계약 양식은 고치지 않는다. 본문을 바꾸면 늘 새 행이 생기고 이전 판은 보관으로
# 내려간다. 한 유형에 사용 중인 판은 언제나 하나다.
#
# 함께 쓰는 API (다른 영역에 있다):
#   GET /api/contracts/{contractId}   신고 상세의 계약서 원문
# =========================================================================== #


class AdminResponse(BaseModel):
    """로그인한 운영자."""

    adminId: int = Field(description="운영자 식별자.", examples=[2])
    loginId: str = Field(description="운영자 로그인 아이디.", examples=["soomin"])


class AdminLoginRequest(BaseModel):
    """운영 콘솔 로그인 요청."""

    loginId: str = Field(
        min_length=1, description="운영자 로그인 아이디.", examples=["soomin"]
    )
    password: str = Field(
        min_length=1, description="비밀번호.", examples=["komit-console"]
    )


class AdminLoginResponse(BaseModel):
    """운영 콘솔 로그인 결과."""

    accessToken: str = Field(
        description="운영 콘솔 전용 토큰. 사용자 토큰과 섞어 쓸 수 없다.",
        examples=["admin.eyJhbGciOiJIUzI1NiJ9"],
    )
    expiresIn: int = Field(description="토큰이 살아 있는 시간(초).", examples=[3600])
    admin: AdminResponse = Field(description="로그인한 운영자.")


class AdminReportCountResponse(BaseModel):
    """콘솔 홈의 신고 처리 칸."""

    openCount: int = Field(description="아직 종결하지 않은 신고 수.", examples=[3])
    resolvedCount: int = Field(description="종결한 신고 수.", examples=[1])


class AdminReconciliationCountResponse(BaseModel):
    """콘솔 홈의 정산 대사 칸."""

    lastResult: Literal[
        "MATCHED", "CACHE_MISMATCH", "BANK_MISMATCH", "REVIEW_REQUIRED", "ERROR"
    ] = Field(
        description="가장 최근 대사 결과. MATCHED면 장부와 잔고가 맞았다는 뜻이다.",
        examples=["MATCHED"],
    )
    lastRunAt: str = Field(
        description="가장 최근 대사를 돌린 시각.",
        examples=["2026-09-02T09:00:00+09:00"],
    )
    exceptionCount: int = Field(
        description=(
            "아직 손이 가지 않은 대사 예외 수. 최근 대사 실행 가운데 결과가 MATCHED가 "
            "아닌 것을 센 값이다."
        ),
        examples=[3],
    )


class AdminTemplateCountResponse(BaseModel):
    """콘솔 홈의 계약 양식 칸."""

    totalCount: int = Field(
        description="등록된 양식 판 수. 보관으로 내려간 판도 센다.",
        examples=[4],
    )
    activeCount: int = Field(
        description="지금 사용 중인 판 수. 유형마다 하나씩이라 유형 수와 같다.",
        examples=[3],
    )


class AdminSummaryResponse(BaseModel):
    """운영 콘솔 홈."""

    admin: AdminResponse = Field(description="로그인한 운영자.")
    asOf: str = Field(
        description="이 숫자들을 센 시각. 화면 머리의 'N월 N일 HH:MM 기준'이다.",
        examples=["2026-09-02T17:10:00+09:00"],
    )
    reports: AdminReportCountResponse = Field(description="신고 처리 칸.")
    reconciliation: AdminReconciliationCountResponse = Field(
        description="정산 대사 칸.",
    )
    contractTemplates: AdminTemplateCountResponse = Field(description="계약 양식 칸.")


class AdminReportPartyResponse(BaseModel):
    """신고에 등장하는 사람 한 명."""

    userId: int = Field(description="사용자 식별자.", examples=[12])
    name: str = Field(description="표시 이름.", examples=["김미미"])
    role: Literal["CLIENT", "DEVELOPER"] = Field(
        description="이 계약에서 맡은 쪽. CLIENT는 의뢰인, DEVELOPER는 개발자다.",
        examples=["CLIENT"],
    )


class AdminReportSummaryResponse(BaseModel):
    """신고 목록의 한 줄."""

    reportId: int = Field(description="신고 식별자.", examples=[77])
    createdAt: str = Field(
        description="접수된 시각.",
        examples=["2026-09-02T16:04:00+09:00"],
    )
    contractId: int = Field(description="문제가 생긴 계약 식별자.", examples=[130])
    projectName: str = Field(
        description="계약이 걸린 프로젝트 이름.",
        examples=["수제 비누 가게"],
    )
    contractType: Literal["CONSULTING", "DEVELOPMENT"] = Field(
        description="계약 종류.",
        examples=["DEVELOPMENT"],
    )
    reporter: AdminReportPartyResponse = Field(description="신고한 사람.")
    target: AdminReportPartyResponse = Field(description="신고 대상이 된 사람.")
    reason: str = Field(
        description=(
            "신고자가 적어 낸 내용. 목록은 길면 줄여서 보여주고 전문은 상세에서 본다."
        ),
        examples=["개발자에게 진행 상황을 문의했지만 나흘째 답변을 받지 못했습니다."],
    )
    status: Literal["OPEN", "RESOLVED"] = Field(
        description="신고 처리. OPEN은 접수됨, RESOLVED는 종결됨이다.",
        examples=["OPEN"],
    )
    resolvedAt: str | None = Field(
        description="종결한 시각. 아직이면 null이고 화면은 줄표를 그린다.",
        examples=[None],
    )


class AdminReportListResponse(BaseModel):
    """신고 목록."""

    content: list[AdminReportSummaryResponse] = Field(
        description="들어온 신고. 최근 접수가 먼저다.",
    )
    openCount: int = Field(
        description="아직 종결하지 않은 신고 수. 조건을 걸어도 전체 기준이다.",
        examples=[3],
    )
    resolvedCount: int = Field(description="종결한 신고 수.", examples=[1])
    page: int = Field(description="현재 페이지 번호. 1부터 시작한다.", examples=[1])
    size: int = Field(description="한 페이지에 담긴 최대 항목 수.", examples=[20])
    totalElements: int = Field(description="조건에 맞는 신고 수.", examples=[4])
    totalPages: int = Field(description="전체 페이지 수.", examples=[1])


class AdminReportDetailResponse(AdminReportSummaryResponse):
    """신고 상세. 목록 한 줄에 첨부를 더한다."""

    evidenceFileId: int | None = Field(
        description="증거로 올린 파일 식별자. 올리지 않았으면 null이다.",
        examples=[601],
    )
    evidenceFileName: str | None = Field(
        description="증거 파일 이름. 없으면 null이다.",
        examples=["화면 캡처.png"],
    )


class ReconciliationEscrowResponse(BaseModel):
    """대사 장부 상세의 한 줄. 예치 중인 거래 하나다."""

    contractId: int = Field(description="Komit 계약 식별자.", examples=[130])
    escrowId: int = Field(description="Escrow 쪽 식별자.", examples=[4021])
    partyLabel: str = Field(
        description="누구와 누구의 거래인지. 의뢰인 이름은 가려서 적는다.",
        examples=["미미 ↔ 이준"],
    )
    projectName: str = Field(
        description="계약이 걸린 프로젝트 이름.",
        examples=["미미헤어샵 예약"],
    )
    pendingCount: int = Field(
        description="아직 통과 확정되지 않은 작업 수. 화면의 'N건 대기'다.",
        examples=[2],
    )
    status: Literal[
        "CREATED", "DEPOSIT_PENDING", "ACTIVE", "COMPLETED", "REFUNDED", "CANCELLED"
    ] = Field(
        description="Escrow 쪽 상태. 화면은 ACTIVE를 '정상'으로 적는다.",
        examples=["ACTIVE"],
    )
    heldAmount: int = Field(
        description="이 거래로 아직 맡고 있는 금액. 원 단위 정수다.",
        examples=[300000],
    )


class ReconciliationRunResponse(BaseModel):
    """대사 한 번. 하루에 한 줄이라 asOf가 곧 그 줄의 식별자다."""

    asOf: str = Field(
        description=(
            "이 시점까지 확정된 원장과 잔고를 비교했다. 하루에 한 줄이라 이 값이 겹치지 "
            "않는다."
        ),
        examples=["2026-09-02T09:00:00+09:00"],
    )
    ledgerTotal: int = Field(
        description="Komit 장부 합계. 확정된 분개로 계산한 주계좌 잔액이다.",
        examples=[12447300],
    )
    bankBalance: int | None = Field(
        description="결제대행사 실제 잔고. 조회에 실패했으면 null이다.",
        examples=[12447300],
    )
    differenceAmount: int | None = Field(
        description="실제 잔고에서 장부를 뺀 값. 잔고를 못 받았으면 null이다.",
        examples=[0],
    )
    result: Literal[
        "MATCHED", "CACHE_MISMATCH", "BANK_MISMATCH", "REVIEW_REQUIRED", "ERROR"
    ] = Field(
        description="대사 결과. MATCHED가 아니면 화면이 '차이 발생'으로 적는다.",
        examples=["MATCHED"],
    )


class ReconciliationResponse(BaseModel):
    """정산 대사 화면 한 판."""

    latest: ReconciliationRunResponse = Field(
        description="가장 최근 대사. 화면 위쪽의 큰 숫자가 여기서 나온다.",
    )
    escrows: list[ReconciliationEscrowResponse] = Field(
        description=(
            "예치 중인 거래. 금액이 큰 것이 먼저다. 화면에 다 담지 않고 위에서 몇 줄만 "
            "보여준 뒤 나머지는 묶어 적는다."
        ),
    )
    escrowTotalAmount: int = Field(
        description="예치 중인 금액 합. 장부 합계와 같아야 한다.",
        examples=[12447300],
    )
    escrowTotalCount: int = Field(
        description=(
            "예치 중인 거래 수. 화면의 '그 외 N건'은 이 수에서 보여준 줄을 뺀 값이다."
        ),
        examples=[24],
    )
    history: list[ReconciliationRunResponse] = Field(
        description="지난 대사 실행. 최근 것이 먼저다.",
    )


class ContractTemplateSummaryResponse(BaseModel):
    """계약 양식 목록의 한 줄. 한 줄이 한 판이다."""

    templateId: int = Field(description="양식 판 식별자.", examples=[14])
    type: Literal["DEV_STANDARD", "CONSULT_STANDARD", "AMENDMENT", "ACCEPTANCE"] = (
        Field(
            description=(
                "양식 종류. DEV_STANDARD는 개발 외주 계약서, CONSULT_STANDARD는 컨설팅 "
                "계약서, ACCEPTANCE는 검수·납품 확인서, AMENDMENT는 부속 합의서다."
            ),
            examples=["DEV_STANDARD"],
        )
    )
    typeName: str = Field(
        description="화면에 그대로 쓰는 종류 이름.",
        examples=["개발 외주 계약서"],
    )
    title: str = Field(description="양식 제목.", examples=["개발 외주 표준계약서 v3"])
    isActive: bool = Field(
        description=(
            "지금 쓰는 판인지. 참이면 '사용 중', 거짓이면 '보관'이다. 한 종류에 참은 "
            "하나뿐이다."
        ),
        examples=[True],
    )
    createdAt: str = Field(
        description="등록한 시각.",
        examples=["2026-08-12T10:20:00+09:00"],
    )
    createdBy: str = Field(description="등록한 운영자 아이디.", examples=["soomin"])


class ContractTemplateTypeResponse(BaseModel):
    """고를 수 있는 양식 종류 하나와, 그 종류에서 쓸 수 있는 자리표시자."""

    type: Literal["DEV_STANDARD", "CONSULT_STANDARD", "AMENDMENT", "ACCEPTANCE"] = (
        Field(description="양식 종류.", examples=["DEV_STANDARD"])
    )
    typeName: str = Field(description="종류 이름.", examples=["개발 외주 계약서"])
    placeholders: list[str] = Field(
        description=(
            "본문에 쓸 수 있는 자리표시자. 계약 데이터로 채우는 자리이며 종류마다 "
            "다르다. 여기 없는 이름을 적으면 채워지지 않는다."
        ),
        examples=[["{{client_name}}", "{{developer_name}}", "{{total_amount}}"]],
    )


class ContractTemplateListResponse(BaseModel):
    """계약 양식 목록."""

    content: list[ContractTemplateSummaryResponse] = Field(
        description="등록된 판 전부. 사용 중인 판이 먼저고 그 뒤에 보관이 온다.",
    )
    types: list[ContractTemplateTypeResponse] = Field(
        description=(
            "고를 수 있는 종류와 자리표시자. 등록·개정 화면의 종류 드롭다운과 '넣을 값' "
            "칩이 이 값을 쓴다."
        ),
    )


class ContractTemplateDetailResponse(ContractTemplateSummaryResponse):
    """계약 양식 한 판의 본문까지."""

    body: str = Field(
        description="양식 본문. 자리표시자를 담은 HTML이다.",
        examples=["<h2>개발 외주 계약서</h2>"],
    )
    placeholders: list[str] = Field(
        description="이 종류에서 쓸 수 있는 자리표시자.",
        examples=[["{{client_name}}", "{{developer_name}}"]],
    )


class WriteContractTemplateRequest(BaseModel):
    """양식 등록·개정 요청. 두 화면이 같은 요청을 보낸다."""

    type: Literal["DEV_STANDARD", "CONSULT_STANDARD", "AMENDMENT", "ACCEPTANCE"] = (
        Field(
            description=(
                "양식 종류. 개정이면 고칠 판과 같은 종류여야 한다 — 종류는 바꿀 수 없다."
            ),
            examples=["DEV_STANDARD"],
        )
    )
    title: str = Field(
        min_length=1, description="양식 제목.", examples=["개발 외주 표준계약서 v4"]
    )
    body: str = Field(
        min_length=1,
        description="양식 본문 HTML.",
        examples=["<h2>개발 외주 계약서</h2>"],
    )
    sourceTemplateId: int | None = Field(
        default=None,
        description=(
            "고쳐서 새 판을 만드는 경우 그 판의 식별자. 새로 등록하는 경우에는 null이다. "
            "어느 쪽이든 새 판이 만들어지고 이전 판은 보관으로 내려간다."
        ),
        examples=[14],
    )


class PreviewContractTemplateRequest(BaseModel):
    """양식 미리보기 요청."""

    type: Literal["DEV_STANDARD", "CONSULT_STANDARD", "AMENDMENT", "ACCEPTANCE"] = (
        Field(description="양식 종류.", examples=["DEV_STANDARD"])
    )
    body: str = Field(
        min_length=1,
        description="아직 저장하지 않은 본문 HTML.",
        examples=["<h2>개발 외주 계약서</h2>"],
    )


class ContractTemplatePreviewResponse(BaseModel):
    """자리표시자를 채운 미리보기."""

    html: str = Field(
        description="자리표시자를 보기용 값으로 채운 HTML.",
        examples=["<h2>개발 외주 계약서</h2>"],
    )
    unknownPlaceholders: list[str] = Field(
        description=(
            "본문에 적혀 있지만 이 종류에서 채울 수 없는 자리표시자. 비어 있지 않으면 "
            "화면이 경고를 띄운다."
        ),
        examples=[[]],
    )


AREA9_TAG = "9 운영 콘솔"

ADMIN_LOGIN_FAILED = ("ADMIN_LOGIN_FAILED", "아이디 또는 비밀번호가 맞지 않습니다.")
REPORT_ALREADY_RESOLVED = ("REPORT_ALREADY_RESOLVED", "이미 종결된 신고입니다.")
TEMPLATE_TYPE_MISMATCH = (
    "TEMPLATE_TYPE_MISMATCH",
    "개정할 때는 양식 종류를 바꿀 수 없습니다.",
)
REPORT_NOT_FOUND = ("REPORT_NOT_FOUND", "신고를 찾을 수 없습니다.")
TEMPLATE_NOT_FOUND = ("TEMPLATE_NOT_FOUND", "계약 양식을 찾을 수 없습니다.")

REPORT_NOT_FOUND_EXAMPLE = {
    "code": REPORT_NOT_FOUND[0],
    "message": REPORT_NOT_FOUND[1],
}
TEMPLATE_NOT_FOUND_EXAMPLE = {
    "code": TEMPLATE_NOT_FOUND[0],
    "message": TEMPLATE_NOT_FOUND[1],
}

ADMIN_LOGIN_FAILED_EXAMPLE = {
    "code": ADMIN_LOGIN_FAILED[0],
    "message": ADMIN_LOGIN_FAILED[1],
}
REPORT_ALREADY_RESOLVED_EXAMPLE = {
    "code": REPORT_ALREADY_RESOLVED[0],
    "message": REPORT_ALREADY_RESOLVED[1],
}
TEMPLATE_TYPE_MISMATCH_EXAMPLE = {
    "code": TEMPLATE_TYPE_MISMATCH[0],
    "message": TEMPLATE_TYPE_MISMATCH[1],
}

# 목에서 실패 응답을 보고 싶을 때 쓰는 값.
ADMIN_LOGIN_ID = "soomin"
ADMIN_PASSWORD = "komit-console"
RESOLVED_REPORT_ID = 409
NOT_FOUND_REPORT_ID = 404
NOT_FOUND_TEMPLATE_ID = 404

ADMIN_EXAMPLE = {"adminId": 2, "loginId": ADMIN_LOGIN_ID}

ADMIN_SUMMARY_EXAMPLE = {
    "admin": ADMIN_EXAMPLE,
    "asOf": "2026-09-02T17:10:00+09:00",
    "reports": {"openCount": 3, "resolvedCount": 1},
    "reconciliation": {
        "lastResult": "MATCHED",
        "lastRunAt": "2026-09-02T09:00:00+09:00",
        "exceptionCount": 3,
    },
    "contractTemplates": {"totalCount": 4, "activeCount": 3},
}

ADMIN_REPORT_LIST_EXAMPLE = {
    "content": [
        {
            "reportId": 77,
            "createdAt": "2026-09-02T16:04:00+09:00",
            "contractId": 130,
            "projectName": "수제 비누 가게",
            "contractType": "DEVELOPMENT",
            "reporter": {"userId": 12, "name": "김미미", "role": "CLIENT"},
            "target": {"userId": 30, "name": "이준", "role": "DEVELOPER"},
            "reason": "개발자에게 진행 상황을 문의했지만 나흘째 답변을 받지 못했습니다.",
            "status": "OPEN",
            "resolvedAt": None,
        },
        {
            "reportId": 76,
            "createdAt": "2026-09-02T11:20:00+09:00",
            "contractId": 128,
            "projectName": "동네 모임 커뮤니티",
            "contractType": "DEVELOPMENT",
            "reporter": {"userId": 19, "name": "장서윤", "role": "CLIENT"},
            "target": {"userId": 33, "name": "박준호", "role": "DEVELOPER"},
            "reason": "계약서에 적지 않은 작업을 해 달라고 합니다.",
            "status": "OPEN",
            "resolvedAt": None,
        },
        {
            "reportId": 74,
            "createdAt": "2026-09-01T18:47:00+09:00",
            "contractId": 122,
            "projectName": "사내 재고 관리",
            "contractType": "CONSULTING",
            "reporter": {"userId": 21, "name": "최수진", "role": "CLIENT"},
            "target": {"userId": 31, "name": "한서", "role": "DEVELOPER"},
            "reason": "약속한 시각에 세션에 들어오지 않았습니다.",
            "status": "RESOLVED",
            "resolvedAt": "2026-09-04T10:30:00+09:00",
        },
        {
            "reportId": 70,
            "createdAt": "2026-08-31T09:12:00+09:00",
            "contractId": 119,
            "projectName": "미미헤어샵 예약",
            "contractType": "DEVELOPMENT",
            "reporter": {"userId": 33, "name": "박준호", "role": "DEVELOPER"},
            "target": {"userId": 12, "name": "김미미", "role": "CLIENT"},
            "reason": "낸 작업을 열흘째 확인해 주지 않습니다.",
            "status": "OPEN",
            "resolvedAt": None,
        },
    ],
    "openCount": 3,
    "resolvedCount": 1,
    "page": 1,
    "size": 20,
    "totalElements": 4,
    "totalPages": 1,
}

# 신고마다 붙은 증거 파일. 올리지 않은 신고는 여기 없고 상세에서 null로 나간다.
ADMIN_REPORT_EVIDENCE = {
    77: {"evidenceFileId": 601, "evidenceFileName": "화면 캡처.png"},
    70: {"evidenceFileId": 612, "evidenceFileName": "대화 내용.pdf"},
}


def _admin_report_detail(reportId: int) -> dict:
    """요청한 신고의 상세. 목록에 없는 id면 첫 신고로 대신한다.

    **id마다 다른 값을 준다.** 목록에서 고른 줄과 아래 상세가 따로 놀면 화면이
    골라도 바뀌지 않는 것처럼 보인다. 저장하는 것이 아니라 어느 줄을 물었는지에
    따라 정해진 예시를 고르는 것이라 호출 순서와는 무관하다.
    """
    rows = ADMIN_REPORT_LIST_EXAMPLE["content"]
    row = next((item for item in rows if item["reportId"] == reportId), rows[0])
    return {
        **row,
        "evidenceFileId": None,
        "evidenceFileName": None,
        **ADMIN_REPORT_EVIDENCE.get(row["reportId"], {}),
    }


ADMIN_REPORT_DETAIL_EXAMPLE = _admin_report_detail(77)

RECONCILIATION_EXAMPLE = {
    "latest": {
        "asOf": "2026-09-02T09:00:00+09:00",
        "ledgerTotal": 12447300,
        "bankBalance": 12447300,
        "differenceAmount": 0,
        "result": "MATCHED",
    },
    "escrows": [
        {
            "contractId": 119,
            "escrowId": 4021,
            "partyLabel": "미미 ↔ 이준",
            "projectName": "미미헤어샵 예약",
            "pendingCount": 2,
            "status": "ACTIVE",
            "heldAmount": 300000,
        },
        {
            "contractId": 126,
            "escrowId": 4033,
            "partyLabel": "박○○ ↔ 한서",
            "projectName": "쇼핑몰 결제 붙이기",
            "pendingCount": 3,
            "status": "ACTIVE",
            "heldAmount": 1200000,
        },
        {
            "contractId": 127,
            "escrowId": 4035,
            "partyLabel": "김○○ ↔ 최민",
            "projectName": "예약 알림톡",
            "pendingCount": 1,
            "status": "REFUNDED",
            "heldAmount": 450000,
        },
    ],
    "escrowTotalAmount": 12447300,
    "escrowTotalCount": 24,
    "history": [
        {
            "asOf": "2026-09-02T09:00:00+09:00",
            "ledgerTotal": 12447300,
            "bankBalance": 12447300,
            "differenceAmount": 0,
            "result": "MATCHED",
        },
        {
            "asOf": "2026-09-01T09:00:00+09:00",
            "ledgerTotal": 12183900,
            "bankBalance": 12183900,
            "differenceAmount": 0,
            "result": "MATCHED",
        },
        {
            "asOf": "2026-08-31T09:00:00+09:00",
            "ledgerTotal": 11806400,
            "bankBalance": 11756400,
            "differenceAmount": -50000,
            "result": "BANK_MISMATCH",
        },
        {
            "asOf": "2026-08-30T09:00:00+09:00",
            "ledgerTotal": 11472150,
            "bankBalance": 11472150,
            "differenceAmount": 0,
            "result": "MATCHED",
        },
        {
            "asOf": "2026-08-29T09:00:00+09:00",
            "ledgerTotal": 11118600,
            "bankBalance": 11118600,
            "differenceAmount": 0,
            "result": "MATCHED",
        },
    ],
}

DEV_PLACEHOLDERS = [
    "{{client_name}}",
    "{{developer_name}}",
    "{{milestone_list}}",
    "{{total_amount}}",
    "{{due_date}}",
]
CONSULT_PLACEHOLDERS = [
    "{{client_name}}",
    "{{developer_name}}",
    "{{session_datetime}}",
    "{{duration_minutes}}",
    "{{total_amount}}",
]
ACCEPTANCE_PLACEHOLDERS = [
    "{{client_name}}",
    "{{developer_name}}",
    "{{milestone_list}}",
    "{{handover_list}}",
]

TEMPLATE_BODY = (
    "<h2>개발 외주 계약서</h2>\n"
    "<p>의뢰인 {{client_name}}과 개발자 {{developer_name}}는 아래 조건으로 "
    "계약한다.</p>\n"
    "<h3>제1조 (작업 범위)</h3>\n"
    "<p>작업은 {{milestone_list}}로 하며, 완료 판정은 각 작업의 확인 문장으로 "
    "한다.</p>\n"
    "<h3>제2조 (대금 지급)</h3>\n"
    "<p>전 작업이 통과 확정된 뒤 {{total_amount}}원을 한 번에 지급한다.</p>"
)

# 보관된 옛 판. 지금 판과 글이 달라야 판을 나눈 뜻이 화면에 드러난다.
TEMPLATE_BODY_DEV_V2 = (
    "<h2>개발 외주 계약서</h2>\n"
    "<p>의뢰인 {{client_name}}과 개발자 {{developer_name}}는 아래 조건으로 "
    "계약한다.</p>\n"
    "<h3>제1조 (작업 범위)</h3>\n"
    "<p>작업은 {{milestone_list}}로 한다.</p>"
)

TEMPLATE_BODY_CONSULT = (
    "<h2>컨설팅 계약서</h2>\n"
    "<p>의뢰인 {{client_name}}과 개발자 {{developer_name}}는 아래 조건으로 "
    "상담을 진행한다.</p>\n"
    "<h3>제1조 (일시)</h3>\n"
    "<p>상담은 {{session_datetime}}에 {{duration_minutes}}분간 진행한다.</p>\n"
    "<h3>제2조 (대금)</h3>\n"
    "<p>대금은 {{total_amount}}원으로 한다.</p>"
)

TEMPLATE_BODY_ACCEPTANCE = (
    "<h2>검수·납품 확인서</h2>\n"
    "<p>의뢰인 {{client_name}}은 개발자 {{developer_name}}로부터 아래를 "
    "인계받았음을 확인한다.</p>\n"
    "<h3>제1조 (확인한 작업)</h3>\n"
    "<p>{{milestone_list}}의 확인 문장을 모두 통과했다.</p>\n"
    "<h3>제2조 (인계물)</h3>\n"
    "<p>{{handover_list}}을 넘겨받았다.</p>"
)

CONTRACT_TEMPLATE_LIST_EXAMPLE = {
    "content": [
        {
            "templateId": 14,
            "type": "DEV_STANDARD",
            "typeName": "개발 외주 계약서",
            "title": "개발 외주 표준계약서 v3",
            "isActive": True,
            "createdAt": "2026-08-12T10:20:00+09:00",
            "createdBy": ADMIN_LOGIN_ID,
        },
        {
            "templateId": 12,
            "type": "CONSULT_STANDARD",
            "typeName": "컨설팅 계약서",
            "title": "컨설팅 표준계약서 v2",
            "isActive": True,
            "createdAt": "2026-07-20T14:05:00+09:00",
            "createdBy": ADMIN_LOGIN_ID,
        },
        {
            "templateId": 16,
            "type": "ACCEPTANCE",
            "typeName": "검수·납품 확인서",
            "title": "인계 확인서 v1",
            "isActive": True,
            "createdAt": "2026-08-28T09:40:00+09:00",
            "createdBy": ADMIN_LOGIN_ID,
        },
        {
            "templateId": 9,
            "type": "DEV_STANDARD",
            "typeName": "개발 외주 계약서",
            "title": "개발 외주 표준계약서 v2",
            "isActive": False,
            "createdAt": "2026-06-01T11:00:00+09:00",
            "createdBy": ADMIN_LOGIN_ID,
        },
    ],
    "types": [
        {
            "type": "DEV_STANDARD",
            "typeName": "개발 외주 계약서",
            "placeholders": DEV_PLACEHOLDERS,
        },
        {
            "type": "CONSULT_STANDARD",
            "typeName": "컨설팅 계약서",
            "placeholders": CONSULT_PLACEHOLDERS,
        },
        {
            "type": "ACCEPTANCE",
            "typeName": "검수·납품 확인서",
            "placeholders": ACCEPTANCE_PLACEHOLDERS,
        },
    ],
}

# 판마다 다른 본문. 목록에서 고른 줄과 아래 본문이 함께 바뀌어야 판이 뜻을 가진다.
CONTRACT_TEMPLATE_BODIES = {
    14: TEMPLATE_BODY,
    12: TEMPLATE_BODY_CONSULT,
    16: TEMPLATE_BODY_ACCEPTANCE,
    9: TEMPLATE_BODY_DEV_V2,
}

TEMPLATE_PREVIEW_EXAMPLE = {
    "html": (
        "<h2>개발 외주 계약서</h2>\n"
        "<p>의뢰인 김미미과 개발자 이준는 아래 조건으로 계약한다.</p>"
    ),
    "unknownPlaceholders": [],
}

TEMPLATE_SAMPLE_VALUES = {
    "{{client_name}}": "김미미",
    "{{developer_name}}": "이준",
    "{{milestone_list}}": "1차 · 관리자 API에 인증 추가 외 3건",
    "{{total_amount}}": "650,000",
    "{{due_date}}": "2026년 9월 30일",
    "{{session_datetime}}": "2026년 8월 29일 14:00",
    "{{duration_minutes}}": "45",
    "{{handover_list}}": "소스 코드 · 배포 계정 · 도메인",
}

TEMPLATE_PLACEHOLDERS_BY_TYPE = {
    "DEV_STANDARD": DEV_PLACEHOLDERS,
    "CONSULT_STANDARD": CONSULT_PLACEHOLDERS,
    "ACCEPTANCE": ACCEPTANCE_PLACEHOLDERS,
    "AMENDMENT": DEV_PLACEHOLDERS,
}


def _contract_template_detail(templateId: int) -> dict:
    """요청한 판의 본문. 목록에 없는 id면 사용 중인 첫 판으로 대신한다.

    **id마다 다른 값을 준다.** 어느 줄을 물었는지에 따라 정해진 예시를 고르는
    것이라 저장하는 것도 아니고 호출 순서에 따라 달라지지도 않는다.
    """
    rows = CONTRACT_TEMPLATE_LIST_EXAMPLE["content"]
    row = next((item for item in rows if item["templateId"] == templateId), rows[0])
    return {
        **row,
        "body": CONTRACT_TEMPLATE_BODIES.get(row["templateId"], TEMPLATE_BODY),
        "placeholders": TEMPLATE_PLACEHOLDERS_BY_TYPE[row["type"]],
    }


CONTRACT_TEMPLATE_DETAIL_EXAMPLE = _contract_template_detail(14)


@router.post(
    "/admin/login",
    response_model=AdminLoginResponse,
    tags=[AREA9_TAG],
    responses={
        200: {
            "content": {
                "application/json": {
                    "example": {
                        "accessToken": "admin.eyJhbGciOiJIUzI1NiJ9",
                        "expiresIn": 3600,
                        "admin": ADMIN_EXAMPLE,
                    }
                }
            }
        },
        401: {
            "model": ErrorResponse,
            "description": "아이디 또는 비밀번호가 맞지 않음",
            "content": {"application/json": {"example": ADMIN_LOGIN_FAILED_EXAMPLE}},
        },
        **AREA1_ERRORS,
    },
    summary="운영 콘솔 로그인",
    description=(
        "**화면 — `9_로그인` › 로그인**\n\n"
        "운영자 계정으로 콘솔에 들어온다. **사용자 계정과 완전히 별개다.** 사용자는 "
        "카카오로 들어오고 운영자는 아이디와 비밀번호로 들어오며, 여기서 받은 토큰으로만 "
        "이 영역을 부를 수 있다. 반대로 사용자 토큰으로는 부를 수 없다.\n\n"
        "아이디가 없는 것인지 비밀번호가 틀린 것인지는 구분해 알려주지 않는다.\n\n"
        "- **200**: 로그인했을 때\n"
        "- **401 / ADMIN_LOGIN_FAILED**: 아이디나 비밀번호가 맞지 않을 때\n"
        "- **422 / INVALID_REQUEST**: `loginId`나 `password`가 비었을 때\n"
        "- **500 / INTERNAL_ERROR**: 로그인을 처리하지 못했을 때\n\n"
        "`loginId`가 `soomin`이고 `password`가 `komit-console`이면 성공하고, "
        "그 밖의 값이면 401을 준다."
    ),
)
def admin_login(request: AdminLoginRequest) -> AdminLoginResponse | JSONResponse:
    if request.loginId != ADMIN_LOGIN_ID or request.password != ADMIN_PASSWORD:
        return _error_response(401, ADMIN_LOGIN_FAILED)
    return AdminLoginResponse(
        accessToken="admin.eyJhbGciOiJIUzI1NiJ9",
        expiresIn=3600,
        admin=AdminResponse(**ADMIN_EXAMPLE),
    )


@router.get(
    "/admin/summary",
    response_model=AdminSummaryResponse,
    tags=[AREA9_TAG],
    responses={
        200: {"content": {"application/json": {"example": ADMIN_SUMMARY_EXAMPLE}}},
        **AREA1_UNAUTHORIZED,
        **AREA1_ERRORS,
    },
    summary="운영 콘솔 홈",
    description=(
        "**화면 — `9_홈` › 업무**\n\n"
        "콘솔 홈의 업무 카드 세 장에 들어갈 숫자를 한 번에 준다. 신고 처리, 정산 대사, "
        "계약 양식이며 각 카드의 '열기'가 그 화면으로 간다.\n\n"
        "`asOf`가 화면 머리의 '기준' 시각이다. 숫자를 센 시점이라 새로고침하면 바뀐다.\n\n"
        "- **200**: 항상\n"
        "- **401 / UNAUTHORIZED**: 운영 콘솔에 로그인하지 않았을 때\n"
        "- **500 / INTERNAL_ERROR**: 숫자를 세지 못했을 때"
    ),
)
def get_admin_summary() -> AdminSummaryResponse:
    return AdminSummaryResponse(**ADMIN_SUMMARY_EXAMPLE)


@router.get(
    "/admin/reports",
    response_model=AdminReportListResponse,
    tags=[AREA9_TAG],
    responses={
        200: {"content": {"application/json": {"example": ADMIN_REPORT_LIST_EXAMPLE}}},
        **AREA1_UNAUTHORIZED,
        **AREA1_ERRORS,
    },
    summary="신고 목록 조회",
    description=(
        "**화면 — `9_신고처리` › 들어온 신고**\n\n"
        "접수된 신고를 최근 순으로 준다. 의뢰인이 낸 문제 신고와 개발자가 낸 이의 제기가 "
        "한 목록에 섞이며, 어느 쪽이 냈는지는 `reporter.role`로 가른다.\n\n"
        "`reason`과 `target`은 목록 표에 칸이 없다. 신고자가 적어 낸 글이라 길어서 "
        "표를 넓히기만 하고, 같은 화면 아래 상세에 전문이 있기 때문이다. 목록은 어느 "
        "건인지 가리는 값만 보인다.\n\n"
        "`status`로 거르면 그 상태만 온다. `openCount`와 `resolvedCount`는 거른 조건이나 "
        "페이지와 상관없이 전체 기준이라, 「접수됨」만 보는 중에도 종결된 건수를 함께 "
        "적을 수 있다.\n\n"
        "- **200**: 항상. 신고가 없으면 빈 배열이다\n"
        "- **401 / UNAUTHORIZED**: 운영 콘솔에 로그인하지 않았을 때\n"
        "- **422 / INVALID_REQUEST**: `page`가 1보다 작거나 `size`가 범위를 벗어났을 때\n"
        "- **500 / INTERNAL_ERROR**: 목록을 만들지 못했을 때"
    ),
)
def get_admin_reports(
    status: Annotated[
        Literal["OPEN", "RESOLVED"] | None,
        Query(description="이 상태의 신고만 준다. 비우면 전부 준다."),
    ] = None,
    page: Annotated[int, Query(description="페이지 번호. 1부터 시작한다.", ge=1)] = 1,
    size: Annotated[
        int, Query(description="한 페이지에 담을 최대 항목 수.", ge=1, le=50)
    ] = 20,
) -> AdminReportListResponse:
    items = ADMIN_REPORT_LIST_EXAMPLE["content"]
    if status is not None:
        items = [item for item in items if item["status"] == status]
    return AdminReportListResponse(
        **{
            **ADMIN_REPORT_LIST_EXAMPLE,
            "content": items,
            "page": page,
            "size": size,
            "totalElements": len(items),
        }
    )


@router.get(
    "/admin/reports/{reportId}",
    response_model=AdminReportDetailResponse,
    tags=[AREA9_TAG],
    responses={
        200: {
            "content": {"application/json": {"example": ADMIN_REPORT_DETAIL_EXAMPLE}}
        },
        404: {
            "model": ErrorResponse,
            "description": "신고를 찾지 못함",
            "content": {"application/json": {"example": REPORT_NOT_FOUND_EXAMPLE}},
        },
        **AREA1_UNAUTHORIZED,
        **AREA1_ERRORS,
    },
    summary="신고 상세 조회",
    description=(
        "**화면 — `9_신고처리` › 신고 상세**\n\n"
        "신고 한 건의 전문과 첨부, 접수 상태를 준다. 계약서 원문은 여기 담지 않고 "
        "화면이 `contractId`로 `GET /api/contracts/{contractId}`를 따로 부른다.\n\n"
        "첨부는 신고자가 증거로 올린 파일 하나다. 올리지 않았으면 `evidenceFileId`가 "
        "null이고 화면에서 그 줄이 빠진다.\n\n"
        "- **200**: 항상\n"
        "- **401 / UNAUTHORIZED**: 운영 콘솔에 로그인하지 않았을 때\n"
        "- **404 / REPORT_NOT_FOUND**: `reportId`가 404일 때\n"
        "- **500 / INTERNAL_ERROR**: 상세를 만들지 못했을 때"
    ),
)
def get_admin_report(
    reportId: Annotated[
        int,
        Path(
            description="신고 식별자. 404는 없음, 409는 이미 종결됨 재현용 예약값이다.",
            examples=[77],
        ),
    ],
) -> AdminReportDetailResponse | JSONResponse:
    if reportId == NOT_FOUND_REPORT_ID:
        return _error_response(404, REPORT_NOT_FOUND)
    return AdminReportDetailResponse(**_admin_report_detail(reportId))


@router.post(
    "/admin/reports/{reportId}/resolve",
    response_model=AdminReportDetailResponse,
    tags=[AREA9_TAG],
    responses={
        200: {
            "content": {
                "application/json": {
                    "example": {
                        **ADMIN_REPORT_DETAIL_EXAMPLE,
                        "status": "RESOLVED",
                        "resolvedAt": "2026-09-02T17:12:00+09:00",
                    }
                }
            }
        },
        404: {
            "model": ErrorResponse,
            "description": "신고를 찾지 못함",
            "content": {"application/json": {"example": REPORT_NOT_FOUND_EXAMPLE}},
        },
        409: {
            "model": ErrorResponse,
            "description": "이미 종결된 신고",
            "content": {
                "application/json": {"example": REPORT_ALREADY_RESOLVED_EXAMPLE}
            },
        },
        **AREA1_UNAUTHORIZED,
        **AREA1_ERRORS,
    },
    summary="신고 종결",
    description=(
        "**화면 — `9_신고처리` › 신고 상세 › 종결 처리**\n\n"
        "신고를 종결한다. 종결한 시각이 해결일로 남고 목록에서 '종결됨'으로 바뀐다. "
        "종결되면 신고한 사람에게 알림이 간다.\n\n"
        "**종결은 판정이 아니다.** Komit은 확인 문장이 통과됐는지만 확인하고 작업의 좋고 "
        "나쁨을 판정하지 않는다. 종결은 운영자가 이 건을 다 봤다는 표시다.\n\n"
        "돌이키는 API는 두지 않는다. 같은 일이 다시 생기면 새 신고로 접수한다.\n\n"
        "- **200**: 종결했을 때\n"
        "- **401 / UNAUTHORIZED**: 운영 콘솔에 로그인하지 않았을 때\n"
        "- **404 / REPORT_NOT_FOUND**: `reportId`가 404일 때\n"
        "- **409 / REPORT_ALREADY_RESOLVED**: `reportId`가 409일 때\n"
        "- **500 / INTERNAL_ERROR**: 종결하지 못했을 때"
    ),
)
def resolve_admin_report(
    reportId: Annotated[
        int,
        Path(
            description="신고 식별자. 404는 없음, 409는 이미 종결됨 재현용 예약값이다.",
            examples=[77],
        ),
    ],
) -> AdminReportDetailResponse | JSONResponse:
    if reportId == NOT_FOUND_REPORT_ID:
        return _error_response(404, REPORT_NOT_FOUND)
    if reportId == RESOLVED_REPORT_ID:
        return _error_response(409, REPORT_ALREADY_RESOLVED)
    return AdminReportDetailResponse(
        **{
            **_admin_report_detail(reportId),
            "status": "RESOLVED",
            "resolvedAt": "2026-09-02T17:12:00+09:00",
        }
    )


@router.get(
    "/admin/reconciliation",
    response_model=ReconciliationResponse,
    tags=[AREA9_TAG],
    responses={
        200: {"content": {"application/json": {"example": RECONCILIATION_EXAMPLE}}},
        503: {
            "model": ErrorResponse,
            "description": "에스크로 서버에 연결하지 못함",
            "content": {"application/json": {"example": ESCROW_UNAVAILABLE_EXAMPLE}},
        },
        **AREA1_UNAUTHORIZED,
        **AREA1_ERRORS,
    },
    summary="정산 대사 조회",
    description=(
        "**화면 — `9_정산대사`**\n\n"
        "대사는 **Escrow 서버가 매일 09:00에 스스로 돌린다.** 이 API는 그 결과를 받아 "
        "Komit의 계약 정보와 엮어 화면 모양으로 만들어 줄 뿐, 대사를 실행하지 않는다. "
        "다시 돌리는 API도 여기 두지 않는다.\n\n"
        "**계좌는 하나이고 구분은 장부로 한다.** 거래마다 계좌를 따로 두지 않으므로 "
        "`escrows`의 줄들은 그 하나의 계좌 안에서 장부상 누구 몫인지를 나눈 것이다. "
        "그 합이 `escrowTotalAmount`이고 `latest.ledgerTotal`과 같아야 한다.\n\n"
        "거래 줄의 이름과 대기 건수는 Komit의 계약·작업에서 오고, 금액과 상태는 "
        "Escrow에서 온다. 의뢰인 이름은 가려서 내보낸다.\n\n"
        "`differenceAmount`는 실제 잔고에서 장부를 뺀 값이다. 0이 아니면 화면이 그날 "
        "줄을 '차이 발생'으로 적는다.\n\n"
        "- **200**: 항상\n"
        "- **401 / UNAUTHORIZED**: 운영 콘솔에 로그인하지 않았을 때\n"
        "- **422 / INVALID_REQUEST**: `days`가 범위를 벗어났을 때\n"
        "- **500 / INTERNAL_ERROR**: 화면을 만들지 못했을 때\n"
        "- **503 / ESCROW_UNAVAILABLE**: 에스크로 서버에 연결하지 못했을 때"
    ),
)
def get_reconciliation(
    days: Annotated[
        int,
        Query(
            description="지난 며칠치 실행을 함께 줄지. 화면은 7일을 쓴다.",
            ge=1,
            le=30,
        ),
    ] = 7,
) -> ReconciliationResponse:
    return ReconciliationResponse(
        **{
            **RECONCILIATION_EXAMPLE,
            "history": RECONCILIATION_EXAMPLE["history"][:days],
        }
    )


@router.get(
    "/admin/contract-templates",
    response_model=ContractTemplateListResponse,
    tags=[AREA9_TAG],
    responses={
        200: {
            "content": {
                "application/json": {"example": CONTRACT_TEMPLATE_LIST_EXAMPLE}
            }
        },
        **AREA1_UNAUTHORIZED,
        **AREA1_ERRORS,
    },
    summary="계약 양식 목록 조회",
    description=(
        "**화면 — `9_계약양식` › 등록된 양식 · `9_펼침_양식유형`**\n\n"
        "등록된 판을 전부 준다. **한 줄이 한 판이다.** 본문을 고치면 기존 줄을 고치지 "
        "않고 새 줄이 생기므로, 같은 종류의 옛 판도 '보관'으로 남아 여기 나온다. "
        "보관된 판을 참조하는 옛 계약은 그대로 그 판을 가리킨다.\n\n"
        "`types`는 고를 수 있는 종류와 그 종류에서 쓸 수 있는 자리표시자다. 등록·개정 "
        "화면의 종류 드롭다운과 '넣을 값' 칩이 이 값으로 그려진다. 자리표시자는 서버가 "
        "채울 수 있는 자리라 운영자가 늘릴 수 없다.\n\n"
        "- **200**: 항상\n"
        "- **401 / UNAUTHORIZED**: 운영 콘솔에 로그인하지 않았을 때\n"
        "- **500 / INTERNAL_ERROR**: 목록을 만들지 못했을 때"
    ),
)
def get_contract_templates() -> ContractTemplateListResponse:
    return ContractTemplateListResponse(**CONTRACT_TEMPLATE_LIST_EXAMPLE)


@router.get(
    "/admin/contract-templates/{templateId}",
    response_model=ContractTemplateDetailResponse,
    tags=[AREA9_TAG],
    responses={
        200: {
            "content": {
                "application/json": {"example": CONTRACT_TEMPLATE_DETAIL_EXAMPLE}
            }
        },
        404: {
            "model": ErrorResponse,
            "description": "양식을 찾지 못함",
            "content": {
                "application/json": {"example": TEMPLATE_NOT_FOUND_EXAMPLE}
            },
        },
        **AREA1_UNAUTHORIZED,
        **AREA1_ERRORS,
    },
    summary="계약 양식 본문 조회",
    description=(
        "**화면 — `9_계약양식` › 양식 본문 · `9_양식편집` › 양식 본문**\n\n"
        "판 하나의 본문까지 준다. 목록에서 줄을 고르면 오른쪽 본문 칸이 이 값을 쓰고, "
        "'새 판으로 개정'을 누르면 편집 화면이 같은 본문으로 시작한다.\n\n"
        "- **200**: 항상\n"
        "- **401 / UNAUTHORIZED**: 운영 콘솔에 로그인하지 않았을 때\n"
        "- **404 / TEMPLATE_NOT_FOUND**: `templateId`가 404일 때\n"
        "- **500 / INTERNAL_ERROR**: 본문을 가져오지 못했을 때"
    ),
)
def get_contract_template(
    templateId: Annotated[
        int,
        Path(
            description="양식 판 식별자. 404는 없음 재현용 예약값이다.",
            examples=[14],
        ),
    ],
) -> ContractTemplateDetailResponse | JSONResponse:
    if templateId == NOT_FOUND_TEMPLATE_ID:
        return _error_response(404, TEMPLATE_NOT_FOUND)
    return ContractTemplateDetailResponse(**_contract_template_detail(templateId))


@router.post(
    "/admin/contract-templates",
    response_model=ContractTemplateDetailResponse,
    status_code=201,
    tags=[AREA9_TAG],
    responses={
        201: {
            "content": {
                "application/json": {"example": CONTRACT_TEMPLATE_DETAIL_EXAMPLE}
            }
        },
        400: {
            "model": ErrorResponse,
            "description": "개정할 판과 종류가 다름",
            "content": {
                "application/json": {"example": TEMPLATE_TYPE_MISMATCH_EXAMPLE}
            },
        },
        404: {
            "model": ErrorResponse,
            "description": "개정할 판을 찾지 못함",
            "content": {
                "application/json": {"example": TEMPLATE_NOT_FOUND_EXAMPLE}
            },
        },
        **AREA1_UNAUTHORIZED,
        **AREA1_ERRORS,
    },
    summary="계약 양식 등록 · 개정",
    description=(
        "**화면 — `9_양식등록` › 등록 · `9_양식편집` › 새 판으로 저장**\n\n"
        "**두 화면이 같은 API를 쓴다.** 새로 등록하든 고쳐서 개정하든 하는 일이 같기 "
        "때문이다 — 언제나 새 판이 만들어지고, 같은 종류에 사용 중인 판이 있었다면 그 "
        "판은 보관으로 내려간다. 기존 판의 본문을 덮어쓰는 API는 두지 않는다.\n\n"
        "둘을 가르는 것은 `sourceTemplateId` 하나다. 개정이면 고칠 판의 식별자를 담고, "
        "새로 등록하면 비운다. 개정에서는 종류를 바꿀 수 없다.\n\n"
        "보관으로 내려간 판을 참조하던 계약은 그대로 그 판을 가리킨다. 이미 맺은 계약의 "
        "글이 뒤늦게 바뀌면 안 되기 때문이다.\n\n"
        "- **201**: 새 판을 만들었을 때\n"
        "- **400 / TEMPLATE_TYPE_MISMATCH**: 개정인데 `type`이 고칠 판과 다를 때\n"
        "- **401 / UNAUTHORIZED**: 운영 콘솔에 로그인하지 않았을 때\n"
        "- **404 / TEMPLATE_NOT_FOUND**: `sourceTemplateId`가 404일 때\n"
        "- **422 / INVALID_REQUEST**: `title`이나 `body`가 비었을 때\n"
        "- **500 / INTERNAL_ERROR**: 저장하지 못했을 때"
    ),
)
def write_contract_template(
    request: WriteContractTemplateRequest,
) -> ContractTemplateDetailResponse | JSONResponse:
    if request.sourceTemplateId == NOT_FOUND_TEMPLATE_ID:
        return _error_response(404, TEMPLATE_NOT_FOUND)
    return ContractTemplateDetailResponse(
        **{
            **CONTRACT_TEMPLATE_DETAIL_EXAMPLE,
            "type": request.type,
            "title": request.title,
            "body": request.body,
            "placeholders": TEMPLATE_PLACEHOLDERS_BY_TYPE[request.type],
        }
    )


@router.post(
    "/admin/contract-templates/preview",
    response_model=ContractTemplatePreviewResponse,
    tags=[AREA9_TAG],
    responses={
        200: {"content": {"application/json": {"example": TEMPLATE_PREVIEW_EXAMPLE}}},
        **AREA1_UNAUTHORIZED,
        **AREA1_ERRORS,
    },
    summary="계약 양식 미리보기",
    description=(
        "**화면 — `9_양식등록` › 미리보기 · `9_양식편집` › 미리보기 · "
        "`9_계약양식` › 미리보기**\n\n"
        "아직 저장하지 않은 본문의 자리표시자를 보기용 값으로 채워 돌려준다. 실제 계약 "
        "데이터가 아니라 운영자가 글을 확인하도록 넣는 값이다.\n\n"
        "저장하기 전에 자리표시자를 잘못 적었는지 여기서 잡는다. 그 종류에서 채울 수 "
        "없는 이름이 본문에 있으면 `unknownPlaceholders`에 담아 준다 — 그대로 저장하면 "
        "계약서에 `{{ }}`가 그대로 찍힌다.\n\n"
        "아무것도 저장하지 않는다.\n\n"
        "- **200**: 항상\n"
        "- **401 / UNAUTHORIZED**: 운영 콘솔에 로그인하지 않았을 때\n"
        "- **422 / INVALID_REQUEST**: `body`가 비었을 때\n"
        "- **500 / INTERNAL_ERROR**: 미리보기를 만들지 못했을 때"
    ),
)
def preview_contract_template(
    request: PreviewContractTemplateRequest,
) -> ContractTemplatePreviewResponse:
    html = request.body
    for name in TEMPLATE_PLACEHOLDERS_BY_TYPE[request.type]:
        html = html.replace(name, TEMPLATE_SAMPLE_VALUES[name])
    unknown = re.findall(r"\{\{[^{}]*\}\}", html)
    return ContractTemplatePreviewResponse(
        html=html, unknownPlaceholders=sorted(set(unknown))
    )


@router.get(
    "/diagnoses/{diagnosisId}/scan-file",
    response_class=Response,
    tags=[AREA2_TAG],
    responses={
        200: {
            "description": "검사 파일. 본문이 파일 내용이다.",
            "content": {"text/markdown": {"schema": {"type": "string", "format": "binary"}}},
        },
        **DIAGNOSIS_ERRORS,
        **AREA1_UNAUTHORIZED,
        500: AREA1_ERRORS[500],
    },
    summary="검사 파일 내려받기",
    description=(
        "**화면 — `2_설문3` › ① 검사 파일 받기 · 내려받기**\n\n"
        "의뢰인이 자기 AI 도구에서 돌릴 검사 파일을 내려받는다. 이 파일을 도구에 읽히면 "
        "결과 파일이 만들어지고, 그 결과 파일만 다시 올린다. 소스 코드는 올리지 "
        "않는다.\n\n"
        "- **200**: 내려받았을 때. 본문이 파일 내용이고 파일 이름은 "
        "`Content-Disposition`에 담긴다\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **404 / DIAGNOSIS_NOT_FOUND**: `diagnosisId`가 404일 때\n"
        "- **500 / INTERNAL_ERROR**: 내려주지 못했을 때"
    ),
)
def download_scan_file(
    diagnosisId: Annotated[
        int,
        Path(
            description="진단 식별자. 404는 없음 재현용 예약값이다.",
            examples=[DIAGNOSIS_ID],
        ),
    ],
) -> Response:
    if diagnosisId == NOT_FOUND_DIAGNOSIS_ID:
        return _error_response(404, DIAGNOSIS_NOT_FOUND)
    return Response(
        content=scan_file_body(diagnosisId),
        media_type="text/markdown",
        headers={"Content-Disposition": f'attachment; filename="{SCAN_FILE_NAME}"'},
    )

@router.get(
    "/deliverables/{deliverableId}/file",
    response_class=Response,
    tags=[AREA5_TAG],
    responses={
        200: {
            "description": "인계물 파일. 본문이 파일 내용이다.",
            "content": {
                "application/octet-stream": {"schema": {"type": "string", "format": "binary"}}
            },
        },
        404: {
            "model": ErrorResponse,
            "description": "인계물 없음",
            "content": {
                "application/json": {"example": DELIVERABLE_NOT_FOUND_EXAMPLE}
            },
        },
        409: {
            "model": ErrorResponse,
            "description": "아직 인계되지 않음",
            "content": {
                "application/json": {"example": DELIVERABLE_NOT_UPLOADED_EXAMPLE}
            },
        },
        **AREA1_UNAUTHORIZED,
        422: {
            "model": ValidationErrorResponse,
            "description": "요청 스키마 유효성 검증 실패",
            "content": {"application/json": {"example": DELIVERABLE_ID_ERROR_EXAMPLE}},
        },
        500: AREA1_ERRORS[500],
    },
    summary="인계물 내려받기",
    description=(
        "**화면 — `5_의뢰인_확인` › 인계 · 소스 · 계정 · 설명서 › 받기**\n\n"
        "개발자가 올린 인계물을 내려받는다. 받아본 뒤 확인 문장을 체크한다.\n\n"
        "이 계약의 의뢰인과 개발자 모두 받을 수 있다. 역할 제한이 없어 403은 없고, 당사자가 "
        "아닌 사람은 404를 받는다.\n\n"
        "- **200**: 내려받았을 때. 본문이 파일 내용이고 파일 이름은 "
        "`Content-Disposition`에 담긴다\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **404 / DELIVERABLE_NOT_FOUND**: `deliverableId`가 404일 때. 없는 인계물이거나 이 "
        "계약의 당사자가 아닌 사람이 부른 경우다. 존재를 알리지 않기 위해 404를 준다\n"
        "- **409 / DELIVERABLE_NOT_UPLOADED**: `deliverableId`가 409일 때. 개발자가 아직 "
        "올리지 않아 받을 파일이 없다\n"
        "- **422 / INVALID_REQUEST**: `deliverableId`가 정수가 아닐 때\n"
        "- **500 / INTERNAL_ERROR**: 내려주지 못했을 때"
    ),
)
def download_deliverable(
    deliverableId: Annotated[
        int,
        Path(
            description=(
                "인계물 식별자. 404는 없음, 409는 미인계 재현용 예약값이다."
            ),
            examples=[401],
        ),
    ],
) -> Response:
    if deliverableId == NOT_FOUND_DELIVERABLE_ID:
        return _error_response(404, DELIVERABLE_NOT_FOUND)
    if deliverableId == NOT_UPLOADED_DELIVERABLE_ID:
        return _error_response(409, DELIVERABLE_NOT_UPLOADED)
    return Response(
        content=DELIVERABLE_FILE_BODY,
        media_type="application/octet-stream",
        headers={
            "Content-Disposition": f'attachment; filename="{DELIVERABLE_FILE_NAME}"'
        },
    )


ContractResponse.model_rebuild()
WriteDevelopmentContractRequest.model_rebuild()


# =========================================================================== #
# 공통 실시간 통신 — 일회용 WebSocket 티켓
# =========================================================================== #


class RealtimeTicketRequest(BaseModel):
    channel: Literal["CONSULTING_SESSION", "CHAT_ROOM"] = Field(
        description="티켓으로 열 연결의 종류. 화상 세션과 채팅 연결은 서로 분리한다.",
        examples=["CONSULTING_SESSION"],
    )
    channelId: str = Field(
        min_length=1,
        max_length=50,
        pattern=r".*\S.*",
        description=(
            "연결할 컨설팅 세션 또는 채팅방 식별자. CONSULTING_SESSION이면 sessionId, "
            "CHAT_ROOM이면 chatRefId다."
        ),
        examples=["55"],
    )


class RealtimeTicketResponse(BaseModel):
    ticket: str = Field(
        description=(
            "WebSocket handshake에 한 번만 사용하는 짧은 수명의 티켓. 영속 DB에 저장하는 "
            "데이터가 아니라 Redis에서 만료·소비되므로 ERD 대상이 아니다. (R-RT-01)"
        ),
        examples=["mock-realtime-ticket-once"],
    )


REALTIME_CHANNEL_FORBIDDEN = (
    "CHANNEL_FORBIDDEN",
    "이 연결로는 다룰 수 없는 대상입니다.",
)
REALTIME_CHANNEL_FORBIDDEN_EXAMPLE = {
    "code": REALTIME_CHANNEL_FORBIDDEN[0],
    "message": REALTIME_CHANNEL_FORBIDDEN[1],
}
REALTIME_SESSION_NOT_FOUND = (
    "SESSION_NOT_FOUND",
    "세션을 찾을 수 없습니다.",
)
REALTIME_SESSION_NOT_FOUND_EXAMPLE = {
    "code": REALTIME_SESSION_NOT_FOUND[0],
    "message": REALTIME_SESSION_NOT_FOUND[1],
}
REALTIME_SESSION_ENDED = (
    "SESSION_ENDED",
    "이미 끝난 세션입니다.",
)
REALTIME_CONSENT_REQUIRED = (
    "CONSENT_REQUIRED",
    "상대가 아직 들어오지 않았습니다. 양쪽이 모두 동의해야 시작할 수 있습니다.",
)
REALTIME_ENTRY_NOT_OPEN = (
    "ENTRY_NOT_OPEN",
    "아직 입장할 수 있는 시각이 아닙니다.",
)


@router.post(
    "/realtime/tickets",
    response_model=RealtimeTicketResponse,
    tags=["공통 실시간 통신"],
    responses={
        200: {
            "description": "일회용 연결 티켓 발급",
            "content": {
                "application/json": {
                    "example": {"ticket": "mock-realtime-ticket-once"}
                }
            },
        },
        401: {
            "model": ErrorResponse,
            "description": "로그인하지 않았거나 액세스 토큰이 만료됨",
            "content": {"application/json": {"example": UNAUTHORIZED_EXAMPLE}},
        },
        403: {
            "model": ErrorResponse,
            "description": "요청한 채널 대상에 접근할 권한이 없음(예: 채팅방)",
            "content": {
                "application/json": {"example": REALTIME_CHANNEL_FORBIDDEN_EXAMPLE}
            },
        },
        404: {
            "model": ErrorResponse,
            "description": "컨설팅 세션이 없거나 요청자가 그 세션의 참가자가 아님",
            "content": {
                "application/json": {"example": REALTIME_SESSION_NOT_FOUND_EXAMPLE}
            },
        },
        409: {
            "model": ErrorResponse,
            "description": "컨설팅 세션 상태상 아직 입장할 수 없거나 이미 종료됨",
            "content": {
                "application/json": {
                    "examples": {
                        "sessionEnded": {
                            "summary": "이미 종료된 세션",
                            "value": {
                                "code": REALTIME_SESSION_ENDED[0],
                                "message": REALTIME_SESSION_ENDED[1],
                            },
                        },
                        "consentRequired": {
                            "summary": "상대 참가자의 동의 대기",
                            "value": {
                                "code": REALTIME_CONSENT_REQUIRED[0],
                                "message": REALTIME_CONSENT_REQUIRED[1],
                            },
                        },
                        "entryNotOpen": {
                            "summary": "입장 가능 시각 전 또는 예약 확인 불가",
                            "value": {
                                "code": REALTIME_ENTRY_NOT_OPEN[0],
                                "message": REALTIME_ENTRY_NOT_OPEN[1],
                            },
                        },
                        "sessionCancelled": {
                            "summary": "취소된 계약의 세션",
                            "value": {
                                "code": SESSION_CANCELLED[0],
                                "message": SESSION_CANCELLED[1],
                            },
                        },
                    }
                }
            },
        },
        422: {
            "model": ValidationErrorResponse,
            "description": "요청 스키마 유효성 검증 실패",
            "content": {
                "application/json": {
                    "example": {
                        "code": "INVALID_REQUEST",
                        "message": "요청 형식이 올바르지 않습니다.",
                        "errors": [
                            {"field": "channelId", "reason": "1자 이상이어야 합니다."}
                        ],
                    }
                }
            },
        },
    },
    summary="WebSocket 연결 티켓 발급",
    description=(
        "인증된 사용자가 Raw WebSocket 연결을 열기 전에 짧은 수명의 일회용 티켓을 "
        "발급받는다. 티켓은 사용자, 채널, 채널 식별자와 허용 기능에 묶이며 한 번 사용하면 "
        "폐기된다. (R-RT-01)\n\n"
        "- **200**: 요청한 세션 또는 채팅방에 참여할 수 있을 때\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았거나 액세스 토큰이 만료되었을 때\n"
        "- **403 / CHANNEL_FORBIDDEN**: 요청한 채널 대상에 접근할 권한이 없을 때. "
        "목에서는 `channelId`가 `403`이면 재현한다\n"
        "- **404 / SESSION_NOT_FOUND**: 컨설팅 세션이 없거나 참가자가 아니어서 존재를 "
        "숨길 때. CONSULTING_SESSION의 `channelId`가 `404`이면 재현한다\n"
        "- **409 / SESSION_ENDED**: 컨설팅 세션이 이미 끝났을 때. CONSULTING_SESSION의 "
        "`channelId`가 `409`이면 재현한다\n"
        "- **409 / CONSENT_REQUIRED**: 상대 참가자의 동의를 기다릴 때. CONSULTING_SESSION의 "
        "`channelId`가 `410`이면 재현한다\n"
        "- **409 / ENTRY_NOT_OPEN**: 입장 가능 시각 전이거나 예약을 확인할 수 없을 때. "
        "CONSULTING_SESSION의 `channelId`가 `411`이면 재현한다\n"
        "- **409 / SESSION_CANCELLED**: 의뢰인이 취소한 계약의 세션일 때 (R-CON-21). "
        f"CONSULTING_SESSION의 `channelId`가 `{CANCELLED_SESSION_ID}`이면 재현한다\n"
        "- **422 / INVALID_REQUEST**: 채널이 허용된 값이 아니거나 channelId가 비었거나 "
        "50자를 초과했을 때"
    ),
)
def issue_realtime_ticket(
    payload: RealtimeTicketRequest,
) -> RealtimeTicketResponse | JSONResponse:
    if payload.channelId == "403":
        return _error_response(403, REALTIME_CHANNEL_FORBIDDEN)
    if payload.channel == "CONSULTING_SESSION":
        if payload.channelId == "404":
            return _error_response(404, REALTIME_SESSION_NOT_FOUND)
        if payload.channelId == "409":
            return _error_response(409, REALTIME_SESSION_ENDED)
        if payload.channelId == "410":
            return _error_response(409, REALTIME_CONSENT_REQUIRED)
        if payload.channelId == "411":
            return _error_response(409, REALTIME_ENTRY_NOT_OPEN)
        if payload.channelId == str(CANCELLED_SESSION_ID):
            return _error_response(409, SESSION_CANCELLED)
    return RealtimeTicketResponse(ticket="mock-realtime-ticket-once")

# 개발자가 상담 요청을 수락·거절하는 기능은 만들지 않기로 했다.
# ERD의 recommendation_items.status(RECOMMENDATION_DEV_RESPONSE)는 쓰지 않는다.
# 의뢰인이 상담을 요청하면 방이 바로 열린다.
#
# 추천 관련 ERD 컬럼을 어떻게 채우는지 — API 응답에는 나가지 않는 값들이다.
#
#   recommendation_batches.viewed_at
#       추천 결과 조회가 처음 불릴 때 찍는다. 이 값이 비어 있으면 아직 열어보지 않은
#       회차이므로, 추천 받기를 다시 불러도 새 회차를 만들지 않고 그 회차를 돌려준다.
#   recommendation_batches.trigger_type / parent_batch_id
#       서버가 정한다. 프로젝트의 첫 회차면 CONSULT_REQUEST, 이미 회차가 있으면
#       REMATCH 로 적고 직전 회차를 부모로 잇는다. MANUAL 은 운영에서만 쓴다.
#   recommendation_batches.outcome
#       계약이 완료되거나 파기될 때 서버가 갱신한다. 추천으로 만난 개발자와 계약했으면
#       CONTRACTED, 상담만 하고 끝났으면 CONSULTED_ONLY, 추천 밖 개발자와 계약했으면
#       OUTSIDE 다. 확정 전에는 비어 있다.
#   recommendation_items.impressed_at
#       추천 결과 조회가 목록에 실어 보낸 항목 전부에 찍는다. 화면이 스크롤로 실제로
#       보여준 것만 가려내지는 않는다. 그 정확도를 얻으려면 화면이 노출을 따로
#       알려줘야 하는데, 그만한 API 를 두지 않기로 했다.
#   recommendation_items.profile_viewed_at
#       쓰지 않는다. 개발자 프로필 조회는 로그인 없이도 부르는 공개 API 라 어느
#       추천에서 눌러 들어왔는지 알 수 없고, 그것을 알리자고 공개 API 에 추적 값을
#       얹지 않는다.
#
# 방 상세의 값을 어디서 만드는지 — 한 컬럼으로 바로 나오지 않는 것들이다.
#
#   RoomSummaryResponse 에 의뢰인 식별자를 담지 않는 이유
#       rooms 에 client_id 가 없다. 방의 의뢰인은 projects.client_id 이며 같은
#       프로젝트의 방은 모두 같은 사람이다. 화면도 developer_id 하나로 보낸 의뢰와
#       받은 의뢰를 가르므로 응답에 넣지 않는다.
#   counterpartName
#       의뢰인이 보면 rooms.developer_id -> developers.user_id -> users.name,
#       개발자가 보면 projects.client_id -> users.name 이다. users.is_deleted 가
#       참이면 이름 대신 '탈퇴한 사용자' 를 내보낸다.
#   request.issueCount
#       rooms.project_id -> diagnoses -> inspections -> issues 로 내려가 센다.
#       **이번 회차만 센다** — inspections.diagnosis_version = diagnoses.version 인
#       검사의 이슈만 대상이며, 그중 status 가 RESOLVED 가 아닌 것을 센다. 회차
#       조건을 빠뜨리면 재진단한 프로젝트에서 숫자가 회차만큼 불어난다.
#   request.updatedAt
#       diagnoses.updated_at 을 쓴다. issues 에는 updated_at 컬럼이 없고
#       qa_criteria 의 것만 보면 요구사항 제목을 고쳤을 때 값이 움직이지 않는다.
#       요청서 저장이 진단 행도 함께 갱신하게 해서 한 값으로 모은다.
#   contractId
#       contracts.room_id = rooms.id 로 찾는다. 컬럼에 UNIQUE 가 걸려 있지 않지만
#       방 하나에 계약은 하나로 두기로 했다. 파기 뒤 재계약을 허용하게 되면 그때
#       어느 것을 내려줄지 다시 정해야 한다.
#   convertedRoomId / convertedRoomType
#       rooms 에 상담방과 이어진 방을 잇는 컬럼이 없다. 같은 프로젝트·같은 개발자의
#       방 가운데 용도가 INQUIRY 가 아닌 것을 찾아 잇는다.
#           WHERE project_id = ? AND developer_id = ? AND type <> 'INQUIRY'
#       이 추정이 성립하는 근거는 두 가지다. 상담 요청이 멱등이라 (프로젝트, 개발자)
#       마다 상담방이 하나뿐이고, 한 상담방은 한 번만 전환되므로(두 번째는 409)
#       이어지는 방도 하나다.
#
#       ⚠️ 컨설팅 방에서 개발로 다시 이어가는 길을 만들면 이 규칙이 깨진다. 한
#       (프로젝트, 개발자)에 CONSULTING 과 DEVELOPMENT 가 함께 생겨 어느 것이 상담방의
#       다음인지 가릴 수 없다. 그때는 rooms.parent_room_id 를 두어야 한다.
#
#       목 데이터는 갈래 두 쪽을 다 보여주려고 방 92(컨설팅)와 93(개발)을 같은
#       (프로젝트 8, 개발자 3)에 함께 두었다. 실서버에서는 상담방 91 이 한 번만
#       전환되므로 둘 중 하나만 생기지만, 목 데이터에 위 질의를 그대로 돌리면 둘 다
#       걸린다. 목을 보고 이 추정을 검증하지 않는다.


# =========================================================================== #
# 태그 설명
#
# 영역마다 무엇을 다루는지, 어떤 화면이 붙는지, 앞 영역에서 무엇을 가져다 쓰는지를
# 문서에 싣는다. 소스 주석은 문서에 나오지 않으므로 같은 내용을 여기에 둔다.
# app/main.py 가 create_app 에 넘긴다.
# =========================================================================== #

SERVICE_TAGS = [
    {
        "name": "default",
        "description": (
            "**규약의 기준 API.** 성공·실패 응답 스키마, 상태코드, description 작성 "
            "방식을 이 두 API에 맞춘다. 도메인 데이터를 다루지 않는다.\n\n"
            "규약이 바뀌면 여기를 먼저 고치고 나머지를 따라 맞춘다."
        ),
    },
    {
        "name": "공통 실시간 통신",
        "description": (
            "Raw WebSocket 연결 전에 필요한 공통 REST 계약이다. WebRTC와 채팅은 같은 "
            "Envelope를 사용하지만 티켓과 물리 연결은 각각 발급하고 연다. 티켓은 Redis에서 "
            "짧게 유지되며 한 번 사용하면 폐기된다."
        ),
    },
    {
        "name": "1 랜딩 · 로그인",
        "description": (
            "랜딩에서 무엇을 파는지 보고 로그인한다.\n\n"
            "**화면** `1_랜딩홈` · `1_로그인` · 가입 약관 동의 · 계좌 등록 제안\n\n"
            "랜딩 홈은 히어로 · 증상별로 찾기 · 추천 개발자 · 진행 순서 · 사전 점검 · "
            "푸터 여섯 섹션이 한 페이지에 들어간다.\n\n"
            "첫 카카오 로그인으로 계정을 만든 뒤 필수 약관 네 항목을 가입 화면에서 "
            "명시적으로 동의받는다. 로그인 안내 문구만으로 동의 처리하지 않는다. "
            "계좌 등록은 가입 중 선택하거나 마이페이지에서 나중에 할 수 있다.\n\n"
            "**여러 영역이 함께 쓰는 API가 여기 있다.** 토큰 재발급, 로그아웃, 내 정보는 전 영역에서 부르지만 처음 나온 이 영역에만 "
            "적는다.\n\n"
            "---\n\n"
            "### 인증\n\n"
            "로그인과 재발급을 뺀 모든 API는 헤더에 `Authorization: Bearer <accessToken>`을 실어 부른다.\n\n"
            "**공개 API 넷은 예외다.** 코드 목록, 개발자 찾기, 개발자 프로필, 개발자 리뷰 목록은 "
            "토큰 없이 부를 수 있다. 랜딩의 '증상별로 찾기'가 로그인 전 진입 경로라서다. 이 넷은 "
            "`401`을 선언하지 않는다.\n\n"
            "**토큰에는 사용자 id와 만료 시각만 들어 있다.**\n\n"
            "**리프레시 토큰은 액세스 토큰을 다시 받을 때만 쓴다.** 다시 받으면 리프레시 토큰도 새 값이 되고 이전 값은 못 쓴다.\n\n"
            "**운영 콘솔은 토큰이 따로다.** `9 운영 콘솔`의 로그인으로 받으며, 사용자 토큰으로는 운영 API를 부를 수 없다.\n\n"
            "⚠️ 이 목 서버는 토큰을 검사하지 않는다. 헤더 없이 불러도 성공 응답이 나온다. 각 API에 적힌 `401`은 실서버 기준 "
            "명세다."
        ),
    },
    {
        "name": "2 사전점검",
        "description": (
            "설문 4단계로 상황을 받고, 의뢰인이 자기 AI 도구로 돌린 검사 결과를 올리면 "
            "진단서를 만든다. 진단서에서 고칠 항목을 고르면 요청서로 넘어간다.\n\n"
            "**화면** `2_설문1`~`2_설문4` · `2_진단서` · `2_요구사항선택` · "
            "`2_클릭_직접해결했어요` · `2_요청서` · `2_의뢰인_요청서수정`\n\n"
            "**요청서는 따로 저장되는 문서가 아니다.** 고르고 다듬은 요구사항과 그 확인 "
            "문장을 모아 부르는 이름이라, 조회는 결함 목록 API가 그대로 맡고 저장은 요청서 "
            "저장 API 하나가 통째로 받는다. 요구사항 줄이나 확인 문장마다 API를 두지 "
            "않는다.\n\n"
            "**설문1~3은 모든 프로젝트에 같은 고정 문항**이라 화면이 문구를 갖고 있다. "
            "**설문4는 검사 결과에서 나온 질문**이라 프로젝트마다 다르고 반드시 설문 조회로 "
            "받아 그린다.\n\n"
            "**검사 과정에 서버 AI를 쓰지 않는다.** 올라온 결과 파일에는 발견한 문제뿐 "
            "아니라 코드만으로 판단할 수 없어 물어야 할 질문도 담겨 있고, 서버는 그것을 "
            "규칙으로 읽어 설문4 문항과 진단서를 만든다.\n\n"
            "**함께 쓰는 API** `GET /api/users/me`"
        ),
    },
    {
        "name": "3 개발자 찾기 · 상담방",
        "description": (
            "개발자를 고르고, 무료 상담방에서 이야기한 뒤, 컨설팅과 개발 중 갈래를 정한다.\n\n"
            "**화면** `3_의뢰인_개발자찾기` · `3_의뢰인_개발자프로필` · "
            "`3_의뢰인_개발자추천` · `3_의뢰인_상담방_대화` · `3_의뢰인_상담방_컨설팅전환` · `3_의뢰인_상담방_개발전환` · "
            "`3_개발자_개발계약서요청받음`\n\n"
            "**상담 요청 API는 들어온 길에 따라 둘이다.** 추천에서 고른 개발자는 추천 "
            "회차의 상담 요청 API로, 직접 찾은 개발자는 프로젝트 상담 요청 API로 보낸다. "
            "직접 찾은 길은 회차를 만들지 않아 방의 `recommendationItemId`가 비어 있다.\n\n"
            "**개발자가 상담 요청을 수락하거나 거절하지 않는다.** 요청하면 그 자리에서 "
            "무료 상담방이 열리고, 개발자는 사이드바의 '받은 의뢰'에서 본다.\n\n"
            "**채팅 메시지는 이 API가 다루지 않는다.** 방 응답의 `chatRefId`로 채팅 쪽에 "
            "넘긴다. 사이드바의 마지막 메시지 미리보기도 그쪽에서 가져온다.\n\n"
            "**함께 쓰는 API** `GET /api/users/me` · `GET /api/diagnoses/{id}`"
        ),
    },
    {
        "name": "4 컨설팅",
        "description": (
            "개발자가 컨설팅 계약서를 쓰고, 양쪽이 서명한 뒤, 정해진 시각에 세션을 하고, "
            "끝나면 요약을 받아 요청서에 반영한다.\n\n"
            "**화면** `4_개발자_계약서작성` · `4_개발자_계약서검토` · "
            "`4_의뢰인_계약서상세` · `4_의뢰인_계약서수정제안` · "
            "`4_의뢰인_입장전` · `4_의뢰인_세션중` · `4_의뢰인_요약`\n\n"
            "**계약 관련 API는 컨설팅과 개발이 함께 쓴다.** 조회·수정 제안·서명·전달 "
            "메시지·예치가 모두 여기 있고, 응답은 `contractType`에 따라 `consulting` 또는 "
            "`development` 한쪽만 채워진다.\n\n"
            "계약서는 판을 나누지 않는다. 수정 제안은 그 계약서 자체를 바꾸며, 한쪽이라도 "
            "서명한 뒤에는 고칠 수 없다.\n\n"
            "**세션 중의 화상과 채팅은 다루지 않는다.** 입장 API는 들어갈 수 있는지 판정하고 "
            "기록만 남긴다.\n\n"
            "**함께 쓰는 API** `GET /api/users/me` · `GET /api/rooms` · "
            "`GET /api/rooms/{roomId}`"
        ),
    },
    {
        "name": "5 개발",
        "description": (
            "개발자가 계약서를 쓰고, 의뢰인이 서명하며 전액을 예치하고, 작업을 하나씩 "
            "확인해 전부 통과하면 대금이 한 번에 나간다.\n\n"
            "**화면** `5_개발자_계약서작성` · `5_개발자_계약서검토` · "
            "`5_의뢰인_계약서상세` · `5_의뢰인_계약서수정제안` · "
            "`5_의뢰인_계약예치` · `5_개발자_착수전` · `5_의뢰인_진행` · "
            "`5_의뢰인_확인` · `5_개발자_확인` · `5_의뢰인_리뷰작성`\n\n"
            "**일괄정산이다.** 작업마다 따로 납기를 두지 않고 최종 납기일 하나만 있으며, "
            "대금도 모든 작업이 통과된 뒤 한 번에 나간다.\n\n"
            "**인계는 가격 0원짜리 작업 하나**로 다른 작업과 같은 구조에 들어간다. "
            "제출·통과와 3일 자동 통과를 그대로 탄다.\n\n"
            "**돈의 원장은 Escrow 서버가 가진다.** 여기서는 예치를 걸고 상태를 확인만 한다.\n\n"
            "계약 파기와 하자보수는 운영 콘솔에서 다룬다.\n\n"
            "**함께 쓰는 API** 계약 조회·수정 제안·서명·전달 메시지·예치는 "
            "`4 컨설팅`에 있다."
        ),
    },
    {
        "name": "6 문제 신고 · 이의 제기",
        "description": (
            "계약이 도는 동안 말이 통하지 않을 때 운영자에게 알린다.\n\n"
            "**화면** `6_의뢰인_문제신고` · `6_개발자_이의제기`\n\n"
            "**의뢰인이 내면 문제 신고, 개발자가 내면 이의 제기**라고 부르지만 접수되는 "
            "내용과 처리 방식은 같다. 접수는 계약이나 작업 상태를 바꾸지 않고, Komit이 "
            "누가 옳은지 정하지도 않는다.\n\n"
            "확인 중인 신고가 있는 계약은 작업이 3일 만에 자동 통과되지 않는다.\n\n"
            "접수된 신고를 확인하고 종결하는 것은 `9 운영 콘솔`에서 다룬다.\n\n"
            "**함께 쓰는 API** `GET /api/rooms/{roomId}` · "
            "`GET /api/contracts/{contractId}`"
        ),
    },
    {
        "name": "7 정산",
        "description": (
            "의뢰인은 쓴 돈과 맡긴 돈을, 개발자는 받을 돈을 본다.\n\n"
            "**화면** `7_의뢰인_소비내역` · `7_의뢰인_예치금` · `7_의뢰인_예치금원장` · "
            "`7_개발자_소득내역` · `7_개발자_소득내역_계좌없음`\n\n"
            "**돈의 원본은 Escrow 서버가 가진다.** 예치·지급·환불 금액과 원장 전표는 "
            "그쪽에 있고, 여기 API는 화면에 필요한 만큼만 받아와 Komit의 계약·작업 정보와 "
            "엮는다.\n\n"
            "**소득 내역의 계좌 칸은 보여주기만 한다.** 대금을 받을 계좌를 등록하고 "
            "바꾸는 화면은 마이페이지 하나뿐이라, 이 영역에는 계좌를 고치는 API가 없다.\n\n"
            "**통과시킨 작업의 대금도 계약이 전부 끝날 때까지 예치금에 남는다.** "
            "일괄정산이라 통과가 곧 지급이 아니다.\n\n"
            "**함께 쓰는 API** `GET /api/contracts/{contractId}`"
        ),
    },
    {
        "name": "8 계정 · 공통",
        "description": (
            "마이페이지에서 계정·계좌·알림을 관리하고, 자기 개발자 프로필과 의뢰인·"
            "개발자로 참여한 일정을 본다. 의뢰 흐름 밖 화면이다.\n\n"
            "### 화면과 API\n\n"
            "| 화면 | API |\n"
            "| --- | --- |\n"
            "| `8_마이페이지` · `8_마이페이지_등록전` | `GET /api/mypage` |\n"
            "| `8_마이페이지` › 이름 바꾸기 | `PATCH /api/users/me` |\n"
            "| `8_마이페이지` › 사진 바꾸기 | `PUT /api/users/me/profile-image` |\n"
            "| `8_마이페이지` › 알림 끄기·켜기 | `PATCH /api/settings/notification` |\n"
            "| `8_계좌등록` | `POST /api/accounts/verifications` |\n"
            "| `8_계좌인증` | `POST /api/accounts/verifications/{verificationId}/confirm` |\n"
            "| `8_알림` | `GET /api/notifications` · "
            "`POST /api/notifications/read-all` · "
            "`PATCH /api/notifications/{notificationId}/read` |\n"
            "| `8_회원탈퇴` | `GET /api/users/me/withdrawal` · "
            "`POST /api/users/me/withdrawal` |\n"
            "| `8_개발자_내프로필` (만들기 · 고치기) | "
            "`GET /api/users/me/developer-profile` · "
            "`PUT /api/users/me/developer-profile` · "
            "`PUT /api/reviews/{reviewId}/reply` |\n"
            "| `8_내일정` | `GET /api/users/me/sessions` |\n"
            "| `8_마이페이지` · `8_개발자_내프로필` › 파일 올리기 | "
            "`POST /api/uploads` · `POST /api/uploads/{uploadId}/complete` |\n"
            "| `8_펼침_은행선택` | `GET /api/codes/{groupKey}` — 영역 3 |\n"
            "| `8_아바타메뉴` | `POST /api/auth/logout` — 영역 1 |\n"
            "| `8_약관` | 없음 |\n\n"
            "`8_약관`은 화면이 가진 정적 문서라 서버가 줄 것이 없다. `8_아바타메뉴`는 "
            "`GET /api/users/me`로 이미 받은 값을 그리고 로그아웃만 부른다. "
            "`8_펼침_은행선택`은 은행 목록을 코드 조회로 받는다 — `groupKey`는 "
            "`BANK_CODE`다.\n\n"
            "**개발자 프로필을 만드는 API는 따로 없다.** "
            "`PUT /api/users/me/developer-profile`이 없으면 만들고 있으면 고친다. "
            "마이페이지의 「프로필 생성」과 「프로필 편집」이 같은 화면으로 오고, "
            "그 화면의 저장 버튼 하나가 두 경우를 다 맡는다.\n\n"
            "필수 약관 **동의 여부**는 가입 영역의 `GET /api/settings/terms`로 조회하고 "
            "`PUT /api/settings/terms`로 기록한다. 판매자 약관은 개발자 프로필을 만드는 "
            "것에 묶여 있다. 약관 문서는 마이페이지 약관 칸에서 본다.\n\n"
            "**함께 쓰는 API** `GET /api/users/me` (전 영역 헤더) · "
            "`POST /api/auth/logout` · `GET /api/codes/{groupKey}` · "
            "`GET /api/settlements/income` (개발자 소득 내역의 계좌 칸)\n\n"
            "---\n\n"
            "### 인가\n\n"
            "**이 영역은 전부 내 것을 다루므로 로그인 여부만 보고 통과시키지 않는다.** "
            "액세스 토큰을 검증해 `sub`를 꺼내고, 다루려는 자원의 주인이 그 `sub`와 "
            "같은지 확인한 뒤에 처리한다.\n\n"
            "- 대상을 요청에 담지 않는 API는 `sub`가 곧 대상이다. 요청에 사용자 "
            "식별자를 실어 보내도 받지 않는다\n"
            "- 경로나 본문에 식별자가 있는 API는 그 자원의 주인이 `sub`와 같은지 "
            "확인하고, 다르면 403 `FORBIDDEN`으로 막는다\n\n"
            "**대상을 담지 않는 API에는 403 `FORBIDDEN`이 없다.** 남의 것을 가리킬 "
            "자리가 없어 나올 수가 없다. 토큰을 고쳐 남의 `sub`를 넣어도 서명이 깨져 "
            "401에서 끝난다. 403이 실려 있는 API는 요청에 남의 식별자를 적을 수 있는 "
            "쪽이다.\n\n"
            "**403은 두 뜻으로 쓴다.** `FORBIDDEN`은 남의 자원을 건드렸다는 뜻이고, "
            "`NO_DEVELOPER_PROFILE`은 개발자 프로필이 반드시 필요한 API에서 프로필이 "
            "없다는 뜻이다. 내 일정은 의뢰인 일정도 포함하므로 이 오류를 쓰지 않는다. "
            "화면 분기는 상태코드가 아니라 `code`로 가른다.\n\n"
            "---\n\n"
            "### 파일\n\n"
            "**파일은 이 서버를 거치지 않는다.** 올릴 파일을 알리면 보낼 주소를 받고, "
            "화면이 그 주소로 파일을 직접 보낸 뒤 완료를 알린다.\n\n"
            "1. `POST /api/uploads` 보낼 주소를 받는다\n"
            "2. 받은 주소로 `PUT` 파일을 직접 보낸다\n"
            "3. `POST /api/uploads/{uploadId}/complete` 제대로 올라왔는지 확인받고 "
            "`fileId`를 받는다\n"
            "4. 그 `fileId`를 쓰는 곳에 붙인다\n\n"
            "**올리기와 붙이기는 별개다.** 프로필 사진은 저장 버튼이 없는 화면이라 "
            "`PUT /api/users/me/profile-image`로 즉시 붙이고, 증빙 서류와 작업물 사진은 "
            "프로필 저장 요청에 `fileId`를 실어 저장할 때 붙는다.\n\n"
            "---\n\n"
            "### 그 밖에 알아 둘 것\n\n"
            "**내 프로필은 줄마다 저장하지 않는다.** 저장 버튼 하나가 소개·기술·경력·"
            "작업물·가격표를 통째로 보내고, 보낸 내용으로 맞춘다.\n\n"
            "**회원 탈퇴는 지우는 것이 아니다.** 사용자 정보는 그대로 두고 삭제 여부만 "
            "참으로 바꾼 뒤 삭제 시각을 적는다. 계약·정산 이력과 메시지, 리뷰가 상대 "
            "화면에도 있는 기록이라 남겨야 하며, 그래서 `DELETE`가 아니라 `POST`다.\n\n"
            "**계좌는 1원 인증으로만 등록한다.** 계좌를 넣는 요청과 입금자명 뒤 네 "
            "자리를 내는 요청이 나뉘고, 확인이 끝나는 순간에만 계좌가 남는다. 사람마다 "
            "한 개이며 개발자가 대금을 받는 데에 쓴다. 환불은 회원가입 때 발급한 SSAFY "
            "계좌로 나간다(`R-PAY-04`). 계좌번호 원문은 보관하지 않고 응답에는 언제나 "
            "가려서 나간다.\n\n"
            "**에스크로 서버는 SSAFY 금융망과 코밋 사이의 프록시다.** 클라이언트는 위 "
            "API들의 간소화된 형식만 쓰고, 코밋이 에스크로에 보낼 때 SSAFY 금융망 "
            "형식(`Header`/`REC`)으로 바꾼다. 금융망 응답코드는 코밋의 `code`로 옮겨져 "
            "나간다."
        ),
    },
    {
        "name": "9 운영 콘솔",
        "description": (
            "Komit 내부 직원만 쓴다. 흐름 밖 화면이고 의뢰인·개발자에게는 보이지 "
            "않는다.\n\n"
            "**화면** `9_로그인` · `9_홈` · `9_신고처리` · `9_정산대사` · "
            "`9_계약양식` · `9_양식편집` · `9_양식등록` · `9_펼침_양식유형`\n\n"
            "**운영자 계정은 사용자와 완전히 별개다.** 사용자는 카카오로, 운영자는 "
            "아이디와 비밀번호로 들어온다. 토큰도 따로 발급하며 사용자 토큰으로는 이 "
            "영역을 부를 수 없다.\n\n"
            "**정산 대사는 Escrow 서버가 매일 스스로 돌린다.** 여기 API는 그 결과를 받아 "
            "Komit의 계약 정보와 엮어 줄 뿐 대사를 실행하지 않는다.\n\n"
            "**계약 양식은 고치지 않는다.** 본문을 바꾸면 늘 새 판이 생기고 이전 판은 "
            "보관으로 내려간다. 한 종류에 사용 중인 판은 언제나 하나이며, 보관된 판을 "
            "참조하던 계약은 그대로 그 판을 가리킨다.\n\n"
            "**함께 쓰는 API** `GET /api/contracts/{contractId}`"
        ),
    },
]
