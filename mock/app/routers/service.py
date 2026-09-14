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

import re

from typing import Annotated, Literal

from fastapi import (
    APIRouter,
    File,
    Form,
    Path,
    Query,
    Request,
    Response,
    UploadFile,
)
from fastapi.encoders import jsonable_encoder
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

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
    if error_type == "string_too_long":
        return f"{context.get('max_length')}자 이하여야 합니다."
    if error_type == "string_too_short":
        min_length = context.get("min_length")
        if min_length == 1:
            return "필수 항목입니다."
        return f"{min_length}자 이상이어야 합니다."
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

    codeId: int = Field(description="코드 항목 식별자.", examples=[4])
    codeName: str = Field(
        description="화면에 그대로 보여주는 코드 항목 이름.",
        examples=["백엔드"],
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
            "그때 화면은 이름 첫 글자를 동그라미에 넣어 보여준다."
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
        description="주로 담당하는 개발 분야. 등록하지 않았으면 null이다.",
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
            "개발자 프로필 보유 여부. 참이면 헤더에 의뢰인·개발자 전환이 보인다."
        ),
        examples=[False],
    )


class TokenResponse(BaseModel):
    """토큰 발급 응답. 재발급도 같은 형태를 쓴다."""

    accessToken: str = Field(
        description="API 호출에 쓰는 토큰. Authorization 헤더에 Bearer로 실어 보낸다.",
        examples=["eyJhbGciOiJIUzI1NiJ9.example"],
    )
    refreshToken: str = Field(
        description=(
            "액세스 토큰을 다시 받을 때 쓰는 토큰. 재발급하면 이전 값은 못 쓴다."
        ),
        examples=["f8c1d2e3-4b5a-6789-abcd-ef0123456789"],
    )
    expiresIn: int = Field(
        description="액세스 토큰이 유효한 시간. 초 단위다.",
        examples=[3600],
    )


class KakaoLoginResponse(TokenResponse):
    """카카오 로그인 응답. 토큰 응답에 가입 여부와 사용자 정보를 더한다."""

    isNewUser: bool = Field(
        description="이번 로그인으로 새로 가입했는지. 참이면 처음 들어온 사용자다.",
        examples=[False],
    )
    user: LoginUserResponse = Field(description="로그인한 사용자.")


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
            "직접 올린 프로필 사진의 파일 식별자. 올린 사진이 없으면 null이고, "
            "그때 화면은 이름 첫 글자를 동그라미에 넣어 보여준다."
        ),
        examples=[None],
    )
    hasDevProfile: bool = Field(
        description="개발자 프로필 보유 여부. 헤더의 의뢰인·개발자 전환 노출을 정한다.",
        examples=[False],
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


# --------------------------------------------------------------------------- #
# 영역 1 — 목 데이터
# --------------------------------------------------------------------------- #

WEEK_START_AT = "2026-09-07T00:00:00+09:00"

FEATURED_DEVELOPERS = [
    {
        "developerId": 3,
        "name": "이준",
        "profileImageFileId": None,
        "intro": "간단한 상담도 받습니다.",
        "yearsOfExperience": 5.0,
        "field": {"codeId": 4, "codeName": "백엔드"},
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
        "intro": "급한 장애 대응 잘합니다.",
        "yearsOfExperience": 6.0,
        "field": {"codeId": 4, "codeName": "백엔드"},
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
        "intro": "상담으로 먼저 방향을 잡아드립니다.",
        "yearsOfExperience": 8.0,
        "field": {"codeId": 6, "codeName": "인프라"},
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
    "expiresIn": 3600,
    "isNewUser": False,
    "user": LOGIN_USER,
}

TOKEN_EXAMPLE = {
    "accessToken": ACCESS_TOKEN,
    "refreshToken": REISSUED_REFRESH_TOKEN,
    "expiresIn": 3600,
}

ME_EXAMPLE = {
    "userId": 12,
    "name": "김미미",
    "profileImageFileId": None,
    "hasDevProfile": False,
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
        **AREA1_ERRORS,
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
        **AREA1_ERRORS,
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
        503: {
            "model": ErrorResponse,
            "description": "카카오 서비스에 연결하지 못함",
            "content": {"application/json": {"example": KAKAO_UNAVAILABLE_EXAMPLE}},
        },
        **AREA1_ERRORS,
    },
    summary="카카오 로그인",
    description=(
        "**화면 — `1_로그인` › 카카오로 시작하기**\n\n"
        "카카오 로그인 화면에서 받은 인가 코드로 로그인한다. 처음 들어온 사용자라면 "
        "이 요청으로 가입까지 끝나며, 별도의 가입 화면이나 약관 동의 화면은 없다.\n\n"
        "로그인 화면이 '계속하시면 아래 이용약관과 개인정보 처리방침에 동의하신 것으로 "
        "간주합니다'라고 안내하므로 필수 약관 동의는 이 요청으로 함께 처리된다. "
        "마케팅 수신과 개발자 판매자 약관은 선택이라 각각 마이페이지와 개발자 전환에서 "
        "따로 받는다.\n\n"
        "- **200**: 로그인에 성공했을 때. 이번에 새로 가입했으면 `isNewUser`가 true다\n"
        "- **401 / KAKAO_AUTH_FAILED**: `code`가 `invalid-code`일 때. 인가 코드가 "
        "만료되었거나 이미 쓴 값이다. 계정이 있는지 없는지는 구분해 알려주지 않는다\n"
        "- **503 / KAKAO_UNAVAILABLE**: `code`가 `kakao-unavailable`일 때. 카카오 쪽 "
        "문제라 잠시 뒤 다시 시도하면 된다\n"
        "- **422 / INVALID_REQUEST**: `code`나 `redirectUri`가 없을 때\n\n"
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
        expiresIn=3600,
        isNewUser=request.code == NEW_USER_CODE,
        user=LoginUserResponse(**LOGIN_USER),
    )


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
        **AREA1_ERRORS,
    },
    summary="액세스 토큰 재발급",
    description=(
        "**화면 — 전 영역 공통. 액세스 토큰이 만료되는 어느 화면에서나 부른다**\n\n"
        "액세스 토큰이 만료되었을 때 리프레시 토큰으로 새 액세스 토큰을 받는다. "
        "재발급하면 리프레시 토큰도 함께 새 값으로 바뀌고 이전 값은 쓸 수 없다.\n\n"
        "- **200**: 리프레시 토큰이 아직 살아 있을 때\n"
        "- **401 / REFRESH_TOKEN_INVALID**: `refreshToken`이 `expired-refresh-token`일 "
        "때. 만료되었거나 이미 다시 발급받아 쓸 수 없는 값이다. 이때는 로그인 화면으로 "
        "보낸다\n"
        "- **422 / INVALID_REQUEST**: `refreshToken`이 없을 때"
    ),
)
def refresh_access_token(request: RefreshTokenRequest) -> TokenResponse | JSONResponse:
    if request.refreshToken == EXPIRED_REFRESH_TOKEN:
        return _error_response(401, REFRESH_TOKEN_INVALID)

    return TokenResponse(
        accessToken=ACCESS_TOKEN,
        refreshToken=REISSUED_REFRESH_TOKEN,
        expiresIn=3600,
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
        "**화면 — 전 영역 헤더 › 아바타 · 의뢰인/개발자 전환 · 알림 배지**\n\n"
        "모든 화면의 헤더를 채우는 내 정보다. 아바타, 의뢰인·개발자 전환, 알림 배지를 "
        "한 번에 받는다.\n\n"
        "- **200**: 로그인되어 있을 때. 개발자 프로필이 없으면 `hasDevProfile`이 "
        "false이고 헤더에 전환이 보이지 않는다. 읽지 않은 알림이 없으면 배지 수가 0이다\n"
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

    techCodeId: int = Field(description="기술 항목 식별자.", examples=[12])
    codeName: str = Field(
        description="화면에 그대로 보여주는 기술 이름.",
        examples=["Next.js"],
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


# --------------------------------------------------------------------------- #
# 영역 2 — 목 데이터
# --------------------------------------------------------------------------- #

# 실패 사례를 재현하는 진단 식별자. 속성을 늘리지 않고 경로 값으로만 고른다.
DIAGNOSIS_ID = 31
NOT_FOUND_DIAGNOSIS_ID = 404
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
        {"techCodeId": 12, "codeName": "Next.js"},
        {"techCodeId": 15, "codeName": "Supabase"},
    ],
}

DIAGNOSIS_EXAMPLE = {
    "diagnosisId": DIAGNOSIS_ID,
    "version": 2,
    "project": PROJECT_EXAMPLE,
    "userInput": "결제 버튼을 누르면 가끔 아무 반응이 없어요",
    "readinessScore": 52,
    "scannedAt": SCANNED_AT,
    "unresolvedIssueCount": 9,
    "totalIssueCount": 11,
    "resolvedSinceLastVersionCount": 3,
}

SURVEY_EXAMPLE = {
    "surveyId": 19,
    "diagnosisVersion": 2,
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
    "readinessScore": 52,
    "scannedAt": SCANNED_AT,
}

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
]

ISSUE_LIST_EXAMPLE = {
    "content": ISSUES[:2],
    "page": 1,
    "size": 20,
    "totalElements": 11,
    "totalPages": 1,
}

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


