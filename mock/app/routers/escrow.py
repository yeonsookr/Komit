"""escrow — 에스크로 목 앱의 기준 API.

에스크로 앱은 /escrow 에 마운트된다 (app/main.py).
응답 형식·상태코드·description 작성 방식을 이 API에 맞춘다.
"""

from datetime import datetime, timedelta, timezone
import re
from typing import Annotated, List, Literal
from uuid import NAMESPACE_URL, uuid5

from fastapi import APIRouter, Body, Request, Response
from fastapi.encoders import jsonable_encoder
from fastapi.exception_handlers import (
    http_exception_handler as fastapi_http_exception_handler,
)
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field
from starlette.exceptions import HTTPException as StarletteHTTPException

# 태그는 라우터가 아니라 엔드포인트마다 단다. 라우터에 달면 FastAPI가 엔드포인트
# 태그와 합쳐버려서, 영역별 API가 기준 API 태그에도 함께 나온다.
router = APIRouter(prefix="/api")

# R-PAY-07: 계약 생성 시 고정하는 기본 요율. 목은 계약 상태를 저장하지 않아 이 값을 사용한다.
MOCK_FEE_RATE_BPS = 100


class CommonRequestHeader(BaseModel):
    apiName: str = Field(
        description=(
            "호출 API 이름. URI의 API 이름과 같아야 하며 이 기준 API에서는 default를 "
            "사용한다. ERD에는 없으며 SSAFY 금융망 공통 요청에 대응하기 위한 필드다."
        ),
        examples=["default"],
        json_schema_extra={"maxLength": 30},
    )
    transmissionDate: str = Field(
        description=(
            "요청 전송일자(YYYYMMDD). ERD에는 없으며 SSAFY 금융망 공통 요청에 "
            "대응하기 위한 필드다."
        ),
        examples=["20260909"],
        json_schema_extra={"pattern": r"^\d{8}$"},
    )
    transmissionTime: str = Field(
        description=(
            "요청 전송시각(HHmmss). 실제 금융망에서는 요청시간 기준 ±5분 조건이 적용된다. "
            "ERD에는 없으며 SSAFY 금융망 공통 요청에 대응하기 위한 필드다."
        ),
        examples=["120000"],
        json_schema_extra={"pattern": r"^\d{6}$"},
    )
    institutionCode: str = Field(
        description=(
            "SSAFY 금융망 기관코드로 00100 고정. ERD에는 없으며 SSAFY 금융망 공통 "
            "요청에 대응하기 위한 필드다."
        ),
        examples=["00100"],
        json_schema_extra={"enum": ["00100"]},
    )
    fintechAppNo: str = Field(
        description=(
            "SSAFY 금융망 핀테크 앱 일련번호로 001 고정. ERD에는 없으며 SSAFY 금융망 "
            "공통 요청에 대응하기 위한 필드다."
        ),
        examples=["001"],
        json_schema_extra={"enum": ["001"]},
    )
    apiServiceCode: str = Field(
        description=(
            "API 서비스 코드로 apiName과 같아야 한다. ERD에는 없으며 SSAFY 금융망 "
            "공통 요청에 대응하기 위한 필드다."
        ),
        examples=["default"],
        json_schema_extra={"maxLength": 30},
    )
    institutionTransactionUniqueNo: str = Field(
        description=(
            "기관별 API 호출을 식별하는 숫자 20자리 고유번호. 실제 요청마다 새로운 값을 "
            "사용해야 한다. ERD에는 없으며 SSAFY 금융망 공통 요청에 대응하기 위한 필드다."
        ),
        examples=["20260909120000000001"],
        json_schema_extra={"pattern": r"^\d{20}$"},
    )
    apiKey: str = Field(
        description=(
            "앱 관리자가 발급받은 API Key. ERD에는 없으며 SSAFY 금융망 공통 요청에 "
            "대응하기 위한 필드다. invalid-api-key는 H1008 실패 재현용 예약값이다."
        ),
        examples=["mock-api-key", "invalid-api-key"],
        json_schema_extra={"minLength": 1, "maxLength": 40},
    )
    userKey: str = Field(
        description=(
            "앱 사용자가 발급받은 User Key. 사용자 대상 API에 필요한 값이다. ERD에는 "
            "없으며 SSAFY 금융망 공통 요청에 대응하기 위한 필드다. invalid-user-key는 "
            "H1009, server-error-user-key는 Q1000 실패 재현용 예약값이다."
        ),
        examples=["mock-user-key", "invalid-user-key", "server-error-user-key"],
        json_schema_extra={"minLength": 1, "maxLength": 40},
    )


class DefaultRequest(BaseModel):
    Header: CommonRequestHeader = Field(
        description=(
            "HTTP Header가 아니라 JSON Body 안에 포함되는 SSAFY 금융망 공통 요청 Header."
        )
    )


class CommonResponseHeader(BaseModel):
    responseCode: str = Field(
        description=(
            "SSAFY 금융망 처리 결과 코드. H0000은 성공이다. 공통 오류는 H1000(Header), "
            "H1001(apiName), H1002(transmissionDate), H1003(transmissionTime), "
            "H1004(institutionCode), H1005(fintechAppNo), H1006(apiServiceCode), "
            "H1007(기관거래고유번호 중복), H1008(apiKey), H1009(userKey), "
            "H1010(기관거래고유번호 형식), Q1000(기타 오류), Q1001(요청 본문 형식·타입)이다. "
            "ERD에는 없으며 SSAFY 금융망 공통 응답에 대응하기 위한 필드다."
        ),
        examples=["H0000"],
    )
    responseMessage: str = Field(
        description=(
            "responseCode에 대응하는 처리 결과 설명. ERD에는 없으며 SSAFY 금융망 "
            "공통 응답에 대응하기 위한 필드다."
        ),
        examples=["정상처리 되었습니다."],
    )
    apiName: str = Field(
        description=(
            "호출한 API 이름. 이 기준 API에서는 default로 고정한다. ERD에는 없으며 "
            "SSAFY 금융망 공통 응답에 대응하기 위한 필드다."
        ),
        max_length=30,
        examples=["default"],
    )
    transmissionDate: str = Field(
        description=(
            "응답 전송일자(YYYYMMDD). 이 목 API에서는 명세 예시값을 고정 반환한다. "
            "ERD에는 없으며 SSAFY 금융망 공통 응답에 대응하기 위한 필드다."
        ),
        pattern=r"^\d{8}$",
        examples=["20260909"],
    )
    transmissionTime: str = Field(
        description=(
            "응답 전송시각(HHmmss). 이 목 API에서는 명세 예시값을 고정 반환한다. "
            "ERD에는 없으며 SSAFY 금융망 공통 응답에 대응하기 위한 필드다."
        ),
        pattern=r"^\d{6}$",
        examples=["120000"],
    )
    institutionCode: Literal["00100"] = Field(
        description=(
            "SSAFY 금융망 기관코드로 00100 고정. ERD에는 없으며 SSAFY 금융망 공통 "
            "응답에 대응하기 위한 필드다."
        ),
        examples=["00100"],
    )
    fintechAppNo: Literal["001"] = Field(
        description=(
            "SSAFY 금융망 핀테크 앱 일련번호로 001 고정. ERD에는 없으며 SSAFY 금융망 "
            "공통 응답에 대응하기 위한 필드다."
        ),
        examples=["001"],
    )
    apiServiceCode: str = Field(
        description=(
            "API 서비스 코드로 apiName과 동일하다. ERD에는 없으며 SSAFY 금융망 공통 "
            "응답에 대응하기 위한 필드다."
        ),
        max_length=30,
        examples=["default"],
    )
    institutionTransactionUniqueNo: str = Field(
        description=(
            "기관별 API 호출을 식별하는 숫자 20자리 고유번호. 이 목 API에서는 명세 "
            "예시값을 고정 반환한다. ERD에는 없으며 SSAFY 금융망 공통 응답에 대응하기 "
            "위한 필드다."
        ),
        pattern=r"^\d{20}$",
        examples=["20260909120000000001"],
    )


class DefaultRecord(BaseModel):
    message: str = Field(
        description="에스크로 목 서버의 정상 응답을 확인하기 위한 고정 문구.",
        examples=["escrow"],
    )


class DefaultResponse(BaseModel):
    Header: CommonResponseHeader = Field(
        description="SSAFY 금융망 공통 성공 응답 Header."
    )
    REC: DefaultRecord = Field(
        description=(
            "API별 응답 데이터가 들어가는 영역. 실제 API에서는 기능에 따라 Object 또는 "
            "Array가 될 수 있으며, 이 기준 API에서는 Object 예시를 반환한다."
        )
    )


class ErrorResponse(BaseModel):
    Header: CommonResponseHeader = Field(
        description=(
            "SSAFY 금융망 공통 실패 응답 Header. 실패 응답에는 REC를 포함하지 않는다."
        )
    )


UNAVAILABLE_DESCRIPTION = (
    "일시적으로 요청을 처리할 수 없습니다. HTTP 503 / Q1000이며 REC는 없습니다. "
    "500·503·타임아웃·연결 종료만으로 업무의 성공 또는 실패를 확정하지 않습니다. "
    "멱등키가 필요한 쓰기 요청은 최초 idempotencyKey와 업무 본문을 유지해 재요청합니다. "
    "전송 시각과 institutionTransactionUniqueNo는 새로 생성합니다. "
    "조회는 재시도할 수 있습니다. 프록시 장애 응답에는 JSON Header가 없을 수 있습니다. "
    "Mock은 default의 userKey=unavailable-user-key, 업무 API의 "
    "clientApiKey=unavailable-client-api-key로 재현합니다."
)
UNAVAILABLE_RESPONSE = {
    "model": ErrorResponse,
    "description": UNAVAILABLE_DESCRIPTION,
}


REQUEST_EXAMPLE = {
    "Header": {
        "apiName": "default",
        "transmissionDate": "20260909",
        "transmissionTime": "120000",
        "institutionCode": "00100",
        "fintechAppNo": "001",
        "apiServiceCode": "default",
        "institutionTransactionUniqueNo": "20260909120000000001",
        "apiKey": "mock-api-key",
        "userKey": "mock-user-key",
    }
}

SUCCESS_EXAMPLE = {
    "Header": {
        "responseCode": "H0000",
        "responseMessage": "정상처리 되었습니다.",
        "apiName": "default",
        "transmissionDate": "20260909",
        "transmissionTime": "120000",
        "institutionCode": "00100",
        "fintechAppNo": "001",
        "apiServiceCode": "default",
        "institutionTransactionUniqueNo": "20260909120000000001",
    },
    "REC": {"message": "escrow"},
}

HEADER_ERROR_EXAMPLE = {
    "Header": {
        "responseCode": "H1009",
        "responseMessage": "USER KEY가 유효하지 않습니다.",
        "apiName": "default",
        "transmissionDate": "20260909",
        "transmissionTime": "120000",
        "institutionCode": "00100",
        "fintechAppNo": "001",
        "apiServiceCode": "default",
        "institutionTransactionUniqueNo": "20260909120000000001",
    }
}

SERVER_ERROR_EXAMPLE = {
    "Header": {
        "responseCode": "Q1000",
        "responseMessage": "그 이외에 에러 메시지",
        "apiName": "default",
        "transmissionDate": "20260909",
        "transmissionTime": "120000",
        "institutionCode": "00100",
        "fintechAppNo": "001",
        "apiServiceCode": "default",
        "institutionTransactionUniqueNo": "20260909120000000001",
    }
}


def _response_header(
    response_code: str,
    response_message: str,
    request_header: CommonRequestHeader | None = None,
) -> CommonResponseHeader:
    return CommonResponseHeader(
        responseCode=response_code,
        responseMessage=response_message,
        apiName=request_header.apiName if request_header else "default",
        transmissionDate=(
            request_header.transmissionDate if request_header else "20260909"
        ),
        transmissionTime=(
            request_header.transmissionTime if request_header else "120000"
        ),
        institutionCode=(
            request_header.institutionCode if request_header else "00100"
        ),
        fintechAppNo=request_header.fintechAppNo if request_header else "001",
        apiServiceCode=(
            request_header.apiServiceCode if request_header else "default"
        ),
        institutionTransactionUniqueNo=(
            request_header.institutionTransactionUniqueNo
            if request_header
            else "20260909120000000001"
        ),
    )


def _error_response(
    status_code: int,
    response_code: str,
    response_message: str,
    request_header: CommonRequestHeader | None = None,
) -> JSONResponse:
    error = ErrorResponse(
        Header=_response_header(response_code, response_message, request_header)
    )
    return JSONResponse(status_code=status_code, content=jsonable_encoder(error))


def _validate_request_header(
    header: CommonRequestHeader,
) -> tuple[str, str] | None:
    if header.apiName != "default" or len(header.apiName) > 30:
        return "H1001", "API 이름이 유효하지 않습니다."

    try:
        datetime.strptime(header.transmissionDate, "%Y%m%d")
        if not re.fullmatch(r"[0-9]{8}", header.transmissionDate):
            raise ValueError("date width")
    except ValueError:
        return "H1002", "전송일자 형식이 유효하지 않습니다."

    try:
        datetime.strptime(header.transmissionTime, "%H%M%S")
        if not re.fullmatch(r"[0-9]{6}", header.transmissionTime):
            raise ValueError("time width")
    except ValueError:
        return "H1003", "전송시각 형식이 유효하지 않습니다."

    if header.institutionCode != "00100":
        return "H1004", "기관코드가 유효하지 않습니다."
    if header.fintechAppNo != "001":
        return "H1005", "핀테크 앱 일련번호가 유효하지 않습니다."
    if header.apiServiceCode != header.apiName or len(header.apiServiceCode) > 30:
        return "H1006", "API 서비스코드가 유효하지 않습니다."
    if header.institutionTransactionUniqueNo == "00000000000000000000":
        return "H1007", "기관거래고유번호가 중복된 값입니다."
    if not re.fullmatch(r"\d{20}", header.institutionTransactionUniqueNo):
        return "H1010", "기관거래고유번호가 유효하지 않습니다."
    if (
        not header.apiKey.strip()
        or len(header.apiKey) > 40
        or header.apiKey == "invalid-api-key"
    ):
        return "H1008", "API KEY가 유효하지 않습니다."
    if (
        not header.userKey.strip()
        or len(header.userKey) > 40
        or header.userKey == "invalid-user-key"
    ):
        return "H1009", "USER KEY가 유효하지 않습니다."
    return None


async def validation_exception_handler(
    request: Request, exc: RequestValidationError
) -> JSONResponse:
    """요청 본문 검증 실패를 SSAFY 금융망 공통 실패 응답으로 바꾼다.

    FastAPI 기본 422 응답은 SSAFY 형식과 다르므로 앱에 이 핸들러를 등록한다
    (app/main.py). 값을 보고 갈리는 오류(H1001~H1010)는 각 엔드포인트가 판정하고,
    여기서는 구조 오류만 다룬다.

    - H1000: Body가 없거나 `Header`가 없거나 Object가 아닐 때
    - Q1001: 그 밖의 JSON 형식·필수 속성·타입 오류
    """
    for error in exc.errors():
        location = error.get("loc", ())
        if error.get("type") == "json_invalid":
            break
        if location in {("body",), ("body", "Header")}:
            return _error_response(400, "H1000", "HEADER 정보가 유효하지 않습니다.")

    return _error_response(
        400,
        "Q1001",
        "요청 본문의 형식이 잘못되었습니다. JSON 형식 또는 데이터 타입을 확인해 주세요.",
    )


async def http_exception_handler(
    request: Request, exc: StarletteHTTPException
) -> Response:
    """본문을 읽다 난 400을 SSAFY 금융망 공통 실패 응답으로 바꾼다.

    FastAPI는 본문이 UTF-8로 읽히지 않으면 `RequestValidationError`가 아니라
    `HTTPException(400)`을 던진다. 그래서 `validation_exception_handler`를 거치지
    않고 Starlette 기본 응답(`{"detail": ...}`)이 나간다. 같은 '본문을 못 읽겠다'인데
    바이트 인코딩 하나로 응답 형식이 갈리므로 여기서 `Q1001`로 맞춘다.

    404·405처럼 경로 단계에서 나는 것은 돌려줄 응답코드가 정해져 있지 않다. 임의로
    만들지 않고 기본 처리에 맡긴다.
    """
    if exc.status_code == 400:
        return _error_response(
            400,
            "Q1001",
            "요청 본문의 형식이 잘못되었습니다. JSON 형식 또는 데이터 타입을 확인해 주세요.",
        )
    return await fastapi_http_exception_handler(request, exc)


