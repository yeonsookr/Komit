"""R-REQ-04: 요구사항 한 건의 저장 전 확인 문장 후보를 생성한다."""

from __future__ import annotations

import json
import os
import sys
from pathlib import Path
from typing import Protocol
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


DEFAULT_BASE_URL = "https://gms.ssafy.io/gmsapi/api.openai.com/v1"
DEFAULT_MODEL = "gpt-4.1-nano"
# R-REQ-04: 후보 3건, 각 문장 1~500자.
CANDIDATE_COUNT = 3
MAX_TEST_CASE_LENGTH = 500
# 계약이 허용하는 최대 출력은 3건 × 500자 = 1,500자다. 실측 0.5토큰/자(한국어 432자가
# 218토큰)로 약 750토큰이고 JSON 구조를 더해 여유를 둔다. 기본값 256으로는 긴 요구사항의
# 응답이 문장 중간에서 잘려 8회 중 6회가 실패했다. (2026-09-24 실측)
QA_MAX_OUTPUT_TOKENS = 1024
ENV_FILE = Path(__file__).with_name(".env")


class GmsUnavailableError(Exception):
    """GMS 접속 또는 인증 실패."""


class InvalidGmsResponseError(Exception):
    """GMS 출력이 확인 문장 계약에 맞지 않음."""


class UnusableInputError(Exception):
    """받은 입력을 쓸 수 없음. 깨진 오디오처럼 다시 걸어도 소용없는 실패다.

    위 둘과 갈리는 기준은 다시 시도하면 되는가다. 이것은 안 된다 — 사람이 봐야 한다.
    """


class ChatClient(Protocol):
    def complete(self, messages: list[dict[str, str]], max_tokens: int) -> str: ...


def read_local_env(path: Path = ENV_FILE) -> dict[str, str]:
    """로컬 .env의 단순 KEY=VALUE 항목을 읽되 환경변수를 우선한다."""
    values: dict[str, str] = {}
    if path.exists():
        for line in path.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            key, separator, value = line.partition("=")
            if not separator or not key.strip():
                raise ValueError("잘못된 .env 형식")
            values[key.strip()] = value.strip().strip('"').strip("'")
    return {**values, **os.environ}


class GmsChatClient:
    def __init__(self, config: dict[str, str] | None = None) -> None:
        config = config if config is not None else read_local_env()
        self._key = config.get("GMS_API_KEY", "")
        if not self._key:
            raise GmsUnavailableError("GMS_API_KEY가 설정되지 않았습니다")
        self._base_url = config.get("GMS_API_BASE_URL", DEFAULT_BASE_URL).rstrip("/")
        self._model = config.get("GMS_MODEL", DEFAULT_MODEL)
        self.last_usage: dict[str, int] | None = None

    def complete(self, messages: list[dict[str, str]], max_tokens: int) -> str:
        """작업마다 출력 크기가 다르므로 한도를 호출에서 **반드시** 받는다.

        확인 문장 세 건과 네 배열 요약은 필요한 길이가 다르다. 한 값으로 묶으면 한쪽이
        잘린다. 예전에는 안 주면 설정에 적힌 기본값을 쓰게 두었는데, 두 작업이 그것을 잊고
        256 으로 불려 응답이 문장 중간에서 끊겼다. 잊을 수 있는 자리를 없애려고 기본값을
        두지 않는다. (`ai/AGENTS.md` — 한도는 호출에서 정한다)
        """
        limit = max_tokens
        if limit < 1:
            raise ValueError("max_tokens는 양수여야 합니다")
        payload = json.dumps(
            {"model": self._model, "messages": messages, "max_tokens": limit},
            ensure_ascii=False,
        ).encode("utf-8")
        request = Request(
            f"{self._base_url}/chat/completions",
            data=payload,
            headers={
                "Authorization": f"Bearer {self._key}",
                "Content-Type": "application/json",
            },
            method="POST",
        )
        try:
            with urlopen(request, timeout=20) as response:
                body = json.load(response)
        except (HTTPError, URLError, TimeoutError, OSError) as exc:
            raise GmsUnavailableError("GMS 호출에 실패했습니다") from exc
        except (ValueError, UnicodeError) as exc:
            raise InvalidGmsResponseError("GMS 응답을 읽을 수 없습니다") from exc
        try:
            choice = body["choices"][0]
            content = choice["message"]["content"]
        except (KeyError, IndexError, TypeError) as exc:
            raise InvalidGmsResponseError("GMS 응답에 메시지가 없습니다") from exc
        # 한도에 걸려 끊긴 응답은 JSON 이 반만 와서 파싱 실패로만 보인다. 원인은 모델이 아니라
        # 우리가 건 한도이므로 여기서 갈라 둔다. 안 그러면 한도를 의심하기까지 오래 걸린다.
        if isinstance(choice, dict) and choice.get("finish_reason") == "length":
            raise InvalidGmsResponseError(f"출력 한도({limit}토큰)에 걸려 응답이 잘렸습니다")
        if not isinstance(content, str):
            raise InvalidGmsResponseError("GMS 메시지가 문자열이 아닙니다")
        usage = body.get("usage") if isinstance(body, dict) else None
        if isinstance(usage, dict):
            self.last_usage = {
                key: value for key, value in usage.items()
                if key in {"prompt_tokens", "completion_tokens", "total_tokens"}
                and isinstance(value, int)
            }
        return content