@router.post(
    "/diagnoses",
    response_model=DiagnosisResponse,
    status_code=201,
    tags=[AREA2_TAG],
    responses={
        201: {"content": {"application/json": {"example": DIAGNOSIS_EXAMPLE}}},
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

    return DiagnosisResponse(**DIAGNOSIS_EXAMPLE)


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
    return SurveyResponse(**SURVEY_EXAMPLE)


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
    return SurveyResponse(**SURVEY_EXAMPLE)


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
    if not (file.filename or "").endswith(".md"):
        return _error_response(400, SCAN_FILE_INVALID)
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
    return DiagnosisResponse(**DIAGNOSIS_EXAMPLE)


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

    rows = ISSUES
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
        category=CodeResponse(codeId=request.categoryCodeId, codeName="기타"),
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

    saved = [
        {
            "issueId": item.issueId or 399,
            "title": item.title,
            "detail": item.detail,
            "location": None,
            "isInput": False,
            "category": {"codeId": item.categoryCodeId, "codeName": "보안"},
            "status": "UNRESOLVED",
            "severity": item.severity,
            "fixedVersion": None,
            "originIssueId": None,
            "qaCriteria": [
                {
                    "qaCriterionId": qa.qaCriterionId or 299,
                    "testCase": qa.testCase,
                    "condition": None,
                    "status": "UNRESOLVED",
                    "issueId": item.issueId or 399,
                }
                for qa in item.qaCriteria
            ],
        }
        for item in request.requirements
    ]
    return IssueListResponse(
        **{
            **ISSUE_LIST_EXAMPLE,
            "content": saved,
            "totalElements": len(saved),
        }
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
# 채팅 메시지는 이 API가 다루지 않는다. 방 응답의 chatRefId로 채팅 쪽에 넘긴다.
# =========================================================================== #


# --------------------------------------------------------------------------- #
# 영역 3 — 응답 모델
# --------------------------------------------------------------------------- #


class RatingResponse(BaseModel):
    """평점 하나와 그 근거가 된 리뷰 수."""

    score: float | None = Field(
        description="평균 평점. 받은 리뷰가 없으면 null이다.",
        examples=[4.4],
    )
    reviewCount: int = Field(description="집계에 들어간 리뷰 수.", examples=[13])


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
    intro: str = Field(
        description="개발자가 적은 한 줄 소개.",
        examples=["간단한 상담도 받습니다. 용어부터 풀어서 설명합니다."],
    )
    yearsOfExperience: float | None = Field(
        description="경력 연차. 등록하지 않았으면 null이다.",
        examples=[5.0],
    )
    field: CodeResponse | None = Field(
        description="주로 담당하는 개발 분야. 등록하지 않았으면 null이다.",
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
        description="이 개발자가 다룰 수 있는 기술. 등록 전이면 빈 배열이다.",
    )
    prices: list[TaskPriceResponse] = Field(
        description="작업 유형별 최저가. 가격표를 등록하지 않은 유형은 빠진다.",
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
        description="배포 사이트나 저장소 주소. 없으면 null이다.",
        examples=["https://example.com"],
    )
    thumbnailFileId: int | None = Field(
        description="썸네일 파일 식별자. 올리지 않았으면 null이다.",
        examples=[None],
    )


class ReviewResponse(BaseModel):
    """계약을 마친 의뢰인이 남긴 리뷰 하나."""

    reviewId: int = Field(description="리뷰 식별자.", examples=[88])
    clientName: str = Field(
        description="리뷰를 쓴 의뢰인의 표시 이름.",
        examples=["김미미"],
    )
    devScore: int = Field(description="개발력 점수. 1에서 5 사이다.", examples=[5])
    commScore: int = Field(description="설명력 점수. 1에서 5 사이다.", examples=[5])
    content: str | None = Field(
        description="리뷰 내용. 점수만 주고 글을 안 썼으면 null이다.",
        examples=["말이 잘 통했습니다."],
    )
    reply: str | None = Field(
        description="개발자가 단 답글. 없으면 null이다.",
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
    totalElements: int = Field(description="전체 리뷰 수.", examples=[20])
    totalPages: int = Field(description="전체 페이지 수.", examples=[1])


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
    careers: list[CareerResponse] = Field(description="등록한 경력.")
    portfolioItems: list[PortfolioItemResponse] = Field(description="등록한 작업물.")


class RecommendationItemResponse(BaseModel):
    """추천된 개발자 한 명."""

    recommendationItemId: int = Field(
        description="추천 항목 식별자. 상담을 요청할 때 이 값을 보낸다.",
        examples=[501],
    )
    rank: int = Field(description="노출 순위. 1부터 시작한다.", examples=[1])
    developer: DeveloperSummaryResponse = Field(description="추천된 개발자.")
    contractCount: int = Field(
        description="이 개발자가 마친 계약 수. 카드의 'N건 완료'에 쓴다.",
        examples=[23],
    )
    status: Literal["ACCEPTED", "REJECTED", "EXPIRED"] | None = Field(
        description=(
            "상담 요청에 대한 개발자 응답. 아직 요청하지 않았으면 null이다. "
            "ACCEPTED는 수락, REJECTED는 거절, EXPIRED는 답이 없어 만료된 것이다."
        ),
        examples=[None],
    )
    roomId: int | None = Field(
        description="상담이 시작되어 열린 방 식별자. 시작 전이면 null이다.",
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
    candidateCount: int = Field(
        description="점수를 매긴 개발자 수.",
        examples=[128],
    )
    items: list[RecommendationItemResponse] = Field(
        description="추천된 개발자. 순위대로 내려간다.",
    )


class CreateRecommendationRequest(BaseModel):
    """추천 회차 생성 요청."""

    diagnosisId: int | None = Field(
        default=None,
        description="어느 진단을 근거로 찾을지. 진단 없이 찾으면 비운다.",
        examples=[31],
    )
    issueIds: list[int] = Field(
        default_factory=list,
        description="요청서에 담을 요구사항 식별자. 진단을 거치지 않았으면 빈 배열이다.",
        examples=[[311, 312]],
    )


class CreateConsultationRequest(BaseModel):
    """고른 개발자들에게 상담을 요청한다."""

    recommendationItemIds: list[int] = Field(
        description="상담을 요청할 추천 항목 식별자. 여러 명에게 한 번에 보낼 수 있다.",
        examples=[[501, 502]],
    )


class RoomSummaryResponse(BaseModel):
    """방 목록 사이드바에 한 줄로 나가는 방."""

    roomId: int = Field(description="방 식별자.", examples=[91])
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
    counterpartName: str = Field(
        description=(
            "상대의 표시 이름. 의뢰인이 보면 개발자 이름, 개발자가 보면 의뢰인 이름이다."
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
    """방 목록."""

    content: list[RoomSummaryResponse] = Field(
        description="조건에 맞는 방. 없으면 빈 배열이다.",
    )


class RoomRequestSummaryResponse(BaseModel):
    """방 머리에 붙는 요청서 요약."""

    diagnosisId: int = Field(description="요청서의 근거가 된 진단 식별자.", examples=[31])
    issueCount: int = Field(
        description="요청서에 담긴 요구사항 수.",
        examples=[3],
    )
    updatedAt: str = Field(
        description="요청서를 마지막으로 고친 시각.",
        examples=["2026-08-18T16:40:00+09:00"],
    )


class RoomDetailResponse(RoomSummaryResponse):
    """방 상세. 목록에 쓰는 값에 요청서 요약을 더한다."""

    request: RoomRequestSummaryResponse | None = Field(
        description="이 방에 딸린 요청서 요약. 요청서 없이 열린 방이면 null이다.",
    )
    convertedRoomId: int | None = Field(
        description=(
            "갈래를 정해 새로 열린 방 식별자. 아직 정하지 않았으면 null이다. "
            "화면의 '컨설팅 방으로' · '개발 방으로' 버튼이 이 방으로 보낸다."
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
RECOMMENDATION_NOT_FOUND = ("RECOMMENDATION_NOT_FOUND", "추천 결과를 찾을 수 없습니다.")
ROOM_NOT_FOUND = ("ROOM_NOT_FOUND", "방을 찾을 수 없습니다.")
CONSULTATION_ALREADY_REQUESTED = (
    "CONSULTATION_ALREADY_REQUESTED",
    "이미 상담을 요청한 개발자입니다.",
)
ROOM_ALREADY_CONVERTED = (
    "ROOM_ALREADY_CONVERTED",
    "이미 갈래가 정해진 상담입니다.",
)
CODE_GROUP_NOT_FOUND = ("CODE_GROUP_NOT_FOUND", "코드 분류를 찾을 수 없습니다.")


# --------------------------------------------------------------------------- #
# 영역 3 — 목 데이터
# --------------------------------------------------------------------------- #

AREA3_TAG = "3 개발자 찾기 · 상담방"

NOT_FOUND_DEVELOPER_ID = 404
NOT_FOUND_BATCH_ID = 404
NOT_FOUND_ROOM_ID = 404
CONVERTED_ROOM_ID = 409

TECH_CODES = [
    {"codeId": 21, "codeName": "Next.js"},
    {"codeId": 22, "codeName": "Supabase"},
    {"codeId": 23, "codeName": "TypeScript"},
]

# 계좌를 등록할 때 고르는 은행. 분야·기술과 같은 코드 항목이라 코드 API 가 함께 낸다.
BANK_CODES = [
    {"codeId": 4, "codeName": "국민은행"},
    {"codeId": 5, "codeName": "신한은행"},
    {"codeId": 6, "codeName": "우리은행"},
    {"codeId": 7, "codeName": "하나은행"},
    {"codeId": 8, "codeName": "농협은행"},
    {"codeId": 9, "codeName": "카카오뱅크"},
    {"codeId": 10, "codeName": "토스뱅크"},
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

DEVELOPERS = [
    {
        "developerId": 3,
        "name": "이준",
        "profileImageFileId": None,
        "intro": "간단한 상담도 받습니다. 용어부터 풀어서 설명합니다.",
        "yearsOfExperience": 5.0,
        "field": {"codeId": 4, "codeName": "백엔드"},
        "isCareerVerified": True,
        "devRating": {"score": 4.4, "reviewCount": 13},
        "explanatoryRating": {"score": 4.9, "reviewCount": 20},
        "techCategories": TECH_CODES,
        "prices": [
            {"taskType": "CONSULT", "price": 50000},
            {"taskType": "DEVELOP", "price": 100000},
        ],
    },
    {
        "developerId": 5,
        "name": "정하",
        "profileImageFileId": None,
        "intro": "짧게 방향만 잡아드려요.",
        "yearsOfExperience": 4.0,
        "field": {"codeId": 5, "codeName": "풀스택"},
        "isCareerVerified": True,
        "devRating": {"score": 4.2, "reviewCount": 9},
        "explanatoryRating": {"score": 4.4, "reviewCount": 11},
        "techCategories": TECH_CODES[1:],
        "prices": [
            {"taskType": "CONSULT", "price": 40000},
            {"taskType": "DEVELOP", "price": 90000},
        ],
    },
    {
        "developerId": 7,
        "name": "한서",
        "profileImageFileId": None,
        "intro": "급한 장애 대응 잘합니다. 새벽도 괜찮습니다.",
        "yearsOfExperience": 6.0,
        "field": {"codeId": 4, "codeName": "백엔드"},
        "isCareerVerified": False,
        "devRating": {"score": 4.7, "reviewCount": 22},
        "explanatoryRating": {"score": 4.1, "reviewCount": 18},
        "techCategories": TECH_CODES[:1],
        "prices": [
            {"taskType": "CONSULT", "price": 60000},
            {"taskType": "DEVELOP", "price": 120000},
        ],
    },
]

TOTAL_DEVELOPERS = 128

DEVELOPER_DETAIL = {
    **DEVELOPERS[0],
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
        }
    ],
}

REVIEWS = [
    {
        "reviewId": 88,
        "clientName": "김미미",
        "devScore": 5,
        "commScore": 5,
        "content": "말이 잘 통했습니다.",
        "reply": None,
        "createdAt": "2026-08-19T14:20:00+09:00",
    },
    {
        "reviewId": 87,
        "clientName": "최수진",
        "devScore": 4,
        "commScore": 5,
        "content": None,
        "reply": "감사합니다.",
        "createdAt": "2026-08-02T09:10:00+09:00",
    },
]

RECOMMENDATION_BATCH = {
    "batchId": 61,
    "sequenceNo": 1,
    "projectId": 8,
    "candidateCount": TOTAL_DEVELOPERS,
    "items": [
        {
            "recommendationItemId": 501,
            "rank": 1,
            "developer": DEVELOPERS[0],
            "contractCount": 23,
            "status": None,
            "roomId": None,
        },
        {
            "recommendationItemId": 502,
            "rank": 2,
            "developer": DEVELOPERS[2],
            "contractCount": 11,
            "status": None,
            "roomId": None,
        },
    ],
}

ROOMS = [
    {
        "roomId": 91,
        "type": "INQUIRY",
        "status": "OPEN",
        "projectId": 8,
        "projectName": "수제 비누 가게",
        "counterpartName": "이준",
        "chatRefId": "room-91-chat",
        "openedAt": "2026-08-20T09:00:00+09:00",
    },
    {
        "roomId": 92,
        "type": "CONSULTING",
        "status": "OPEN",
        "projectId": 8,
        "projectName": "수제 비누 가게",
        "counterpartName": "이준",
        "chatRefId": "room-92-chat",
        "openedAt": "2026-08-22T11:40:00+09:00",
    },
]

ROOM_DETAIL = {
    **ROOMS[0],
    "request": {
        "diagnosisId": DIAGNOSIS_ID,
        "issueCount": 3,
        "updatedAt": "2026-08-18T16:40:00+09:00",
    },
    "convertedRoomId": None,
}

DEVELOPER_LIST_EXAMPLE = {
    "content": DEVELOPERS[:1],
    "page": 1,
    "size": 20,
    "totalElements": 5,
    "totalPages": 1,
    "totalDevelopers": TOTAL_DEVELOPERS,
}

REVIEW_LIST_EXAMPLE = {
    "content": REVIEWS,
    "page": 1,
    "size": 20,
    "totalElements": 20,
    "totalPages": 1,
}

CODE_LIST_EXAMPLE = {"content": FIELD_CODES}

DEVELOPER_NOT_FOUND_EXAMPLE = {
    "code": "DEVELOPER_NOT_FOUND",
    "message": "개발자를 찾을 수 없습니다.",
}

ROOM_NOT_FOUND_EXAMPLE = {"code": "ROOM_NOT_FOUND", "message": "방을 찾을 수 없습니다."}


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
        "`8_펼침_은행선택` › 은행 선택**\n\n"
        "화면에서 고르게 할 항목을 내려준다. 분야 칩이나 은행 목록처럼 운영에서 늘고 줄 수 "
        "있는 목록이라 화면에 문구를 박지 않고 이 API로 받는다.\n\n"
        "**고르게 할 목록은 모두 이 API로 받는다.** 분류마다 API를 따로 두지 않는다.\n\n"
        "- **200**: 분류가 있을 때. 쓸 수 있는 항목만 순서대로 내려간다\n"
        "- **404 / CODE_GROUP_NOT_FOUND**: `groupKey`가 `UNKNOWN`일 때\n"
        "- **500 / INTERNAL_ERROR**: 목록을 읽지 못했을 때"
    ),
)
def get_codes(
    groupKey: Annotated[
        str,
        Path(
            description=(
                "코드 분류 키. 개발 분야는 `DEV_FIELD`, 기술은 `TECH_CATEGORY`, "
                "계좌를 등록할 때 고르는 은행은 `BANK_CODE`다. `UNKNOWN`은 분류 없음 "
                "재현용 예약값이다."
            ),
            examples=["DEV_FIELD"],
        ),
    ],
) -> CodeListResponse | JSONResponse:
    if groupKey == "UNKNOWN":
        return _error_response(404, CODE_GROUP_NOT_FOUND)
    rows = {"TECH_CATEGORY": TECH_CODES, "BANK_CODE": BANK_CODES}.get(
        groupKey, FIELD_CODES
    )
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
        "의뢰인이 직접 개발자를 찾는다. 추천을 거치지 않고 스스로 고르는 경로다.\n\n"
        "검색은 두 가지다. `problem`은 겪고 있는 문제를 문장으로 적어 맞는 개발자를 찾고, "
        "`name`은 이름으로 바로 찾는다.\n\n"
        "`totalDevelopers`는 조건을 걸지 않았을 때의 전체 수라, 화면의 "
        "'128명 중 5명'에서 앞 숫자에 해당한다.\n\n"
        "- **200**: 항상. 조건에 맞는 사람이 없으면 `content`가 빈 배열이다\n"
        "- **422 / INVALID_REQUEST**: `searchMode`나 `sort`가 허용된 값이 아니거나 "
        "`size`가 범위를 벗어날 때\n"
        "- **500 / INTERNAL_ERROR**: 목록을 만들지 못했을 때"
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
            description="분야 코드 식별자. 여러 개를 보내면 그중 하나라도 맞는 개발자를 찾는다.",
            examples=[[41, 42]],
        ),
    ] = None,
    sort: Annotated[
        Literal["devRating,desc", "explanatoryRating,desc", "price,asc", "contract,desc"],
        Query(
            description=(
                "정렬 기준. 차례로 실력 높은 순, 설명력 높은 순, 비용 낮은 순, 계약 많은 순이다."
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
    return DeveloperDetailResponse(**DEVELOPER_DETAIL)


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
        "몇 건만 보여주고 '모두 보기'에서 페이지를 넘긴다.\n\n"
        "- **200**: 개발자가 있을 때. 받은 리뷰가 없으면 `content`가 빈 배열이다\n"
        "- **404 / DEVELOPER_NOT_FOUND**: `developerId`가 404일 때\n"
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
        **AREA1_UNAUTHORIZED,
        **AREA1_ERRORS,
    },
    summary="개발자 추천 받기",
    description=(
        "**화면 — `2_요구사항선택` › 상담 요청하기. 결과는 `3_의뢰인_개발자추천`에 나온다**\n\n"
        "고른 요구사항을 근거로 맞는 개발자를 찾아 추천 회차를 만든다. 이 회차의 결과가 "
        "추천 화면에 순위대로 나온다.\n\n"
        "같은 프로젝트에서 다시 찾으면 회차 번호가 1 오른다. 이전 회차는 지우지 않고 "
        "남겨서 어떤 추천이 계약으로 이어졌는지 볼 수 있다.\n\n"
        "- **201**: 추천을 만들었을 때. 맞는 개발자가 없으면 `items`가 빈 배열이다\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **404 / PROJECT_NOT_FOUND**: `projectId`가 404일 때. 없거나 남의 프로젝트다\n"
        "- **500 / INTERNAL_ERROR**: 추천을 만들지 못했을 때"
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
    if projectId == 404:
        return _error_response(
            404, ("PROJECT_NOT_FOUND", "프로젝트를 찾을 수 없습니다.")
        )
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
        "추천 회차의 결과를 순위대로 내려준다. 개발자마다 상담을 요청했는지, 요청했다면 "
        "수락했는지가 `status`에 담긴다.\n\n"
        "- **200**: 추천 회차가 있고 내 것일 때\n"
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
        409: {
            "model": ErrorResponse,
            "description": "이미 상담을 요청함",
            "content": {
                "application/json": {
                    "example": {
                        "code": "CONSULTATION_ALREADY_REQUESTED",
                        "message": "이미 상담을 요청한 개발자입니다.",
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
        "같은 요청을 다시 보내도 방이 더 생기지 않는다.\n\n"
        "- **201**: 요청을 보냈을 때. 열린 방 목록이 내려온다\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **404 / RECOMMENDATION_NOT_FOUND**: `batchId`가 404일 때\n"
        "- **409 / CONSULTATION_ALREADY_REQUESTED**: `batchId`가 409일 때. 이미 상담을 "
        "요청한 개발자가 목록에 들어 있다\n"
        "- **422 / INVALID_REQUEST**: `recommendationItemIds`가 비었을 때\n"
        "- **500 / INTERNAL_ERROR**: 요청을 보내지 못했을 때"
    ),
)
def request_consultations(
    batchId: Annotated[
        int,
        Path(
            description=(
                "추천 회차 식별자. 404는 없음, 409는 중복 요청 재현용 예약값이다."
            ),
            examples=[61],
        ),
    ],
    request: CreateConsultationRequest,
) -> RoomListResponse | JSONResponse:
    if batchId == NOT_FOUND_BATCH_ID:
        return _error_response(404, RECOMMENDATION_NOT_FOUND)
    if batchId == 409:
        return _error_response(409, CONSULTATION_ALREADY_REQUESTED)
    if not request.recommendationItemIds:
        body = ValidationErrorResponse(
            code=INVALID_REQUEST[0],
            message=INVALID_REQUEST[1],
            errors=[
                FieldError(
                    field="recommendationItemIds", reason="한 명 이상 골라야 합니다."
                )
            ],
        )
        return JSONResponse(status_code=422, content=jsonable_encoder(body))
    return RoomListResponse(content=[RoomSummaryResponse(**ROOMS[0])])


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
        "내가 들어가 있는 방을 프로젝트별로 묶어 내려준다. 의뢰인이 보면 '보낸 의뢰', "
        "개발자가 보면 '받은 의뢰'가 된다.\n\n"
        "**마지막 메시지와 미리보기는 이 응답에 없다.** 메시지는 채팅 쪽이 다루므로 "
        "`chatRefId`로 그쪽에서 가져온다.\n\n"
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
            description="방 용도로 거른다. 비우면 전부 내려간다.",
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
            description="방 식별자. 404는 없음 재현용 예약값이다.",
            examples=[91],
        ),
    ],
) -> RoomDetailResponse | JSONResponse:
    if roomId == NOT_FOUND_ROOM_ID:
        return _error_response(404, ROOM_NOT_FOUND)
    return RoomDetailResponse(**ROOM_DETAIL)


@router.post(
    "/rooms/{roomId}/conversion",
    response_model=RoomDetailResponse,
    status_code=201,
    tags=[AREA3_TAG],
    responses={
        201: {
            "content": {
                "application/json": {
                    "example": {**ROOM_DETAIL, "status": "CLOSED", "convertedRoomId": 92}
                }
            }
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
        "응답의 `convertedRoomId`가 새로 열린 방이다. 화면은 '컨설팅 방으로' 또는 "
        "'개발 방으로' 버튼으로 그 방에 보낸다. 개발자 화면에는 '개발 계약서 작성'이 뜬다.\n\n"
        "- **201**: 갈래를 정해 새 방이 열렸을 때\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
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
    if roomId == NOT_FOUND_ROOM_ID:
        return _error_response(404, ROOM_NOT_FOUND)
    if roomId == CONVERTED_ROOM_ID:
        return _error_response(409, ROOM_ALREADY_CONVERTED)
    return RoomDetailResponse(
        **{**ROOM_DETAIL, "status": "CLOSED", "convertedRoomId": 92}
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


class ConsultingSessionResponse(BaseModel):
    """컨설팅 세션 하나."""

    sessionId: int = Field(description="컨설팅 세션 식별자.", examples=[55])
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
    recordingFileId: int | None = Field(
        description="세션 녹음 파일 식별자. 녹음 전이면 null이다.",
        examples=[None],
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
            "컨설팅 진행. SIGNED는 체결됨, DEPOSITED는 예치됨, IN_PROGRESS는 진행중, "
            "COMPLETED는 완료됨, TERMINATED는 파기됨이다."
        ),
        examples=["SIGNED"],
    )
    totalAmount: int = Field(
        description="이 계약으로 오가는 전체 금액. 속한 세션 금액의 합이다.",
        examples=[50000],
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
    status: Literal["NEGOTIATING", "AGREED", "SIGNED"] = Field(
        description=(
            "계약 진행. NEGOTIATING은 협의중, AGREED는 합의됨, SIGNED는 양쪽이 서명해 "
            "체결된 것이다."
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
    issueIds: list[int] = Field(
        description="이 계약에서 다룰 요구사항 식별자.",
        examples=[[312, 311]],
    )
    detail: str | None = Field(
        description="계약서의 상세 설명. 적지 않았으면 null이다.",
        examples=["회원탈퇴 처리 흐름과 결제 실패 재현 방법을 화면 공유로 봅니다."],
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

    issueIds: list[int] = Field(
        description="이 계약에서 다룰 요구사항 식별자.",
        examples=[[312, 311]],
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
        description="계약서와 함께 상대에게 남길 전달 메시지.",
        examples=["세션 전에 확인하고 싶은 게 있으면 적어주세요."],
    )


class AmendConsultingContractRequest(BaseModel):
    """컨설팅 계약서 수정 제안. 보낸 항목만 바뀐다."""

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
        description="바꿀 상세 설명.",
        examples=[None],
    )
    message: str | None = Field(
        default=None,
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
        description="반영할 변경 후보 식별자.",
        examples=[[1, 2]],
    )


# --------------------------------------------------------------------------- #
# 영역 4 — 실패 응답 문구
# --------------------------------------------------------------------------- #

CONTRACT_NOT_FOUND = ("CONTRACT_NOT_FOUND", "계약서를 찾을 수 없습니다.")
SESSION_NOT_FOUND = ("SESSION_NOT_FOUND", "세션을 찾을 수 없습니다.")
CONTRACT_ALREADY_SIGNED = (
    "CONTRACT_ALREADY_SIGNED",
    "이미 체결된 계약서는 고칠 수 없습니다.",
)
ALREADY_SIGNED_BY_ME = ("ALREADY_SIGNED_BY_ME", "이미 서명하셨습니다.")
SESSION_NOT_OPEN = (
    "SESSION_NOT_OPEN",
    "아직 입장할 수 없습니다. 시작 10분 전부터 들어갈 수 있습니다.",
)
SESSION_NOT_ENDED = ("SESSION_NOT_ENDED", "세션이 아직 끝나지 않았습니다.")
SUMMARY_NOT_READY = ("SUMMARY_NOT_READY", "요약을 아직 만드는 중입니다.")


# --------------------------------------------------------------------------- #
# 영역 4 — 목 데이터
# --------------------------------------------------------------------------- #

AREA4_TAG = "4 컨설팅"

CONTRACT_ID = 120
SESSION_ID = 55
NOT_FOUND_CONTRACT_ID = 404
SIGNED_CONTRACT_ID = 409
NOT_FOUND_SESSION_ID = 404
NOT_OPEN_SESSION_ID = 409
NO_SUMMARY_SESSION_ID = 202

SESSION_EXAMPLE = {
    "sessionId": SESSION_ID,
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
    "recordingFileId": None,
}

CONSULTING_CONTRACT_EXAMPLE = {
    "contractId": CONTRACT_ID,
    "roomId": 92,
    "contractType": "CONSULTING",
    "status": "NEGOTIATING",
    "consulting": {
        "status": "SIGNED",
        "totalAmount": 50000,
        "sessions": [SESSION_EXAMPLE],
    },
    "development": None,
    "issueIds": [312, 311],
    "detail": "회원탈퇴 처리 흐름과 결제 실패 재현 방법을 화면 공유로 봅니다.",
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
    return ContractResponse(**CONSULTING_CONTRACT_EXAMPLE)


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
            "description": "이미 체결되어 고칠 수 없음",
            "content": {
                "application/json": {
                    "example": {
                        "code": "CONTRACT_ALREADY_SIGNED",
                        "message": "이미 체결된 계약서는 고칠 수 없습니다.",
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
        "- **422 / INVALID_REQUEST**: `scheduledAt`이 지난 시각이거나 `price`가 음수일 때\n"
        "- **500 / INTERNAL_ERROR**: 고치지 못했을 때"
    ),
)
def amend_contract(
    contractId: Annotated[
        int,
        Path(
            description=(
                "계약 식별자. 404는 없음, 409는 이미 체결됨 재현용 예약값이다."
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
                        "status": "SIGNED",
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
            "description": "이미 서명함",
            "content": {
                "application/json": {
                    "example": {
                        "code": "ALREADY_SIGNED_BY_ME",
                        "message": "이미 서명하셨습니다.",
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
        "- **422 / INVALID_REQUEST**: `signatureImageFileId`가 없을 때\n"
        "- **500 / INTERNAL_ERROR**: 서명하지 못했을 때"
    ),
)
def sign_contract(
    contractId: Annotated[
        int,
        Path(
            description=(
                "계약 식별자. 404는 없음, 409는 이미 서명함 재현용 예약값이다."
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
    return ContractResponse(
        **{
            **CONSULTING_CONTRACT_EXAMPLE,
            "status": "SIGNED",
            "clientSignature": {
                "userId": 12,
                "isSigned": True,
                "signedAt": "2026-08-22T16:10:00+09:00",
                "signatureImageFileId": request.signatureImageFileId,
            },
        }
    )


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
        "시각을 견주어 입장 버튼을 열지 정한다. 시작 10분 전부터 들어갈 수 있다.\n\n"
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
    return ConsultingSessionResponse(**SESSION_EXAMPLE)


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
        409: {
            "model": ErrorResponse,
            "description": "아직 입장할 수 없음",
            "content": {
                "application/json": {
                    "example": {
                        "code": "SESSION_NOT_OPEN",
                        "message": (
                            "아직 입장할 수 없습니다. 시작 10분 전부터 들어갈 수 있습니다."
                        ),
                    }
                }
            },
        },
        **SESSION_ERRORS,
    },
    summary="세션 입장",
    description=(
        "**화면 — `4_의뢰인_입장전` › 입장**\n\n"
        "세션에 들어간다. 들어간 시각이 참여 기록으로 남고, 먼저 들어온 사람이 있으면 "
        "그때 세션이 시작된 것으로 본다.\n\n"
        "화상 연결 자체는 이 API가 하지 않는다. 들어갈 수 있는지 판정하고 기록만 남긴다.\n\n"
        "- **201**: 들어갔을 때. 세션이 이때 시작되면 `startedAt`이 채워진다\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **404 / SESSION_NOT_FOUND**: `sessionId`가 404일 때\n"
        "- **409 / SESSION_NOT_OPEN**: `sessionId`가 409일 때. 아직 이른 시각이거나 이미 "
        "끝난 세션이다\n"
        "- **500 / INTERNAL_ERROR**: 들어가지 못했을 때"
    ),
)
def join_consulting_session(
    sessionId: Annotated[
        int,
        Path(
            description=(
                "컨설팅 세션 식별자. 404는 없음, 409는 입장 불가 재현용 예약값이다."
            ),
            examples=[SESSION_ID],
        ),
    ],
) -> ConsultingSessionResponse | JSONResponse:
    if sessionId == NOT_FOUND_SESSION_ID:
        return _error_response(404, SESSION_NOT_FOUND)
    if sessionId == NOT_OPEN_SESSION_ID:
        return _error_response(409, SESSION_NOT_OPEN)
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
                        "recordingFileId": 62,
                    }
                }
            }
        },
        **SESSION_ERRORS,
    },
    summary="세션 종료",
    description=(
        "**화면 — `4_의뢰인_세션중` › 세션 종료**\n\n"
        "세션을 마친다. 끝난 시각이 기록되고 요약 만들기가 시작된다. 같은 요청을 다시 "
        "보내도 결과가 같다.\n\n"
        "- **200**: 마쳤을 때. 녹음이 있으면 `recordingFileId`가 채워진다\n"
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
            "recordingFileId": 62,
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
# 화면: 5_개발자_계약서작성 · 5_의뢰인_계약서상세 · 5_의뢰인_계약서수정제안 ·
#       5_개발자_계약서검토 · 5_의뢰인_계약예치 · 5_개발자_착수전 ·
#       5_의뢰인_진행 · 5_의뢰인_확인 · 5_개발자_확인 · 5_의뢰인_리뷰작성
#
# 함께 쓰는 API (다른 영역에 있다):
#   GET   /api/users/me                      전 영역 헤더
#   GET   /api/rooms · /api/rooms/{roomId}   방 사이드바와 머리
#   GET   /api/contracts/{contractId}        계약서 조회 (contractType으로 갈린다)
#   PATCH /api/contracts/{contractId}        수정 제안
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
    title: str = Field(
        description="작업 이름.",
        examples=["1차 · 관리자 API에 인증 추가"],
    )
    price: int = Field(
        description=(
            "작업 가격. 원 단위 정수다. 인계 작업은 0원이며 화면에 가격을 보여주지 않는다."
        ),
        examples=[400000],
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
            "개발자가 확인 요청을 보낸 시각. 자동 통과 기한 7일을 여기서부터 센다. "
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
        examples=["2026-09-05T11:20:00+09:00"],
    )
    qaCriteria: list[QaCriterionResponse] = Field(
        description="이 작업의 확인 문장. 전부 체크되어야 통과시킬 수 있다.",
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
        "PRE_SIGNED",
        "SIGNED",
        "DEPOSITED",
        "IN_PROGRESS",
        "AFTER_SERVICE",
        "COMPLETED",
        "TERMINATED",
    ] = Field(
        description=(
            "개발 진행. PRE_SIGNED는 **가계약**으로, 계약서를 주고받으며 조정하는 동안의 "
            "상태다. 양쪽이 서명하면 SIGNED, 예치가 끝나면 DEPOSITED, 착수하면 "
            "IN_PROGRESS가 된다. AFTER_SERVICE는 하자보수, COMPLETED는 완료됨, "
            "TERMINATED는 파기됨이며 이 셋은 운영 콘솔에서 다룬다."
        ),
        examples=["IN_PROGRESS"],
    )
    totalAmount: int = Field(
        description="이 계약으로 오가는 전체 금액. 속한 작업 가격의 합이다.",
        examples=[650000],
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
    """개발 계약서 작성 요청."""

    issueIds: list[int] = Field(
        description="이 계약에서 다룰 요구사항 식별자.",
        examples=[[312, 311]],
    )
    startDate: str = Field(description="착수일.", examples=["2026-08-26"])
    dueDate: str = Field(description="최종 납기일.", examples=["2026-09-16"])
    freeRepairDays: int = Field(
        description="무상 하자보수 기간(일).",
        examples=[14],
    )
    milestones: list["WriteMilestoneRequest"] = Field(
        description="맡기로 한 작업. 가격의 합이 계약 금액이 된다.",
    )
    rejectedIssues: list["RejectedIssueRequest"] = Field(
        default_factory=list,
        description=(
            "맡지 않기로 한 요구사항과 그 사유. 요청서에는 있었지만 이 계약에서 빼는 것들이다."
        ),
    )
    deliverableTitles: list[str] = Field(
        default_factory=list,
        description="넘겨받을 인계물 이름.",
        examples=[["소스 파일 전체", "관리자 계정과 비밀번호", "운영 설명서"]],
    )
    detail: str | None = Field(
        default=None,
        description="계약서의 상세 설명. 적지 않아도 된다.",
        examples=[None],
    )
    message: str | None = Field(
        default=None,
        description="계약서와 함께 남길 전달 메시지.",
        examples=[None],
    )


class RejectedIssueRequest(BaseModel):
    """맡지 않기로 한 요구사항 하나."""

    issueId: int = Field(description="빼는 요구사항 식별자.", examples=[313])
    reason: str = Field(
        description="왜 맡지 않는지. 의뢰인이 계약서에서 이 사유를 본다.",
        examples=["이번 범위에서는 다루기 어렵습니다."],
    )


class WriteMilestoneRequest(BaseModel):
    """계약서에 담을 작업 하나."""

    title: str = Field(
        description="작업 이름.",
        examples=["1차 · 관리자 API에 인증 추가"],
    )
    price: int = Field(
        description="작업 가격. 인계 작업은 0으로 보낸다.",
        examples=[400000],
    )
    qaCriteria: list[str] = Field(
        description="이 작업의 확인 문장.",
        examples=[["관리자 주소로 들어가면 로그인 화면이 뜬다"]],
    )


class EscrowDepositResponse(BaseModel):
    """예치 상태. 돈의 원장은 Escrow 서버가 가지고 여기서는 확인만 한다."""

    contractId: int = Field(description="계약 식별자.", examples=[130])
    amount: int = Field(description="예치할 금액. 원 단위 정수다.", examples=[650000])
    status: Literal["PENDING", "DEPOSITED", "FAILED"] = Field(
        description=(
            "예치 진행. PENDING은 결제창을 띄운 뒤 결과를 기다리는 상태, DEPOSITED는 "
            "예치가 확인된 상태, FAILED는 실패한 상태다."
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
        description="쓸 결제 수단 식별자. 마이페이지에 등록해 둔 카드다.",
        examples=[9],
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
        description="의뢰인에게 함께 남길 말. 적지 않아도 된다.",
        examples=[None],
    )


class ConfirmMilestoneRequest(BaseModel):
    """작업 통과 처리."""

    qaCriterionIds: list[int] = Field(
        description="체크한 확인 문장 식별자. 전부 체크해야 통과시킬 수 있다.",
        examples=[[201, 202]],
    )


class WriteReviewRequest(BaseModel):
    """계약을 마친 뒤 남기는 리뷰."""

    devScore: int = Field(description="개발력 점수. 1에서 5 사이다.", examples=[5])
    commScore: int = Field(description="설명력 점수. 1에서 5 사이다.", examples=[5])
    content: str | None = Field(
        default=None,
        description="리뷰 내용. 점수만 주고 넘어가도 된다.",
        examples=["말이 잘 통했습니다."],
    )


# --------------------------------------------------------------------------- #
# 영역 5 — 실패 응답 문구
# --------------------------------------------------------------------------- #

MILESTONE_NOT_FOUND = ("MILESTONE_NOT_FOUND", "작업을 찾을 수 없습니다.")
DEPOSIT_REQUIRED = ("DEPOSIT_REQUIRED", "예치가 끝나야 착수할 수 있습니다.")
ALREADY_DEPOSITED = ("ALREADY_DEPOSITED", "이미 예치가 끝났습니다.")
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


# --------------------------------------------------------------------------- #
# 영역 5 — 목 데이터
# --------------------------------------------------------------------------- #

AREA5_TAG = "5 개발"

DEV_CONTRACT_ID = 130
MILESTONE_ID = 301
NOT_FOUND_MILESTONE_ID = 404
CONFLICT_MILESTONE_ID = 409
NOT_DEPOSITED_CONTRACT_ID = 402
DEPOSITED_CONTRACT_ID = 409

MILESTONES = [
    {
        "milestoneId": 301,
        "title": "1차 · 관리자 API에 인증 추가",
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
        "title": "2차 · 회원탈퇴 경로 구현",
        "price": 200000,
        "status": "SUBMITTED",
        "rejectMessage": None,
        "submittedAt": "2026-08-29T11:20:00+09:00",
        "confirmedAt": None,
        "autoPassAt": "2026-09-05T11:20:00+09:00",
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
        "title": "3차 · 결제 간헐 실패 수정",
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
        "title": "인계 · 소스 · 계정 · 설명서",
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
                "issueId": None,
            }
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
        "startDate": "2026-08-26",
        "dueDate": "2026-09-16",
        "freeRepairDays": 14,
        "milestones": MILESTONES,
        "deliverables": DELIVERABLES,
    },
    "issueIds": [312, 313, 311],
    "detail": None,
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

STARTED_CONTRACT_ID = 409


# --------------------------------------------------------------------------- #
# 영역 5 — 계약과 예치
# --------------------------------------------------------------------------- #


@router.post(
    "/contracts/{contractId}/start",
    response_model=ContractResponse,
    tags=[AREA5_TAG],
    responses={
        200: {"content": {"application/json": {"example": DEV_CONTRACT_EXAMPLE}}},
        403: {
            "model": ErrorResponse,
            "description": "예치 전이라 착수할 수 없음",
            "content": {"application/json": {"example": DEPOSIT_REQUIRED_EXAMPLE}},
        },
        409: {
            "model": ErrorResponse,
            "description": "이미 착수한 계약",
            "content": {"application/json": {"example": ALREADY_STARTED_EXAMPLE}},
        },
        **CONTRACT_ERRORS,
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
        "- **403 / DEPOSIT_REQUIRED**: `contractId`가 402일 때. 예치가 끝나지 않았다\n"
        "- **404 / CONTRACT_NOT_FOUND**: `contractId`가 404일 때\n"
        "- **409 / ALREADY_STARTED**: `contractId`가 409일 때\n"
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
        return _error_response(403, DEPOSIT_REQUIRED)
    if contractId == STARTED_CONTRACT_ID:
        return _error_response(409, ALREADY_STARTED)
    return ContractResponse(**DEV_CONTRACT_EXAMPLE)


@router.post(
    "/rooms/{roomId}/development-contracts",
    response_model=ContractResponse,
    status_code=201,
    tags=[AREA5_TAG],
    responses={
        201: {"content": {"application/json": {"example": DEV_CONTRACT_EXAMPLE}}},
        404: {
            "model": ErrorResponse,
            "description": "방 없음",
            "content": {"application/json": {"example": ROOM_NOT_FOUND_EXAMPLE}},
        },
        **AREA1_UNAUTHORIZED,
        **AREA1_ERRORS,
    },
    summary="개발 계약서 작성",
    description=(
        "**화면 — `5_개발자_계약서작성` › 요구사항 고르기 · 작업 나누기 · 확인 문장 · 납기 · 보내기**\n\n"
        "개발자가 개발 계약서를 써서 의뢰인에게 보낸다. 요청서에 담긴 요구사항 중 **맡을 "
        "것을 고르고**, 고른 것을 작업으로 나눠 가격과 확인 문장을 붙인다. 작업 가격의 합이 "
        "계약 금액이 된다.\n\n"
        "**고르지 않은 요구사항은 맡지 않는 것으로 기록된다.** `rejectedIssues`에 사유와 "
        "함께 담아 보내면 의뢰인이 계약서에서 왜 빠졌는지 볼 수 있다.\n\n"
        "보낸 계약서는 **가계약** 상태다. 양쪽이 서명할 때까지 서로 주고받으며 조정하는 "
        "구간이며, 서명이 끝나야 체결된다.\n\n"
        "**작업마다 따로 납기를 두지 않는다.** 최종 납기일 하나만 있고, 대금도 모든 작업이 "
        "통과된 뒤 한 번에 나간다.\n\n"
        "인계물은 가격 0원짜리 작업 하나로 함께 들어간다. 0원인 작업은 화면에 가격을 "
        "보여주지 않는다.\n\n"
        "- **201**: 계약서를 보냈을 때\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **403 / FORBIDDEN**: 이 방의 개발자가 아닐 때\n"
        "- **404 / ROOM_NOT_FOUND**: `roomId`가 404일 때\n"
        "- **422 / INVALID_REQUEST**: 작업이 하나도 없거나, `dueDate`가 `startDate`보다 "
        "이르거나, 확인 문장이 없는 작업이 있을 때\n"
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
    return ContractResponse(
        **{
            **DEV_CONTRACT_EXAMPLE,
            "status": "NEGOTIATING",
            "clientSignature": None,
            "developerSignature": None,
            "contractText": None,
            "development": {
                **DEV_CONTRACT_EXAMPLE["development"],
                "status": "PRE_SIGNED",
            },
        }
    )


@router.post(
    "/contracts/{contractId}/deposit",
    response_model=EscrowDepositResponse,
    status_code=201,
    tags=[AREA4_TAG],
    responses={
        201: {"content": {"application/json": {"example": DEPOSIT_EXAMPLE}}},
        409: {
            "model": ErrorResponse,
            "description": "이미 예치됨",
            "content": {
                "application/json": {
                    "example": {
                        "code": "ALREADY_DEPOSITED",
                        "message": "이미 예치가 끝났습니다.",
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
        "`paymentMethodId`는 마이페이지에 등록해 둔 카드다. 실시간 계좌이체를 고르면 "
        "결제창에서 은행 앱으로 바로 내므로 미리 등록해 둘 것이 없다.\n\n"
        "- **201**: 예치를 걸었을 때. 결제가 바로 확인되면 `DEPOSITED`, 결과를 기다리는 "
        "중이면 `PENDING`이다\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **403 / FORBIDDEN**: 이 계약의 의뢰인이 아닐 때. 예치는 의뢰인만 한다\n"
        "- **404 / CONTRACT_NOT_FOUND**: `contractId`가 404일 때\n"
        "- **409 / ALREADY_DEPOSITED**: `contractId`가 409일 때\n"
        "- **422 / INVALID_REQUEST**: `paymentMethodId`나 `idempotencyKey`가 없을 때\n"
        "- **500 / INTERNAL_ERROR**: 예치를 걸지 못했을 때"
    ),
)
def deposit_contract(
    contractId: Annotated[
        int,
        Path(
            description=(
                "계약 식별자. 404는 없음, 409는 이미 예치됨 재현용 예약값이다."
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
        **MILESTONE_ERRORS,
    },
    summary="작업 확인 요청",
    description=(
        "**화면 — `5_개발자_확인` › 확인 요청 보내기 · 확인 요청 다시 보내기**\n\n"
        "착수한 작업을 마치고 의뢰인에게 확인을 요청한다. 이때부터 **7일**을 세고, "
        "의뢰인이 답하지 않으면 자동으로 통과된다. 다시 보내면 기한도 다시 시작한다.\n\n"
        "응답의 `autoPassAt`이 자동 통과 시각이다. 화면은 이 값으로 '9월 5일 자동 통과'를 "
        "보여준다.\n\n"
        "- **201**: 확인 요청을 보냈을 때. 상태가 SUBMITTED가 되고 `submittedAt`이 채워진다\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **404 / MILESTONE_NOT_FOUND**: `milestoneId`가 404일 때\n"
        "- **409 / MILESTONE_STATE_CONFLICT**: `milestoneId`가 409일 때. 이미 통과된 "
        "작업이다\n"
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
    request: SubmitMilestoneRequest,
) -> MilestoneResponse | JSONResponse:
    if milestoneId == NOT_FOUND_MILESTONE_ID:
        return _error_response(404, MILESTONE_NOT_FOUND)
    if milestoneId == CONFLICT_MILESTONE_ID:
        return _error_response(409, MILESTONE_STATE_CONFLICT)
    return MilestoneResponse(**MILESTONES[1])


@router.post(
    "/milestones/{milestoneId}/confirmation",
    response_model=MilestoneResponse,
    status_code=201,
    tags=[AREA5_TAG],
    responses={
        201: {"content": {"application/json": {"example": MILESTONES[0]}}},
        409: {
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
        **MILESTONE_ERRORS,
    },
    summary="작업 통과시키기",
    description=(
        "**화면 — `5_의뢰인_확인` › 확인된 기능 제출**\n\n"
        "의뢰인이 확인 문장을 모두 체크하고 작업을 통과시킨다. **통과시키면 되돌릴 수 "
        "없다.** 확인 문장이 하나라도 남아 있으면 통과시킬 수 없다.\n\n"
        "대금은 여기서 나가지 않는다. 모든 작업이 통과된 뒤 한 번에 지급된다.\n\n"
        "- **201**: 통과시켰을 때. 상태가 CONFIRMED가 되고 `confirmedAt`이 채워진다\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **403 / FORBIDDEN**: 이 계약의 의뢰인이 아닐 때. 통과 판정은 의뢰인만 한다\n"
        "- **404 / MILESTONE_NOT_FOUND**: `milestoneId`가 404일 때\n"
        "- **409 / QA_NOT_ALL_CHECKED**: `milestoneId`가 409일 때. 아직 체크하지 않은 "
        "확인 문장이 있다\n"
        "- **500 / INTERNAL_ERROR**: 통과시키지 못했을 때"
    ),
)
def confirm_milestone(
    milestoneId: Annotated[
        int,
        Path(
            description=(
                "작업 식별자. 404는 없음, 409는 확인 문장 미완료 재현용 예약값이다."
            ),
            examples=[MILESTONE_ID],
        ),
    ],
    request: ConfirmMilestoneRequest,
) -> MilestoneResponse | JSONResponse:
    if milestoneId == NOT_FOUND_MILESTONE_ID:
        return _error_response(404, MILESTONE_NOT_FOUND)
    if milestoneId == CONFLICT_MILESTONE_ID:
        return _error_response(409, QA_NOT_ALL_CHECKED)
    return MilestoneResponse(**MILESTONES[0])


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
        404: {
            "model": ErrorResponse,
            "description": "인계물 없음",
            "content": {
                "application/json": {
                    "example": {
                        "code": "DELIVERABLE_NOT_FOUND",
                        "message": "인계물을 찾을 수 없습니다.",
                    }
                }
            },
        },
        **AREA1_UNAUTHORIZED,
        **AREA1_ERRORS,
    },
    summary="인계물 올리기",
    description=(
        "**화면 — `5_개발자_확인` › 인계 작업의 파일 첨부**\n\n"
        "소스·계정·설명서 같은 인계물을 파일로 올린다. 올린 시각이 인계 시각으로 남고, "
        "의뢰인 화면에서는 '받기'로 내려받을 수 있게 된다.\n\n"
        "- **201**: 올렸을 때\n"
        "- **400 / FILE_TOO_LARGE**: 허용 용량을 넘겼을 때\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **403 / FORBIDDEN**: 이 계약의 개발자가 아닐 때\n"
        "- **404 / DELIVERABLE_NOT_FOUND**: `deliverableId`가 404일 때\n"
        "- **500 / INTERNAL_ERROR**: 올리지 못했을 때"
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
    file: Annotated[UploadFile, File(description="인계물 파일.")],
) -> DeliverableResponse | JSONResponse:
    if deliverableId == 404:
        return _error_response(
            404, ("DELIVERABLE_NOT_FOUND", "인계물을 찾을 수 없습니다.")
        )
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
        **CONTRACT_ERRORS,
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
        "- **404 / CONTRACT_NOT_FOUND**: `contractId`가 404일 때\n"
        "- **409 / REVIEW_ALREADY_WRITTEN**: `contractId`가 409일 때\n"
        "- **422 / INVALID_REQUEST**: 점수가 1에서 5 사이가 아닐 때\n"
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
    if contractId == 409:
        return _error_response(409, REVIEW_ALREADY_WRITTEN)
    if contractId == 400:
        return _error_response(400, CONTRACT_NOT_COMPLETED)
    return ReviewResponse(
        **{
            **REVIEWS[0],
            "devScore": request.devScore,
            "commScore": request.commScore,
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
#
# ERD 확인 필요: notifications.target_type(NOTIFICATION_TARGET_TYPE)에 신고를 가리키는
# 값이 없다. ANNOUNCEMENT / CONTRACT / ISSUE 뿐이라 지금은 CONTRACT 로 계약을 가리켜
# 알림에서 그 계약으로 들어가게 한다. 신고를 바로 열어야 하면 값을 하나 늘려야 한다.
# =========================================================================== #


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
        **CONTRACT_ERRORS,
    },
    summary="문제 신고 · 이의 제기 접수",
    description=(
        "**화면 — `6_의뢰인_문제신고` › 신고하기 · `6_개발자_이의제기` › 이의 제기 보내기**\n\n"
        "계약이 도는 동안 상대와 말이 통하지 않을 때 운영자에게 알린다. 의뢰인이 내면 "
        "문제 신고, 개발자가 내면 이의 제기라고 부르지만 **접수되는 내용과 처리 방식은 "
        "같다.** 누가 냈는지는 신고자로 구분한다.\n\n"
        "**접수는 계약이나 작업 상태를 바꾸지 않는다.** Komit이 누가 옳은지 정하지도 "
        "않는다. 운영자가 내용을 확인한 뒤 종결한다.\n\n"
        "**종결되면 신고한 사람에게 알림이 간다.** 신고 상태를 따로 보는 화면은 두지 "
        "않고, 알림에서 해당 계약으로 들어간다.\n\n"
        "다만 확인 중인 신고가 있는 계약은 **작업이 7일 만에 자동 통과되지 않는다.** "
        "답이 없어 자동으로 넘어가는 일을 막기 위해서다.\n\n"
        "증거 파일은 하나까지 올릴 수 있고 없어도 된다.\n\n"
        "- **201**: 접수했을 때. 상태는 `OPEN`이고 종결 시각은 null이다\n"
        "- **400 / FILE_TOO_LARGE**: 첨부가 허용 용량을 넘겼을 때\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **403 / FORBIDDEN**: 이 계약의 당사자가 아닐 때\n"
        "- **404 / CONTRACT_NOT_FOUND**: `contractId`가 404일 때\n"
        "- **409 / REPORT_ALREADY_OPEN**: `contractId`가 409일 때. 아직 확인 중인 신고가 "
        "있어 새로 낼 수 없다\n"
        "- **422 / INVALID_REQUEST**: `reason`이 비었거나 255자를 넘을 때\n"
        "- **500 / INTERNAL_ERROR**: 접수하지 못했을 때"
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
    reason: Annotated[
        str,
        Form(
            max_length=255,
            description="적어 낼 내용. 255자까지 받는다.",
            examples=["8월 31일에 작업 진행 상황을 문의했지만 답변을 받지 못했습니다."],
        ),
    ],
    file: Annotated[
        UploadFile | None,
        File(description="증거로 올릴 파일. 없어도 된다."),
    ] = None,
) -> ReportResponse | JSONResponse:
    if contractId == NOT_FOUND_REPORT_CONTRACT_ID:
        return _error_response(404, CONTRACT_NOT_FOUND)
    if contractId == OPEN_REPORT_CONTRACT_ID:
        return _error_response(409, REPORT_ALREADY_OPEN)
    return ReportResponse(
        **{
            **REPORT_EXAMPLE,
            "contractId": contractId,
            "reason": reason,
            "fileId": 81 if file is not None else None,
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
    """등록한 계좌. 의뢰인은 이 계좌로 예치금을 내고 환불을 받으며,
    개발자는 이 계좌로 대금을 받는다. 사람마다 한 개다."""

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
        description="계약이 끝나면 받을 돈의 합.",
        examples=[650000],
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
    "expectedAmount": 650000,
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
        "개발 대금은 작업을 모두 통과한 뒤 한 번에 나가고, 컨설팅은 세션이 끝난 뒤 나간다. "
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
#       8_개발자_내프로필 · 8_개발자_내일정
#
# 8_약관과 8_아바타메뉴에는 API 가 없다. 약관 문서와 개정일, 사업자 정보는 서버가
# 들고 있지 않고 화면이 가진 정적 문서이며(ERD 에 약관 테이블이 없다), 아바타 메뉴는
# 이미 받은 값으로 그리는 메뉴다.
#
# 내 프로필은 줄마다 저장하지 않는다. 화면의 저장 버튼 하나가 소개·기술·경력·작업물·
# 가격표를 통째로 보내고, 보낸 내용으로 맞춘다. 파일만 먼저 올려 식별자를 받는다.
#
# 회원 탈퇴는 지우는 것이 아니다. 사용자 정보는 그대로 두고 삭제 여부 값만 참으로
# 바꾼 뒤 삭제 시각을 적는다. 계약·정산 이력과 메시지, 리뷰가 상대 화면에도 있는
# 기록이라 남겨야 하기 때문이다. 그래서 DELETE 가 아니라 POST 를 쓴다.
#
# 돈을 내는 수단과 받는 계좌는 다르다. 내는 쪽은 payment_methods 로 카드와
# 실시간 계좌이체가 있고, 받는 쪽은 accounts 로 사람마다 한 개다. 의뢰인은 그
# 계좌로 환불을 받고 개발자는 대금을 받는다.
#
# 계좌는 1원 인증으로만 등록한다. 계좌를 넣는 요청과 입금자명 뒤 네 자리를 내는
# 요청이 나뉘며, 확인이 끝나는 순간에만 accounts 에 계좌가 남는다. 1원 이체와
# 확인 호출은 Escrow 서버가 금융망에 하고, Komit 은 확인 건의 식별자와 가린
# 계좌번호만 들고 있다. 계좌번호 원문은 어느 쪽에도 남지 않는다.
#
# Escrow 서버는 SSAFY 금융망과 Komit 사이의 프록시로 동작한다. 클라이언트에서
# Komit 까지는 아래 API 들의 간소화된 형식을 쓰고, Komit 에서 Escrow 까지는 SSAFY
# 금융망 형식(Header/REC)을 쓴다. 클라이언트는 SSAFY 형식을 알 필요가 없다.
#
# 이 영역은 전부 내 것을 다루므로 인가를 반드시 검증한다. 액세스 토큰을 검증해
# sub 를 꺼내고, 다루려는 자원의 주인이 그 sub 와 같은지 확인한 뒤에 처리한다.
# 대상을 요청에 담지 않는 API 는 sub 가 곧 대상이고, 경로에 식별자가 있는 API 는
# 그 자원의 주인이 sub 와 같은지 확인해 다르면 403 FORBIDDEN 으로 막는다.
#
# 403 은 두 뜻으로 쓴다. FORBIDDEN 은 남의 자원, NO_DEVELOPER_PROFILE 은 개발자로
# 전환하지 않은 것이다. 화면 분기는 상태코드가 아니라 code 로 가른다.
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
            "계좌번호. 하이픈 없는 숫자만 받는다. 확인이 끝날 때까지만 두고 그 뒤에는 "
            "가린 번호만 남는다."
        ),
        examples=["11012345678842"],
    )
    accountHolder: str = Field(description="예금주.", examples=["이준"])


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
        description="입금자명 뒤에 붙은 숫자 네 자리.",
        examples=["1234"],
    )

AREA8_TAG = "8 계정 · 공통"

ACCOUNT_REJECTED = (
    "ACCOUNT_REJECTED",
    "은행과 계좌번호를 다시 확인해 주세요.",
)
AUTH_CODE_MISMATCH = (
    "AUTH_CODE_MISMATCH",
    "숫자 네 자리가 맞지 않습니다. 입금 내역을 다시 확인해 주세요.",
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
REJECTED_ACCOUNT_NUMBER = "00000000000000"
VALID_AUTH_CODE = "1234"
NOT_FOUND_VERIFICATION_ID = 404
NOT_FOUND_NOTIFICATION_ID = 404

ACCOUNT_REJECTED_EXAMPLE = {
    "code": ACCOUNT_REJECTED[0],
    "message": ACCOUNT_REJECTED[1],
}
AUTH_CODE_MISMATCH_EXAMPLE = {
    "code": AUTH_CODE_MISMATCH[0],
    "message": AUTH_CODE_MISMATCH[1],
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
        "사람마다 계좌는 하나이고, 의뢰인은 그 계좌로 예치금을 내고 환불을 받으며 "
        "개발자는 그 계좌로 대금을 받는다.\n\n"
        "넣은 계좌로 1원을 보내 확인 건을 연다. 실제 이체는 Escrow 서버가 "
        "금융망에 요청하고, Komit은 확인 건의 식별자와 가린 계좌번호만 들고 있는다. "
        "계좌번호 원문은 Komit에도 Escrow에도 남지 않는다.\n\n"
        "이 요청만으로는 계좌가 등록되지 않는다. 입금자명 뒤 4자리를 확인 API에 "
        "내야 등록이 끝난다. `expiresAt`이 지나면 이 확인 건은 쓸 수 없고, 같은 "
        "요청을 다시 보내 새 확인 건을 연다. 화면의 **다시 보내기**가 이 경로다.\n\n"
        "이미 등록한 계좌가 있어도 여기서 막지 않는다. 확인이 끝나는 순간 새 계좌가 "
        "이전 계좌를 대신한다.\n\n"
        "**확인 건은 토큰의 주인에게 달린다.** 액세스 토큰을 검증해 `sub`를 꺼내고 그 "
        "사용자의 확인 건으로 연다. 누구의 계좌인지는 요청에 담지 않는다.\n\n"
        "- **201**: 1원을 보냈고 확인 건이 열렸을 때\n"
        "- **400 / ACCOUNT_REJECTED**: 은행이 계좌를 받지 않았을 때. 계좌번호나 "
        "은행이 틀렸거나 거래할 수 없는 계좌다\n"
        "- **400 / VERIFICATION_TOO_MANY**: 같은 계좌로 너무 자주 요청했을 때\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **422 / INVALID_REQUEST**: 은행·계좌번호·예금주가 비었거나 계좌번호에 "
        "숫자가 아닌 글자가 섞였을 때\n"
        "- **500 / INTERNAL_ERROR**: 확인 건을 열지 못했을 때\n"
        "- **503 / ESCROW_UNAVAILABLE**: 에스크로 서버에 연결하지 못했을 때\n\n"
        "`accountNumber`가 `00000000000000`이면 400 `ACCOUNT_REJECTED`를 준다."
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
        expiresAt="2026-09-02T14:12:00+09:00",
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
        400: {
            "model": ErrorResponse,
            "description": "네 자리가 맞지 않거나 확인 건을 더 쓸 수 없음",
            "content": {"application/json": {"example": AUTH_CODE_MISMATCH_EXAMPLE}},
        },
        403: {
            "model": ErrorResponse,
            "description": "토큰의 주인이 연 확인 건이 아님",
            "content": {"application/json": {"example": FORBIDDEN_EXAMPLE}},
        },
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
        "입금자명 뒤 네 자리를 내면 확인 건이 끝나고 그 계좌가 등록된다. 등록한 계좌가 "
        "이미 있으면 이 시점에 대체된다.\n\n"
        "계좌번호는 암호화해 보관하고 응답에는 언제나 가려서 내보낸다.\n\n"
        "**지금 단계에서는 네 자리를 실제로 검사하지 않는다.** 은행 앱을 쓸 수 없어 "
        "입금자명에 찍힌 인증코드를 읽어올 방법이 없기 때문이다. 클라이언트가 어떤 "
        "값을 보내든, 에스크로 서버가 금융망에 맞는 값을 대신 건네 확인을 끝낸다. "
        "따라서 이 단계에서 400 `AUTH_CODE_MISMATCH`는 실제로 나오지 않는다.\n\n"
        "은행 앱을 쓸 수 있게 되면 클라이언트가 보낸 값을 그대로 금융망에 넘기도록 "
        "바꾼다. **요청·응답 형식은 그대로이므로 클라이언트는 고칠 것이 없다.**\n\n"
        "**확인 건의 주인이 토큰의 주인인지 검증한다.** `verificationId`의 확인 건을 "
        "연 사람이 `sub`와 같은지 확인한 뒤에 처리하고, 다르면 403으로 막는다. 남의 "
        "확인 건을 끝내 그 계좌를 내 계좌로 등록하는 일이 없어야 한다. 등록되는 계좌는 "
        "언제나 `sub`의 계좌다.\n\n"
        "- **200**: 네 자리가 맞아 계좌가 등록됐을 때\n"
        "- **400 / AUTH_CODE_MISMATCH**: 네 자리가 다를 때. 확인 건은 아직 살아 "
        "있어 다시 낼 수 있다\n"
        "- **400 / VERIFICATION_EXPIRED**: `expiresAt`이 지났을 때\n"
        "- **400 / VERIFICATION_CLOSED**: 여러 번 틀려 확인 건이 닫혔을 때. 계좌 "
        "입력부터 다시 한다\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **403 / FORBIDDEN**: `sub`가 연 확인 건이 아닐 때\n"
        "- **404 / VERIFICATION_NOT_FOUND**: `verificationId`에 해당하는 확인 건이 "
        "없을 때\n"
        "- **422 / INVALID_REQUEST**: `authCode`가 네 자리 숫자가 아닐 때\n"
        "- **500 / INTERNAL_ERROR**: 계좌를 저장하지 못했을 때\n\n"
        "`authCode`가 `1234`면 등록에 성공하고, 그 밖의 값이면 400 "
        "`AUTH_CODE_MISMATCH`를 준다."
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
    if request.authCode != VALID_AUTH_CODE:
        return _error_response(400, AUTH_CODE_MISMATCH)

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
    profileImageFileId: int | None = Field(
        description=(
            "직접 올린 프로필 사진의 파일 식별자. null이면 카카오 사진을 쓰고 있는 "
            "것이고, 화면은 '카카오 프로필 사진을 쓰고 있습니다'라고 적는다."
        ),
        examples=[None],
    )


class MyProjectResponse(BaseModel):
    """마이페이지에 한 줄로 나가는, 내가 등록한 프로젝트."""

    projectId: int = Field(description="프로젝트 식별자.", examples=[12])
    name: str = Field(description="프로젝트 이름.", examples=["수제 비누 가게"])
    latestDiagnosisId: int | None = Field(
        description="가장 최근 진단서 식별자. 아직 진단이 없으면 null이다.",
        examples=[31],
    )
    diagnosisCount: int = Field(
        description="이 프로젝트에서 만든 진단서 수. 화면의 '진단 v2'에서 v 뒤 숫자다.",
        examples=[2],
    )
    latestDiagnosedAt: str | None = Field(
        description="가장 최근에 진단한 시각. 진단이 없으면 null이다.",
        examples=["2026-08-17T09:12:00+09:00"],
    )
    roomCount: int = Field(description="이 프로젝트로 연 방 수.", examples=[4])


class MyDeveloperResponse(BaseModel):
    """마이페이지의 개발자 프로필 칸. 개발자로 전환한 사람에게만 내려간다."""

    developerId: int = Field(description="개발자 식별자.", examples=[3])
    isPublished: bool = Field(
        description="프로필이 공개 중인지. 거짓이면 개발자 찾기에 나오지 않는다.",
        examples=[True],
    )
    reviewCount: int = Field(description="받은 리뷰 수.", examples=[12])
    upcomingSessionCount: int = Field(
        description="아직 하지 않은 컨설팅 세션 수. 화면의 '잡힌 세션 N건'이다.",
        examples=[1],
    )


class PaymentMethodResponse(BaseModel):
    """예치금을 낼 때 쓰는 수단."""

    paymentMethodId: int = Field(description="결제 수단 식별자.", examples=[9])
    methodType: Literal["CARD", "REALTIME_TRANSFER"] = Field(
        description=(
            "수단 종류. CARD는 등록해 둔 카드, REALTIME_TRANSFER는 결제할 때 은행 "
            "앱에서 바로 내는 계좌이체다. 계좌이체는 미리 등록하지 않으므로 지난번에 "
            "쓴 기록으로만 남는다."
        ),
        examples=["CARD"],
    )
    maskedNumber: str | None = Field(
        description="가려진 번호. 전체 번호는 내려보내지 않는다.",
        examples=["···· 4412"],
    )
    cardCompany: CodeResponse | None = Field(
        description="카드사. 계좌이체면 null이다.",
    )
    isDefault: bool = Field(
        description="기본 수단인지. 예치 화면이 이 수단을 먼저 고른다.",
        examples=[True],
    )


class NotificationSettingResponse(BaseModel):
    """알림 수신 설정."""

    isEnabled: bool = Field(
        description=(
            "카카오 알림톡을 받을지. 거짓이면 방 소식과 확인 요청을 보내지 않는다."
        ),
        examples=[True],
    )


class MyPageResponse(BaseModel):
    """마이페이지 한 판."""

    account: MyAccountResponse = Field(description="계정 칸.")
    paymentMethods: list[PaymentMethodResponse] = Field(
        description=(
            "등록한 결제 수단. 하나도 없으면 빈 배열이고 화면은 등록 안내를 낸다."
        ),
    )
    settlementAccount: AccountResponse | None = Field(
        description="환불과 대금을 받는 계좌. 등록하지 않았으면 null이다.",
    )
    settlementAccountVerifiedAt: str | None = Field(
        description="그 계좌를 1원 인증으로 확인한 시각. 계좌가 없으면 null이다.",
        examples=["2026-09-02T14:05:00+09:00"],
    )
    projects: list[MyProjectResponse] = Field(
        description="내가 등록한 프로젝트. 없으면 빈 배열이다.",
    )
    developer: MyDeveloperResponse | None = Field(
        description=(
            "개발자 프로필 칸. 개발자로 전환하지 않았으면 null이고 화면에서 빠진다."
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

    isEnabled: bool = Field(
        description="참이면 알림톡을 받고, 거짓이면 받지 않는다.",
        examples=[False],
    )


class RegisterPaymentMethodRequest(BaseModel):
    """카드 등록 요청.

    카드 번호는 결제대행사 결제창에서 받으므로 Komit 서버로 넘어오지 않는다.
    여기로는 결제창이 돌려준 키와 화면에 보여줄 값만 온다.
    """

    billingKey: str = Field(
        description="결제대행사가 돌려준 카드 키. 이 값으로 다음 결제를 건다.",
        examples=["bk_2f91c0e4"],
    )
    cardCompanyCodeId: int = Field(description="카드사 코드 식별자.", examples=[21])
    maskedNumber: str = Field(
        description="결제창이 돌려준 가려진 카드번호.",
        examples=["···· 4412"],
    )


# --------------------------------------------------------------------------- #
# 알림
# --------------------------------------------------------------------------- #


class NotificationResponse(BaseModel):
    """받은 알림 한 줄."""

    notificationId: int = Field(description="알림 식별자.", examples=[901])
    type: str = Field(
        description="알림 종류. 코드 분류 NOTIFICATION_TYPE의 코드 이름이다.",
        examples=["승인알림"],
    )
    content: str = Field(
        description="알림 한 줄. 공지는 제목만 담고 본문은 body에 있다.",
        examples=["이준 개발자가 3차를 끝냈습니다"],
    )
    body: str | None = Field(
        description="공지 본문. 공지가 아니면 null이다.",
        examples=[None],
    )
    targetType: Literal["ANNOUNCEMENT", "CONTRACT", "ISSUE"] = Field(
        description="'확인하기'를 눌렀을 때 갈 곳의 종류.",
        examples=["CONTRACT"],
    )
    targetId: int | None = Field(
        description="갈 곳의 식별자. 갈 곳이 없으면 null이다.",
        examples=[130],
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
        description="안 읽은 알림 수. 헤더 종 옆 숫자와 '안 읽음' 뱃지에 쓴다.",
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
        description="화면에 보여줄 줄.",
        examples=["수제 비누 가게 · 진행 중인 의뢰"],
    )
    amount: int | None = Field(
        description="금액. 돈이 아닌 항목이면 null이다.",
        examples=[None],
    )
    targetId: int | None = Field(
        description="'이동'을 눌렀을 때 갈 계약의 식별자. 갈 곳이 없으면 null이다.",
        examples=[130],
    )


class WithdrawalResultResponse(BaseModel):
    """탈퇴 처리 결과.

    계정을 지우는 것이 아니라 삭제 표시를 켜는 것이므로, 무엇이 바뀌었고 언제부터
    다시 가입할 수 있는지를 돌려준다.
    """

    isDeleted: bool = Field(
        description=(
            "삭제 표시. 탈퇴로 켜진 값이며, 사용자 정보는 지우지 않고 이 표시만 바꾼다."
        ),
        examples=[True],
    )
    withdrawnAt: str = Field(
        description="삭제 표시를 켠 시각.",
        examples=["2026-09-14T10:05:00+09:00"],
    )
    rejoinableFrom: str = Field(
        description="같은 카카오 계정으로 다시 가입할 수 있게 되는 시각.",
        examples=["2026-10-14T10:05:00+09:00"],
    )


class WithdrawalCheckResponse(BaseModel):
    """탈퇴할 수 있는지."""

    canWithdraw: bool = Field(
        description="지금 탈퇴할 수 있는지. 거짓이면 화면이 탈퇴 버튼을 잠근다.",
        examples=[False],
    )
    blockers: list[WithdrawalBlockerResponse] = Field(
        description="정리해야 할 것. 없으면 빈 배열이고 이때 canWithdraw가 참이다.",
    )


# --------------------------------------------------------------------------- #
# 개발자 프로필 · 일정
# --------------------------------------------------------------------------- #


class MyPriceResponse(BaseModel):
    """내 가격표 한 줄."""

    priceId: int = Field(description="가격표 식별자.", examples=[61])
    taskType: Literal["CONSULT", "DEVELOP"] = Field(
        description="작업 종류. CONSULT는 컨설팅 세션, DEVELOP은 개발 작업이다.",
        examples=["DEVELOP"],
    )
    taskName: str = Field(
        description=(
            "작업 이름. 컨설팅은 '30분 화상'처럼 시간을, 개발은 '로그인 붙이기'처럼 "
            "할 일을 적는다."
        ),
        examples=["로그인 붙이기"],
    )
    price: int = Field(description="가격. 원 단위 정수다.", examples=[400000])
    periodDays: int | None = Field(
        description="예상 작업 기간(일). 컨설팅 세션은 null이다.",
        examples=[4],
    )


class MyDeveloperProfileResponse(DeveloperDetailResponse):
    """내 프로필 편집 화면이 쓰는 값. 남에게 보이는 상세에 가격표와 리뷰를 더한다."""

    isPublished: bool = Field(description="프로필이 공개 중인지.", examples=[True])
    myPrices: list[MyPriceResponse] = Field(
        description=(
            "내가 등록한 가격표 전부. 남에게 보이는 상세의 `prices`는 유형별 최저가만 "
            "담지만, 편집 화면은 줄마다 고쳐야 하므로 전부 내려준다."
        ),
    )
    reviews: list[ReviewResponse] = Field(
        description="받은 리뷰. 새로운 것이 먼저다. 답글을 여기서 단다.",
    )
    replyCount: int = Field(description="답글을 단 리뷰 수.", examples=[1])
    updatedAt: str = Field(
        description="마지막으로 저장한 시각. 화면의 '마지막 저장'에 쓴다.",
        examples=["2026-08-14T18:20:00+09:00"],
    )


class WriteCareerRequest(BaseModel):
    """저장할 경력 한 줄."""

    careerId: int | None = Field(
        default=None,
        description="이미 있는 경력이면 그 식별자, 새로 추가하는 줄이면 null이다.",
        examples=[41],
    )
    company: str = Field(description="회사 이름.", examples=["카카오스타일"])
    role: str = Field(description="맡았던 일.", examples=["백엔드 · 주문/정산 서비스"])
    startDate: str = Field(description="시작일.", examples=["2021-03-01"])
    endDate: str | None = Field(
        description="종료일. 지금도 다니는 중이면 null이다.",
        examples=["2024-08-31"],
    )
    evidenceFileId: int | None = Field(
        default=None,
        description=(
            "증빙 서류 파일 식별자. 파일 올리기로 먼저 올린 뒤 그 식별자를 넣는다. "
            "증빙은 경력 확인에만 쓰고 확인이 끝나면 원본을 지운다."
        ),
        examples=[512],
    )


class WritePortfolioItemRequest(BaseModel):
    """저장할 작업물 하나."""

    portfolioItemId: int | None = Field(
        default=None,
        description="이미 있는 작업물이면 그 식별자, 새로 추가하면 null이다.",
        examples=[7],
    )
    title: str = Field(description="작업물 제목.", examples=["미미헤어샵 예약"])
    description: str | None = Field(
        description="설명.",
        examples=["예약이 저장되지 않던 문제를 고쳤습니다."],
    )
    linkUrl: str | None = Field(
        description="배포 사이트나 저장소 주소. 없으면 null이다.",
        examples=["https://hairshop-mimi.com"],
    )
    thumbnailFileId: int | None = Field(
        description="썸네일 파일 식별자. 올리지 않았으면 null이다.",
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
        description="작업 종류.",
        examples=["DEVELOP"],
    )
    taskName: str = Field(description="작업 이름.", examples=["로그인 붙이기"])
    price: int = Field(description="가격. 원 단위 정수다.", examples=[400000])
    periodDays: int | None = Field(
        description="예상 작업 기간(일). 컨설팅 세션이면 비운다.",
        examples=[4],
    )


class WriteDeveloperProfileRequest(BaseModel):
    """내 프로필 저장 요청. 화면의 저장 버튼 하나가 이 요청 하나를 보낸다."""

    intro: str = Field(
        description="한 줄 소개.",
        examples=["간단한 상담도 받습니다. 용어부터 풀어서 설명합니다."],
    )
    description: str | None = Field(
        description="소개 글.",
        examples=["AI로 만든 걸 실제로 돌아가게 만드는 일을 주로 합니다."],
    )
    fieldCodeId: int | None = Field(
        description="주로 담당하는 개발 분야 코드 식별자.",
        examples=[4],
    )
    yearsOfExperience: float | None = Field(
        description="경력 연차.",
        examples=[5.0],
    )
    activeFrom: str | None = Field(
        description="주로 활동하는 시간대의 시작.",
        examples=["10:00"],
    )
    activeTo: str | None = Field(
        description="주로 활동하는 시간대의 끝.",
        examples=["19:00"],
    )
    techCodeIds: list[int] = Field(
        description="다룰 수 있는 기술의 코드 식별자. 보낸 목록이 그대로 저장된다.",
        examples=[[31, 32, 33]],
    )
    careers: list[WriteCareerRequest] = Field(
        description=(
            "경력 전부. 보낸 목록에 없는 기존 경력은 지운 것으로 본다. 확인이 끝난 "
            "경력의 확인 표시는 저장해도 그대로 남는다."
        ),
    )
    portfolioItems: list[WritePortfolioItemRequest] = Field(
        description="작업물 전부. 보낸 순서가 화면에 보이는 순서다.",
    )
    prices: list[WritePriceRequest] = Field(
        description="가격표 전부. 보낸 목록에 없는 기존 줄은 지운 것으로 본다.",
    )
    isPublished: bool = Field(
        description=(
            "참이면 저장하면서 프로필을 공개한다. 화면의 '저장하고 프로필 공개'가 "
            "참을 보낸다."
        ),
        examples=[True],
    )


class WriteReviewReplyRequest(BaseModel):
    """리뷰 답글 요청."""

    reply: str = Field(
        description="답글 내용.",
        examples=["감사합니다. 도메인 만료 전에 미리 알려드리겠습니다."],
    )


class MySessionResponse(BaseModel):
    """내 일정에 한 줄로 나가는 컨설팅 세션."""

    sessionId: int = Field(description="컨설팅 세션 식별자.", examples=[55])
    contractId: int = Field(description="이 세션이 달린 계약 식별자.", examples=[120])
    clientName: str = Field(description="의뢰인 이름.", examples=["김미미"])
    scheduledAt: str = Field(
        description="예약한 일시.",
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


class UploadedFileResponse(BaseModel):
    """올린 파일 하나."""

    fileId: int = Field(description="파일 식별자.", examples=[512])
    fileName: str = Field(description="올린 파일 이름.", examples=["재직증명서.pdf"])
    contentType: str | None = Field(
        description="파일 종류.",
        examples=["application/pdf"],
    )
    sizeBytes: int = Field(description="파일 크기(바이트).", examples=[284193])
    uploadedAt: str = Field(
        description="올린 시각.",
        examples=["2026-08-14T18:12:00+09:00"],
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
FILE_TOO_LARGE = ("FILE_TOO_LARGE", "파일이 너무 큽니다.")
UNSUPPORTED_FILE_TYPE = ("UNSUPPORTED_FILE_TYPE", "올릴 수 없는 형식입니다.")

WITHDRAWAL_BLOCKED_EXAMPLE = {
    "code": WITHDRAWAL_BLOCKED[0],
    "message": WITHDRAWAL_BLOCKED[1],
}
NO_DEVELOPER_PROFILE_EXAMPLE = {
    "code": NO_DEVELOPER_PROFILE[0],
    "message": NO_DEVELOPER_PROFILE[1],
}
FILE_TOO_LARGE_EXAMPLE = {"code": FILE_TOO_LARGE[0], "message": FILE_TOO_LARGE[1]}

AREA8_FORBIDDEN: dict = {
    403: {
        "model": ErrorResponse,
        "description": "개발자 프로필이 없음",
        "content": {"application/json": {"example": NO_DEVELOPER_PROFILE_EXAMPLE}},
    },
}

MY_PAGE_EXAMPLE = {
    "account": {
        "name": "김미미",
        "email": "mimi@kakao.com",
        "profileImageFileId": None,
    },
    "paymentMethods": [
        {
            "paymentMethodId": 9,
            "methodType": "CARD",
            "maskedNumber": "···· 4412",
            "cardCompany": {"codeId": 21, "codeName": "우리카드"},
            "isDefault": True,
        }
    ],
    "settlementAccount": {
        "accountId": 22,
        "bankName": "신한은행",
        "maskedNumber": "···· 4412",
        "accountHolder": "김미미",
    },
    "settlementAccountVerifiedAt": "2026-09-02T14:05:00+09:00",
    "projects": [
        {
            "projectId": 12,
            "name": "수제 비누 가게",
            "latestDiagnosisId": 31,
            "diagnosisCount": 2,
            "latestDiagnosedAt": "2026-08-17T09:12:00+09:00",
            "roomCount": 4,
        },
        {
            "projectId": 11,
            "name": "미미헤어샵 예약",
            "latestDiagnosisId": 18,
            "diagnosisCount": 1,
            "latestDiagnosedAt": "2026-07-02T11:40:00+09:00",
            "roomCount": 2,
        },
        {
            "projectId": 14,
            "name": "사내 재고 관리",
            "latestDiagnosisId": None,
            "diagnosisCount": 0,
            "latestDiagnosedAt": None,
            "roomCount": 1,
        },
    ],
    "developer": None,
    "notificationSetting": {"isEnabled": True},
}

MY_PAGE_EMPTY_EXAMPLE = {
    **MY_PAGE_EXAMPLE,
    "paymentMethods": [],
    "settlementAccount": None,
    "settlementAccountVerifiedAt": None,
}

PAYMENT_METHOD_EXAMPLE = MY_PAGE_EXAMPLE["paymentMethods"][0]

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
            "content": "9월 1일부터 정산 지급 주기가 바뀝니다",
            "body": (
                "작업 전 건이 통과 확정되면 영업일 3일 이내에 지급하던 것을 영업일 "
                "2일 이내로 앞당깁니다. 8월 31일까지 체결된 계약에도 동일하게 "
                "적용됩니다."
            ),
            "targetType": "ANNOUNCEMENT",
            "targetId": 7,
            "isRead": False,
            "createdAt": "2026-08-28T10:00:00+09:00",
        },
        {
            "notificationId": 951,
            "type": "승인알림",
            "content": "이준 개발자가 3차를 끝냈습니다",
            "body": None,
            "targetType": "CONTRACT",
            "targetId": 130,
            "isRead": False,
            "createdAt": "2026-08-30T14:30:00+09:00",
        },
        {
            "notificationId": 950,
            "type": "착수알림",
            "content": "한서 개발자가 계약서를 보냈습니다",
            "body": None,
            "targetType": "CONTRACT",
            "targetId": 131,
            "isRead": False,
            "createdAt": "2026-08-30T11:02:00+09:00",
        },
        {
            "notificationId": 930,
            "type": "예약알림톡",
            "content": "컨설팅 세션이 30분 뒤에 시작합니다",
            "body": None,
            "targetType": "CONTRACT",
            "targetId": 120,
            "isRead": True,
            "createdAt": "2026-08-29T13:30:00+09:00",
        },
        {
            "notificationId": 910,
            "type": "승인알림",
            "content": "'관리자 API에 인증 추가'가 통과 확정됐습니다",
            "body": None,
            "targetType": "CONTRACT",
            "targetId": 130,
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
        },
        {
            "type": "DEPOSIT",
            "title": "예치금 650,000원",
            "amount": 650000,
            "targetId": 130,
        },
    ],
}

WITHDRAWAL_RESULT_EXAMPLE = {
    "isDeleted": True,
    "withdrawnAt": "2026-09-14T10:05:00+09:00",
    "rejoinableFrom": "2026-10-14T10:05:00+09:00",
}

MY_DEVELOPER_PROFILE_EXAMPLE = {
    "developerId": 3,
    "name": "이준",
    "profileImageFileId": None,
    "intro": "간단한 상담도 받습니다. 용어부터 풀어서 설명합니다.",
    "yearsOfExperience": 5.0,
    "field": {"codeId": 4, "codeName": "백엔드"},
    "isCareerVerified": True,
    "devRating": {"score": 4.8, "reviewCount": 20},
    "explanatoryRating": {"score": 4.9, "reviewCount": 20},
    "techCategories": [
        {"codeId": 31, "codeName": "TypeScript"},
        {"codeId": 32, "codeName": "Next.js"},
        {"codeId": 33, "codeName": "Supabase"},
    ],
    "prices": [
        {"taskType": "CONSULT", "price": 50000},
        {"taskType": "DEVELOP", "price": 100000},
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
        },
        {
            "careerId": 42,
            "company": "당근마켓",
            "role": "백엔드 인턴 · 검색 색인",
            "startDate": "2020-06-01",
            "endDate": "2020-12-31",
            "isVerified": True,
            "verifiedAt": "2026-03-04T10:00:00+09:00",
        },
        {
            "careerId": 43,
            "company": "프리랜서",
            "role": "소상공인 웹 서비스 14건 · Komit 13건",
            "startDate": "2024-09-01",
            "endDate": None,
            "isVerified": False,
            "verifiedAt": None,
        },
    ],
    "portfolioItems": [
        {
            "portfolioItemId": 7,
            "title": "미미헤어샵 예약",
            "description": "예약이 저장되지 않던 문제를 고쳤습니다.",
            "linkUrl": "https://hairshop-mimi.com",
            "thumbnailFileId": 512,
        },
        {
            "portfolioItemId": 8,
            "title": "동네 필라테스 수업",
            "description": "회원가입 없이 수업을 예약할 수 있게 했습니다.",
            "linkUrl": "https://pilates-onnuri.kr",
            "thumbnailFileId": 513,
        },
        {
            "portfolioItemId": 9,
            "title": "사내 재고 관리",
            "description": "엑셀로 관리하던 재고를 DB로 옮겼습니다.",
            "linkUrl": None,
            "thumbnailFileId": None,
        },
    ],
    "isPublished": True,
    "myPrices": [
        {
            "priceId": 60,
            "taskType": "CONSULT",
            "taskName": "30분 화상",
            "price": 50000,
            "periodDays": None,
        },
        {
            "priceId": 61,
            "taskType": "CONSULT",
            "taskName": "60분 화상",
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
            "taskName": "로그인 붙이기",
            "price": 400000,
            "periodDays": 4,
        },
    ],
    "reviews": [
        {
            "reviewId": 88,
            "clientName": "김미미",
            "devScore": 5,
            "commScore": 5,
            "content": (
                "예약이 저장되지 않던 문제를 고쳐주셨고, 왜 그랬는지도 제가 알아듣게 "
                "설명해주셨습니다."
            ),
            "reply": "감사합니다. 도메인 만료 전에 미리 알려드리겠습니다.",
            "createdAt": "2026-08-19T14:20:00+09:00",
        },
        {
            "reviewId": 84,
            "clientName": "최수진",
            "devScore": 4,
            "commScore": 5,
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
            "contractId": 121,
            "clientName": "김미미",
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
            "clientName": "김미미",
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
    "fileName": "재직증명서.pdf",
    "contentType": "application/pdf",
    "sizeBytes": 284193,
    "uploadedAt": "2026-08-14T18:12:00+09:00",
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
                            "summary": "결제 수단과 계좌를 등록한 사람",
                            "value": MY_PAGE_EXAMPLE,
                        },
                        "empty": {
                            "summary": "아직 아무것도 등록하지 않은 사람",
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
        "마이페이지 한 판을 한 번에 내려준다. 계정, 결제 수단, 계좌, 등록한 프로젝트, "
        "개발자 프로필 요약, 알림 설정이 들어간다.\n\n"
        "`paymentMethods`가 빈 배열이거나 `settlementAccount`가 null이면 화면이 그 칸을 "
        "등록 안내로 바꾼다. 같은 API이며 화면만 갈린다.\n\n"
        "`developer`는 개발자로 전환하지 않은 사람에게 null로 나가고, 화면에서 개발자 "
        "프로필 칸이 통째로 빠진다.\n\n"
        "약관 칸은 이 응답에 없다. 약관 문서와 개정일은 서버가 들고 있지 않고 화면이 "
        "가진 정적 문서다.\n\n"
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
        403: {
            "model": ErrorResponse,
            "description": "토큰의 주인이 아닌 계정을 바꾸려 함",
            "content": {"application/json": {"example": FORBIDDEN_EXAMPLE}},
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
        "- **200**: 바꿨을 때\n"
        "- **401 / UNAUTHORIZED**: 토큰이 없거나 만료·위조됐을 때, `sub`의 사용자가 "
        "없거나 탈퇴한 계정일 때\n"
        "- **403 / FORBIDDEN**: 토큰의 `sub`가 아닌 계정을 바꾸려 할 때\n"
        "- **422 / INVALID_REQUEST**: `name`이 비었거나 20자를 넘었을 때\n"
        "- **500 / INTERNAL_ERROR**: 저장하지 못했을 때"
    ),
)
def update_my_name(request: UpdateMyNameRequest) -> MyAccountResponse:
    return MyAccountResponse(
        name=request.name,
        email=MY_PAGE_EXAMPLE["account"]["email"],
        profileImageFileId=None,
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
            "description": "파일이 너무 크거나 형식이 맞지 않음",
            "content": {"application/json": {"example": FILE_TOO_LARGE_EXAMPLE}},
        },
        **AREA1_UNAUTHORIZED,
        **AREA1_ERRORS,
    },
    summary="프로필 사진 바꾸기",
    description=(
        "**화면 — `8_마이페이지` › 계정 › 사진 바꾸기**\n\n"
        "프로필 사진을 직접 올린다. 올리기 전에는 카카오 프로필 사진을 쓰고, 한 번 "
        "올리면 그 뒤로는 올린 사진을 쓴다.\n\n"
        "**바뀌는 것은 토큰의 주인의 사진이다.** 액세스 토큰을 검증해 `sub`를 꺼내 그 "
        "계정에 붙이며, 대상을 요청에 담지 않는다.\n\n"
        "- **200**: 바꿨을 때. `profileImageFileId`에 새 파일 식별자가 담긴다\n"
        "- **400 / FILE_TOO_LARGE**: 파일이 허용 크기를 넘었을 때\n"
        "- **400 / UNSUPPORTED_FILE_TYPE**: 이미지가 아닐 때\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **500 / INTERNAL_ERROR**: 저장하지 못했을 때"
    ),
)
def update_my_profile_image(
    image: Annotated[UploadFile, File(description="올릴 사진 파일.")],
) -> MyAccountResponse:
    return MyAccountResponse(
        name=MY_PAGE_EXAMPLE["account"]["name"],
        email=MY_PAGE_EXAMPLE["account"]["email"],
        profileImageFileId=512,
    )


@router.patch(
    "/settings/notification",
    response_model=NotificationSettingResponse,
    tags=[AREA8_TAG],
    responses={
        200: {"content": {"application/json": {"example": {"isEnabled": False}}}},
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
        "- **200**: 바꿨을 때\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **422 / INVALID_REQUEST**: `isEnabled`가 없을 때\n"
        "- **500 / INTERNAL_ERROR**: 저장하지 못했을 때"
    ),
)
def update_notification_setting(
    request: UpdateNotificationSettingRequest,
) -> NotificationSettingResponse:
    return NotificationSettingResponse(isEnabled=request.isEnabled)


@router.post(
    "/payment-methods",
    response_model=PaymentMethodResponse,
    status_code=201,
    tags=[AREA8_TAG],
    responses={
        201: {"content": {"application/json": {"example": PAYMENT_METHOD_EXAMPLE}}},
        **AREA1_UNAUTHORIZED,
        **AREA1_ERRORS,
    },
    summary="카드 등록 · 변경",
    description=(
        "**화면 — `8_마이페이지` › 결제 수단 › 카드 등록 · 카드 바꾸기**\n\n"
        "예치금을 낼 카드를 등록한다. **카드 번호는 이 서버로 오지 않는다.** 화면이 "
        "결제대행사 결제창을 띄워 카드를 받고, 결제창이 돌려준 키와 가린 번호만 여기로 "
        "온다.\n\n"
        "새로 등록한 카드가 기본 수단이 되고, 이전 카드는 기본에서 내려간다.\n\n"
        "**카드는 토큰의 주인에게 등록된다.** 액세스 토큰을 검증해 `sub`를 꺼내 그 "
        "계정에 붙이며, 누구의 카드인지는 요청에 담지 않는다. 남의 계정에 내 카드를 "
        "달거나 남의 카드를 내 계정에 다는 경로가 없어야 한다.\n\n"
        "실시간 계좌이체는 결제할 때 은행 앱에서 바로 내므로 등록할 것이 없다. 이 API는 "
        "카드만 받는다.\n\n"
        "- **201**: 등록했을 때\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **422 / INVALID_REQUEST**: `billingKey`나 `maskedNumber`가 비었을 때\n"
        "- **500 / INTERNAL_ERROR**: 저장하지 못했을 때"
    ),
)
def register_payment_method(
    request: RegisterPaymentMethodRequest,
) -> PaymentMethodResponse:
    return PaymentMethodResponse(
        paymentMethodId=9,
        methodType="CARD",
        maskedNumber=request.maskedNumber,
        cardCompany=CodeResponse(codeId=request.cardCompanyCodeId, codeName="우리카드"),
        isDefault=True,
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
        "받은 알림을 새로운 것부터 내려준다. 공지도 같은 목록에 섞이며, 공지만 `body`에 "
        "본문을 담아 화면이 목록에서 펼쳐 보여준다.\n\n"
        "`unreadCount`는 `unreadOnly`와 페이지에 상관없이 언제나 안 읽은 알림 전체 수다. "
        "헤더 종 옆 숫자가 이 값이다.\n\n"
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
        403: {
            "model": ErrorResponse,
            "description": "토큰의 주인이 받은 알림이 아님",
            "content": {"application/json": {"example": FORBIDDEN_EXAMPLE}},
        },
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
        **AREA8_FORBIDDEN,
        **AREA1_UNAUTHORIZED,
        **AREA1_ERRORS,
    },
    summary="내 개발자 프로필 조회",
    description=(
        "**화면 — `8_개발자_내프로필`**\n\n"
        "편집 화면이 쓰는 값을 한 번에 내려준다. 남에게 보이는 개발자 상세에 가격표 "
        "전부와 받은 리뷰를 더한 모양이다.\n\n"
        "`prices`는 남에게 보이는 것과 같은 유형별 최저가이고, 편집에 쓰는 줄 단위 "
        "가격표는 `myPrices`에 있다.\n\n"
        "평점은 읽기 전용이다. 유료 거래를 마친 의뢰인의 리뷰로만 매겨지므로 저장 "
        "요청으로 바꿀 수 없다.\n\n"
        "- **200**: 항상\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **403 / NO_DEVELOPER_PROFILE**: 개발자로 전환하지 않았을 때\n"
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
        **AREA8_FORBIDDEN,
        **AREA1_UNAUTHORIZED,
        **AREA1_ERRORS,
    },
    summary="내 개발자 프로필 저장",
    description=(
        "**화면 — `8_개발자_내프로필` › 저장하고 프로필 공개**\n\n"
        "화면의 저장 버튼 하나가 이 요청 하나를 보낸다. 소개, 기술, 경력, 작업물, "
        "가격표를 통째로 받아 **보낸 내용으로 맞춘다** — 목록에서 빠진 줄은 지운 것으로 "
        "본다. 줄마다 따로 부르는 API는 두지 않는다.\n\n"
        "새로 추가하는 줄은 식별자를 null로 보내고, 서버가 만들어 응답에 담아 준다.\n\n"
        "**저장되는 곳은 토큰의 주인의 프로필이다.** 액세스 토큰을 검증해 `sub`를 꺼내 "
        "그 사용자의 개발자 프로필에 저장하며, 대상을 요청에 담지 않는다. 요청에 실린 "
        "`careerId`·`portfolioItemId`·`priceId`가 그 프로필의 것인지도 확인하고, 남의 "
        "줄을 가리키면 저장하지 않는다.\n\n"
        "**저장해도 바뀌지 않는 것이 있다.** 경력의 증빙 확인 표시는 운영에서 확인한 "
        "값이라 그대로 남고, 평점과 리뷰 수는 의뢰인이 남긴 것이라 손대지 않는다.\n\n"
        "`isPublished`가 참이면 저장과 동시에 프로필이 공개되어 개발자 찾기에 나온다.\n\n"
        "- **200**: 저장했을 때. 저장된 뒤의 프로필을 그대로 준다\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **403 / NO_DEVELOPER_PROFILE**: 개발자로 전환하지 않았을 때\n"
        "- **422 / INVALID_REQUEST**: `intro`가 비었거나, 경력의 시작일이 종료일보다 "
        "늦거나, 가격이 0 이하일 때\n"
        "- **500 / INTERNAL_ERROR**: 저장하지 못했을 때"
    ),
)
def write_my_developer_profile(
    request: WriteDeveloperProfileRequest,
) -> MyDeveloperProfileResponse:
    return MyDeveloperProfileResponse(
        **{
            **MY_DEVELOPER_PROFILE_EXAMPLE,
            "intro": request.intro,
            "description": request.description,
            "isPublished": request.isPublished,
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
        403: {
            "model": ErrorResponse,
            "description": "남의 리뷰이거나 개발자 프로필이 없음",
            "content": {
                "application/json": {
                    "examples": {
                        "forbidden": {
                            "summary": "`sub`가 받은 리뷰가 아닐 때",
                            "value": FORBIDDEN_EXAMPLE,
                        },
                        "noDeveloperProfile": {
                            "summary": "개발자로 전환하지 않았을 때",
                            "value": NO_DEVELOPER_PROFILE_EXAMPLE,
                        },
                    }
                }
            },
        },
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
        "**이 API의 403은 두 가지다.** 남의 리뷰면 `FORBIDDEN`, 개발자로 전환하지 "
        "않았으면 `NO_DEVELOPER_PROFILE`이다. 화면은 `code`로 가른다.\n\n"
        "- **200**: 달았거나 고쳤을 때\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **403 / FORBIDDEN**: `sub`가 받은 리뷰가 아닐 때\n"
        "- **403 / NO_DEVELOPER_PROFILE**: 개발자로 전환하지 않았을 때\n"
        "- **404 / REVIEW_NOT_FOUND**: `reviewId`가 404일 때\n"
        "- **422 / INVALID_REQUEST**: `reply`가 비었을 때\n"
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
        **AREA8_FORBIDDEN,
        **AREA1_UNAUTHORIZED,
        **AREA1_ERRORS,
    },
    summary="내 일정 조회",
    description=(
        "**화면 — `8_개발자_내일정`**\n\n"
        "잡힌 컨설팅 세션과 지난 세션을 나눠 내려준다. 한 세션에 예약은 하나뿐이고 "
        "양쪽이 서명한 뒤에는 시각을 바꾸지 않으므로, 여기 `scheduledAt`이 곧 확정된 "
        "시각이다.\n\n"
        "화면의 '시작 N분 전'은 `scheduledAt`에서, '언제까지 질문 받습니다'는 "
        "`followUpUntil`에서 만든다.\n\n"
        "개발 계약에는 예약이 없으므로 이 목록에 나오지 않는다.\n\n"
        "- **200**: 항상. 잡힌 세션이 없으면 빈 배열이다\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **403 / NO_DEVELOPER_PROFILE**: 개발자로 전환하지 않았을 때\n"
        "- **500 / INTERNAL_ERROR**: 일정을 만들지 못했을 때"
    ),
)
def get_my_sessions() -> MySessionListResponse:
    return MySessionListResponse(**MY_SESSION_LIST_EXAMPLE)


@router.post(
    "/files",
    response_model=UploadedFileResponse,
    status_code=201,
    tags=[AREA8_TAG],
    responses={
        201: {"content": {"application/json": {"example": UPLOADED_FILE_EXAMPLE}}},
        400: {
            "model": ErrorResponse,
            "description": "파일이 너무 크거나 형식이 맞지 않음",
            "content": {"application/json": {"example": FILE_TOO_LARGE_EXAMPLE}},
        },
        **AREA1_UNAUTHORIZED,
        **AREA1_ERRORS,
    },
    summary="파일 올리기",
    description=(
        "**화면 — `8_개발자_내프로필` › 증빙 서류 파일 올리기 · 작업물 사진 추가**\n\n"
        "파일을 먼저 올려 식별자를 받고, 그 식별자를 프로필 저장 요청에 담는다. 올리기만 "
        "해서는 어디에도 붙지 않으며, 저장 요청에 담기지 않은 파일은 붙지 않은 채로 "
        "남는다.\n\n"
        "**증빙 서류는 경력 확인에만 쓴다.** 운영에서 확인이 끝나면 원본을 지우고 "
        "프로필에는 확인 표시만 남는다.\n\n"
        "작업물 사진은 의뢰인이 공개에 동의한 것만 올릴 수 있다. 동의 여부는 계약에 "
        "적혀 있고 서버가 확인한다.\n\n"
        "- **201**: 올렸을 때\n"
        "- **400 / FILE_TOO_LARGE**: 파일이 허용 크기를 넘었을 때\n"
        "- **400 / UNSUPPORTED_FILE_TYPE**: 받지 않는 형식일 때\n"
        "- **401 / UNAUTHORIZED**: 로그인하지 않았을 때\n"
        "- **422 / INVALID_REQUEST**: `file`이나 `purpose`가 없을 때\n"
        "- **500 / INTERNAL_ERROR**: 저장하지 못했을 때"
    ),
)
def upload_file(
    file: Annotated[UploadFile, File(description="올릴 파일.")],
    purpose: Annotated[
        Literal["CAREER_EVIDENCE", "PORTFOLIO_IMAGE"],
        Form(
            description=(
                "이 파일을 어디에 쓸지. 쓰임에 따라 받는 형식과 크기가 다르고 보관 "
                "기간도 다르다."
            )
        ),
    ],
) -> UploadedFileResponse:
    return UploadedFileResponse(
        **{**UPLOADED_FILE_EXAMPLE, "fileName": file.filename or "file"}
    )


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

    loginId: str = Field(description="운영자 로그인 아이디.", examples=["soomin"])
    password: str = Field(description="비밀번호.", examples=["komit-console"])


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
    """대사 실행 한 번."""

    runId: int = Field(description="대사 실행 식별자.", examples=[512])
    asOf: str = Field(
        description="이 시점까지 확정된 원장과 잔고를 비교했다.",
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
    title: str = Field(description="양식 제목.", examples=["개발 외주 표준계약서 v4"])
    body: str = Field(
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

ADMIN_REPORT_DETAIL_EXAMPLE = {
    **ADMIN_REPORT_LIST_EXAMPLE["content"][0],
    "evidenceFileId": 601,
    "evidenceFileName": "화면 캡처.png",
}

RECONCILIATION_EXAMPLE = {
    "latest": {
        "runId": 512,
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
            "runId": 512,
            "asOf": "2026-09-02T09:00:00+09:00",
            "ledgerTotal": 12447300,
            "bankBalance": 12447300,
            "differenceAmount": 0,
            "result": "MATCHED",
        },
        {
            "runId": 508,
            "asOf": "2026-09-01T09:00:00+09:00",
            "ledgerTotal": 12183900,
            "bankBalance": 12183900,
            "differenceAmount": 0,
            "result": "MATCHED",
        },
        {
            "runId": 504,
            "asOf": "2026-08-31T09:00:00+09:00",
            "ledgerTotal": 11806400,
            "bankBalance": 11756400,
            "differenceAmount": -50000,
            "result": "BANK_MISMATCH",
        },
        {
            "runId": 500,
            "asOf": "2026-08-30T09:00:00+09:00",
            "ledgerTotal": 11472150,
            "bankBalance": 11472150,
            "differenceAmount": 0,
            "result": "MATCHED",
        },
        {
            "runId": 496,
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

CONTRACT_TEMPLATE_DETAIL_EXAMPLE = {
    **CONTRACT_TEMPLATE_LIST_EXAMPLE["content"][0],
    "body": TEMPLATE_BODY,
    "placeholders": DEV_PLACEHOLDERS,
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
        "목록의 '내용' 칸은 `reason`이다. 신고자가 적어 낸 글이라 길 수 있으므로 화면이 "
        "줄여서 보여주고 전문은 상세에서 본다.\n\n"
        "`openCount`와 `resolvedCount`는 조건이나 페이지와 상관없이 전체 기준이다.\n\n"
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
    return AdminReportDetailResponse(**ADMIN_REPORT_DETAIL_EXAMPLE)


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
            **ADMIN_REPORT_DETAIL_EXAMPLE,
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
    return ContractTemplateDetailResponse(**CONTRACT_TEMPLATE_DETAIL_EXAMPLE)


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
        html = html.replace(name, TEMPLATE_SAMPLE_VALUES.get(name, name))
    unknown = re.findall(r"\{\{[^{}]*\}\}", html)
    return ContractTemplatePreviewResponse(
        html=html, unknownPlaceholders=sorted(set(unknown))
    )


ContractResponse.model_rebuild()
WriteDevelopmentContractRequest.model_rebuild()

# 개발자가 상담 요청을 수락·거절하는 기능은 만들지 않기로 했다.
# ERD의 recommendation_items.status(RECOMMENDATION_DEV_RESPONSE)는 쓰지 않는다.
# 의뢰인이 상담을 요청하면 방이 바로 열린다.


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
        "name": "1 랜딩 · 로그인",
        "description": (
            "랜딩에서 무엇을 파는지 보고 로그인한다.\n\n"
            "**화면** `1_랜딩홈` · `1_로그인`\n\n"
            "랜딩 홈은 히어로 · 증상별로 찾기 · 추천 개발자 · 진행 순서 · 사전 점검 · "
            "푸터 여섯 섹션이 한 페이지에 들어간다.\n\n"
            "**여러 영역이 함께 쓰는 API가 여기 있다.** 토큰 재발급, 로그아웃, 내 정보는 "
            "전 영역에서 부르지만 처음 나온 이 영역에만 적는다. 뒤 영역에서는 반복하지 "
            "않으니, 영역을 검토할 때 이 셋은 여기서 확인한다."
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
            "않는다. **'보내기'도 없다** — 요청서는 방에 걸려 있어 개발자가 언제든 같은 "
            "것을 보므로, 저장하면 그 자리에서 최신 내용이 된다.\n\n"
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
            "제출·통과와 7일 자동 통과를 그대로 탄다.\n\n"
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
            "확인 중인 신고가 있는 계약은 작업이 7일 만에 자동 통과되지 않는다.\n\n"
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
            "마이페이지에서 계정과 돈 쓰는 수단을 관리한다. 흐름 밖 화면이다.\n\n"
            "**화면** `8_마이페이지` · `8_마이페이지_등록전` · `8_계좌등록` · "
            "`8_계좌인증` · `8_펼침_은행선택` · `8_아바타메뉴` · `8_회원탈퇴` · "
            "`8_알림` · `8_약관` · `8_개발자_내프로필` · `8_개발자_내일정`\n\n"
            "**`8_약관`과 `8_아바타메뉴`에는 API가 없다.** 약관 문서와 개정일, 사업자 "
            "정보는 서버가 들고 있지 않고 화면이 가진 정적 문서이며, 아바타 메뉴는 이미 "
            "받은 값으로 그리는 메뉴다.\n\n"
            "**내 프로필은 줄마다 저장하지 않는다.** 화면의 저장 버튼 하나가 소개·기술·"
            "경력·작업물·가격표를 통째로 보내고, 보낸 내용으로 맞춘다. 파일만 먼저 올려 "
            "식별자를 받는다.\n\n"
            "**회원 탈퇴는 지우는 것이 아니다.** 사용자 정보는 그대로 두고 삭제 여부 값만 "
            "참으로 바꾼 뒤 삭제 시각을 적는다. 계약·정산 이력과 메시지, 리뷰가 상대 "
            "화면에도 있는 기록이라 남겨야 하기 때문이며, 그래서 `DELETE`가 아니라 "
            "`POST`를 쓴다.\n\n"
            "**돈을 내는 수단과 받는 계좌는 다르다.** 내는 쪽은 카드와 실시간 계좌이체이고, "
            "받는 쪽은 1원 인증으로 등록한 계좌 하나다. 의뢰인은 그 계좌로 환불을 받고 "
            "개발자는 대금을 받는다. **계좌 API는 두 역할이 함께 쓴다.**\n\n"
            "**계좌는 1원 인증으로만 등록한다.** 계좌를 넣는 요청과 입금자명 뒤 네 자리를 "
            "내는 요청이 나뉘고, 확인이 끝나는 순간에만 계좌가 남는다. 계좌번호 원문은 "
            "어디에도 보관하지 않으며 응답에는 언제나 가려서 나간다.\n\n"
            "**에스크로 서버는 SSAFY 금융망과 코밋 사이의 프록시로 동작한다.** 계층마다 "
            "형식이 다르다.\n\n"
            "- 클라이언트 → 코밋: 아래 API들의 간소화된 형식\n"
            "- 코밋 → 에스크로: SSAFY 금융망 형식(`Header`/`REC`)\n\n"
            "클라이언트는 SSAFY 형식을 알 필요가 없다. `Header`와 기업명은 코밋이 "
            "채우고, 금융망 응답코드는 코밋의 `code`로 옮겨져 나간다.\n\n"
            "**이 영역은 전부 내 것을 다루므로 인가를 반드시 검증한다.** 로그인 여부만 "
            "확인하고 통과시키면 남의 계정·알림·리뷰·계좌를 건드릴 수 있다. 액세스 "
            "토큰을 검증해 `sub`를 꺼내고, 다루려는 자원의 주인이 그 `sub`와 같은지 "
            "확인한 뒤에 처리한다.\n\n"
            "- 대상을 요청에 담지 않는 API(`/users/me...`, `/mypage`, `/notifications`, "
            "`/payment-methods`, `/files`)는 `sub`가 곧 대상이다. 요청에 사용자 식별자를 "
            "실어 보내도 받지 않는다\n"
            "- 경로에 식별자가 있는 API(`/accounts/verifications/{verificationId}/confirm`, "
            "`/notifications/{notificationId}/read`, `/reviews/{reviewId}/reply`)는 그 "
            "자원의 주인이 `sub`와 같은지 확인하고, 다르면 403 `FORBIDDEN`으로 막는다\n\n"
            "**403이 두 뜻으로 쓰인다.** `FORBIDDEN`은 남의 자원을 건드렸다는 뜻이고, "
            "`NO_DEVELOPER_PROFILE`은 개발자로 전환하지 않았다는 뜻이다. 화면 분기는 "
            "상태코드가 아니라 `code`로 가른다.\n\n"
            "**함께 쓰는 API** `GET /api/settlements/income`"
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
