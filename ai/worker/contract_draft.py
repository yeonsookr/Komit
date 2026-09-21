"""무료 상담 채팅 한 건을 계약 유형별 초안으로 정리한다. (`docs/ai/chat-contract-draft.md`)

AI는 개발자가 검토할 초안 필드만 만든다. 일정·금액·인계물 등 개발자 입력 필드와
계약 제출·서명은 이 모듈이 다루지 않는다.
"""

from __future__ import annotations

import json
import sys
from datetime import datetime
from typing import Any, Iterable, Sequence

from qa_suggestion import (
    MAX_TEST_CASE_LENGTH,
    ChatClient,
    GmsChatClient,
    GmsUnavailableError,
    InvalidGmsResponseError,
)


CONSULTING = "CONSULTING"
DEVELOPMENT = "DEVELOPMENT"
TARGET_TYPES = (CONSULTING, DEVELOPMENT)
AUTHOR_ROLES = ("CLIENT", "DEVELOPER")
MESSAGE_FIELDS = {"chatRefId", "authorRole", "createdAt", "content"}
# 목 WriteConsultingContractRequest.topic 제약.
MAX_TOPIC_LENGTH = 255
# 목 WriteDevelopmentContractRequest: milestones와 qaCriteria는 하나 이상.
MIN_MILESTONE_COUNT = 1
MIN_QA_CRITERIA_COUNT = 1
# AI가 채우는 필드만. 나머지는 개발자 입력 필드다.
DRAFT_FIELDS = {
    CONSULTING: {"topic", "detail"},
    DEVELOPMENT: {"issueIds", "milestones", "rejectedIssues"},
}