@router.post(
    "/default",
    response_model=DefaultResponse,
    tags=["default"],
    responses={
        200: {
            "description": "SSAFY 금융망 공통 Header와 REC를 포함한 정상 응답",
            "content": {"application/json": {"example": SUCCESS_EXAMPLE}},
        },
        400: {
            "model": ErrorResponse,
            "description": "공통 요청 Header 또는 요청 본문 검증 오류",
            "content": {"application/json": {"example": HEADER_ERROR_EXAMPLE}},
        },
        503: UNAVAILABLE_RESPONSE,
        500: {
            "model": ErrorResponse,
            "description": "Q1000 코드와 공통 Header를 포함한 서버 오류 응답",
            "content": {"application/json": {"example": SERVER_ERROR_EXAMPLE}},
        },
    },
    summary="에스크로 공통 응답 조회",
    description=(
        "에스크로 목 서버의 응답 형식·HTTP 상태코드·SSAFY 금융망 응답코드 작성 기준을 "
        "보여주는 API다. 요청의 `Header`는 HTTP Header가 아니라 JSON Body의 공통부다. "
        "도메인 데이터는 다루지 않으며, 실제 API에도 존재하는 요청 Header 속성값으로 "
        "응답 사례를 선택한다.\n\n"
        "- **200 / H0000**: 공통 Header가 유효하고 실패 재현용 예약값이 없을 때. 요청의 API 정보, "
        "전송일시, 기관코드와 기관거래고유번호를 응답 `Header`에 반영하고 `REC`에는 "
        "고정 메시지를 반환한다.\n"
        "- **400 / H1000**: `Header`가 없거나 Object 형식이 아닐 때.\n"
        "- **400 / H1001~H1006, H1008~H1010**: 각 공통 Header 속성이 규격에 맞지 않을 때.\n"
        "- **400 / H1007**: 기관거래고유번호가 `00000000000000000000`일 때. 상태를 "
        "저장하지 않는 목 서버에서 중복 번호 오류를 재현하기 위한 예약값이다.\n"
        "- **400 / H1008**: `apiKey`가 비어 있거나 40자를 초과하거나 `invalid-api-key`일 때.\n"
        "- **400 / H1009**: `userKey`가 비어 있거나 40자를 초과하거나 `invalid-user-key`일 때.\n"
        "- **400 / Q1001**: JSON 형식, 필수 속성 또는 속성 타입이 올바르지 않을 때.\n"
        "- **500 / Q1000**: 유효한 요청에서 `userKey`가 `server-error-user-key`일 때. "
        "분류되지 않은 서버 오류를 "
        "나타내며 `Header`만 반환하고 `REC`는 포함하지 않는다.\n\n"
        "기능별 도메인 예외코드와 REC 상세 구조는 각 API 명세에서 별도로 정의한다. "
        "Header의 각 필드는 에스크로 서버가 SSAFY 금융망 API 형식을 맞추기 위해 사용하는 "
        "ERD 외부 연동 필드다."
    ),
)
async def get_default(
    payload: Annotated[DefaultRequest, Body(examples=[REQUEST_EXAMPLE])],
) -> DefaultResponse | JSONResponse:
    header_error = _validate_request_header(payload.Header)
    if header_error:
        response_code = header_error[0]
        echoable_header = (
            payload.Header
            if response_code in {"H1007", "H1008", "H1009"}
            else None
        )
        return _error_response(400, *header_error, echoable_header)

    if payload.Header.userKey == "unavailable-user-key":
        return _error_response(503, "Q1000", "서비스를 일시적으로 사용할 수 없습니다.", payload.Header)

    if payload.Header.userKey == "server-error-user-key":
        return _error_response(
            500,
            "Q1000",
            "그 이외에 에러 메시지",
            payload.Header,
        )

    return DefaultResponse(
        Header=_response_header(
            "H0000", "정상처리 되었습니다.", payload.Header
        ),
        REC=DefaultRecord(message="escrow"),
    )


# --------------------------------------------------------------------------- #
# 에스크로 업무 API — 공통부
#
# komit이 에스크로를 부를 때 쓰는 Header다. SSAFY 금융망 Header와 모양은 같지만
# 인증 수단이 다르다. 에스크로가 SSAFY를 부를 때 쓰는 apiKey는 에스크로만 보관하고
# komit에게 노출하지 않으므로, 이 Header에는 clientApiKey가 대신 들어간다.
# --------------------------------------------------------------------------- #

INVALID_CLIENT_API_KEY = "invalid-client-api-key"
SERVER_ERROR_CLIENT_API_KEY = "server-error-client-api-key"
UNAVAILABLE_CLIENT_API_KEY = "unavailable-client-api-key"
MOCK_OPERATOR_CLIENT_API_KEY = "mock-operator-client-api-key"
CONFLICT_IDEMPOTENCY_KEY = "conflict-idempotency-key"


class EscrowRequestHeader(BaseModel):
    """에스크로 업무 API 공통 요청 Header."""

    apiName: str = Field(
        description=(
            "호출 API 이름. 요청 URL의 마지막 경로명과 같아야 한다. ERD에는 없으며 "
            "SSAFY 금융망 공통 요청 형식에 맞추기 위한 필드다."
        ),
        examples=["createEscrow"],
        json_schema_extra={"maxLength": 30},
    )
    transmissionDate: str = Field(
        description=(
            "요청 전송일자(YYYYMMDD). ERD에는 없으며 SSAFY 금융망 공통 요청 형식에 "
            "맞추기 위한 필드다."
        ),
        examples=["20260909"],
        json_schema_extra={"pattern": r"^\d{8}$"},
    )
    transmissionTime: str = Field(
        description=(
            "요청 전송시각(HHmmss). 실제 금융망에서는 서버 시각 기준 ±5분 조건이 "
            "적용된다. ERD에는 없으며 SSAFY 금융망 공통 요청 형식에 맞추기 위한 필드다."
        ),
        examples=["120000"],
        json_schema_extra={"pattern": r"^\d{6}$"},
    )
    institutionCode: str = Field(
        description=(
            "SSAFY 금융망 기관코드로 00100 고정. ERD에는 없으며 SSAFY 금융망 공통 "
            "요청 형식에 맞추기 위한 필드다."
        ),
        examples=["00100"],
        json_schema_extra={"enum": ["00100"]},
    )
    fintechAppNo: str = Field(
        description=(
            "SSAFY 금융망 핀테크 앱 일련번호로 001 고정. ERD에는 없으며 SSAFY 금융망 "
            "공통 요청 형식에 맞추기 위한 필드다."
        ),
        examples=["001"],
        json_schema_extra={"enum": ["001"]},
    )
    apiServiceCode: str = Field(
        description=(
            "API 서비스 코드로 apiName과 같아야 한다. ERD에는 없으며 SSAFY 금융망 "
            "공통 요청 형식에 맞추기 위한 필드다."
        ),
        examples=["createEscrow"],
        json_schema_extra={"maxLength": 30},
    )
    institutionTransactionUniqueNo: str = Field(
        description=(
            "호출마다 새로 만드는 숫자 20자리 전송 추적번호. 업무 멱등키와는 다른 "
            "값이며 같은 업무를 다시 보낼 때도 이 값은 새로 만든다. ERD에는 없으며 "
            "SSAFY 금융망 공통 요청 형식에 맞추기 위한 필드다."
        ),
        examples=["20260909120000000001"],
        json_schema_extra={"pattern": r"^\d{20}$"},
    )
    clientApiKey: str = Field(
        description=(
            "일반 API에서는 고객사 서비스 키, inquireReconciliation에서는 에스크로 "
            "운영 전용 키다. 일반 서비스 키의 해시는 ERD service_clients.api_key_hash에 "
            "대응한다. 운영 키는 배포 환경 Secret으로 관리한다. 에스크로가 SSAFY를 "
            "부를 때 쓰는 apiKey와는 다르다. invalid-client-api-key는 E5001, "
            "server-error-client-api-key는 Q1000 실패 재현용 예약값이다."
        ),
        examples=[
            "mock-client-api-key",
            MOCK_OPERATOR_CLIENT_API_KEY,
            INVALID_CLIENT_API_KEY,
            SERVER_ERROR_CLIENT_API_KEY,
            UNAVAILABLE_CLIENT_API_KEY,
        ],
        json_schema_extra={"minLength": 1, "maxLength": 255},
    )
    idempotencyKey: str | None = Field(
        default=None,
        description=(
            "업무 멱등키. 1원 인증 요청, 사용자·계좌 발급, 자금 이동, 취소·동결·해제 API에는 반드시 담고, "
            "같은 업무를 다시 보낼 때는 같은 값을 유지한다. 범위는 인증된 서비스와 API별이며 "
            "같은 범위의 키로 다른 본문을 보내면 E5003이다. 조회 API에서는 쓰지 않는다. "
            "ERD account_auth_requests, member_account_provisions, escrow_transactions, escrow_state_commands의 "
            "idempotency_key에 각각 대응한다. "
            "conflict-idempotency-key는 본문이 처음과 달라 거절되는 사례의 "
            "재현용 예약값이다."
        ),
        examples=["komit-createEscrow-CONTRACT-999", CONFLICT_IDEMPOTENCY_KEY],
        json_schema_extra={"minLength": 1, "maxLength": 255},
    )
    userKey: str | None = Field(
        default=None,
        description=(
            "계좌 소유자의 SSAFY 사용자 키. 계좌 등록과 출금이 있는 예치·추가예치에만 "
            "담는다. 에스크로는 이 값을 저장하지 않고 SSAFY 호출에만 쓴다. ERD에는 "
            "없으며 SSAFY 금융망 연동에 필요한 필드다. invalid-user-key는 H1009 실패 "
            "재현용 예약값이다."
        ),
        examples=["mock-user-key", "invalid-user-key"],
        json_schema_extra={"minLength": 1, "maxLength": 255},
    )


class EscrowErrorResponse(BaseModel):
    """에스크로 업무 API 실패 응답. 실패에는 REC를 포함하지 않는다."""

    Header: CommonResponseHeader = Field(
        description="SSAFY 금융망 형식을 따르는 공통 실패 응답 Header."
    )


def _echo(value: str, shape: str, fallback: str) -> str:
    """응답 Header에 되비출 값을 고른다.

    Header 형식 오류(H1001~H1010)를 돌려줄 때는 요청 값 자체가 응답 Header 규격에
    어긋나 있다. 그대로 되비추면 응답을 만들다가 터지므로, 규격에 맞을 때만
    되비추고 어긋나면 명세 예시값으로 대신한다.
    """
    return value if re.fullmatch(shape, value) else fallback


def _escrow_response_header(
    response_code: str,
    response_message: str,
    header: EscrowRequestHeader,
) -> CommonResponseHeader:
    """요청 Header를 되비추는 응답 Header를 만든다."""
    return CommonResponseHeader(
        responseCode=response_code,
        responseMessage=response_message,
        apiName=header.apiName[:30],
        transmissionDate=_echo(header.transmissionDate, r"\d{8}", "20260909"),
        transmissionTime=_echo(header.transmissionTime, r"\d{6}", "120000"),
        # 기관코드와 핀테크 앱 일련번호는 명세상 고정값이라 요청이 무엇이든 고정값을 낸다.
        institutionCode="00100",
        fintechAppNo="001",
        apiServiceCode=header.apiServiceCode[:30],
        institutionTransactionUniqueNo=_echo(
            header.institutionTransactionUniqueNo,
            r"\d{20}",
            "20260909120000000001",
        ),
    )


def _escrow_error(
    status_code: int,
    response_code: str,
    response_message: str,
    header: EscrowRequestHeader,
) -> JSONResponse:
    body = EscrowErrorResponse(
        Header=_escrow_response_header(response_code, response_message, header)
    )
    return JSONResponse(status_code=status_code, content=jsonable_encoder(body))


def _validate_escrow_header(
    header: EscrowRequestHeader,
    api_name: str,
    needs_idempotency_key: bool = False,
    needs_user_key: bool = False,
) -> tuple[int, str, str] | None:
    """공통 Header를 본다. 어긋나면 (HTTP 상태, 응답코드, 메시지)를 준다."""
    if header.apiName != api_name:
        return 400, "H1001", "API 이름이 유효하지 않습니다."

    try:
        datetime.strptime(header.transmissionDate, "%Y%m%d")
        if not re.fullmatch(r"[0-9]{8}", header.transmissionDate):
            raise ValueError("date width")
    except ValueError:
        return 400, "H1002", "전송일자 형식이 유효하지 않습니다."

    try:
        datetime.strptime(header.transmissionTime, "%H%M%S")
        if not re.fullmatch(r"[0-9]{6}", header.transmissionTime):
            raise ValueError("time width")
    except ValueError:
        return 400, "H1003", "전송시각 형식이 유효하지 않습니다."

    if header.institutionCode != "00100":
        return 400, "H1004", "기관코드가 유효하지 않습니다."
    if header.fintechAppNo != "001":
        return 400, "H1005", "핀테크 앱 일련번호가 유효하지 않습니다."
    if header.apiServiceCode != header.apiName:
        return 400, "H1006", "API 서비스코드가 유효하지 않습니다."
    if header.institutionTransactionUniqueNo == "00000000000000000000":
        return 400, "H1007", "기관거래고유번호가 중복된 값입니다."
    if not re.fullmatch(r"\d{20}", header.institutionTransactionUniqueNo):
        return 400, "H1010", "기관거래고유번호가 유효하지 않습니다."
    if (
        not header.clientApiKey.strip() or len(header.clientApiKey) > 255
        or header.clientApiKey == INVALID_CLIENT_API_KEY
    ):
        return 400, "E5001", "CLIENT API KEY가 유효하지 않습니다."
    if (
        header.clientApiKey == MOCK_OPERATOR_CLIENT_API_KEY
        and api_name != "inquireReconciliation"
    ):
        return 400, "E5001", "운영 키는 이 API에 사용할 수 없습니다."
    if needs_idempotency_key:
        if not (header.idempotencyKey or "").strip() or len(header.idempotencyKey) > 255:
            return 400, "E5002", "멱등키가 필요합니다."
        # 조회는 멱등 기록을 남기지 않으므로 이 오류가 날 수 없다. 선언한 오류는
        # 실제로 낼 수 있어야 하므로 쓰기 API에서만 본다.
        if header.idempotencyKey == CONFLICT_IDEMPOTENCY_KEY:
            return 400, "E5003", "같은 멱등키로 다른 요청이 접수되었습니다."
    if needs_user_key and (
        not (header.userKey or "").strip() or len(header.userKey) > 40 or header.userKey == "invalid-user-key"
    ):
        return 400, "H1009", "USER KEY가 유효하지 않습니다."
    # 분류되지 않은 서버 오류. 업무 API는 userKey를 늘 받지 않으므로 기준 API의
    # server-error-user-key 대신 모든 업무 API가 함께 받는 clientApiKey로 고른다.
    if header.clientApiKey == UNAVAILABLE_CLIENT_API_KEY:
        return 503, "Q1000", "서비스를 일시적으로 사용할 수 없습니다."
    if header.clientApiKey == SERVER_ERROR_CLIENT_API_KEY:
        return 500, "Q1000", "그 이외에 에러 메시지"
    return None


# --------------------------------------------------------------------------- #
# 계좌 등록 (1원 인증) — 패스스루
#
# 에스크로는 1원 송금의 멱등·복구 기록을 보관한다. 등록 계좌는 Komit이 관리하며
# 예치 출금은 R-PAY-09에 따라 회원가입 발급 계좌를 사용한다. 아래 요청·응답 필드는 에스크로
# ERD가 아니라 SSAFY 금융망 규격을 그대로 따른다.
# --------------------------------------------------------------------------- #

ACCOUNT_NO_PATTERN = r"^\d{16}$"

MOCK_ACCOUNT_NO = "0011234567890123"
INVALID_ACCOUNT_NO = "0000000000000000"

# SSAFY 금융망 형식은 오류를 본문의 responseCode로 전한다. HTTP 상태코드는 기준 API와
# 같이 400·500·503을 쓰고, 업무 오류는 responseCode로 가른다.
ACCOUNT_ERRORS: dict = {
    503: UNAVAILABLE_RESPONSE,
    400: {
        "model": EscrowErrorResponse,
        "description": "공통 Header 또는 업무 값 오류",
    },
    500: {
        "model": EscrowErrorResponse,
        "description": "분류되지 않은 서버 오류",
    },
}


class OpenAccountAuthRequest(BaseModel):
    """1원 인증 요청."""

    Header: EscrowRequestHeader = Field(
        description="JSON Body 안에 담는 공통 요청 Header."
    )
    accountNo: str = Field(
        description=(
            "인증할 원화 수시입출금 계좌번호 16자리. ERD account_auth_requests에 암호화해 보관한다. "
            "0000000000000000은 계좌번호 오류 재현용 예약값이다."
        ),
        examples=[MOCK_ACCOUNT_NO],
        pattern=ACCOUNT_NO_PATTERN,
    )


class OpenAccountAuthRecord(BaseModel):
    """1원 송금 결과."""

    transactionUniqueNo: str = Field(
        description=(
            "SSAFY가 돌려준 1원 송금 거래번호. 응답이 숫자여도 문자열로 정규화해 "
            "내려준다. ERD에는 없으며 SSAFY 금융망 응답에 대응하는 필드다."
        ),
        examples=["1000000001"],
    )
    accountNo: str = Field(
        description="인증 대상 계좌번호. 요청에서 받은 값을 그대로 돌려준다.",
        examples=[MOCK_ACCOUNT_NO],
    )
    expiresAt: str = Field(
        description=(
            "이 시각이 지나면 인증이 만료된다. 화면의 남은 시간이 이 값에서 나온다. "
            "유효 시간은 R-ACC-02다. 에스크로가 계산해 내려주고 Komit은 그대로 "
            "전달한다. ERD account_auth_requests.expires_at이며 같은 요청의 재시도로 연장되지 않는다."
        ),
        examples=["2026-09-09T12:10:00+09:00"],
    )


