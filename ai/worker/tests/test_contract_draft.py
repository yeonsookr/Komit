import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from contract_draft import (  # noqa: E402
    DRAFT_MAX_OUTPUT_TOKENS,
    InvalidGmsResponseError,
    draft_contract,
    normalize_messages,
    parse_draft,
)


MESSAGES = [
    {
        "chatRefId": "room-91-chat",
        "authorRole": "CLIENT",
        "createdAt": "2026-08-20T10:00:00+09:00",
        "content": "관리자 화면이 아무나 들어가집니다. 결제 실패도 가끔 나요.",
    },
    {
        "chatRefId": "room-91-chat",
        "authorRole": "DEVELOPER",
        "createdAt": "2026-08-20T10:02:00+09:00",
        "content": "관리자 인증은 맡겠습니다. 결제 실패는 재현이 어려워 이번에는 빼겠습니다.",
    },
]
ISSUES = [
    {"issueId": 312, "title": "관리자 화면에 인증이 없다"},
    {"issueId": 313, "title": "결제가 가끔 실패한다"},
]
CONSULTING_DRAFT = {
    "topic": "관리자 인증과 결제 실패를 함께 봅니다",
    "detail": "관리자 화면 접근 경로와 결제 실패 재현 방법을 확인합니다.",
}
DEVELOPMENT_DRAFT = {
    "issueIds": [312],
    "milestones": [
        {
            "title": "관리자 화면에 인증 추가",
            "qaCriteria": ["로그인하지 않고 관리자 주소로 들어가면 로그인 화면이 나타난다"],
        }
    ],
    "rejectedIssues": [{"issueId": 313, "reason": "재현이 어려워 이번 범위에서 뺍니다."}],
}


def payload(draft, proceed=True):
    return json.dumps({"proceed": proceed, "draft": draft}, ensure_ascii=False)


class FakeClient:
    def __init__(self, raw):
        self.raw = raw
        self.messages = None
        self.max_tokens = None

    def complete(self, messages, max_tokens=None):
        self.messages = messages
        self.max_tokens = max_tokens
        return self.raw


