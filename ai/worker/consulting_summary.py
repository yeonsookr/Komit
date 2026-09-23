"""컨설팅 전사에서 요약 네 배열과 근거 발화 후보를 만든다.

무료 상담 채팅으로 만드는 계약서 초안(`contract_draft`)과 구분한다. 이쪽은 컨설팅 세션이
끝난 뒤의 전사를 읽고 논의·결정·다음 행동·변경점을 가린다.

근거 순번의 실존 검증은 여기서 하지 않는다. `226`이 이 출력 뒤에서 결정적으로 검사한다.
"""

from __future__ import annotations

import json
import sys

from qa_suggestion import (
    ChatClient,
    GmsChatClient,
    GmsUnavailableError,
    InvalidGmsResponseError,
)

# 출력 길이가 발화 수에 비례해 확인 문장(실측 209~222)과 다르다. 발화 세 건짜리 사례는
# 151~165 토큰이었으나 90분 세션은 아직 재지 못했다 — 오디오 fixture 가 없다.
# 지금은 넉넉히 두고 실제 전사가 나오면 다시 잰다. 작업별 한도를 두는 이유다.
MAX_OUTPUT_TOKENS = 2048

SECTIONS = ("discussed", "decided", "nextActions", "changes")
ITEM_FIELDS = {"title", "detail", "evidenceUtteranceIndexes", "reviewRequired"}
CHANGE_FIELDS = ITEM_FIELDS | {"kind", "issueId"}
CHANGE_KINDS = ("UPDATE", "ADD")

NEWLINE = chr(10)

MAX_TITLE_LENGTH = 200
MAX_DETAIL_LENGTH = 1000

INSTRUCTION = (
    "컨설팅 세션 전사를 읽고 네 갈래로 정리합니다. "
    "JSON 객체 하나만 출력하고 discussed, decided, nextActions, changes 네 키만 가집니다. "
    "각 값은 배열이며 비어 있을 수 있습니다. "
    "discussed 는 이야기만 하고 끝난 것, decided 는 양쪽이 합의한 것, "
    "nextActions 는 누가 무엇을 하기로 한 것, changes 는 요청서에 반영할 후보입니다. "
    "네 배열의 각 항목은 title, detail, evidenceUtteranceIndexes, reviewRequired 를 반드시 "
    "가집니다. changes 의 항목은 그 네 개에 더해 kind 와 issueId 까지 여섯 개를 모두 가집니다. "
    "changes 라고 해서 title 이나 근거를 빼면 안 됩니다. "
    "kind 는 UPDATE 또는 ADD 입니다. "
    "아래 요구사항 목록에 있는 것을 고칠 때만 UPDATE 를 쓰고 그 줄 맨 앞의 숫자를 issueId 에 "
    "반드시 적습니다. issueId 를 적을 수 없으면 UPDATE 를 쓰지 말고 ADD 로 하고 issueId 를 "
    "null 로 둡니다. 목록이 비어 있으면 UPDATE 를 쓰지 않습니다. "
    "title 은 한 문장으로 적고 detail 은 보충이 없으면 null 로 둡니다. "
    "evidenceUtteranceIndexes 에는 그 항목을 뒷받침하는 발화의 utteranceIndex 를 적습니다. "
    "발화에 없는 합의·금액·일정을 만들지 않습니다. reviewRequired 는 항상 false 로 둡니다."
)


def issue_ids(requirements: list[dict]) -> tuple[int, ...]:
    """요구사항 목록에서 식별자만 뽑는다."""
    return tuple(row["issueId"] for row in requirements)


def build_messages(
    utterances: list[dict], requirements: list[dict] | None = None
) -> list[dict[str, str]]:
    """전사 발화와 기존 요구사항을 프롬프트로 만든다.

    요구사항은 식별자만 넘기지 않고 제목을 함께 넘긴다. 식별자만 주면 모델이 발화 속
    요구사항과 그 숫자를 이을 근거가 없어 ``UPDATE`` 를 고르고도 ``issueId`` 를 빠뜨린다.
    """
    requirements = requirements or []
    lines = []
    for row in utterances:
        lines.append(
            f"{row['utteranceIndex']} [{row['speaker']}] {row['normalizedText']}"
        )
    if requirements:
        rows = [f"  {row['issueId']}  {row['title']}" for row in requirements]
        known = NEWLINE.join(rows)
    else:
        known = "  없음"
    body = "요구사항" + NEWLINE + known + NEWLINE + NEWLINE + "발화" + NEWLINE + NEWLINE.join(lines)
    return [
        {"role": "system", "content": INSTRUCTION},
        {"role": "user", "content": body},
    ]