class OpenAccountAuthResponse(BaseModel):
    Header: CommonResponseHeader = Field(description="공통 성공 응답 Header.")
    REC: OpenAccountAuthRecord = Field(description="1원 송금 결과.")


class InquireTransactionHistoryListRequest(BaseModel):
    """거래내역 조회 요청."""

    Header: EscrowRequestHeader = Field(
        description="JSON Body 안에 담는 공통 요청 Header."
    )
    accountNo: str = Field(
        description=(
            "조회할 계좌번호 16자리. ERD에는 없으며 SSAFY 금융망 요청에 대응하는 "
            "필드다."
        ),
        examples=[MOCK_ACCOUNT_NO],
        pattern=ACCOUNT_NO_PATTERN,
    )
    startDate: str = Field(
        description="조회 시작일(YYYYMMDD).",
        examples=["20260909"],
        pattern=r"^\d{8}$",
    )
    endDate: str = Field(
        description="조회 종료일(YYYYMMDD).",
        examples=["20260909"],
        pattern=r"^\d{8}$",
    )
    transactionType: Literal["M", "D", "A"] = Field(
        description="거래 구분. M은 입금, D는 출금, A는 전체다.",
        examples=["M"],
    )
    orderByType: Literal["ASC", "DESC"] | None = Field(
        default=None,
        description="정렬 방향. 비우면 SSAFY 기본값을 따른다.",
        examples=["DESC"],
    )


class TransactionHistoryItem(BaseModel):
    """거래내역 한 건. SSAFY 응답 항목을 그대로 옮긴다."""

    transactionUniqueNo: str = Field(
        description="거래 고유번호.", examples=["1000000001"]
    )
    transactionDate: str = Field(description="거래일(YYYYMMDD).", examples=["20260909"])
    transactionTime: str = Field(description="거래시각(HHmmss).", examples=["120001"])
    transactionType: str = Field(description="입출금 구분.", examples=["M"])
    transactionTypeName: str = Field(description="입출금 구분명.", examples=["입금"])
    transactionBalance: str = Field(
        description="거래 금액. 원 단위 정수를 문자열로 내려준다.", examples=["1"]
    )
    transactionAfterBalance: str = Field(
        description="거래 후 잔액.", examples=["1000001"]
    )
    transactionSummary: str = Field(
        description=(
            "거래 표시 문구. 1원 인증 건에는 기업명과 네 자리 인증번호가 함께 찍힌다. "
            "화면은 기업명이 일치하는 가장 최근 입금 건을 찾는다."
        ),
        examples=["KOMIT 1234"],
    )


class InquireTransactionHistoryListRecord(BaseModel):
    totalCount: int = Field(description="조회된 거래 건수.", examples=[1])
    # SSAFY 응답 필드명이 list다. 내장 list를 가리므로 typing.List로 적는다.
    list: List[TransactionHistoryItem] = Field(
        description="거래내역. 없으면 빈 배열이다."
    )


class InquireTransactionHistoryListResponse(BaseModel):
    Header: CommonResponseHeader = Field(description="공통 성공 응답 Header.")
    REC: InquireTransactionHistoryListRecord = Field(description="거래내역 조회 결과.")


class CheckAuthCodeRequest(BaseModel):
    """인증번호 검증 요청."""

    transactionUniqueNo: str = Field(
        description="openAccountAuth가 반환한 인증 거래번호. 같은 서비스·사용자·계좌의 성공 요청만 확인한다. 1000000408은 만료, 1000000404는 없음 또는 소유자 불일치 사례다.",
        min_length=1, max_length=100, examples=["1000000001"],
    )

    Header: EscrowRequestHeader = Field(
        description="JSON Body 안에 담는 공통 요청 Header."
    )
    accountNo: str = Field(
        description="인증 대상 계좌번호 16자리.",
        examples=[MOCK_ACCOUNT_NO],
        pattern=ACCOUNT_NO_PATTERN,
    )
    authCode: str = Field(
        description=(
            "사용자가 입력한 네 자리 인증번호. 앞자리 0을 보존해야 하므로 숫자가 아니라 "
            "문자열로 다룬다. 은행 앱이 없는 데모에서는 어떤 네 자리 숫자도 성공한다."
        ),
        examples=["1234", "9999"],
        pattern=r"^\d{4}$",
    )


class CheckAuthCodeRecord(BaseModel):
    status: Literal["SUCCESS", "FAIL"] = Field(
        description="검증 결과.", examples=["SUCCESS"]
    )
    transactionUniqueNo: str = Field(
        description="인증 거래번호.", examples=["1000000002"]
    )
    accountNo: str = Field(description="인증된 계좌번호.", examples=[MOCK_ACCOUNT_NO])


class CheckAuthCodeResponse(BaseModel):
    Header: CommonResponseHeader = Field(description="공통 성공 응답 Header.")
    REC: CheckAuthCodeRecord = Field(description="인증번호 검증 결과.")


def _account_header_example(api_name: str) -> dict:
    return {
        **({"idempotencyKey": "auth-request-1"} if api_name == "openAccountAuth" else {}),
        "apiName": api_name,
        "transmissionDate": "20260909",
        "transmissionTime": "120000",
        "institutionCode": "00100",
        "fintechAppNo": "001",
        "apiServiceCode": api_name,
        "institutionTransactionUniqueNo": "20260909120000000001",
        "clientApiKey": "mock-client-api-key",
        "userKey": "mock-user-key",
    }


def _ok(description: str, api_name: str, rec: dict) -> dict:
    """성공 응답 한 벌. Header는 요청을 되비추는 예시로 채운다."""
    return {
        "description": description,
        "content": {
            "application/json": {
                "example": {"Header": _success_header_example(api_name), "REC": rec}
            }
        },
    }


def _success_header_example(api_name: str) -> dict:
    return {
        "responseCode": "H0000",
        "responseMessage": "정상처리 되었습니다.",
        "apiName": api_name,
        "transmissionDate": "20260909",
        "transmissionTime": "120000",
        "institutionCode": "00100",
        "fintechAppNo": "001",
        "apiServiceCode": api_name,
        "institutionTransactionUniqueNo": "20260909120000000001",
    }


@router.post(
    "/v1/edu/accountAuth/openAccountAuth",
    response_model=OpenAccountAuthResponse,
    tags=["에스크로 계좌 등록"],
    responses={
        200: _ok(
            "1원을 보내고 거래내역에 기업명과 인증번호를 남겼다",
            "openAccountAuth",
            {
                "transactionUniqueNo": "1000000001",
                "accountNo": MOCK_ACCOUNT_NO,
                "expiresAt": "2026-09-09T12:10:00+09:00",
            },
        ),
        **ACCOUNT_ERRORS,
    },
    summary="계좌 1원 인증 요청",
    description=(
        "인증할 계좌에 1원을 입금하고 거래내역에 기업명과 네 자리 인증번호를 남긴다. "
        "원화 수시입출금 계좌만 쓸 수 있다.\n\n"
        "기업명은 에스크로가 고객사 설정에서 꺼내 채우므로 요청 본문에 담지 않는다. "
        "같은 멱등키와 같은 사용자·계좌 요청은 최초 결과와 만료 시각을 반환하며 재송금하지 않는다. "
        "만료 후 새 인증은 새 멱등키로 요청한다. 결과 확인 중에는 503을 반환하며 "
        "pending-auth-key로 재현한다. 성공 예시는 고정 인증 시각을 사용한다.\n\n"
        "- **400 / E5002·E5003**: 멱등키 누락 또는 같은 키의 요청 내용 충돌\n"
        "- **200 / H0000**: 1원을 보냈을 때\n"
        "- **400 / A1003**: `accountNo`가 `0000000000000000`일 때. 계좌번호가 "
        "유효하지 않다\n"
        "- **400 / H1009**: `userKey`가 없거나 `invalid-user-key`일 때\n"
        "- **400 / E5001**: `clientApiKey`가 없거나 `invalid-client-api-key`일 때\n"
        "- **400 / Q1001**: 필수 속성이 없거나 형식이 맞지 않을 때\n"
        "- **500 / Q1000**: `clientApiKey`가 `server-error-client-api-key`일 때. "
        "분류되지 않은 서버 오류다"
    ),
)
async def open_account_auth(
    payload: Annotated[
        OpenAccountAuthRequest,
        Body(
            examples=[
                {
                    "Header": _account_header_example("openAccountAuth"),
                    "accountNo": MOCK_ACCOUNT_NO,
                }
            ]
        ),
    ],
) -> OpenAccountAuthResponse | JSONResponse:
    error = _validate_escrow_header(
        payload.Header, "openAccountAuth", needs_user_key=True, needs_idempotency_key=True
    )
    if error:
        return _escrow_error(*error, payload.Header)
    if payload.accountNo == INVALID_ACCOUNT_NO:
        return _escrow_error(
            400, "A1003", "계좌번호가 유효하지 않습니다.", payload.Header
        )

    if payload.Header.idempotencyKey == "pending-auth-key":
        return _escrow_error(503, "Q1000", "인증 요청 결과를 확인 중입니다. 같은 멱등키로 다시 확인해 주세요.", payload.Header)

    return OpenAccountAuthResponse(
        Header=_escrow_response_header(
            "H0000", "정상처리 되었습니다.", payload.Header
        ),
        REC=OpenAccountAuthRecord(
            transactionUniqueNo="1000000001",
            accountNo=payload.accountNo,
            # R-ACC-02: 고정 사례의 최초 성공 시각 기준. 재전송에도 만료 시각 유지.
            expiresAt="2026-09-09T12:10:00+09:00",
        ),
    )


@router.post(
    "/v1/edu/demandDeposit/inquireTransactionHistoryList",
    response_model=InquireTransactionHistoryListResponse,
    tags=["에스크로 계좌 등록"],
    responses={
        200: {
            "description": "거래내역을 돌려준다",
            "content": {
                "application/json": {
                    "example": {
                        "Header": _success_header_example(
                            "inquireTransactionHistoryList"
                        ),
                        "REC": {
                            "totalCount": 1,
                            "list": [
                                {
                                    "transactionUniqueNo": "1000000001",
                                    "transactionDate": "20260909",
                                    "transactionTime": "120001",
                                    "transactionType": "M",
                                    "transactionTypeName": "입금",
                                    "transactionBalance": "1",
                                    "transactionAfterBalance": "1000001",
                                    "transactionSummary": "KOMIT 1234",
                                }
                            ],
                        },
                    }
                }
            },
        },
        **ACCOUNT_ERRORS,
    },
    summary="계좌 거래내역 조회",
    description=(
        "사용자가 1원 입금 문구와 인증번호를 눈으로 확인할 수 있도록 거래내역을 "
        "조회한다. 은행 앱이 따로 없어 고객사 화면이 통장 역할을 하며, 이 조회도 키가 "
        "필요해 에스크로를 거친다.\n\n"
        "화면은 `transactionSummary`가 기업명으로 시작하는 가장 최근 입금 건을 찾아 "
        "네 자리 인증번호를 읽는다.\n\n"
        "- **200 / H0000**: 조회했을 때. 거래가 없으면 `totalCount`가 0이고 `list`가 "
        "빈 배열이다\n"
        "- **400 / A1003**: `accountNo`가 `0000000000000000`일 때\n"
        "- **400 / H1009**: `userKey`가 없거나 `invalid-user-key`일 때\n"
        "- **400 / E5001**: `clientApiKey`가 유효하지 않을 때\n"
        "- **400 / Q1001**: 필수 속성이 없거나 형식이 맞지 않을 때\n"
        "- **500 / Q1000**: `clientApiKey`가 `server-error-client-api-key`일 때. "
        "분류되지 않은 서버 오류다"
    ),
)
async def inquire_transaction_history_list(
    payload: Annotated[
        InquireTransactionHistoryListRequest,
        Body(
            examples=[
                {
                    "Header": _account_header_example(
                        "inquireTransactionHistoryList"
                    ),
                    "accountNo": MOCK_ACCOUNT_NO,
                    "startDate": "20260909",
                    "endDate": "20260909",
                    "transactionType": "M",
                    "orderByType": "DESC",
                }
            ]
        ),
    ],
) -> InquireTransactionHistoryListResponse | JSONResponse:
    error = _validate_escrow_header(
        payload.Header, "inquireTransactionHistoryList", needs_user_key=True
    )
    if error:
        return _escrow_error(*error, payload.Header)
    if payload.accountNo == INVALID_ACCOUNT_NO:
        return _escrow_error(
            400, "A1003", "계좌번호가 유효하지 않습니다.", payload.Header
        )

    return InquireTransactionHistoryListResponse(
        Header=_escrow_response_header(
            "H0000", "정상처리 되었습니다.", payload.Header
        ),
        REC=InquireTransactionHistoryListRecord(
            totalCount=1,
            list=[
                TransactionHistoryItem(
                    transactionUniqueNo="1000000001",
                    transactionDate=payload.startDate,
                    transactionTime="120001",
                    transactionType="M",
                    transactionTypeName="입금",
                    transactionBalance="1",
                    transactionAfterBalance="1000001",
                    transactionSummary="KOMIT 1234",
                )
            ],
        ),
    )


@router.post(
    "/v1/edu/accountAuth/checkAuthCode",
    response_model=CheckAuthCodeResponse,
    tags=["에스크로 계좌 등록"],
    responses={
        200: _ok(
            "네 자리 숫자 인증번호가 입력되어 인증을 완료했다",
            "checkAuthCode",
            {
                "status": "SUCCESS",
                "transactionUniqueNo": "1000000001",
                "accountNo": MOCK_ACCOUNT_NO,
            },
        ),
        **ACCOUNT_ERRORS,
    },
    summary="계좌 인증번호 검증",
    description=(
        "은행 앱이 없는 데모이므로 사용자가 네 자리 숫자를 입력하면 실제 인증번호와 "
        "대조하지 않고 성공으로 처리한다. `9999`도 성공한다. (`R-ACC-02`)\n\n"
        "성공하면 고객사가 그 계좌를 인증됨으로 표시하고 보관한다. 에스크로는 "
        "복구 기록으로 인증 거래와 사용자·계좌를 대조한다. 인증 확인은 읽기 전용이며 반복해도 송금하지 않는다.\n\n"
        "- **200 / H0000**: 해당 서비스·사용자·계좌의 만료 전 성공 요청이며 authCode가 네 자리 숫자일 때\n"
        "- **400 / E5011**: 인증 요청이 만료된 경우\n"
        "- **400 / E5012**: 인증 거래가 없거나 요청 소유자·계좌가 불일치한 경우\n"
        "- **400 / A1003**: `accountNo`가 `0000000000000000`일 때\n"
        "- **400 / H1009**: `userKey`가 없거나 `invalid-user-key`일 때\n"
        "- **400 / E5001**: `clientApiKey`가 유효하지 않을 때\n"
        "- **400 / Q1001**: `authCode`가 네 자리 숫자가 아닐 때\n"
        "- **500 / Q1000**: `clientApiKey`가 `server-error-client-api-key`일 때. "
        "분류되지 않은 서버 오류다"
    ),
)
async def check_auth_code(
    payload: Annotated[
        CheckAuthCodeRequest,
        Body(
            examples=[
                {
                    "Header": _account_header_example("checkAuthCode"),
                    "accountNo": MOCK_ACCOUNT_NO,
                    "authCode": "1234",
                    "transactionUniqueNo": "1000000001",
                }
            ]
        ),
    ],
) -> CheckAuthCodeResponse | JSONResponse:
    error = _validate_escrow_header(
        payload.Header, "checkAuthCode", needs_user_key=True
    )
    if error:
        return _escrow_error(*error, payload.Header)
    if payload.accountNo == INVALID_ACCOUNT_NO:
        return _escrow_error(
            400, "A1003", "계좌번호가 유효하지 않습니다.", payload.Header
        )
    if payload.transactionUniqueNo == "1000000408":
        return _escrow_error(400, "E5011", "인증 요청이 만료되었습니다.", payload.Header)
    if (payload.transactionUniqueNo != "1000000001" or payload.accountNo != MOCK_ACCOUNT_NO
            or payload.Header.userKey != "mock-user-key" or payload.Header.clientApiKey != "mock-client-api-key"):
        return _escrow_error(400, "E5012", "인증 요청을 찾을 수 없습니다.", payload.Header)
    return CheckAuthCodeResponse(
        Header=_escrow_response_header(
            "H0000", "정상처리 되었습니다.", payload.Header
        ),
        REC=CheckAuthCodeRecord(
            status="SUCCESS",
            transactionUniqueNo=payload.transactionUniqueNo,
            accountNo=payload.accountNo,
        ),
    )