class ContractDraftTest(unittest.TestCase):
    def test_consulting_draft_keeps_only_ai_fields(self):
        client = FakeClient(payload(CONSULTING_DRAFT))
        result = draft_contract("CONSULTING", MESSAGES, client)
        self.assertEqual(result, CONSULTING_DRAFT)

    def test_development_draft_keeps_only_ai_fields(self):
        client = FakeClient(payload(DEVELOPMENT_DRAFT))
        result = draft_contract("DEVELOPMENT", MESSAGES, client, ISSUES)
        self.assertEqual(result, DEVELOPMENT_DRAFT)
        self.assertIn("312: 관리자 화면에 인증이 없다", client.messages[-1]["content"])

    def test_developer_input_fields_are_rejected(self):
        cases = (
            ("CONSULTING", {**CONSULTING_DRAFT, "price": 50000}),
            ("CONSULTING", {**CONSULTING_DRAFT, "scheduledAt": "2026-08-29T14:00:00+09:00"}),
            ("DEVELOPMENT", {**DEVELOPMENT_DRAFT, "startDate": "2026-08-26"}),
            ("DEVELOPMENT", {**DEVELOPMENT_DRAFT, "deliverableTitles": ["소스 파일 전체"]}),
        )
        for target_type, draft in cases:
            with self.subTest(field=sorted(set(draft) - {"topic", "detail", "issueIds", "milestones", "rejectedIssues"})):
                with self.assertRaises(InvalidGmsResponseError):
                    parse_draft(payload(draft), target_type, (312, 313))

        milestone = {**DEVELOPMENT_DRAFT["milestones"][0], "price": 400000}
        with self.assertRaises(InvalidGmsResponseError):
            parse_draft(
                payload({**DEVELOPMENT_DRAFT, "milestones": [milestone]}),
                "DEVELOPMENT",
                (312, 313),
            )

    def test_missing_fields_and_mixed_contract_types(self):
        cases = (
            ("CONSULTING", {"topic": CONSULTING_DRAFT["topic"]}),
            ("CONSULTING", DEVELOPMENT_DRAFT),
            ("DEVELOPMENT", CONSULTING_DRAFT),
            ("DEVELOPMENT", {key: value for key, value in DEVELOPMENT_DRAFT.items() if key != "rejectedIssues"}),
        )
        for target_type, draft in cases:
            with self.subTest(target_type=target_type, keys=sorted(draft)):
                with self.assertRaises(InvalidGmsResponseError):
                    parse_draft(payload(draft), target_type, (312, 313))

    def test_rejects_malformed_output(self):
        bad = [
            "not json",
            json.dumps({"draft": CONSULTING_DRAFT}),
            json.dumps({"proceed": "true", "draft": CONSULTING_DRAFT}),
            payload({**CONSULTING_DRAFT, "topic": "  "}),
            payload({**CONSULTING_DRAFT, "topic": "주" * 256}),
            payload({**CONSULTING_DRAFT, "detail": ""}),
        ]
        for raw in bad:
            with self.subTest(raw=raw[:40]), self.assertRaises(InvalidGmsResponseError):
                parse_draft(raw, "CONSULTING")

    def test_rejects_invented_or_conflicting_issue_ids(self):
        bad = [
            {**DEVELOPMENT_DRAFT, "issueIds": [999]},
            {**DEVELOPMENT_DRAFT, "issueIds": [312, 312]},
            {**DEVELOPMENT_DRAFT, "rejectedIssues": [{"issueId": 312, "reason": "빼겠습니다."}]},
            {**DEVELOPMENT_DRAFT, "rejectedIssues": [{"issueId": 313, "reason": " "}]},
            {**DEVELOPMENT_DRAFT, "rejectedIssues": [{"issueId": 313}]},
            {**DEVELOPMENT_DRAFT, "milestones": []},
            {**DEVELOPMENT_DRAFT, "milestones": [{"title": "관리자 인증", "qaCriteria": []}]},
            {
                **DEVELOPMENT_DRAFT,
                "milestones": [{"title": "관리자 인증", "qaCriteria": ["가" * 501]}],
            },
        ]
        for draft in bad:
            with self.subTest(draft=sorted(draft)), self.assertRaises(InvalidGmsResponseError):
                parse_draft(payload(draft), "DEVELOPMENT", (312, 313))

    def test_draft_uses_its_own_output_limit(self):
        """기본값(256)으로는 요구사항 여러 건짜리 초안이 잘린다. 작업별 한도를 넘겨야 한다."""
        client = FakeClient(payload(CONSULTING_DRAFT))
        draft_contract("CONSULTING", MESSAGES, client)
        self.assertEqual(client.max_tokens, DRAFT_MAX_OUTPUT_TOKENS)

    def test_string_issue_ids_are_told_apart_from_unknown_ones(self):
        """모델이 식별자를 "312" 처럼 문자열로 내놓는다. 목록에 없는 것과 갈라 말해야 한다."""
        as_text = {**DEVELOPMENT_DRAFT, "issueIds": ["312"], "rejectedIssues": []}
        with self.assertRaises(InvalidGmsResponseError) as caught:
            parse_draft(payload(as_text), "DEVELOPMENT", (312, 313))
        self.assertIn("숫자", str(caught.exception))

        unknown = {**DEVELOPMENT_DRAFT, "issueIds": [999], "rejectedIssues": []}
        with self.assertRaises(InvalidGmsResponseError) as caught:
            parse_draft(payload(unknown), "DEVELOPMENT", (312, 313))
        self.assertIn("입력에 없는", str(caught.exception))

    def test_unfinished_consultation_makes_no_draft(self):
        client = FakeClient(payload(None, proceed=False))
        self.assertIsNone(draft_contract("CONSULTING", MESSAGES, client))

    def test_unfinished_consultation_cannot_carry_a_draft(self):
        with self.assertRaises(InvalidGmsResponseError):
            parse_draft(payload(CONSULTING_DRAFT, proceed=False), "CONSULTING")

    def test_rejects_unknown_target_type(self):
        with self.assertRaises(ValueError):
            draft_contract("NONE", MESSAGES, FakeClient(payload(CONSULTING_DRAFT)))

    def test_rejects_mixed_rooms_and_broken_input(self):
        other_room = [MESSAGES[0], {**MESSAGES[1], "chatRefId": "room-92-chat"}]
        out_of_order = [MESSAGES[1], MESSAGES[0]]
        bad = [
            [],
            other_room,
            out_of_order,
            [{**MESSAGES[0], "authorRole": "ADMIN"}],
            [{**MESSAGES[0], "content": "   "}],
            [{**MESSAGES[0], "createdAt": "어제"}],
            [{**MESSAGES[0], "roomId": 91}],
        ]
        for messages in bad:
            with self.subTest(messages=len(messages)), self.assertRaises(ValueError):
                normalize_messages(messages)


if __name__ == "__main__":
    unittest.main()