def parse_suggestions(raw: str) -> list[dict[str, str]]:
    try:
        data = json.loads(raw)
    except json.JSONDecodeError as exc:
        raise InvalidGmsResponseError("GMS 메시지가 JSON이 아닙니다") from exc
    if not isinstance(data, dict) or set(data) != {"content"}:
        raise InvalidGmsResponseError("GMS 메시지의 필드가 올바르지 않습니다")
    rows = data["content"]
    if not isinstance(rows, list) or len(rows) != CANDIDATE_COUNT:
        raise InvalidGmsResponseError("확인 문장 후보는 정확히 3건이어야 합니다")
    seen: set[str] = set()
    for row in rows:
        if not isinstance(row, dict) or set(row) != {"testCase"}:
            raise InvalidGmsResponseError("후보 필드는 testCase만 허용합니다")
        sentence = row["testCase"]
        if not isinstance(sentence, str) or not 1 <= len(sentence) <= MAX_TEST_CASE_LENGTH or not sentence.strip():
            raise InvalidGmsResponseError("확인 문장 길이가 올바르지 않습니다")
        normalized = " ".join(sentence.split())
        if normalized in seen:
            raise InvalidGmsResponseError("중복된 확인 문장이 있습니다")
        seen.add(normalized)
    return rows


def suggest(requirement: str, client: ChatClient) -> list[dict[str, str]]:
    if not isinstance(requirement, str) or not requirement.strip():
        raise ValueError("요구사항 문장 한 건이 필요합니다")
    messages = [
        {
            "role": "system",
            "content": (
                "요구사항 한 건에 대해 의뢰인이 직접 실행하고 통과 여부를 판단할 수 있는 "
                "서로 다른 확인 문장 3개를 한국어로 제안하세요. 비개발자도 이해할 말로 쓰고, "
                "각 문장에 사용자 상태, 시도할 동작, 확인할 결과를 구체적으로 쓰세요. "
                "후보 간 사용자 상태나 접근 경로가 다르면 그 차이를 명시하고, "
                "같은 상황과 결과를 표현만 바꿔 반복하지 마세요. "
                "입력에 없는 화면·메뉴·경로·기능이나 조건은 꾸며내지 마세요. "
                '설명이나 코드 블록 없이 JSON 객체 {"content":[{"testCase":"..."}]}만 반환하세요. '
                "각 testCase는 1~500자입니다."
            ),
        },
        {"role": "user", "content": requirement},
    ]
    return parse_suggestions(client.complete(messages, max_tokens=QA_MAX_OUTPUT_TOKENS))


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stdin.reconfigure(encoding="utf-8")
    requirement_text = input("요구사항 한 건: ")
    try:
        client = GmsChatClient()
        print(json.dumps(suggest(requirement_text, client), ensure_ascii=False, indent=2))
        if client.last_usage:
            print(json.dumps({"usage": client.last_usage}), file=sys.stderr)
    except (GmsUnavailableError, InvalidGmsResponseError, ValueError) as error:
        raise SystemExit(str(error)) from None