# --------------------------------------------------------------------------- #
# 에스크로 자금 — 예치 · 추가예치 · 지급 · 환불 · 취소
#
# 여기서부터는 패스스루가 아니라 에스크로 고유 기능이다. 결과가 내부 원장에 남고
# 응답 필드는 escrows / escrow_transactions 컬럼에 근거한다.
# 계좌번호와 예금주명은 ERD에 원문으로 저장되지 않으며 요청 경계에서만 오간다.
# --------------------------------------------------------------------------- #

ESCROW_ID = 42
SETTLEMENT_PROCESSING_ESCROW_ID = 43
SETTLEMENT_UNKNOWN_ESCROW_ID = 44
SETTLEMENT_FAILED_ESCROW_ID = 45
REFUND_FIXTURE_STATUSES = {46: 'COMPLETED', 47: 'PROCESSING', 48: 'UNKNOWN', 49: 'FAILED'}
REFUND_FIXTURE_CONTRACT_IDS = {key: f'CONTRACT-REFUND-{value}' for key, value in REFUND_FIXTURE_STATUSES.items()}
SETTLEMENT_FIXTURE_CONTRACT_IDS = {
    SETTLEMENT_PROCESSING_ESCROW_ID: "CONTRACT-SETTLEMENT-PROCESSING",
    SETTLEMENT_UNKNOWN_ESCROW_ID: "CONTRACT-SETTLEMENT-UNKNOWN",
    SETTLEMENT_FAILED_ESCROW_ID: "CONTRACT-SETTLEMENT-FAILED",
    **REFUND_FIXTURE_CONTRACT_IDS,
}
SETTLEMENT_FIXTURE_ESCROW_IDS = {
    contract_id: escrow_id
    for escrow_id, contract_id in SETTLEMENT_FIXTURE_CONTRACT_IDS.items()
}
SETTLEMENT_FIXTURE_STATUSES = {
    SETTLEMENT_PROCESSING_ESCROW_ID: "PROCESSING",
    SETTLEMENT_UNKNOWN_ESCROW_ID: "UNKNOWN",
    SETTLEMENT_FAILED_ESCROW_ID: "FAILED",
}
SETTLEMENT_FIXTURE_REFERENCE_ID = "SETTLEMENT:9001"
SETTLEMENT_FIXTURE_GROSS_AMOUNT = 400000
SETTLEMENT_FIXTURE_FEE_AMOUNT = (
    SETTLEMENT_FIXTURE_GROSS_AMOUNT * MOCK_FEE_RATE_BPS // 10000
)
SETTLEMENT_FIXTURE_NET_AMOUNT = (
    SETTLEMENT_FIXTURE_GROSS_AMOUNT - SETTLEMENT_FIXTURE_FEE_AMOUNT
)
NOT_FOUND_ESCROW_ID = 404
HELD_ESCROW_ID = 409
DEPOSIT_PENDING_ESCROW_ID = 202
DEPOSIT_FIXTURE_STATUSES = {202: 'PENDING', 203: 'PROCESSING', 204: 'UNKNOWN', 205: 'FAILED'}
DEPOSIT_FIXTURE_CONTRACT_IDS = {203: 'CONTRACT-DEPOSIT-PROCESSING', 204: 'CONTRACT-DEPOSIT-UNKNOWN', 205: 'CONTRACT-DEPOSIT-FAILED'}
PENDING_DEPOSIT_TRANSACTION_ID = 1004
# createEscrow에서 이 contractId로 부르면 입금이 아직 확인되지 않은 DEPOSIT_PENDING을
# 돌려준다. 별도 속성을 늘리지 않고 실제 필드(contractId) 값으로 분기한다.
PENDING_CONTRACT_ID = "CONTRACT-PENDING"
NUMERIC_CONTRACT_ESCROW_OFFSET = 1_000_000
HASHED_CONTRACT_ESCROW_START = 1_000_000_000_000_000
MAX_ESCROW_ID = 2**63 - 1


def _mock_escrow_id(contract_id: str) -> int:
    """무상태 목에서 같은 계약에 같은 BIGINT 범위의 식별자를 준다."""
    if contract_id == PENDING_CONTRACT_ID:
        return DEPOSIT_PENDING_ESCROW_ID
    for key, value in DEPOSIT_FIXTURE_CONTRACT_IDS.items():
        if value == contract_id:
            return key
    if contract_id == "CONTRACT-HELD":
        return HELD_ESCROW_ID
    if contract_id == "CONTRACT-999":
        return ESCROW_ID
    if contract_id in SETTLEMENT_FIXTURE_ESCROW_IDS:
        return SETTLEMENT_FIXTURE_ESCROW_IDS[contract_id]
    if contract_id.isascii() and contract_id.isdecimal():
        number = int(contract_id)
        if number < HASHED_CONTRACT_ESCROW_START - NUMERIC_CONTRACT_ESCROW_OFFSET:
            return NUMERIC_CONTRACT_ESCROW_OFFSET + number
    return HASHED_CONTRACT_ESCROW_START + (
        uuid5(NAMESPACE_URL, f"komit-escrow:{contract_id}").int
        % (MAX_ESCROW_ID - HASHED_CONTRACT_ESCROW_START + 1)
    )


def _mock_contract_id(escrow_id: int) -> str | None:
    if escrow_id == ESCROW_ID:
        return "CONTRACT-999"
    if escrow_id in DEPOSIT_FIXTURE_CONTRACT_IDS:
        return DEPOSIT_FIXTURE_CONTRACT_IDS[escrow_id]
    if escrow_id == HELD_ESCROW_ID:
        return "CONTRACT-HELD"
    if escrow_id in SETTLEMENT_FIXTURE_CONTRACT_IDS:
        return SETTLEMENT_FIXTURE_CONTRACT_IDS[escrow_id]
    if escrow_id in DEPOSIT_FIXTURE_STATUSES:
        return PENDING_CONTRACT_ID
    if NUMERIC_CONTRACT_ESCROW_OFFSET <= escrow_id < HASHED_CONTRACT_ESCROW_START:
        return str(escrow_id - NUMERIC_CONTRACT_ESCROW_OFFSET)
    return None


def _mock_escrow_balance(escrow_id: int) -> int:
    if escrow_id in REFUND_FIXTURE_STATUSES:
        return 0 if REFUND_FIXTURE_STATUSES[escrow_id] == 'COMPLETED' else 250000
    if escrow_id in DEPOSIT_FIXTURE_STATUSES:
        return 0
    if escrow_id in (
        SETTLEMENT_PROCESSING_ESCROW_ID,
        SETTLEMENT_UNKNOWN_ESCROW_ID,
    ):
        return 650000 - SETTLEMENT_FIXTURE_NET_AMOUNT
    return 650000

BANK_CODE_PATTERN = r"^\d{3}$"

ESCROW_ERRORS: dict = {
    503: UNAVAILABLE_RESPONSE,
    400: {
        "model": EscrowErrorResponse,
        "description": (
            "공통 Header 오류, 에스크로를 찾을 수 없음, 상태·멱등키 불일치. "
            "무엇인지는 responseCode로 가른다."
        ),
    },
    500: {
        "model": EscrowErrorResponse,
        "description": "분류되지 않은 서버 오류",
    },
}


class WithdrawalAccount(BaseModel):
    """출금 계좌. 최초 계약금 예치에 사용한다."""

    withdrawalBankCode: str = Field(
        description=(
            "출금 은행코드 세 자리. 예치 거래의 source_bank_code에 보관한다."
        ),
        examples=["001"],
        pattern=BANK_CODE_PATTERN,
    )
    withdrawalAccountNo: str = Field(
        description=(
            "의뢰인이 회원가입 때 발급받은 SSAFY 출금 계좌번호 16자리. "
            "원문으로 저장하지 않는다. (R-PAY-09)"
        ),
        examples=[MOCK_ACCOUNT_NO],
        pattern=ACCOUNT_NO_PATTERN,
    )


class CreateEscrowRequest(WithdrawalAccount):
    """에스크로 생성과 계약금 전액 예치 요청."""

    Header: EscrowRequestHeader = Field(
        description="JSON Body 안에 담는 공통 요청 Header."
    )
    contractId: str = Field(
        description=(
            "고객사의 계약 식별자. 에스크로는 뜻을 해석하지 않고 그대로 보관한다. "
            "서비스와 이 값의 짝이 유일해 한 계약에 에스크로가 하나만 생긴다. ERD "
            "escrows.external_contract_id에 대응한다."
        ),
        examples=["CONTRACT-999"],
        min_length=1,
        max_length=100,
    )
    contractType: str = Field(
        description=(
            "고객사가 쓰는 계약 종류. 에스크로는 뜻을 해석하지 않고 보관만 한다. ERD "
            "escrows.contract_type에 대응한다."
        ),
        examples=["DEVELOPMENT"],
        min_length=1,
        max_length=30,
    )
    depositorName: str = Field(
        description=(
            "가상계좌 입금자명 일치 여부를 확인할 기준값. 실제 출금 계좌 명의의 "
            "인증 결과를 뜻하지 않으며 원문으로 저장하지 않는다."
        ),
        examples=["김미미"],
        min_length=1,
        max_length=20,
    )
    amount: int = Field(
        description=(
            "예치할 총액. 원 단위 양수다. ERD escrows.expected_amount로 고정된다."
        ),
        examples=[650000],
        gt=0, le=9999999999999999999, strict=True,
    )


class EscrowAmountRecord(BaseModel):
    """자금 API가 공통으로 돌려주는 에스크로 금액 상태."""

    escrowId: int = Field(
        description="에스크로 식별자. ERD escrows.id에 대응한다.", examples=[ESCROW_ID]
    )
    status: str = Field(
        description=(
            "에스크로 상태. CREATED / DEPOSIT_PENDING / ACTIVE / COMPLETED / "
            "REFUNDED / CANCELLED 중 하나다. ACTIVE는 입금 검증과 보관계좌 "
            "정산이 모두 확정된 상태다. ERD escrows.status에 대응한다."
        ),
        examples=["ACTIVE"],
    )
    depositedAmount: int = Field(
        description=(
            "예치가 끝난 총액. 저장 컬럼이 아니라 ERD escrow_transactions의 완료된 "
            "DEPOSIT 금액 합계로 계산한다."
        ),
        examples=[650000],
    )
    balance: int = Field(
        description=(
            "가용 잔액. ERD escrow_transactions에서 완료된 DEPOSIT 합계에서 RELEASE, "
            "FEE, REFUND 합계를 뺀 값이며, 저장 컬럼이 아니라 계산값이다."
        ),
        examples=[650000],
    )
    transactionId: int = Field(
        description="이번 거래의 원장 식별자. ERD escrow_transactions.id에 대응한다.",
        examples=[1001],
    )


class CreateEscrowResponse(BaseModel):
    Header: CommonResponseHeader = Field(description="공통 성공 응답 Header.")
    REC: EscrowAmountRecord = Field(description="생성과 예치 결과.")


class ReleaseEscrowRequest(BaseModel):
    """승인된 작업 대금 지급 요청."""

    Header: EscrowRequestHeader = Field(
        description="JSON Body 안에 담는 공통 요청 Header."
    )
    escrowId: int = Field(
        description="대상 에스크로. 404는 없음, 409는 동결 중 재현용 예약값이다.",
        examples=[ESCROW_ID], gt=0,
    )
    referenceId: str = Field(
        description=(
            "정산 식별자. 계약 최종 정산의 지급과 환불이 같은 `SETTLEMENT:정산ID`를 "
            "쓰고 거래 유형으로 구분한다. 마일스톤 ID를 담지 않는다. ERD "
            "escrow_transactions.external_reference_id에 대응한다."
        ),
        examples=["SETTLEMENT:9001"],
        min_length=12, pattern=r"^SETTLEMENT:.+$",
        max_length=100,
    )
    amount: int = Field(
        description=(
            "수수료 공제 전 승인 총액. 정상 완료는 전액, 중도 종료는 통과한 작업분 "
            "합계다. 마일스톤 단위로 나눠 지급하지 않는다. (R-PAY-07)"
        ),
        examples=[400000],
        gt=0, le=9999999999999999999, strict=True,
    )
    recipientBankCode: str = Field(
        description="수취 은행코드 세 자리. 거래의 recipient_bank_code에 보관한다.",
        examples=["001"],
        pattern=BANK_CODE_PATTERN,
    )
    recipientAccountNo: str = Field(
        description="작업자의 수취 계좌번호 16자리. 원문으로 저장하지 않는다.",
        examples=["0019876543210987"],
        pattern=ACCOUNT_NO_PATTERN,
    )
    recipientName: str = Field(
        description=(
            "작업자 수취 계좌의 금융망 예금주명. 송금 전에 조회한 이름과 대조하며 "
            "다르면 보내지 않는다. Komit 프로필명과 다를 수 있다."
        ),
        examples=["이준"],
        min_length=1,
        max_length=20,
    )


class ReleaseEscrowRecord(BaseModel):
    transactionId: int = Field(
        description="지급 원장 거래 식별자. ERD escrow_transactions.id에 대응한다.",
        examples=[1002],
    )
    releasedAmount: int = Field(
        description=(
            "이번 정산에서 완료가 확인된 작업자 순지급(RELEASE)과 수수료(FEE)의 "
            "합계. 정산 완료 전에는 요청한 승인 총액보다 작을 수 있다. (R-PAY-12)"
        ),
        examples=[400000],
    )
    feeAmount: int = Field(
        description=(
            "이번 정산에서 이체 완료가 확인된 플랫폼 수수료. 미확정 FEE는 0으로 "
            "집계한다. ERD escrow_transactions의 완료된 FEE 금액이다. (R-PAY-12)"
        ),
        examples=[4000],
    )
    netAmount: int = Field(
        description=(
            "이번 정산에서 지급 완료가 확인된 작업자 금액. ERD escrow_transactions의 "
            "완료된 RELEASE 금액이다. (R-PAY-12)"
        ),
        examples=[396000],
    )
    balance: int = Field(
        description=(
            "완료가 확인된 거래만 반영한 보관 잔액. 정산이 PROCESSING·UNKNOWN이면 "
            "새 지급·환불에 사용할 수 없다. (R-PAY-12)"
        ),
        examples=[250000],
    )
    status: str = Field(
        description=(
            "정산 상태. PROCESSING / UNKNOWN / COMPLETED / FAILED 중 "
            "하나다. RELEASE와 수수료가 발생한 FEE가 모두 완료돼야 COMPLETED다. "
            "FAILED는 출금이 전혀 없고 실패가 확정된 경우에만 쓴다. "
            "각 거래 상태는 ERD escrow_transactions.status에 대응한다. (R-PAY-12)"
        ),
        examples=["COMPLETED"],
    )


class ReleaseEscrowResponse(BaseModel):
    Header: CommonResponseHeader = Field(description="공통 성공 응답 Header.")
    REC: ReleaseEscrowRecord = Field(description="지급 결과.")


class RefundEscrowRequest(BaseModel):
    """남은 잔액 환불 요청."""

    Header: EscrowRequestHeader = Field(
        description="JSON Body 안에 담는 공통 요청 Header."
    )
    escrowId: int = Field(
        description="대상 에스크로. 404는 없음, 409는 동결 중 재현용 예약값이다.",
        examples=[ESCROW_ID], gt=0,
    )
    amount: int = Field(
        description="최종 정산에서 남은 금액 전액. 부분 환불은 허용하지 않는다.",
        examples=[250000],
        gt=0, le=9999999999999999999, strict=True,
    )
    refundBankCode: str = Field(
        description=(
            "해당 계약의 예치에 사용한 회원가입 발급 계좌의 은행코드 세 자리. "
            "예치 거래의 출금 은행코드와 같아야 한다. (R-PAY-04)"
        ),
        examples=["001"],
        pattern=BANK_CODE_PATTERN,
    )
    refundAccountNo: str = Field(
        description=(
            "해당 계약의 예치에 사용한 회원가입 발급 계좌번호 16자리. "
            "예치 거래의 출금 계좌와 같아야 한다. 거래에는 암호화해 기록한다. "
            "(R-PAY-04)"
        ),
        examples=[MOCK_ACCOUNT_NO],
        pattern=ACCOUNT_NO_PATTERN,
    )
    refundHolderName: str = Field(
        description=(
            "환불 받을 계좌의 금융망 예금주명. 자유 입력만으로 소유권을 인정하지 "
            "않고 조회한 이름과 대조한다. Komit 프로필명과 다를 수 있다."
        ),
        examples=["김미미"],
        min_length=1,
        max_length=20,
    )
    referenceId: str = Field(
        min_length=12, pattern=r"^SETTLEMENT:.+$",
        description=(
            "정산 식별자. 같은 정산의 지급과 같은 `SETTLEMENT:정산ID`를 쓰고 거래 "
            "유형으로 구분한다. ERD escrow_transactions.external_reference_id에 "
            "대응한다."
        ),
        examples=["SETTLEMENT:9001"],
        max_length=100,
    )


