"""Service 가 AI 작업을 부르는 HTTP 경계.

`docs/job-contract.md` 가 정한 것만 구현한다. 작업이 무엇을 만드는지는 각 모듈과 그
계약 문서에 있고, 여기서는 받아 넘기고 실패를 두 갈래로 되돌리는 일만 한다.

**부르는 쪽은 Service 하나다.** 브라우저도 FE Route Handler 도 이 서버를 직접 부르지
않는다. 권한과 소유권 확인은 Service 가 하고, 이 서버는 넘겨받은 값만 본다.

문서 경로(`/docs`·`/openapi.json`)를 열지 않는다. 클러스터 안에서만 부르는 서비스라
밖에 내보일 것이 없고, 열면 요청·응답 예시가 그대로 노출된다.
"""

from __future__ import annotations

from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException
from pydantic import BaseModel, ConfigDict

from consulting_summary import summarize
from contract_draft import draft_contract
from qa_suggestion import (
    ChatClient,
    GmsChatClient,
    GmsUnavailableError,
    InvalidGmsResponseError,
    read_local_env,
    suggest,
)

UNAVAILABLE_CODE = "AI_SERVICE_UNAVAILABLE"
INVALID_CODE = "AI_RESPONSE_INVALID"
REQUEST_CODE = "AI_REQUEST_INVALID"
UNAVAILABLE_MESSAGE = "AI 서비스에 연결하지 못했습니다."
INVALID_MESSAGE = "AI가 계약에 맞는 결과를 만들지 못했습니다."


def require_config() -> dict[str, str]:
    """키가 없으면 기동을 멈춘다.

    키 없이 떠 있으면 모든 요청이 `503` 이 되고, 배포는 성공한 것처럼 보인다. Service 는
    같은 이유로 인증 값이 없으면 Pod 가 기동하지 않는다(`application-prod.yml`).
    """
    config = read_local_env()
    if not config.get("GMS_API_KEY"):
        raise RuntimeError("GMS_API_KEY 가 없습니다. 이 값 없이는 어떤 작업도 할 수 없습니다")
    return config


@asynccontextmanager
async def lifespan(app: FastAPI):
    app.state.config = require_config()
    yield


app = FastAPI(lifespan=lifespan, docs_url=None, redoc_url=None, openapi_url=None)


def build_client() -> ChatClient:
    """요청마다 새로 만든다.

    `GmsChatClient` 는 마지막 사용량을 인스턴스에 들고 있어 동시 요청이 겹치면 서로
    덮어쓴다. 연결 풀을 쓰지 않으므로 새로 만드는 비용은 거의 없다.
    """
    return GmsChatClient(app.state.config)


class Body(BaseModel):
    """계약에 없는 필드를 받지 않는다."""

    model_config = ConfigDict(extra="forbid")


# 필드를 `dict` 로 받으면 이름이 틀려도 여기서 통과하고 모듈이 `KeyError` 로 터져 `500` 이
# 나간다. 계약은 어떤 실패도 `code`·`message` 로 돌려주기로 했으므로 경계에서 걸러야 한다.
# 계약 문서의 형식을 여기 한 벌 더 적게 되지만, 그것이 경계가 하는 일이다.


class Utterance(Body):
    """`docs/ai/transcript-evidence-contract.md` 2절. 시각은 있으면 받고 쓰지는 않는다."""

    utteranceIndex: int
    speaker: str
    rawText: str
    normalizedText: str
    startMs: int | None = None
    endMs: int | None = None


class Requirement(Body):
    """식별자만으로는 모델이 발화와 이을 근거가 없어 제목을 함께 받는다."""

    issueId: int
    title: str


class ChatMessage(Body):
    """`docs/ai/chat-contract-draft.md`. 방 식별자가 섞였는지는 모듈이 본다."""

    chatRefId: str
    authorRole: str
    createdAt: str
    content: str


class QaSuggestionRequest(Body):
    requirement: str


class ContractDraftRequest(Body):
    targetType: str
    messages: list[ChatMessage]
    issues: list[Requirement] | None = None


class ConsultingSummaryRequest(Body):
    utterances: list[Utterance]
    requirements: list[Requirement] | None = None


def dump(rows: list[Body] | None) -> list[dict]:
    """모듈은 평범한 dict 를 받는다. 값이 없는 선택 필드는 빼고 넘긴다."""
    return [row.model_dump(exclude_none=True) for row in rows or []]


def failure(status: int, code: str, message: str) -> JSONResponse:
    return JSONResponse(status_code=status, content={"code": code, "message": message})


@app.exception_handler(GmsUnavailableError)
async def unavailable(request, error: GmsUnavailableError) -> JSONResponse:
    return failure(503, UNAVAILABLE_CODE, UNAVAILABLE_MESSAGE)


@app.exception_handler(InvalidGmsResponseError)
async def invalid(request, error: InvalidGmsResponseError) -> JSONResponse:
    return failure(502, INVALID_CODE, INVALID_MESSAGE)


@app.exception_handler(RequestValidationError)
async def malformed(request, error: RequestValidationError) -> JSONResponse:
    return failure(422, REQUEST_CODE, "요청 형식이 계약과 다릅니다.")


@app.exception_handler(HTTPException)
async def refused(request, error: HTTPException) -> JSONResponse:
    """없는 경로·허용하지 않는 메서드·읽지 못한 본문도 같은 형식으로 돌려준다.

    이것들을 그대로 두면 `{"detail": ...}` 로 나가 계약의 `code`·`message` 와 형식이
    달라진다. Service 가 실패 본문을 한 가지로만 읽을 수 있게 맞춘다.

    직접 `HTTPException` 을 던지는 곳은 없다. 여기로 오는 것은 전부 프레임워크가 만든
    4xx 다.
    """
    return failure(error.status_code, REQUEST_CODE, str(error.detail))


@app.exception_handler(ValueError)
async def rejected(request, error: ValueError) -> JSONResponse:
    """모듈이 입력을 거부한 것은 AI 실패가 아니라 요청 오류다.

    Pydantic 이 막지 못하는 것들이 여기로 온다. 발화 목록이 비었거나, 방이 섞였거나,
    작성 시각 순이 아니거나, 계약 유형이 셋 중 하나가 아닌 경우다. 이것을 `502` 로
    올리면 Service 가 자기 요청이 틀린 것을 AI 고장으로 읽는다.

    사유를 그대로 싣는다. 부르는 쪽이 클러스터 안의 Service 하나뿐이라 밖으로 새지
    않고, 고칠 사람이 바로 읽을 수 있다.
    """
    return failure(422, REQUEST_CODE, str(error))


@app.get("/health")
def health() -> dict[str, str]:
    """기동·생존 확인. 모델을 부르지 않는다.

    여기서 GMS 를 부르면 상류가 잠깐 흔들릴 때 Pod 가 통째로 재시작된다. 처리 중인
    다른 요청까지 끊기므로 상류 상태는 실패 응답으로만 알린다.
    """
    return {"status": "UP"}


@app.post("/qa-suggestions")
def qa_suggestions(body: QaSuggestionRequest) -> dict:
    return {"content": suggest(body.requirement, build_client())}


@app.post("/contract-draft")
def contract_draft(body: ContractDraftRequest) -> dict:
    draft = draft_contract(
        body.targetType, dump(body.messages), build_client(), dump(body.issues)
    )
    return {"draft": draft}


@app.post("/consulting-summary")
def consulting_summary(body: ConsultingSummaryRequest) -> dict:
    return summarize(dump(body.utterances), build_client(), dump(body.requirements))