def _require_text(value: Any, message: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise InvalidGmsResponseError(message)
    return value


def _require_known_id(value: Any, known: frozenset[int], message: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value not in known:
        raise InvalidGmsResponseError(message)
    return value


def normalize_issues(issues: Iterable[dict[str, Any]]) -> list[dict[str, Any]]:
    """기존 요구사항 목록을 확인한다. 초안은 이 안에서만 식별자를 고른다."""
    rows: list[dict[str, Any]] = []
    seen: set[int] = set()
    for issue in issues:
        if not isinstance(issue, dict) or set(issue) != {"issueId", "title"}:
            raise ValueError("요구사항은 issueId와 title만 가진다")
        issue_id = issue["issueId"]
        if isinstance(issue_id, bool) or not isinstance(issue_id, int):
            raise ValueError("issueId는 정수여야 합니다")
        if issue_id in seen:
            raise ValueError("요구사항 식별자가 중복됩니다")
        if not isinstance(issue["title"], str) or not issue["title"].strip():
            raise ValueError("요구사항 제목이 비어 있습니다")
        seen.add(issue_id)
        rows.append({"issueId": issue_id, "title": issue["title"]})
    return rows


def normalize_messages(messages: Sequence[dict[str, Any]]) -> list[dict[str, str]]:
    """한 상담방의 메시지인지, 작성 시각 순인지 확인한다."""
    if not isinstance(messages, Sequence) or isinstance(messages, str) or not messages:
        raise ValueError("무료 상담 메시지가 필요합니다")
    rows: list[dict[str, str]] = []
    chat_ref_id: str | None = None
    previous: datetime | None = None
    for message in messages:
        if not isinstance(message, dict) or set(message) != MESSAGE_FIELDS:
            raise ValueError("메시지 필드가 계약과 다릅니다")
        if not isinstance(message["chatRefId"], str) or not message["chatRefId"].strip():
            raise ValueError("chatRefId가 비어 있습니다")
        if chat_ref_id is None:
            chat_ref_id = message["chatRefId"]
        elif message["chatRefId"] != chat_ref_id:
            raise ValueError("서로 다른 상담방의 메시지를 섞을 수 없습니다")
        if message["authorRole"] not in AUTHOR_ROLES:
            raise ValueError("authorRole은 CLIENT 또는 DEVELOPER입니다")
        if not isinstance(message["createdAt"], str):
            raise ValueError("createdAt이 없습니다")
        try:
            created_at = datetime.fromisoformat(message["createdAt"])
        except ValueError as exc:
            raise ValueError("createdAt 형식이 올바르지 않습니다") from exc
        if previous is not None and created_at < previous:
            raise ValueError("메시지가 작성 시각 순이 아닙니다")
        previous = created_at
        if not isinstance(message["content"], str) or not message["content"].strip():
            raise ValueError("메시지 본문이 비어 있습니다")
        rows.append({"authorRole": message["authorRole"], "content": message["content"]})
    return rows


def _parse_consulting(draft: dict[str, Any]) -> dict[str, Any]:
    topic = _require_text(draft["topic"], "topic이 비어 있습니다")
    if len(topic) > MAX_TOPIC_LENGTH:
        raise InvalidGmsResponseError("topic 길이가 올바르지 않습니다")
    detail = draft["detail"]
    if detail is not None:
        detail = _require_text(detail, "detail은 null이거나 내용이 있어야 합니다")
    return {"topic": topic, "detail": detail}


def _parse_milestones(rows: Any) -> list[dict[str, Any]]:
    if not isinstance(rows, list) or len(rows) < MIN_MILESTONE_COUNT:
        raise InvalidGmsResponseError("작업은 하나 이상이어야 합니다")
    milestones: list[dict[str, Any]] = []
    for row in rows:
        if not isinstance(row, dict) or set(row) != {"title", "qaCriteria"}:
            raise InvalidGmsResponseError("작업 필드는 title과 qaCriteria만 허용합니다")
        criteria = row["qaCriteria"]
        if not isinstance(criteria, list) or len(criteria) < MIN_QA_CRITERIA_COUNT:
            raise InvalidGmsResponseError("확인 문장은 하나 이상이어야 합니다")
        seen: set[str] = set()
        for sentence in criteria:
            # R-REQ-04: 확인 문장은 1~500자.
            _require_text(sentence, "확인 문장이 비어 있습니다")
            if len(sentence) > MAX_TEST_CASE_LENGTH:
                raise InvalidGmsResponseError("확인 문장 길이가 올바르지 않습니다")
            normalized = " ".join(sentence.split())
            if normalized in seen:
                raise InvalidGmsResponseError("한 작업에 같은 확인 문장이 있습니다")
            seen.add(normalized)
        milestones.append(
            {
                "title": _require_text(row["title"], "작업 이름이 비어 있습니다"),
                "qaCriteria": list(criteria),
            }
        )
    return milestones


def _parse_development(draft: dict[str, Any], known: frozenset[int]) -> dict[str, Any]:
    issue_ids = draft["issueIds"]
    if not isinstance(issue_ids, list):
        raise InvalidGmsResponseError("issueIds 형식이 올바르지 않습니다")
    taken: list[int] = []
    for issue_id in issue_ids:
        value = _require_known_id(issue_id, known, "입력에 없는 요구사항 식별자입니다")
        if value in taken:
            raise InvalidGmsResponseError("요구사항 식별자가 중복됩니다")
        taken.append(value)
    rejected_rows = draft["rejectedIssues"]
    if not isinstance(rejected_rows, list):
        raise InvalidGmsResponseError("rejectedIssues 형식이 올바르지 않습니다")
    rejected: list[dict[str, Any]] = []
    for row in rejected_rows:
        if not isinstance(row, dict) or set(row) != {"issueId", "reason"}:
            raise InvalidGmsResponseError("제외 항목 필드는 issueId와 reason만 허용합니다")
        value = _require_known_id(row["issueId"], known, "입력에 없는 요구사항 식별자입니다")
        if value in taken or any(other["issueId"] == value for other in rejected):
            raise InvalidGmsResponseError("같은 요구사항을 맡으면서 제외할 수 없습니다")
        rejected.append(
            {
                "issueId": value,
                "reason": _require_text(row["reason"], "제외 사유가 비어 있습니다"),
            }
        )
    return {
        "issueIds": taken,
        "milestones": _parse_milestones(draft["milestones"]),
        "rejectedIssues": rejected,
    }


def parse_draft(
    raw: str,
    target_type: str,
    known_issue_ids: Iterable[int] = (),
) -> dict[str, Any] | None:
    """모델 출력을 계약 유형의 초안 모양으로 검증한다. 진행하지 않은 상담은 None이다."""
    try:
        data = json.loads(raw)
    except json.JSONDecodeError as exc:
        raise InvalidGmsResponseError("GMS 메시지가 JSON이 아닙니다") from exc
    if not isinstance(data, dict) or set(data) != {"proceed", "draft"}:
        raise InvalidGmsResponseError("GMS 메시지의 필드가 올바르지 않습니다")
    if not isinstance(data["proceed"], bool):
        raise InvalidGmsResponseError("proceed는 true 또는 false입니다")
    if not data["proceed"]:
        if data["draft"] is not None:
            raise InvalidGmsResponseError("진행하지 않은 상담에는 초안이 없습니다")
        return None
    draft = data["draft"]
    if not isinstance(draft, dict) or set(draft) != DRAFT_FIELDS[target_type]:
        raise InvalidGmsResponseError("초안 필드가 계약 유형과 다릅니다")
    if target_type == CONSULTING:
        return _parse_consulting(draft)
    return _parse_development(draft, frozenset(known_issue_ids))


CONSULTING_INSTRUCTION = (
    "컨설팅 계약 초안을 만듭니다. draft는 topic과 detail만 가집니다. "
    "topic은 이 세션에서 다룰 것을 한 줄로 255자 안에 적고, detail은 합의한 상담 내용의 "
    "상세 설명입니다. 근거가 없으면 detail을 null로 두세요."
)
DEVELOPMENT_INSTRUCTION = (
    "개발 계약 초안을 만듭니다. draft는 issueIds, milestones, rejectedIssues만 가집니다. "
    "issueIds와 rejectedIssues[].issueId는 아래 요구사항 목록의 식별자 중에서만 고르고, "
    "같은 식별자를 양쪽에 함께 넣지 마세요. 제외 사유가 대화에 없으면 그 요구사항을 "
    "rejectedIssues에 넣지 마세요. milestones는 하나 이상이며 각 항목은 title과 "
    "qaCriteria만 가집니다. qaCriteria는 의뢰인이 직접 실행해 통과 여부를 판단할 수 있는 "
    f"확인 문장을 하나 이상 담고 각 문장은 1~{MAX_TEST_CASE_LENGTH}자입니다."
)


def build_messages(
    target_type: str,
    messages: list[dict[str, str]],
    issues: list[dict[str, Any]],
) -> list[dict[str, str]]:
    instruction = (
        CONSULTING_INSTRUCTION if target_type == CONSULTING else DEVELOPMENT_INSTRUCTION
    )
    transcript = "\n".join(f"{row['authorRole']}: {row['content']}" for row in messages)
    user_content = transcript
    if target_type == DEVELOPMENT:
        listing = "\n".join(f"{row['issueId']}: {row['title']}" for row in issues)
        user_content = f"[기존 요구사항]\n{listing or '(없음)'}\n\n[상담 대화]\n{transcript}"
    return [
        {
            "role": "system",
            "content": (
                "무료 상담 대화를 읽고 개발자가 검토할 계약서 초안을 한국어로 정리하세요. "
                f"{instruction} "
                "대화에 없는 합의·작업·제외 사유·금액·일정을 지어내지 마세요. "
                "일정, 기간, 금액, 인계물은 개발자가 직접 입력하므로 초안에 넣지 마세요. "
                "진행하지 않기로 끝난 상담이면 proceed를 false로 두고 draft를 null로 두세요. "
                '설명이나 코드 블록 없이 JSON 객체 {"proceed":true,"draft":{...}}만 '
                "반환하세요."
            ),
        },
        {"role": "user", "content": user_content},
    ]


def draft_contract(
    target_type: str,
    messages: Sequence[dict[str, Any]],
    client: ChatClient,
    issues: Iterable[dict[str, Any]] = (),
) -> dict[str, Any] | None:
    """전환에 성공한 상담방 하나의 메시지로 계약 유형별 초안을 만든다. (`R-MATCH-03`)"""
    if target_type not in TARGET_TYPES:
        raise ValueError("계약 유형은 CONSULTING 또는 DEVELOPMENT입니다")
    rows = normalize_messages(messages)
    known = normalize_issues(issues)
    raw = client.complete(build_messages(target_type, rows, known))
    return parse_draft(raw, target_type, (row["issueId"] for row in known))


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    if len(sys.argv) != 2:
        raise SystemExit("사용법: contract_draft.py <입력 JSON 경로>")
    with open(sys.argv[1], encoding="utf-8") as handle:
        payload = json.load(handle)
    try:
        result = draft_contract(
            payload["targetType"],
            payload["messages"],
            GmsChatClient(),
            payload.get("issues", ()),
        )
    except (GmsUnavailableError, InvalidGmsResponseError, ValueError) as error:
        raise SystemExit(str(error)) from None
    print(json.dumps(result, ensure_ascii=False, indent=2))