class RefundEscrowRecord(BaseModel):
    transactionId: int = Field(
        description="환불 원장 거래 식별자.", examples=[1003]
    )
    refundedAmount: int = Field(description="이번에 환불한 금액.", examples=[250000])
    balance: int = Field(description="환불 뒤 남은 가용 잔액.", examples=[0])
    status: str = Field(
        description=(
            "환불 거래 상태. PROCESSING / UNKNOWN / COMPLETED / FAILED. "
            "ERD escrow_transactions.status에 대응한다. 계약 상태는 inquireEscrow로 확인한다."
        ),
        examples=["COMPLETED"],
    )


class RefundEscrowResponse(BaseModel):
    Header: CommonResponseHeader = Field(description="공통 성공 응답 Header.")
    REC: RefundEscrowRecord = Field(description="환불 결과.")


class CancelEscrowRequest(BaseModel):
    """예치 전 에스크로 취소 요청."""

    Header: EscrowRequestHeader = Field(
        description="JSON Body 안에 담는 공통 요청 Header."
    )
    escrowId: int = Field(
        description=(
            "취소할 에스크로. CREATED 또는 DEPOSIT_PENDING이며 자금 이동이 없다고 확정된 경우만 가능하다. 203·204는 송금 진행·결과 불명, 205는 무출금 확정 실패다. 404는 "
            "없음, 42와 409는 이미 ACTIVE 재현용 예약값이다."
        ),
        examples=[DEPOSIT_PENDING_ESCROW_ID], gt=0,
    )


class CancelEscrowRecord(BaseModel):
    escrowId: int = Field(description="대상 에스크로.", examples=[ESCROW_ID])
    status: str = Field(
        description="취소된 상태. ERD escrows.status에 대응한다.",
        examples=["CANCELLED"],
    )


class CancelEscrowResponse(BaseModel):
    Header: CommonResponseHeader = Field(description="공통 성공 응답 Header.")
    REC: CancelEscrowRecord = Field(description="취소 결과.")


def _escrow_header_example(api_name: str, with_user_key: bool = False) -> dict:
    example = {
        "apiName": api_name,
        "transmissionDate": "20260909",
        "transmissionTime": "120000",
        "institutionCode": "00100",
        "fintechAppNo": "001",
        "apiServiceCode": api_name,
        "institutionTransactionUniqueNo": "20260909120000000001",
        "clientApiKey": "mock-client-api-key",
        "idempotencyKey": f"komit-{api_name}-CONTRACT-999",
    }
    if with_user_key:
        example["userKey"] = "mock-user-key"
    return example


def _escrow_state_error(
    escrow_id: int, header: EscrowRequestHeader
) -> JSONResponse | None:
    """예약값으로 없음·동결·예치 대기를 가른다."""
    if escrow_id == NOT_FOUND_ESCROW_ID:
        return _escrow_error(400, "E5004", "에스크로를 찾을 수 없습니다.", header)
    if escrow_id == HELD_ESCROW_ID:
        return _escrow_error(
            400, "E5005", "동결 중에는 자금을 옮길 수 없습니다.", header
        )
    if escrow_id in DEPOSIT_FIXTURE_STATUSES:
        return _escrow_error(
            400, "E5007", "예치가 완료되기 전에는 자금을 옮길 수 없습니다.", header
        )
    return None


# --------------------------------------------------------------------------- #
# 회원가입용 SSAFY 사용자·입출금 계좌 발급
# --------------------------------------------------------------------------- #

MOCK_MEMBER_USER_ID = "member@komit.example"
MOCK_MEMBER_BANK_CODE = "001"


class CreateMemberAccountRequest(BaseModel):
    Header: EscrowRequestHeader = Field(
        description="JSON Body 안에 담는 공통 요청 Header. 발급 전이므로 userKey는 필요하지 않다."
    )
    userId: str = Field(
        description=(
            "SSAFY 금융망 회원 등록에 사용할 이메일 형식의 사용자 ID. 회원가입 재시도에도 "
            "동일한 값을 보낸다. ERD에 없는 SSAFY 회원 등록 요청 필드다."
        ),
        examples=[MOCK_MEMBER_USER_ID],
        max_length=40,
        pattern=r"^[^@\s]+@[^@\s]+\.[^@\s]+$",
    )


class CreateMemberAccountRecord(BaseModel):
    userId: str = Field(
        description="발급 대상 사용자 ID. ERD에 없는 SSAFY 회원 등록 응답 필드다."
    )
    userKey: str = Field(
        description=(
            "발급된 SSAFY 사용자 키. 고객사인 komit이 보관해 후속 금융 요청의 Header에 "
            "담는다. ERD에 없는 SSAFY 회원 등록 응답 필드다."
        )
    )
    bankCode: str = Field(
        description=(
            "발급 계좌의 은행코드. komit이 계좌번호와 함께 보관한다. "
            "ERD에 없는 SSAFY 입출금 계좌 개설 응답 필드다."
        )
    )
    accountNo: str = Field(
        description=(
            "발급된 입출금 계좌번호. komit이 사용자 키와 함께 보관한다. "
            "ERD에 없는 SSAFY 입출금 계좌 개설 응답 필드다."
        )
    )


class CreateMemberAccountResponse(BaseModel):
    Header: CommonResponseHeader = Field(description="공통 성공 응답 Header.")
    REC: CreateMemberAccountRecord = Field(description="SSAFY 사용자와 입출금 계좌 발급 결과.")


def _mock_member_account(user_id: str) -> CreateMemberAccountRecord:
    """상태 저장 없이 사용자별로 고정된 테스트 발급값을 만든다."""
    member_id = uuid5(NAMESPACE_URL, f"komit-ssafy-member:{user_id}")
    account_id = uuid5(NAMESPACE_URL, f"komit-ssafy-account:{user_id}")
    return CreateMemberAccountRecord(
        userId=user_id,
        userKey=str(member_id),
        bankCode=MOCK_MEMBER_BANK_CODE,
        accountNo=MOCK_MEMBER_BANK_CODE + f"{account_id.int % 10**13:013d}",
    )


@router.post(
    "/v1/escrow/createMemberAccount",
    response_model=CreateMemberAccountResponse,
    tags=["에스크로 계좌 발급"],
    responses={
        200: _ok(
            "SSAFY 사용자와 입출금 계좌가 모두 발급되었을 때",
            "createMemberAccount",
            _mock_member_account(MOCK_MEMBER_USER_ID).model_dump(),
        ),
        **ESCROW_ERRORS,
    },
    summary="회원가입용 SSAFY 사용자·입출금 계좌 발급",
    description=(
        "komit이 회원가입할 때 에스크로에 호출한다. 에스크로가 SSAFY 사용자와 "
        "입출금 계좌를 발급하고, `updateDemandDepositAccountDeposit`으로 회원 계좌에 "
        "데모 자금을 한 번 입금한 뒤 komit이 보관할 사용자 키와 계좌 정보를 반환한다. "
        "입금액은 에스크로 설정값이며 계약 예치 원장에 포함하지 않는다. "
        "입금 성공을 확인해야 발급 완료 응답을 반환한다. (`R-PAY-10`) "
        "은행 `001`의 원화 수시입출금 상품은 에스크로 설정을 따른다 (`R-ACC-10`). "
        "같은 `userId`로 다시 요청하면 "
        "같은 사용자 키와 계좌 정보를 반환하며 데모 입금을 반복하지 않는다. "
        "처리 중이거나 결과 불명인 발급은 503으로 알린다. pending-member-key로 재현하며 "
        "동일한 userId와 멱등키를 유지해 다시 확인한다. "
        "목 서버는 상태를 저장하지 않고 userId별 고정 테스트 값을 반환한다.\n\n"
        "- **200 / H0000**: 사용자·계좌 발급과 데모 입금이 완료됐거나, 같은 `userId`로 재요청했을 때\n"
        "- **400 / E5001**: `clientApiKey`가 유효하지 않을 때\n"
        "- **400 / E5002**: `idempotencyKey`가 없을 때\n"
        "- **400 / E5003**: `idempotencyKey`가 `conflict-idempotency-key`일 때\n"
        "- **400 / H1001~H1010**: 요청 Header가 규격과 다를 때\n"
        "- **400 / Q1001**: `userId`가 이메일 형식이 아니거나 40자를 초과할 때\n"
        "- **500 / Q1000**: `clientApiKey`가 `server-error-client-api-key`일 때"
    ),
)
async def create_member_account(
    payload: Annotated[
        CreateMemberAccountRequest,
        Body(
            examples=[
                {
                    "Header": _escrow_header_example("createMemberAccount"),
                    "userId": MOCK_MEMBER_USER_ID,
                }
            ]
        ),
    ],
) -> CreateMemberAccountResponse | JSONResponse:
    error = _validate_escrow_header(
        payload.Header, "createMemberAccount", needs_idempotency_key=True
    )
    if error:
        return _escrow_error(*error, payload.Header)
    if payload.Header.idempotencyKey == "pending-member-key":
        return _escrow_error(503, "Q1000", "회원 계좌 발급 결과를 확인 중입니다. 같은 멱등키로 다시 확인해 주세요.", payload.Header)
    return CreateMemberAccountResponse(
        Header=_escrow_response_header(
            "H0000", "정상처리 되었습니다.", payload.Header
        ),
        REC=_mock_member_account(payload.userId),
    )


@router.post(
    "/v1/escrow/createEscrow",
    response_model=CreateEscrowResponse,
    tags=["에스크로 자금"],
    responses={
        200: _ok(
            "에스크로를 만들고 계약금을 예치했다",
            "createEscrow",
            {
                "escrowId": ESCROW_ID,
                "status": "ACTIVE",
                "depositedAmount": 650000,
                "balance": 650000,
                "transactionId": 1001,
            },
        ),
        **ESCROW_ERRORS,
    },
    summary="에스크로 생성과 계약금 예치",
    description=(
        "E5017은 출금 계좌가 해당 회원의 가입 발급 계좌와 다른 경우다. "
        "Mock은 은행코드가 001이 아니거나 계좌번호가 0019999999999999일 때 이 오류를 재현한다.\n\n"
        "계약이 맺어진 직후 한 번 부른다. 에스크로를 만들고 의뢰인 계좌에서 계약금 "
        "전액을 받아 금액과 입금자명을 확인한 뒤 원장에 기록한다.\n\n"
        "출금 계좌는 회원가입 때 발급한 의뢰인의 SSAFY 입출금 계좌로 고정한다. "
        "Komit이 보관한 `Header.userKey`, `withdrawalBankCode`, "
        "`withdrawalAccountNo`를 전달한다. 다른 출금 계좌를 선택하지 않는다. "
        "(`R-PAY-09`)\n\n"
        "한 계약에는 에스크로가 하나만 생긴다. 같은 `contractId`로 다시 불러도 새로 "
        "만들지 않는다.\n\n"
        "- **200 / H0000**: 요청이 접수됐다. 입금 검증과 보관계좌 정산이 모두 "
        "확정됐으면 `status`가 ACTIVE다. 아직 확정되지 않았으면 DEPOSIT_PENDING이고 "
        "예치액과 잔액은 0이다. 고객사는 조회에서 ACTIVE를 확인한 뒤 계약을 진행한다. "
        "`contractId`가 `CONTRACT-PENDING`이면 대기 사례를 재현한다. "
        "목에서는 같은 `contractId`에 같은 `escrowId`를 반환한다\n"
        "- **400 / H1009**: `userKey`가 없거나 `invalid-user-key`일 때\n"
        "- **400 / E5002**: `idempotencyKey`가 없을 때\n"
        "- **400 / E5001**: `clientApiKey`가 유효하지 않을 때\n"
        "- **400 / E5003**: `idempotencyKey`가 `conflict-idempotency-key`일 때. 같은 "
        "멱등키로 본문이 다른 요청이 이미 접수되었다\n"
        "- **400 / Q1001**: 필수 속성이 없거나 형식이 맞지 않을 때\n"
        "- **500 / Q1000**: `clientApiKey`가 `server-error-client-api-key`일 때. "
        "분류되지 않은 서버 오류다\n\n"
        "잔액 부족이나 이체한도 초과는 다시 보내도 결과가 같으므로 재시도하지 않는다. "
        "응답을 받지 못했으면 같은 멱등키로 다시 부르거나 조회로 결과를 확인한다. "
        "escrowId를 받지 못했다면 inquireEscrowList에 contractIds를 전달해 찾는다. "
        "조회 결과가 없다는 사실만으로 새 멱등키를 발급하지 않는다."
    ),
)
async def create_escrow(
    payload: Annotated[
        CreateEscrowRequest,
        Body(
            examples=[
                {
                    "Header": _escrow_header_example("createEscrow", True),
                    "contractId": "CONTRACT-999",
                    "contractType": "DEVELOPMENT",
                    "depositorName": "김미미",
                    "amount": 650000,
                    "withdrawalBankCode": "001",
                    "withdrawalAccountNo": MOCK_ACCOUNT_NO,
                }
            ]
        ),
    ],
) -> CreateEscrowResponse | JSONResponse:
    error = _validate_escrow_header(
        payload.Header, "createEscrow", needs_idempotency_key=True, needs_user_key=True
    )
    if error:
        return _escrow_error(*error, payload.Header)

    if payload.withdrawalBankCode != '001' or payload.withdrawalAccountNo == '0019999999999999':
        return _escrow_error(400, 'E5017', '회원가입 발급 계좌와 일치하지 않습니다.', payload.Header)

    # 입금이 아직 확인되지 않은 경우. 예치액은 0이고 고객사는 ACTIVE를 조회로 기다린다.
    if payload.contractId == PENDING_CONTRACT_ID or payload.contractId in DEPOSIT_FIXTURE_CONTRACT_IDS.values():
        return CreateEscrowResponse(
            Header=_escrow_response_header(
                "H0000", "정상처리 되었습니다.", payload.Header
            ),
            REC=EscrowAmountRecord(
                escrowId=_mock_escrow_id(payload.contractId),
                status="DEPOSIT_PENDING",
                depositedAmount=0,
                balance=0,
                transactionId=PENDING_DEPOSIT_TRANSACTION_ID,
            ),
        )

    fixture_id = _mock_escrow_id(payload.contractId)
    if fixture_id in SETTLEMENT_FIXTURE_CONTRACT_IDS:
        if payload.amount != 650000:
            return _escrow_error(400, 'E5003', '기존 계약의 예치 요청과 다릅니다.', payload.Header)
        return CreateEscrowResponse(
            Header=_escrow_response_header('H0000', '정상처리 되었습니다.', payload.Header),
            REC=EscrowAmountRecord(escrowId=fixture_id,
                status='COMPLETED' if fixture_id == 46 else 'ACTIVE',
                depositedAmount=650000, balance=_mock_escrow_balance(fixture_id),
                transactionId=fixture_id*100+1))

    return CreateEscrowResponse(
        Header=_escrow_response_header(
            "H0000", "정상처리 되었습니다.", payload.Header
        ),
        REC=EscrowAmountRecord(
            escrowId=_mock_escrow_id(payload.contractId),
            status="ACTIVE",
            depositedAmount=payload.amount,
            balance=payload.amount,
            transactionId=1001,
        ),
    )


