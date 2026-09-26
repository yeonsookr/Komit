"""무료 상담 채팅 한 건을 계약 유형별 초안으로 정리한다. (`docs/ai/chat-contract-draft.md`)

AI는 개발자가 검토할 초안 필드만 만든다. 일정·금액·인계물 등 개발자 입력 필드와
계약 제출·서명은 이 모듈이 다루지 않는다.

개발 계약은 요청서와 채팅을 종합한다. 요청서의 요구사항은 서버가 계약서에 그대로
넣으므로 AI는 다시 적지 않고, 채팅에서 **합의한 것만** 더한다 — 요청서 밖에 더 하기로
한 새 작업, 요청서 요구사항에 새로 합의한 확인 문장. 합의하지 않은 확인 문장은
지어내지 않는다.
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
# 목 WriteMilestoneRequest 의 title·detail 제약. 새 작업의 이름과 설명이 그대로 들어간다.
MAX_MILESTONE_TITLE_LENGTH = 200
MAX_MILESTONE_DETAIL_LENGTH = 1000
# 마일스톤과 확인 문장의 개수에 상한이 없어 계산으로는 못 낸다. 실측으로 잡는다 —
# 요구사항 6건·마일스톤 2~3개가 174~255토큰이었고, 컨설팅 초안은 62토큰이었다.
# 문장이 길어지면 기본값 256을 바로 넘으므로 여유를 크게 둔다. (2026-09-24 실측)
# 이 실측은 요청서 요구사항까지 모델이 다시 적던 이전 출력 형식이다. 지금은 채팅에서
# 더한 것만 내므로 더 짧다. 새 형식은 아직 재지 않았다.
DRAFT_MAX_OUTPUT_TOKENS = 2048
# AI가 채우는 필드만. 나머지는 개발자 입력 필드다.
DRAFT_FIELDS = {
    CONSULTING: {"topic", "detail"},
    DEVELOPMENT: {"criteriaAdditions", "newMilestones"},
}
REQUIREMENT_FIELDS = {"issueId", "title", "detail", "qaCriteria"}
REJECTED_FIELDS = {"issueId", "title"}


def _require_text(value: Any, message: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise InvalidGmsResponseError(message)
    return value


def _require_known_id(value: Any, known: frozenset[int], message: str) -> int:
    # 타입이 틀린 것과 모르는 식별자를 갈라 말한다. 모델이 식별자를 "312" 처럼 문자열로
    # 내놓는 일이 잦은데, 한 문구로 묶으면 목록에 없는 것으로 읽혀 원인을 못 찾는다.
    if isinstance(value, bool) or not isinstance(value, int):
        raise InvalidGmsResponseError("요구사항 식별자는 따옴표 없는 숫자여야 합니다")
    if value not in known:
        raise InvalidGmsResponseError(message)
    return value


def _normalize_sentence(sentence: str) -> str:
    return " ".join(sentence.split())


def _require_issue_id(issue: dict[str, Any], seen: set[int]) -> int:
    issue_id = issue["issueId"]
    if isinstance(issue_id, bool) or not isinstance(issue_id, int):
        raise ValueError("issueId는 정수여야 합니다")
    if issue_id in seen:
        raise ValueError("요구사항 식별자가 중복됩니다")
    if not isinstance(issue["title"], str) or not issue["title"].strip():
        raise ValueError("요구사항 제목이 비어 있습니다")
    seen.add(issue_id)
    return issue_id


def normalize_issues(issues: Iterable[dict[str, Any]]) -> list[dict[str, Any]]:
    """요청서의 요구사항을 확인한다. 설명과 확인 문장은 없을 수 있다."""
    rows: list[dict[str, Any]] = []
    seen: set[int] = set()
    for issue in issues:
        if not isinstance(issue, dict) or not {"issueId", "title"} <= set(issue) <= REQUIREMENT_FIELDS:
            raise ValueError("요구사항은 issueId·title과 선택 항목 detail·qaCriteria만 가진다")
        issue_id = _require_issue_id(issue, seen)
        detail = issue.get("detail")
        if detail is not None and not isinstance(detail, str):
            raise ValueError("요구사항 설명은 문자열이어야 합니다")
        criteria = issue.get("qaCriteria", [])
        if not isinstance(criteria, list) or not all(
            isinstance(sentence, str) and sentence.strip() for sentence in criteria
        ):
            raise ValueError("요구사항의 확인 문장은 내용이 있는 문자열 목록이어야 합니다")
        rows.append(
            {"issueId": issue_id, "title": issue["title"], "detail": detail, "qaCriteria": list(criteria)}
        )
    return rows


def normalize_rejected(
    rejected: Iterable[dict[str, Any]], issues: Sequence[dict[str, Any]]
) -> list[dict[str, Any]]:
    """고르지 않은 요구사항을 확인한다. 요청서에 담긴 것과 겹치면 안 된다."""
    chosen = {issue["issueId"] for issue in issues}
    rows: list[dict[str, Any]] = []
    seen: set[int] = set()
    for issue in rejected:
        if not isinstance(issue, dict) or set(issue) != REJECTED_FIELDS:
            raise ValueError("고르지 않은 요구사항은 issueId와 title만 가진다")
        issue_id = _require_issue_id(issue, seen)
        if issue_id in chosen:
            raise ValueError("요청서에 담긴 요구사항을 고르지 않은 것으로 함께 보낼 수 없습니다")
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


def _parse_criteria(criteria: Any, already: set[str]) -> list[str]:
    """확인 문장 목록. R-REQ-04: 문장마다 1~500자. 같은 문장을 두 번 적지 않는다."""
    if not isinstance(criteria, list):
        raise InvalidGmsResponseError("확인 문장은 목록이어야 합니다")
    seen = set(already)
    for sentence in criteria:
        _require_text(sentence, "확인 문장이 비어 있습니다")
        if len(sentence) > MAX_TEST_CASE_LENGTH:
            raise InvalidGmsResponseError("확인 문장 길이가 올바르지 않습니다")
        normalized = _normalize_sentence(sentence)
        if normalized in seen:
            raise InvalidGmsResponseError("같은 확인 문장이 이미 있습니다")
        seen.add(normalized)
    return list(criteria)


def _parse_additions(rows: Any, issues: Sequence[dict[str, Any]]) -> list[dict[str, Any]]:
    """요청서 요구사항에 새로 합의한 확인 문장. 요청서에 이미 있는 문장은 다시 받지 않는다."""
    if not isinstance(rows, list):
        raise InvalidGmsResponseError("criteriaAdditions는 목록이어야 합니다")
    existing = {
        issue["issueId"]: {_normalize_sentence(sentence) for sentence in issue["qaCriteria"]}
        for issue in issues
    }
    known = frozenset(existing)
    additions: list[dict[str, Any]] = []
    for row in rows:
        if not isinstance(row, dict) or set(row) != {"issueId", "qaCriteria"}:
            raise InvalidGmsResponseError("확인 문장 추가는 issueId와 qaCriteria만 가집니다")
        issue_id = _require_known_id(row["issueId"], known, "요청서에 없는 요구사항 식별자입니다")
        if any(addition["issueId"] == issue_id for addition in additions):
            raise InvalidGmsResponseError("같은 요구사항에 확인 문장 추가가 두 번 있습니다")
        criteria = _parse_criteria(row["qaCriteria"], existing[issue_id])
        if not criteria:
            raise InvalidGmsResponseError("추가할 확인 문장이 없습니다")
        additions.append({"issueId": issue_id, "qaCriteria": criteria})
    return additions


def _parse_new_milestones(rows: Any) -> list[dict[str, Any]]:
    """채팅에서 요청서 밖에 더 하기로 합의한 작업. 합의한 확인 문장이 없으면 비어 있다."""
    if not isinstance(rows, list):
        raise InvalidGmsResponseError("newMilestones는 목록이어야 합니다")
    milestones: list[dict[str, Any]] = []
    for row in rows:
        if not isinstance(row, dict) or set(row) != {"title", "detail", "qaCriteria"}:
            raise InvalidGmsResponseError("새 작업은 title·detail·qaCriteria만 가집니다")
        title = _require_text(row["title"], "작업 이름이 비어 있습니다")
        if len(title) > MAX_MILESTONE_TITLE_LENGTH:
            raise InvalidGmsResponseError("작업 이름 길이가 올바르지 않습니다")
        detail = row["detail"]
        if detail is not None:
            detail = _require_text(detail, "작업 설명은 null이거나 내용이 있어야 합니다")
            if len(detail) > MAX_MILESTONE_DETAIL_LENGTH:
                raise InvalidGmsResponseError("작업 설명 길이가 올바르지 않습니다")
        milestones.append(
            {"title": title, "detail": detail, "qaCriteria": _parse_criteria(row["qaCriteria"], set())}
        )
    return milestones


def _parse_development(draft: dict[str, Any], issues: Sequence[dict[str, Any]]) -> dict[str, Any]:
    return {
        "criteriaAdditions": _parse_additions(draft["criteriaAdditions"], issues),
        "newMilestones": _parse_new_milestones(draft["newMilestones"]),
    }


def parse_draft(
    raw: str,
    target_type: str,
    issues: Sequence[dict[str, Any]] = (),
) -> dict[str, Any]:
    """모델 출력을 계약 유형의 초안 모양으로 검증한다. 개발 계약은 요청서 요구사항과 견준다."""
    try:
        data = json.loads(raw)
    except json.JSONDecodeError as exc:
        raise InvalidGmsResponseError("GMS 메시지가 JSON이 아닙니다") from exc
    if not isinstance(data, dict) or set(data) != {"draft"}:
        raise InvalidGmsResponseError("GMS 메시지의 필드가 올바르지 않습니다")
    draft = data["draft"]
    if not isinstance(draft, dict) or set(draft) != DRAFT_FIELDS[target_type]:
        raise InvalidGmsResponseError("초안 필드가 계약 유형과 다릅니다")
    if target_type == CONSULTING:
        return _parse_consulting(draft)
    return _parse_development(draft, issues)


CONSULTING_INSTRUCTION = (
    "컨설팅 계약 초안을 만듭니다. draft는 topic과 detail만 가집니다. "
    "topic은 이 세션에서 다룰 것을 한 줄로 255자 안에 적고, detail은 합의한 상담 내용의 "
    "상세 설명입니다. 근거가 없으면 detail을 null로 두세요."
)
DEVELOPMENT_INSTRUCTION = (
    "개발 계약 초안을 만듭니다. [요청서]의 요구사항은 의뢰인이 고른 것으로 계약서에 모두 "
    "그대로 들어가니 다시 적지 마세요. 요청서와 상담 대화를 종합해 대화에서 합의한 것만 "
    "더합니다. draft는 criteriaAdditions와 newMilestones 두 칸만 가지며, 더할 것이 없으면 "
    "빈 배열로 둡니다. 두 칸은 언제나 있어야 합니다. "
    "criteriaAdditions는 요청서의 요구사항에 대해 대화에서 새로 합의한 확인 문장입니다. "
    "각 항목은 issueId와 qaCriteria만 가집니다. issueId는 [요청서] 줄 맨 앞의 숫자를 "
    '따옴표 없는 JSON 숫자로 적습니다. "312" 가 아니라 312 입니다. [요청서]에 없는 숫자는 '
    "적지 말고, 요청서에 이미 있는 확인 문장은 다시 적지 마세요. "
    "newMilestones는 대화에서 요청서 밖에 더 하기로 합의한 작업입니다. 각 항목은 title, "
    f"detail, qaCriteria만 가집니다. title은 {MAX_MILESTONE_TITLE_LENGTH}자 안, detail은 "
    f"{MAX_MILESTONE_DETAIL_LENGTH}자 안이며 근거가 없으면 detail을 null로 둡니다. "
    "[고르지 않은 요구사항]과 같은 일이라도 대화에서 하기로 합의했으면 newMilestones로 적고, "
    "합의하지 않았으면 적지 마세요. "
    "확인 문장은 대화에서 합의한 것만 적고, 합의한 확인 문장이 없으면 qaCriteria를 빈 배열로 "
    "둡니다. 각 문장은 의뢰인이 직접 실행해 통과 여부를 판단할 수 있어야 하고 "
    f"1~{MAX_TEST_CASE_LENGTH}자입니다."
)


def _requirement_listing(issues: Sequence[dict[str, Any]]) -> str:
    lines: list[str] = []
    for issue in issues:
        lines.append(f"{issue['issueId']}: {issue['title']}")
        if issue["detail"]:
            lines.append(f"  설명: {issue['detail']}")
        for sentence in issue["qaCriteria"]:
            lines.append(f"  확인 문장: {sentence}")
    return "\n".join(lines) or "(없음)"


def build_messages(
    target_type: str,
    messages: list[dict[str, str]],
    issues: Sequence[dict[str, Any]],
    rejected: Sequence[dict[str, Any]] = (),
) -> list[dict[str, str]]:
    instruction = (
        CONSULTING_INSTRUCTION if target_type == CONSULTING else DEVELOPMENT_INSTRUCTION
    )
    transcript = "\n".join(f"{row['authorRole']}: {row['content']}" for row in messages)
    user_content = transcript
    if target_type == DEVELOPMENT:
        rejected_listing = "\n".join(f"{row['issueId']}: {row['title']}" for row in rejected)
        user_content = (
            f"[요청서]\n{_requirement_listing(issues)}\n\n"
            f"[고르지 않은 요구사항]\n{rejected_listing or '(없음)'}\n\n"
            f"[상담 대화]\n{transcript}"
        )
    return [
        {
            "role": "system",
            "content": (
                "무료 상담 대화를 읽고 개발자가 검토할 계약서 초안을 한국어로 정리하세요. "
                f"{instruction} "
                "대화에 없는 합의·작업·확인 문장·금액·일정을 지어내지 마세요. "
                "일정, 기간, 금액, 인계물은 개발자가 직접 입력하므로 초안에 넣지 마세요. "
                "전환된 계약방의 요청이므로 대화가 짧아도 확인 가능한 내용으로 초안을 만드세요. "
                '설명이나 코드 블록 없이 JSON 객체 {"draft":{...}}만 '
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
    rejected_issues: Iterable[dict[str, Any]] = (),
) -> dict[str, Any]:
    """전환에 성공한 상담방 하나의 메시지로 계약 유형별 초안을 만든다. (`R-MATCH-03`)"""
    if target_type not in TARGET_TYPES:
        raise ValueError("계약 유형은 CONSULTING 또는 DEVELOPMENT입니다")
    rows = normalize_messages(messages)
    known = normalize_issues(issues)
    rejected = normalize_rejected(rejected_issues, known)
    raw = client.complete(
        build_messages(target_type, rows, known, rejected), max_tokens=DRAFT_MAX_OUTPUT_TOKENS
    )
    return parse_draft(raw, target_type, known)


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
            payload.get("rejectedIssues", ()),
        )
    except (GmsUnavailableError, InvalidGmsResponseError, ValueError) as error:
        raise SystemExit(str(error)) from None
    print(json.dumps(result, ensure_ascii=False, indent=2))