def _check_item(row: object, *, is_change: bool) -> None:
    allowed = CHANGE_FIELDS if is_change else ITEM_FIELDS
    if not isinstance(row, dict) or set(row) != allowed:
        raise InvalidGmsResponseError("요약 항목의 필드가 계약과 다릅니다")

    title = row["title"]
    if not isinstance(title, str) or not title.strip():
        raise InvalidGmsResponseError("title 이 비어 있습니다")
    if len(title) > MAX_TITLE_LENGTH:
        raise InvalidGmsResponseError("title 이 너무 깁니다")

    detail = row["detail"]
    if detail is not None:
        if not isinstance(detail, str) or not detail.strip():
            raise InvalidGmsResponseError("detail 이 비어 있습니다")
        if len(detail) > MAX_DETAIL_LENGTH:
            raise InvalidGmsResponseError("detail 이 너무 깁니다")

    indexes = row["evidenceUtteranceIndexes"]
    if not isinstance(indexes, list):
        raise InvalidGmsResponseError("evidenceUtteranceIndexes 가 배열이 아닙니다")
    for index in indexes:
        if not isinstance(index, int) or isinstance(index, bool) or index < 0:
            raise InvalidGmsResponseError("근거 순번이 0 이상 정수가 아닙니다")
    if len(set(indexes)) != len(indexes):
        raise InvalidGmsResponseError("근거 순번이 중복됩니다")

    if not isinstance(row["reviewRequired"], bool):
        raise InvalidGmsResponseError("reviewRequired 가 참거짓이 아닙니다")

    if not is_change:
        return

    if row["kind"] not in CHANGE_KINDS:
        raise InvalidGmsResponseError("kind 가 UPDATE 도 ADD 도 아닙니다")
    issue_id = row["issueId"]
    if row["kind"] == "UPDATE":
        if not isinstance(issue_id, int) or isinstance(issue_id, bool) or issue_id < 1:
            raise InvalidGmsResponseError("UPDATE 는 issueId 가 있어야 합니다")
    elif issue_id is not None:
        raise InvalidGmsResponseError("ADD 는 issueId 가 null 이어야 합니다")


def parse_summary(raw: str, requirements: list[dict] | None = None) -> dict:
    """모델 출력을 계약대로 검사해 돌려준다. 어긋나면 형식 위반으로 실패시킨다."""
    try:
        data = json.loads(raw)
    except (ValueError, UnicodeError) as exc:
        raise InvalidGmsResponseError("요약이 JSON 이 아닙니다") from exc
    if not isinstance(data, dict) or set(data) != set(SECTIONS):
        raise InvalidGmsResponseError("요약의 최상위 키가 계약과 다릅니다")

    known = set(issue_ids(requirements or []))
    for section in SECTIONS:
        rows = data[section]
        if not isinstance(rows, list):
            raise InvalidGmsResponseError(f"{section} 이 배열이 아닙니다")
        for row in rows:
            _check_item(row, is_change=section == "changes")
            if section == "changes" and row["kind"] == "UPDATE":
                # 입력으로 받은 요구사항 안에서만 고른다. 없는 식별자 생성 금지.
                if row["issueId"] not in known:
                    raise InvalidGmsResponseError("입력에 없는 issueId 를 가리킵니다")
    return data


def summarize(
    utterances: list[dict],
    client: ChatClient,
    requirements: list[dict] | None = None,
) -> dict:
    if not isinstance(utterances, list) or not utterances:
        raise ValueError("전사 발화가 비어 있습니다")
    messages = build_messages(utterances, requirements)
    raw = client.complete(messages, max_tokens=MAX_OUTPUT_TOKENS)
    return parse_summary(raw, requirements)


if __name__ == "__main__":
    with open(sys.argv[1], encoding="utf-8") as handle:
        payload = json.load(handle)
    try:
        result = summarize(
            payload["utterances"],
            GmsChatClient(),
            payload.get("requirements", []),
        )
    except (GmsUnavailableError, InvalidGmsResponseError) as error:
        print(f"{type(error).__name__}: {error}", file=sys.stderr)
        raise SystemExit(1)
    print(json.dumps(result, ensure_ascii=False, indent=2))