@router.post(
    "/v1/escrow/releaseEscrow",
    response_model=ReleaseEscrowResponse,
    tags=["에스크로 자금"],
    responses={
        200: _ok(
            "정산 요청의 현재 처리 상태를 돌려준다",
            "releaseEscrow",
            {
                "transactionId": 1002,
                "releasedAmount": 400000,
                "feeAmount": 4000,
                "netAmount": 396000,
                "balance": 250000,
                "status": "COMPLETED",
            },
        ),
        **ESCROW_ERRORS,
    },
    summary="작업 대금 지급",
    description=(
        "수취 예금주명 invalid-holder-name은 금융망 예금주 불일치 E5016 사례다.\n\n"
        "전 작업이 통과 확정된 뒤 계약 대금을 한 번에 정산할 때 부른다. 요청 금액은 "
        "수수료 공제 전 승인 총액이며 작업자 순지급액과 플랫폼 수수료를 구분한다 "
        "(R-PAY-07). 보내기 직전에 "
        "예금주와 동결 여부를 다시 확인한다.\n\n"
        "마일스톤 단위로 나눠 지급하지 않는다. 같은 `referenceId`로 다시 부르면 두 번 "
        "보내지 않는다.\n\n"
        "- **200 / H0000**: 요청을 정상 처리하고 현재 정산 상태를 반환했을 때. "
        "`H0000`만으로 지급 완료를 판단하지 않는다. `status=COMPLETED`는 두 이체가 "
        "모두 확인된 경우다. `escrowId` 43은 수수료 이체 진행 중, 44는 수수료 이체 "
        "결과 불명, 45는 출금 전 확정 실패를 재현한다. (R-PAY-12)\n"
        "- **400 / E5002**: `idempotencyKey`가 없을 때\n"
        "- **400 / E5001**: `clientApiKey`가 유효하지 않을 때\n"
        "- **400 / E5004**: `escrowId`가 404일 때\n"
        "- **400 / E5005**: `escrowId`가 409일 때. 동결 중에는 지급하지 않는다\n"
        "- **400 / E5007**: `escrowId`가 202일 때. 예치 완료 전에는 지급하지 않는다\n"
        "- **400 / E5008**: 요청 금액이 목의 예치 잔액을 초과할 때\n"
        "- **400 / E5010**: 진행 중이거나 결과 불명인 정산이 있는데 다른 "
        "`referenceId`로 새 지급을 요청할 때\n"
        "- **400 / E5003**: `idempotencyKey`가 `conflict-idempotency-key`일 때\n"
        "- **400 / Q1001**: 필수 속성이 없거나 형식이 맞지 않을 때\n"
        "- **500 / Q1000**: `clientApiKey`가 `server-error-client-api-key`일 때. "
        "분류되지 않은 서버 오류다\n\n"
        "같은 `referenceId`로 재요청하면 이미 확인된 이체를 중복 실행하지 않고 현재 "
        "상태를 돌려준다. 예금주가 다르면 보내지 않고 거절한다. 응답을 받지 못했을 "
        "때는 조회로 결과를 먼저 확인한다."
    ),
)
async def release_escrow(
    payload: Annotated[
        ReleaseEscrowRequest,
        Body(
            examples=[
                {
                    "Header": {
                        **_escrow_header_example("releaseEscrow"),
                        "idempotencyKey": f"komit-releaseEscrow-{escrow_id}",
                    },
                    "escrowId": escrow_id,
                    "referenceId": "SETTLEMENT:9001",
                    "amount": 400000,
                    "recipientBankCode": "001",
                    "recipientAccountNo": "0019876543210987",
                    "recipientName": "이준",
                }
                for escrow_id in (
                    ESCROW_ID,
                    SETTLEMENT_PROCESSING_ESCROW_ID,
                    SETTLEMENT_UNKNOWN_ESCROW_ID,
                    SETTLEMENT_FAILED_ESCROW_ID,
                )
            ]
        ),
    ],
) -> ReleaseEscrowResponse | JSONResponse:
    error = _validate_escrow_header(
        payload.Header, "releaseEscrow", needs_idempotency_key=True
    )
    if error:
        return _escrow_error(*error, payload.Header)
    if payload.recipientName == 'invalid-holder-name':
        return _escrow_error(400, 'E5016', '금융망 예금주명과 일치하지 않습니다.', payload.Header)
    state_error = _escrow_state_error(payload.escrowId, payload.Header)
    if state_error:
        return state_error
    if payload.escrowId in REFUND_FIXTURE_STATUSES:
        if payload.referenceId != SETTLEMENT_FIXTURE_REFERENCE_ID:
            return _escrow_error(400, 'E5010', '기존 정산이 있습니다.', payload.Header)
        if payload.amount != 400000:
            return _escrow_error(400, 'E5003', '기존 지급 요청과 다릅니다.', payload.Header)
        return ReleaseEscrowResponse(
            Header=_escrow_response_header('H0000', '정상처리 되었습니다.', payload.Header),
            REC=ReleaseEscrowRecord(transactionId=payload.escrowId*100+2,
                releasedAmount=400000, feeAmount=4000, netAmount=396000,
                balance=_mock_escrow_balance(payload.escrowId), status='COMPLETED'))
    if payload.escrowId in SETTLEMENT_FIXTURE_STATUSES:
        fixture_status = SETTLEMENT_FIXTURE_STATUSES[payload.escrowId]
        if payload.referenceId != SETTLEMENT_FIXTURE_REFERENCE_ID:
            if fixture_status != "FAILED":
                return _escrow_error(
                    400, "E5010", "진행 중인 정산이 있습니다.", payload.Header
                )
        else:
            if (
                payload.amount != SETTLEMENT_FIXTURE_GROSS_AMOUNT
                or payload.recipientBankCode != "001"
                or payload.recipientAccountNo != "0019876543210987"
                or payload.recipientName != "이준"
            ):
                return _escrow_error(
                    400, "E5003", "같은 정산 식별자로 다른 요청이 접수되었습니다.",
                    payload.Header,
                )
            paid = fixture_status != "FAILED"
            net_amount = SETTLEMENT_FIXTURE_NET_AMOUNT if paid else 0
            return ReleaseEscrowResponse(
                Header=_escrow_response_header(
                    "H0000", "정상처리 되었습니다.", payload.Header
                ),
                REC=ReleaseEscrowRecord(
                    transactionId=payload.escrowId * 100 + 2,
                    releasedAmount=net_amount,
                    feeAmount=0,
                    netAmount=net_amount,
                    balance=650000 - net_amount,
                    status=fixture_status,
                ),
            )
    if payload.amount > 650000:
        return _escrow_error(
            400, "E5008", "에스크로 잔액이 부족합니다.", payload.Header
        )

    fee_amount = payload.amount * MOCK_FEE_RATE_BPS // 10000
    return ReleaseEscrowResponse(
        Header=_escrow_response_header(
            "H0000", "정상처리 되었습니다.", payload.Header
        ),
        REC=ReleaseEscrowRecord(
            transactionId=1002,
            releasedAmount=payload.amount,
            feeAmount=fee_amount,
            netAmount=payload.amount - fee_amount,
            balance=650000 - payload.amount,
            status="COMPLETED",
        ),
    )


@router.post(
    "/v1/escrow/refundEscrow",
    response_model=RefundEscrowResponse,
    tags=["에스크로 자금"],
    responses={
        200: _ok(
            "남은 잔액을 의뢰인에게 돌려줬다",
            "refundEscrow",
            {
                "transactionId": 1003,
                "refundedAmount": 250000,
                "balance": 0,
                "status": "COMPLETED",
            },
        ),
        **ESCROW_ERRORS,
    },
    summary="잔액 환불",
    description=(
        "수취 예금주명 invalid-holder-name은 금융망 예금주 불일치 E5016 사례다.\n\n"
        "중도 종료로 통과분을 지급한 뒤 남은 잔액을 의뢰인에게 돌려줄 때 부른다. 절차는 "
        "지급과 같다. 환불 계좌는 해당 계약의 예치에 사용한 회원가입 발급 계좌로 "
        "고정한다. 요청의 은행코드·계좌번호를 예치 출금 계좌와 대조하고, 금융망 "
        "예금주를 확인한 뒤 송금한다. 별도로 등록·교체한 계좌로는 환불하지 않는다. "
        "(R-PAY-04)\n\n"
        "환불은 같은 최종 정산의 남은 금액 전액을 한 거래로 처리한다. 같은 referenceId와 "
        "멱등키의 재요청은 확인된 환불을 반복하지 않고 현재 결과를 반환한다. "
        "escrowId 46은 지급 400000원 후 250000원 환불 완료, 47은 처리 중, "
        "48은 결과 불명, 49는 환불 이체 확정 실패 사례다.\n\n"
        "- **200 / H0000**: 현재 환불 거래 상태를 반환한다. COMPLETED만 환불 완료이며 "
        "PROCESSING·UNKNOWN·FAILED에서는 refundedAmount가 0이다\n"
        "- **400 / E5013**: 남은 금액 전액과 환불 요청 금액이 다를 때\n"
        "- **400 / E5002**: `idempotencyKey`가 없을 때\n"
        "- **400 / E5001**: `clientApiKey`가 유효하지 않을 때\n"
        "- **400 / E5004**: `escrowId`가 404일 때\n"
        "- **400 / E5005**: `escrowId`가 409일 때. 동결 중에는 환불하지 않는다\n"
        "- **400 / E5007**: `escrowId`가 202일 때. 예치 완료 전에는 환불하지 않는다\n"
        "- **400 / E5008**: 요청 금액이 목의 예치 잔액을 초과할 때\n"
        "- **400 / E5010**: `escrowId` 43·44처럼 지급 정산이 진행 중이거나 "
        "결과 불명이라 환불에 사용할 수 없을 때. (R-PAY-12)\n"
        "- **400 / E5009**: 환불 계좌가 예치 출금 계좌와 다를 때\n"
        "- **400 / E5003**: `idempotencyKey`가 `conflict-idempotency-key`일 때\n"
        "- **400 / Q1001**: 필수 속성이 없거나 형식이 맞지 않을 때\n"
        "- **500 / Q1000**: `clientApiKey`가 `server-error-client-api-key`일 때. "
        "분류되지 않은 서버 오류다\n\n"
        "전액이 이미 지급된 뒤에는 환불할 수 없다."
    ),
)
async def refund_escrow(
    payload: Annotated[
        RefundEscrowRequest,
        Body(
            examples=[
                {
                    "Header": _escrow_header_example("refundEscrow"),
                    "escrowId": 46,
                    "amount": 250000,
                    "refundBankCode": "001",
                    "refundAccountNo": MOCK_ACCOUNT_NO,
                    "refundHolderName": "김미미",
                    "referenceId": "SETTLEMENT:9001",
                }
            ]
        ),
    ],
) -> RefundEscrowResponse | JSONResponse:
    error = _validate_escrow_header(
        payload.Header, "refundEscrow", needs_idempotency_key=True
    )
    if error:
        return _escrow_error(*error, payload.Header)
    if payload.refundHolderName == 'invalid-holder-name':
        return _escrow_error(400, 'E5016', '금융망 예금주명과 일치하지 않습니다.', payload.Header)
    state_error = _escrow_state_error(payload.escrowId, payload.Header)
    if state_error:
        return state_error
    if payload.escrowId in (
        SETTLEMENT_PROCESSING_ESCROW_ID,
        SETTLEMENT_UNKNOWN_ESCROW_ID,
    ):
        return _escrow_error(
            400, "E5010", "진행 중인 정산이 있어 환불할 수 없습니다.", payload.Header
        )
    # 무상태 목은 42번 예시 계약의 예치 출금 계좌만 알고 있다 (R-PAY-04).
    if (
        payload.refundBankCode != "001"
        or payload.refundAccountNo != MOCK_ACCOUNT_NO
    ):
        return _escrow_error(
            400, "E5009", "환불 계좌가 예치 출금 계좌와 다릅니다.", payload.Header
        )
    if payload.amount > 650000:
        return _escrow_error(
            400, "E5008", "에스크로 잔액이 부족합니다.", payload.Header
        )

    fixture_status = REFUND_FIXTURE_STATUSES.get(payload.escrowId, 'COMPLETED')
    expected_refund = 250000 if payload.escrowId in REFUND_FIXTURE_STATUSES else 650000
    if payload.escrowId in REFUND_FIXTURE_STATUSES and payload.referenceId != SETTLEMENT_FIXTURE_REFERENCE_ID:
        return _escrow_error(400, 'E5010', '기존 정산 식별자로 요청해야 합니다.', payload.Header)
    if payload.amount != expected_refund:
        return _escrow_error(400, 'E5013', '남은 금액 전액을 환불해야 합니다.', payload.Header)
    confirmed = fixture_status == 'COMPLETED'
    remaining = 0 if confirmed else expected_refund
    return RefundEscrowResponse(
        Header=_escrow_response_header(
            "H0000", "정상처리 되었습니다.", payload.Header
        ),
        REC=RefundEscrowRecord(
            transactionId=payload.escrowId*100+4,
            refundedAmount=payload.amount if confirmed else 0,
            balance=remaining,
            status=fixture_status,
        ),
    )


@router.post(
    "/v1/escrow/cancelEscrow",
    response_model=CancelEscrowResponse,
    tags=["에스크로 자금"],
    responses={
        200: _ok("예치 전 에스크로를 닫았다", "cancelEscrow", {"escrowId": DEPOSIT_PENDING_ESCROW_ID, "status": "CANCELLED"}),
        **ESCROW_ERRORS,
    },
    summary="예치 전 에스크로 취소",
    description=(
        "예치 송금이 진행 중이거나 결과가 불명확하면 E5015로 거절한다. "
        "DEPOSIT_PENDING 상태만으로 취소 가능 여부를 판단하지 않는다.\n\n"
        "아직 예치가 끝나지 않은 에스크로를 닫는다. 돈이 움직이기 전이면 받을 자리만 "
        "닫고 끝낸다.\n\n"
        "이미 돈이 들어왔으면 취소가 아니라 환불로 처리한다. ACTIVE가 된 뒤에는 취소할 "
        "수 없다.\n\n"
        "- **200 / H0000**: 닫았을 때. `status`가 CANCELLED가 된다\n"
        "- **400 / E5002**: `idempotencyKey`가 없을 때\n"
        "- **400 / E5001**: `clientApiKey`가 유효하지 않을 때\n"
        "- **400 / E5004**: `escrowId`가 404일 때\n"
        "- **400 / E5006**: 예치가 완료된 에스크로일 때. 42 또는 409로 재현한다\n"
        "- **400 / E5003**: `idempotencyKey`가 `conflict-idempotency-key`일 때\n"
        "- **400 / Q1001**: 필수 속성이 없거나 형식이 맞지 않을 때\n"
        "- **500 / Q1000**: `clientApiKey`가 `server-error-client-api-key`일 때. "
        "분류되지 않은 서버 오류다\n\n"
        "입금이 없었다는 것이 확인된 뒤에만 취소를 확정한다. 모호하면 취소 완료를 "
        "돌려주지 않는다."
    ),
)
async def cancel_escrow(
    payload: Annotated[
        CancelEscrowRequest,
        Body(
            examples=[
                {
                    "Header": _escrow_header_example("cancelEscrow"),
                    "escrowId": DEPOSIT_PENDING_ESCROW_ID,
                }
            ]
        ),
    ],
) -> CancelEscrowResponse | JSONResponse:
    error = _validate_escrow_header(
        payload.Header, "cancelEscrow", needs_idempotency_key=True
    )
    if error:
        return _escrow_error(*error, payload.Header)
    if payload.escrowId == NOT_FOUND_ESCROW_ID:
        return _escrow_error(
            400, "E5004", "에스크로를 찾을 수 없습니다.", payload.Header
        )
    if payload.escrowId in (203, 204):
        return _escrow_error(400, 'E5015', '예치 송금 결과를 확인하기 전에는 취소할 수 없습니다.', payload.Header)
    if payload.escrowId not in (DEPOSIT_PENDING_ESCROW_ID, 205):
        return _escrow_error(
            400, "E5006", "이미 예치가 끝나 취소할 수 없습니다.", payload.Header
        )

    return CancelEscrowResponse(
        Header=_escrow_response_header(
            "H0000", "정상처리 되었습니다.", payload.Header
        ),
        REC=CancelEscrowRecord(escrowId=payload.escrowId, status="CANCELLED"),
    )


# --------------------------------------------------------------------------- #
# 동결 — 신고가 접수되면 걸고 끝나면 푼다
#
# 금융망은 관여하지 않고 에스크로 안의 상태만 바뀐다. 동결은 에스크로당 하나이며
# escrows.hold_status(NONE / HELD / RELEASED)로 가른다. 활성 신고는 최대 1개다.
# escrows.status는 동결로 바뀌지 않는다.
# --------------------------------------------------------------------------- #


class HoldEscrowRequest(BaseModel):
    """동결 요청."""

    Header: EscrowRequestHeader = Field(
        description="JSON Body 안에 담는 공통 요청 Header."
    )
    escrowId: int = Field(
        description="동결할 에스크로. 404는 없음 재현용 예약값이다.",
        examples=[ESCROW_ID], gt=0,
    )
    reportId: str = Field(
        description=(
            "고객사의 신고 식별자. 에스크로당 활성 신고는 최대 1개다. ERD "
            "escrows.external_report_id에 대응한다."
        ),
        examples=["REPORT-7"],
        min_length=1,
        max_length=100,
    )


class HoldRecord(BaseModel):
    escrowId: int = Field(description="대상 에스크로.", examples=[ESCROW_ID])
    isHeld: bool = Field(
        description=(
            "해당 명령 성공 당시의 동결 여부. 같은 멱등키 재요청은 당시 결과를 반환한다. 현재 상태는 inquireEscrow로 확인한다. "
            "잠긴 동안은 지급과 일반 환불이 막힌다."
        ),
        examples=[True],
    )


class HoldEscrowResponse(BaseModel):
    Header: CommonResponseHeader = Field(description="공통 성공 응답 Header.")
    REC: HoldRecord = Field(description="동결 결과.")


class UnholdEscrowRequest(BaseModel):
    """동결 해제 요청."""

    Header: EscrowRequestHeader = Field(
        description="JSON Body 안에 담는 공통 요청 Header."
    )
    escrowId: int = Field(
        description="대상 에스크로. 404는 없음 재현용 예약값이다.",
        examples=[ESCROW_ID], gt=0,
    )
    reportId: str = Field(
        description=(
            "풀 신고의 식별자. 그 신고로 걸린 동결을 해제한다. ERD "
            "escrows.external_report_id에 대응하고 released_at을 채운다."
        ),
        examples=["REPORT-7"],
        min_length=1,
        max_length=100,
    )


class UnholdEscrowResponse(BaseModel):
    Header: CommonResponseHeader = Field(description="공통 성공 응답 Header.")
    REC: HoldRecord = Field(description="해제 뒤 남은 동결 상태.")


