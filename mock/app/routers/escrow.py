"""escrow — 에스크로 목 앱의 기준 API.

에스크로 앱은 /escrow 에 마운트된다 (app/main.py).
응답 형식·상태코드·description 작성 방식을 이 API에 맞춘다.
"""

from datetime import datetime
import re
from typing import Annotated, Literal

from fastapi import APIRouter, Body, Request
from fastapi.encoders import jsonable_encoder
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

router = APIRouter(prefix="/api", tags=["default"])


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
    except ValueError:
        return "H1002", "전송일자 형식이 유효하지 않습니다."

    try:
        datetime.strptime(header.transmissionTime, "%H%M%S")
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


@router.post(
    "/default",
    response_model=DefaultResponse,
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