@router.post(
    "/v1/escrow/holdEscrow",
    response_model=HoldEscrowResponse,
    tags=["에스크로 동결"],
    responses={
        200: _ok(
            "동결을 걸었다",
            "holdEscrow",
            {
                "escrowId": ESCROW_ID,
                "isHeld": True,
            },
        ),
        **ESCROW_ERRORS,
    },
    summary="신고 접수로 자금 동결",
    description=(
        "E5014는 신고 식별자 충돌, E5007은 예치 전 또는 종료된 계약의 상태 변경 거절이다. "
        "409번 계약은 REPORT-7로 동결된 고정 사례다.\n\n"
        "고객사에 신고가 접수되면 자동으로 건다. 관리자가 버튼을 눌러 거는 것이 "
        "아니다.\n\n"
        "동결 중에는 지급도 환불도 거절된다. 에스크로당 활성 동결은 하나이며, 같은 "
        "신고로 다시 불러도 이미 걸려 있으므로 성공으로 처리한다.\n\n"
        "- **200 / H0000**: 걸었을 때. 이미 걸려 있어도 같은 응답을 준다\n"
        "- **400 / E5002**: `idempotencyKey`가 없을 때\n"
        "- **400 / E5001**: `clientApiKey`가 유효하지 않을 때\n"
        "- **400 / E5004**: `escrowId`가 404일 때\n"
        "- **400 / E5003**: `idempotencyKey`가 `conflict-idempotency-key`일 때\n"
        "- **400 / Q1001**: 필수 속성이 없거나 형식이 맞지 않을 때\n"
        "- **500 / Q1000**: `clientApiKey`가 `server-error-client-api-key`일 때. "
        "분류되지 않은 서버 오류다"
    ),
)
async def hold_escrow(
    payload: Annotated[
        HoldEscrowRequest,
        Body(
            examples=[
                {
                    "Header": _escrow_header_example("holdEscrow"),
                    "escrowId": ESCROW_ID,
                    "reportId": "REPORT-7",
                }
            ]
        ),
    ],
) -> HoldEscrowResponse | JSONResponse:
    error = _validate_escrow_header(
        payload.Header, "holdEscrow", needs_idempotency_key=True
    )
    if error:
        return _escrow_error(*error, payload.Header)
    if payload.escrowId == NOT_FOUND_ESCROW_ID:
        return _escrow_error(
            400, "E5004", "에스크로를 찾을 수 없습니다.", payload.Header
        )

    if payload.escrowId in DEPOSIT_FIXTURE_STATUSES or payload.escrowId == 46:
        return _escrow_error(400, 'E5007', '동결 상태를 변경할 수 없는 계약입니다.', payload.Header)
    if payload.escrowId == HELD_ESCROW_ID and payload.reportId != 'REPORT-7':
        return _escrow_error(400, 'E5014', '현재 동결의 신고 식별자와 다릅니다.', payload.Header)
    return HoldEscrowResponse(
        Header=_escrow_response_header(
            "H0000", "정상처리 되었습니다.", payload.Header
        ),
        REC=HoldRecord(escrowId=payload.escrowId, isHeld=True),
    )


@router.post(
    "/v1/escrow/unholdEscrow",
    response_model=UnholdEscrowResponse,
    tags=["에스크로 동결"],
    responses={
        200: _ok(
            "해당 신고의 동결을 풀었다",
            "unholdEscrow",
            {
                "escrowId": ESCROW_ID,
                "isHeld": False,
            },
        ),
        **ESCROW_ERRORS,
    },
    summary="신고 종료로 동결 해제",
    description=(
        "E5014는 신고 식별자 충돌, E5007은 예치 전 또는 종료된 계약의 상태 변경 거절이다. "
        "409번 계약은 REPORT-7로 동결된 고정 사례다.\n\n"
        "신고가 끝나면 걸려 있던 동결을 푼다. 에스크로당 활성 동결은 하나이므로 "
        "풀면 바로 잠금이 해제된다.\n\n"
        "같은 신고의 동결 완료를 확인한 뒤에만 해제를 요청한다. 동결·해제 요청을 "
        "재전송할 때는 각각 처음 사용한 `idempotencyKey`를 유지한다. 수신 시각만으로 "
        "서로 다른 신고의 사건 순서를 판정하지 않는다.\n\n"
        "- **200 / H0000**: 해당 해제 명령의 성공 결과. 이후 새 신고로 다시 동결될 수 있으므로 현재 상태는 조회한다\n"
        "- **400 / E5002**: `idempotencyKey`가 없을 때\n"
        "- **400 / E5001**: `clientApiKey`가 유효하지 않을 때\n"
        "- **400 / E5004**: `escrowId`가 404일 때\n"
        "- **400 / E5003**: `idempotencyKey`가 `conflict-idempotency-key`일 때\n"
        "- **400 / Q1001**: 필수 속성이 없거나 형식이 맞지 않을 때\n"
        "- **500 / Q1000**: `clientApiKey`가 `server-error-client-api-key`일 때. "
        "분류되지 않은 서버 오류다"
    ),
)
async def unhold_escrow(
    payload: Annotated[
        UnholdEscrowRequest,
        Body(
            examples=[
                {
                    "Header": _escrow_header_example("unholdEscrow"),
                    "escrowId": ESCROW_ID,
                    "reportId": "REPORT-7",
                }
            ]
        ),
    ],
) -> UnholdEscrowResponse | JSONResponse:
    error = _validate_escrow_header(
        payload.Header, "unholdEscrow", needs_idempotency_key=True
    )
    if error:
        return _escrow_error(*error, payload.Header)
    if payload.escrowId == NOT_FOUND_ESCROW_ID:
        return _escrow_error(
            400, "E5004", "에스크로를 찾을 수 없습니다.", payload.Header
        )

    if payload.escrowId in DEPOSIT_FIXTURE_STATUSES or payload.escrowId == 46:
        return _escrow_error(400, 'E5007', '동결 상태를 변경할 수 없는 계약입니다.', payload.Header)
    if payload.escrowId == HELD_ESCROW_ID and payload.reportId != 'REPORT-7':
        return _escrow_error(400, 'E5014', '현재 동결의 신고 식별자와 다릅니다.', payload.Header)
    if payload.reportId != 'REPORT-7':
        return _escrow_error(400, 'E5014', '해제할 신고가 일치하지 않습니다.', payload.Header)
    return UnholdEscrowResponse(
        Header=_escrow_response_header(
            "H0000", "정상처리 되었습니다.", payload.Header
        ),
        REC=HoldRecord(escrowId=payload.escrowId, isHeld=False),
    )


# --------------------------------------------------------------------------- #
# 조회 — 읽기만 하고 아무것도 쓰지 않는다
#
# 멱등 기록을 남기지 않으므로 idempotencyKey를 쓰지 않는다. 고객사는 잔액을 자기 DB에
# 두지 않고 매번 여기에 묻는다. 인증된 서비스의 에스크로만 돌려준다.
# --------------------------------------------------------------------------- #


class EscrowTransactionItem(BaseModel):
    """원장 항목 한 건."""

    transactionId: int = Field(
        description="거래 식별자. ERD escrow_transactions.id에 대응한다.",
        examples=[1001],
    )
    transactionType: str = Field(
        description=(
            "거래 종류. DEPOSIT은 예치, RELEASE는 작업자 순지급, FEE는 플랫폼 "
            "수수료 이체, REFUND는 환불이다. ERD "
            "escrow_transactions.transaction_type에 대응한다."
        ),
        examples=["DEPOSIT"],
    )
    amount: int = Field(description="거래 금액. 원 단위 양수다.", examples=[650000])
    status: str = Field(
        description=(
            "거래 상태. ERD escrow_transactions.status에 대응한다. 확정되지 않은 "
            "PENDING과 UNKNOWN은 끝난 거래로 보지 않는다."
        ),
        examples=["COMPLETED"],
    )
    referenceId: str = Field(
        description=(
            "고객사가 준 참조 식별자. ERD "
            "escrow_transactions.external_reference_id에 대응한다."
        ),
        examples=["CONTRACT-999"],
    )


class InquireEscrowRequest(BaseModel):
    """단건 조회 요청."""

    Header: EscrowRequestHeader = Field(
        description="JSON Body 안에 담는 공통 요청 Header."
    )
    escrowId: int = Field(
        description=(
            "조회할 에스크로. 202는 예치 대기, 404는 없음 재현용 예약값이다."
        ),
        examples=[ESCROW_ID], gt=0,
    )
    contractId: str | None = Field(
        default=None,
        description=(
            "고객사의 계약 식별자. 전달하면 `escrowId`와 같은 계약을 가리키는지 확인한다. "
            "불일치하면 E5004다. ERD escrows.external_contract_id에 대응한다."
        ),
        min_length=1,
        max_length=100,
    )


class InquireEscrowRecord(BaseModel):
    escrowId: int = Field(description="에스크로 식별자.", examples=[ESCROW_ID])
    contractId: str = Field(
        description="고객사의 계약 식별자. ERD escrows.external_contract_id.",
        examples=["CONTRACT-999"],
    )
    status: str = Field(description="에스크로 상태.", examples=["ACTIVE"])
    depositedAmount: int = Field(
        description="예치된 총액. ERD escrow_transactions의 완료된 DEPOSIT 합계다.",
        examples=[650000],
    )
    releasedAmount: int = Field(
        description=(
            "완료가 확인된 작업자 순지급(RELEASE)과 수수료(FEE)의 합계. "
            "정산 완료 전에는 승인 총액보다 작을 수 있다. (R-PAY-12)"
        ),
        examples=[0],
    )
    feeAmount: int = Field(
        description="플랫폼으로 이체한 수수료 총액. 완료된 FEE 합계다. (R-PAY-07)",
        examples=[0],
    )
    refundedAmount: int = Field(
        description="환불된 총액. ERD escrow_transactions의 완료된 REFUND 합계다.",
        examples=[0],
    )
    balance: int = Field(
        description=(
            "가용 잔액. ERD escrow_transactions에서 DEPOSIT 합계에서 RELEASE, FEE, REFUND "
            "합계를 뺀 계산값이다. 정산 진행 중에는 새 지급·환불에 사용할 수 없다. "
            "(R-PAY-12)"
        ),
        examples=[650000],
    )
    isHeld: bool = Field(
        description="동결 여부. ERD escrows.hold_status가 HELD이면 참이다.",
        examples=[False],
    )
    transactions: List[EscrowTransactionItem] = Field(
        description="원장 항목. 없으면 빈 배열이다."
    )


class InquireEscrowResponse(BaseModel):
    Header: CommonResponseHeader = Field(description="공통 성공 응답 Header.")
    REC: InquireEscrowRecord = Field(description="단건 조회 결과.")


class InquireEscrowListRequest(BaseModel):
    """목록 조회 요청."""

    Header: EscrowRequestHeader = Field(
        description="JSON Body 안에 담는 공통 요청 Header."
    )
    contractIds: List[Annotated[str, Field(min_length=1, max_length=100)]] | None = Field(
        default=None,
        description="특정 계약만 거를 때 쓴다. 비우면 전부 내려간다.",
        examples=[["CONTRACT-999"]],
        max_length=100,
    )
    status: Literal[
        "CREATED", "DEPOSIT_PENDING", "ACTIVE", "COMPLETED", "REFUNDED", "CANCELLED"
    ] | None = Field(
        default=None,
        description="상태로 거를 때 쓴다. 비우면 전부 내려간다.",
        examples=["ACTIVE"],
    )


class EscrowSummaryItem(BaseModel):
    escrowId: int = Field(description="에스크로 식별자.", examples=[ESCROW_ID])
    contractId: str = Field(description="계약 식별자.", examples=["CONTRACT-999"])
    status: str = Field(description="에스크로 상태.", examples=["ACTIVE"])
    balance: int = Field(description="가용 잔액.", examples=[650000])
    isHeld: bool = Field(description="동결 여부.", examples=[False])


class InquireEscrowListRecord(BaseModel):
    totalCount: int = Field(description="조건에 맞는 건수.", examples=[1])
    list: List[EscrowSummaryItem] = Field(
        description="에스크로 요약. 없으면 빈 배열이다."
    )


class InquireEscrowListResponse(BaseModel):
    Header: CommonResponseHeader = Field(description="공통 성공 응답 Header.")
    REC: InquireEscrowListRecord = Field(description="목록 조회 결과.")


INQUIRE_ERRORS: dict = dict(ESCROW_ERRORS)


@router.post(
    "/v1/escrow/inquireEscrow",
    response_model=InquireEscrowResponse,
    tags=["에스크로 조회"],
    responses={
        200: _ok(
            "상태와 잔액, 동결, 원장 항목을 돌려준다",
            "inquireEscrow",
            {
                "escrowId": ESCROW_ID,
                "contractId": "CONTRACT-999",
                "status": "ACTIVE",
                "depositedAmount": 650000,
                "releasedAmount": 0,
                "feeAmount": 0,
                "refundedAmount": 0,
                "balance": 650000,
                "isHeld": False,
                "transactions": [
                    {
                        "transactionId": 1001,
                        "transactionType": "DEPOSIT",
                        "amount": 650000,
                        "status": "COMPLETED",
                        "referenceId": "INITIAL",
                    },
                ],
            },
        ),
        **INQUIRE_ERRORS,
    },
    summary="에스크로 단건 조회",
    description=(
        "에스크로 하나의 상태와 잔액, 동결 여부, 원장 항목을 돌려준다. 고객사는 잔액을 "
        "자기 DB에 두지 않고 필요할 때마다 여기에 묻는다.\n\n"
        "읽기만 하므로 `idempotencyKey`를 쓰지 않는다. 아직 확정되지 않은 거래는 끝난 "
        "거래처럼 보여주지 않는다. `status=ACTIVE`인 계약도 RELEASE 또는 FEE가 "
        "PROCESSING·UNKNOWN이면 정산 중이므로 새 지급·환불을 시작하지 않는다. "
        "(R-PAY-12)\n\n"
        "- **200 / H0000**: 조회했을 때. `escrowId` 202는 "
        "`CONTRACT-PENDING`의 DEPOSIT_PENDING과 예치액 0을, 42는 ACTIVE를 돌려준다. "
        "43·44는 작업자 지급 완료 후 수수료 이체 진행 중·결과 불명, 45는 "
        "출금 전 확정 실패 원장을 돌려준다. 46~49는 작업자 지급과 수수료 완료 후 "
        "환불 완료·처리 중·결과 불명·확정 실패 사례다. "
        "Komit의 숫자 계약 ID도 생성·조회에서 같은 계약을 가리킨다. "
        "문자형 계약 ID는 `contractId`를 함께 보내 조회할 수 있다\n"
        "- **400 / E5001**: `clientApiKey`가 유효하지 않을 때\n"
        "- **400 / E5004**: `escrowId`가 404이거나 다른 서비스의 에스크로일 때. 남의 "
        "에스크로는 있고 없고를 구분해 알려주지 않는다\n"
        "- **400 / Q1001**: 필수 속성이 없거나 형식이 맞지 않을 때\n"
        "- **500 / Q1000**: `clientApiKey`가 `server-error-client-api-key`일 때. "
        "분류되지 않은 서버 오류다"
    ),
)
async def inquire_escrow(
    payload: Annotated[
        InquireEscrowRequest,
        Body(
            examples=[
                {
                    "Header": {
                        k: v
                        for k, v in _escrow_header_example("inquireEscrow").items()
                        if k != "idempotencyKey"
                    },
                    "escrowId": escrow_id,
                }
                for escrow_id in (
                    ESCROW_ID,
                    SETTLEMENT_PROCESSING_ESCROW_ID,
                    SETTLEMENT_UNKNOWN_ESCROW_ID,
                    SETTLEMENT_FAILED_ESCROW_ID,
                )
            ]
        ),
    ],
) -> InquireEscrowResponse | JSONResponse:
    error = _validate_escrow_header(payload.Header, "inquireEscrow")
    if error:
        return _escrow_error(*error, payload.Header)
    contract_id = _mock_contract_id(payload.escrowId)
    if payload.contractId is not None:
        if _mock_escrow_id(payload.contractId) != payload.escrowId:
            return _escrow_error(
                400, "E5004", "에스크로를 찾을 수 없습니다.", payload.Header
            )
        contract_id = payload.contractId
    if contract_id is None:
        return _escrow_error(
            400, "E5004", "에스크로를 찾을 수 없습니다.", payload.Header
        )

    # 목은 상태를 저장하지 않으므로 예치 대기와 완료를 각각 고정 사례로 제공한다.
    if payload.escrowId in DEPOSIT_FIXTURE_STATUSES:
        return InquireEscrowResponse(
            Header=_escrow_response_header(
                "H0000", "정상처리 되었습니다.", payload.Header
            ),
            REC=InquireEscrowRecord(
                escrowId=payload.escrowId,
                contractId=contract_id,
                status="DEPOSIT_PENDING",
                depositedAmount=0,
                releasedAmount=0,
                feeAmount=0,
                refundedAmount=0,
                balance=0,
                isHeld=False,
                transactions=[
                    EscrowTransactionItem(
                        transactionId=PENDING_DEPOSIT_TRANSACTION_ID,
                        transactionType="DEPOSIT",
                        amount=650000,
                        status=DEPOSIT_FIXTURE_STATUSES[payload.escrowId],
                        referenceId="INITIAL",
                    )
                ],
            ),
        )

    if payload.escrowId in REFUND_FIXTURE_STATUSES:
        state = REFUND_FIXTURE_STATUSES[payload.escrowId]
        completed = state == 'COMPLETED'
        return InquireEscrowResponse(
            Header=_escrow_response_header('H0000', '정상처리 되었습니다.', payload.Header),
            REC=InquireEscrowRecord(
                escrowId=payload.escrowId, contractId=contract_id,
                status='COMPLETED' if completed else 'ACTIVE', depositedAmount=650000,
                releasedAmount=400000, feeAmount=4000, refundedAmount=250000 if completed else 0,
                balance=0 if completed else 250000, isHeld=False,
                transactions=[EscrowTransactionItem(transactionId=payload.escrowId*100+i,
                    transactionType=kind, amount=amount, status=tx_status, referenceId=ref)
                    for i, kind, amount, tx_status, ref in [
                        (1,'DEPOSIT',650000,'COMPLETED','INITIAL'),
                        (2,'RELEASE',396000,'COMPLETED',SETTLEMENT_FIXTURE_REFERENCE_ID),
                        (3,'FEE',4000,'COMPLETED',SETTLEMENT_FIXTURE_REFERENCE_ID),
                        (4,'REFUND',250000,state,SETTLEMENT_FIXTURE_REFERENCE_ID)]],
            ),
        )

    if payload.escrowId in SETTLEMENT_FIXTURE_STATUSES:
        fixture_status = SETTLEMENT_FIXTURE_STATUSES[payload.escrowId]
        paid = fixture_status != "FAILED"
        transactions = [
            EscrowTransactionItem(
                transactionId=payload.escrowId * 100 + 1,
                transactionType="DEPOSIT",
                amount=650000,
                status="COMPLETED",
                referenceId="INITIAL",
            ),
            EscrowTransactionItem(
                transactionId=payload.escrowId * 100 + 2,
                transactionType="RELEASE",
                amount=SETTLEMENT_FIXTURE_NET_AMOUNT,
                status="COMPLETED" if paid else "FAILED",
                referenceId=SETTLEMENT_FIXTURE_REFERENCE_ID,
            ),
        ]
        if paid:
            transactions.append(
                EscrowTransactionItem(
                    transactionId=payload.escrowId * 100 + 3,
                    transactionType="FEE",
                    amount=SETTLEMENT_FIXTURE_FEE_AMOUNT,
                    status=fixture_status,
                    referenceId=SETTLEMENT_FIXTURE_REFERENCE_ID,
                )
            )
        released_amount = SETTLEMENT_FIXTURE_NET_AMOUNT if paid else 0
        return InquireEscrowResponse(
            Header=_escrow_response_header(
                "H0000", "정상처리 되었습니다.", payload.Header
            ),
            REC=InquireEscrowRecord(
                escrowId=payload.escrowId,
                contractId=contract_id,
                status="ACTIVE",
                depositedAmount=650000,
                releasedAmount=released_amount,
                feeAmount=0,
                refundedAmount=0,
                balance=650000 - released_amount,
                isHeld=False,
                transactions=transactions,
            ),
        )

    return InquireEscrowResponse(
        Header=_escrow_response_header(
            "H0000", "정상처리 되었습니다.", payload.Header
        ),
        REC=InquireEscrowRecord(
            escrowId=payload.escrowId,
            contractId=contract_id,
            status="ACTIVE",
            depositedAmount=650000,
            releasedAmount=0,
            feeAmount=0,
            refundedAmount=0,
            balance=650000,
            isHeld=payload.escrowId == HELD_ESCROW_ID,
            transactions=[
                EscrowTransactionItem(
                    transactionId=1001,
                    transactionType="DEPOSIT",
                    amount=650000,
                    status="COMPLETED",
                    referenceId="INITIAL",
                ),
            ],
        ),
    )


@router.post(
    "/v1/escrow/inquireEscrowList",
    response_model=InquireEscrowListResponse,
    tags=["에스크로 조회"],
    responses={
        200: _ok(
            "인증된 서비스의 에스크로 목록을 돌려준다",
            "inquireEscrowList",
            {
                "totalCount": 2,
                "list": [
                    {
                        "escrowId": ESCROW_ID,
                        "contractId": "CONTRACT-999",
                        "status": "ACTIVE",
                        "balance": 650000,
                        "isHeld": False,
                    },
                    {
                        "escrowId": DEPOSIT_PENDING_ESCROW_ID,
                        "contractId": PENDING_CONTRACT_ID,
                        "status": "DEPOSIT_PENDING",
                        "balance": 0,
                        "isHeld": False,
                    },
                ],
            },
        ),
        **ESCROW_ERRORS,
    },
    summary="에스크로 목록 조회",
    description=(
        "인증된 서비스의 에스크로만 골라 요약으로 돌려준다. 다른 서비스의 에스크로는 "
        "섞이지 않는다.\n\n"
        "상세한 거래내역은 단건 조회에서 본다. 읽기만 하므로 `idempotencyKey`를 쓰지 "
        "않는다. ACTIVE인 계약도 지급 거래가 PROCESSING·UNKNOWN이면 새 지급·환불이 "
        "차단된다. (R-PAY-12)\n\n"
        "- **200 / H0000**: 조회했을 때. 조건에 맞는 건이 없으면 `totalCount`가 0이고 "
        "`list`가 빈 배열이다\n"
        "- **400 / E5001**: `clientApiKey`가 유효하지 않을 때\n"
        "- **400 / Q1001**: 속성 형식이 맞지 않을 때\n"
        "- **500 / Q1000**: `clientApiKey`가 `server-error-client-api-key`일 때. "
        "분류되지 않은 서버 오류다"
    ),
)
async def inquire_escrow_list(
    payload: Annotated[
        InquireEscrowListRequest,
        Body(
            examples=[
                {
                    "Header": {
                        k: v
                        for k, v in _escrow_header_example("inquireEscrowList").items()
                        if k != "idempotencyKey"
                    },
                    "contractIds": ["CONTRACT-999"],
                    "status": "ACTIVE",
                }
            ]
        ),
    ],
) -> InquireEscrowListResponse | JSONResponse:
    error = _validate_escrow_header(payload.Header, "inquireEscrowList")
    if error:
        return _escrow_error(*error, payload.Header)

    rows = [
        EscrowSummaryItem(
            escrowId=ESCROW_ID,
            contractId="CONTRACT-999",
            status="ACTIVE",
            balance=650000,
            isHeld=False,
        ),
        EscrowSummaryItem(
            escrowId=DEPOSIT_PENDING_ESCROW_ID,
            contractId=PENDING_CONTRACT_ID,
            status="DEPOSIT_PENDING",
            balance=0,
            isHeld=False,
        ),
    ]
    if payload.contractIds is not None:
        rows = [
            EscrowSummaryItem(
                escrowId=_mock_escrow_id(contract_id),
                contractId=contract_id,
                status=("DEPOSIT_PENDING" if _mock_escrow_id(contract_id) in DEPOSIT_FIXTURE_STATUSES else
                        "COMPLETED" if _mock_escrow_id(contract_id) == 46 else "ACTIVE"),
                balance=_mock_escrow_balance(_mock_escrow_id(contract_id)),
                isHeld=_mock_escrow_id(contract_id) == HELD_ESCROW_ID,
            )
            for contract_id in dict.fromkeys(payload.contractIds)
            if contract_id
        ]
    if payload.status is not None:
        rows = [r for r in rows if r.status == payload.status]

    return InquireEscrowListResponse(
        Header=_escrow_response_header(
            "H0000", "정상처리 되었습니다.", payload.Header
        ),
        REC=InquireEscrowListRecord(totalCount=len(rows), list=rows),
    )


# --------------------------------------------------------------------------- #
# 정산 대사
# --------------------------------------------------------------------------- #


class InquireReconciliationRequest(BaseModel):
    """정산 대사 조회 요청. 운영 권한으로 보관계좌 전체를 본다."""

    Header: EscrowRequestHeader = Field(
        description="JSON Body 안에 담는 공통 요청 Header."
    )
    days: int = Field(
        default=7,
        description=(
            "지난 며칠치를 함께 볼지. 오늘을 1일째로 세며 1 이상 30 이하다. "
            "비우면 7일이다."
        ),
        examples=[7],
        ge=1,
        le=30,
    )


class ReconciliationHistoryItem(BaseModel):
    """하루치 대사 결과."""

    asOf: str = Field(
        description="이 시점까지 확정된 거래로 맞춰 봤다.",
        examples=["2026-09-09T09:00:00+09:00"],
    )
    ledgerTotal: int = Field(
        description=(
            "그 시점까지 모든 이용 서비스의 확정 거래로 계산한 보관 총액. "
            "ERD escrow_transactions의 completed_at 기준이다."
        ),
        examples=[1000000],
    )
    bankBalance: int = Field(
        description=(
            "그 시점의 보관 주체 계좌 잔액. ERD에는 없으며 SSAFY 금융망 거래내역 "
            "조회(inquireTransactionHistoryList)의 거래 후 잔액에 대응하는 필드다."
        ),
        examples=[1000000],
    )
    differenceAmount: int = Field(
        description="bankBalance에서 ledgerTotal을 뺀 값.",
        examples=[0],
    )
    result: Literal["MATCHED", "BANK_MISMATCH"] = Field(
        description="그날의 판정.",
        examples=["MATCHED"],
    )


class ReconciliationRecord(BaseModel):
    """대사 결과. 원장이 말하는 보관액과 은행이 말하는 잔액을 맞춰 본 것이다."""

    ledgerTotal: int = Field(
        description=(
            "원장 기준으로 지금 보관 중인 총액. ERD escrows에 속한 "
            "escrow_transactions의 예치액에서 작업자 순지급액·플랫폼 수수료·환불액을 "
            "뺀 값을 보관계좌를 공유하는 모든 이용 서비스의 에스크로에 대해 더한 것이다."
        ),
        examples=[1000000],
    )
    bankBalance: int = Field(
        description=(
            "보관 주체 계좌에 실제로 남아 있는 잔액. ERD에는 없으며 SSAFY 금융망 "
            "계좌 잔액 조회(inquireDemandDepositAccountBalance) 응답에 대응하는 필드다."
        ),
        examples=[1000000],
    )
    differenceAmount: int = Field(
        description=(
            "bankBalance에서 ledgerTotal을 뺀 값. 0이면 맞은 것이고, 음수면 은행 잔액이 "
            "원장보다 적다는 뜻이다."
        ),
        examples=[0],
    )
    result: Literal["MATCHED", "BANK_MISMATCH"] = Field(
        description=(
            "대사 판정. differenceAmount가 0이면 MATCHED, 아니면 BANK_MISMATCH다."
        ),
        examples=["MATCHED"],
    )
    escrowCount: int = Field(
        description=(
            "모든 이용 서비스에서 ledgerTotal에 잡힌 에스크로 수. 잔액이 남아 있는 "
            "것만 센다. ERD escrows의 건수다."
        ),
        examples=[2],
    )
    history: List[ReconciliationHistoryItem] = Field(
        description=(
            "요청한 일수만큼의 하루치 결과. 최근 것이 먼저이고 첫 항목은 지금 결과와 "
            "같다."
        ),
    )


class InquireReconciliationResponse(BaseModel):
    Header: CommonResponseHeader = Field(description="공통 성공 응답 Header.")
    REC: ReconciliationRecord = Field(description="대사 결과.")


# 대사 기준일. 목은 상태를 두지 않으므로 오늘 날짜가 아니라 명세 예시일을 쓴다.
RECONCILIATION_BASE_DATE = datetime(2026, 9, 9, 9, 0, 0)
RECONCILIATION_KOMIT_BALANCE = 650000
RECONCILIATION_OTHER_SERVICE_BALANCE = 350000
RECONCILIATION_ESCROW_COUNT = 2

# 은행 잔액이 어긋난 날. 며칠 전인지로 적는다.
RECONCILIATION_MISMATCH_OFFSET = 3


def _reconciliation_day(offset: int) -> ReconciliationHistoryItem:
    """며칠 전 대사 한 줄. 지나간 날은 값이 바뀌지 않으므로 offset만으로 정해진다."""
    as_of = RECONCILIATION_BASE_DATE - timedelta(days=offset)
    ledger_total = (
        RECONCILIATION_KOMIT_BALANCE + RECONCILIATION_OTHER_SERVICE_BALANCE
    )
    bank_balance = (
        ledger_total - 50000
        if offset == RECONCILIATION_MISMATCH_OFFSET
        else ledger_total
    )
    difference = bank_balance - ledger_total
    return ReconciliationHistoryItem(
        asOf=as_of.strftime("%Y-%m-%dT%H:%M:%S+09:00"),
        ledgerTotal=ledger_total,
        bankBalance=bank_balance,
        differenceAmount=difference,
        result="MATCHED" if difference == 0 else "BANK_MISMATCH",
    )


@router.post(
    "/v1/escrow/inquireReconciliation",
    response_model=InquireReconciliationResponse,
    tags=["에스크로 조회"],
    responses={
        200: _ok(
            "원장 합계와 은행 잔액을 맞춰 본 결과를 돌려준다",
            "inquireReconciliation",
            {
                "ledgerTotal": 1000000,
                "bankBalance": 1000000,
                "differenceAmount": 0,
                "result": "MATCHED",
                "escrowCount": 2,
                "history": [
                    {
                        "asOf": "2026-09-09T09:00:00+09:00",
                        "ledgerTotal": 1000000,
                        "bankBalance": 1000000,
                        "differenceAmount": 0,
                        "result": "MATCHED",
                    },
                    {
                        "asOf": "2026-09-06T09:00:00+09:00",
                        "ledgerTotal": 1000000,
                        "bankBalance": 950000,
                        "differenceAmount": -50000,
                        "result": "BANK_MISMATCH",
                    },
                ],
            },
        ),
        **INQUIRE_ERRORS,
    },
    summary="정산 대사 조회",
    description=(
        "에스크로 운영 전용 API다. 보관계좌를 공유하는 모든 이용 서비스의 원장 "
        "보관액과 해당 계좌의 금융망 잔액을 비교한다. Header.clientApiKey에는 "
        "일반 서비스 키 대신 운영 전용 키를 넣는다. 목 운영 키는 "
        "`mock-operator-client-api-key`다.\n\n"
        "보관총액은 완료된 DEPOSIT에서 RELEASE, FEE, REFUND를 뺀 값이다. "
        "목 예시는 Komit 650,000원과 다른 서비스 350,000원을 합산한다. "
        "개별 서비스의 계약 조회에는 다른 서비스의 계약이 나타나지 않는다. "
        "읽기만 하므로 `idempotencyKey`를 쓰지 않는다. (`R-PAY-13`)\n\n"
        "대사 기준 시각은 응답 Header의 `transmissionDate`와 `transmissionTime`이다.\n\n"
        "**지금 결과와 함께 `days`만큼의 하루치 이력을 돌려준다.** `history`는 최근 것이 "
        "먼저이고 첫 항목이 지금 결과와 같다. `days`를 비우면 7일이다.\n\n"
        "- **200 / H0000**: 대사했을 때. 원장에 보관액이 없어도 은행 잔액이 "
        "남아 있으면 `BANK_MISMATCH`다\n"
        "- **400 / E5001**: 운영 전용 키가 아닐 때\n"
        "- **400 / Q1001**: 필수 속성이 없거나 형식이 맞지 않을 때\n"
        "- **500 / Q1000**: `clientApiKey`가 `server-error-client-api-key`일 때. "
        "분류되지 않은 서버 오류다"
    ),
)
async def inquire_reconciliation(
    payload: Annotated[
        InquireReconciliationRequest,
        Body(
            examples=[
                {
                    "Header": {
                        **{
                            k: v
                            for k, v in _escrow_header_example(
                                "inquireReconciliation"
                            ).items()
                            if k != "idempotencyKey"
                        },
                        "clientApiKey": MOCK_OPERATOR_CLIENT_API_KEY,
                    },
                    "days": 7,
                }
            ]
        ),
    ],
) -> InquireReconciliationResponse | JSONResponse:
    error = _validate_escrow_header(payload.Header, "inquireReconciliation")
    if error:
        return _escrow_error(*error, payload.Header)
    if payload.Header.clientApiKey != MOCK_OPERATOR_CLIENT_API_KEY:
        return _escrow_error(400, "E5001", "운영 전용 키가 필요합니다.", payload.Header)

    history = [_reconciliation_day(offset) for offset in range(payload.days)]
    today = history[0]

    return InquireReconciliationResponse(
        Header=_escrow_response_header(
            "H0000", "정상처리 되었습니다.", payload.Header
        ),
        REC=ReconciliationRecord(
            ledgerTotal=today.ledgerTotal,
            bankBalance=today.bankBalance,
            differenceAmount=today.differenceAmount,
            result=today.result,
            escrowCount=RECONCILIATION_ESCROW_COUNT,
            history=history,
        ),
    )
