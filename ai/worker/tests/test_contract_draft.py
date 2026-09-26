import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from contract_draft import (  # noqa: E402
    DRAFT_MAX_OUTPUT_TOKENS,
    InvalidGmsResponseError,
    draft_contract,
    normalize_issues,
    normalize_messages,
    normalize_rejected,
    parse_draft,
)


MESSAGES = [
    {
        "chatRefId": "room-91-chat",
        "authorRole": "CLIENT",
        "createdAt": "2026-08-20T10:00:00+09:00",
        "content": "관리자 화면이 아무나 들어가집니다. 관리자 계정으로만 열려야 해요. 회원 검색도 붙여 주세요.",
    },
    {
        "chatRefId": "room-91-chat",
        "authorRole": "DEVELOPER",
        "createdAt": "2026-08-20T10:02:00+09:00",
        "content": "관리자 인증은 관리자 계정만 들어가게 맞추고, 회원 검색도 같이 하겠습니다.",
    },
]
ISSUES = [
    {
        "issueId": 312,
        "title": "관리자 화면에 인증이 없다",
        "detail": "로그인하지 않아도 /admin 이 열린다",
        "qaCriteria": ["로그인하지 않고 관리자 주소로 들어가면 로그인 화면이 나타난다"],
    },
    {"issueId": 311, "title": "비밀번호가 그대로 저장된다", "qaCriteria": []},
]
REJECTED = [{"issueId": 313, "title": "결제가 가끔 실패한다"}]
CONSULTING_DRAFT = {
    "topic": "관리자 인증과 결제 실패를 함께 봅니다",
    "detail": "관리자 화면 접근 경로와 결제 실패 재현 방법을 확인합니다.",
}
DEVELOPMENT_DRAFT = {
    "criteriaAdditions": [
        {"issueId": 312, "qaCriteria": ["일반 회원 계정으로 관리자 주소에 들어가면 막힌다"]}
    ],
    "newMilestones": [
        {"title": "관리자 화면에 회원 검색 추가", "detail": None, "qaCriteria": []}
    ],
}


def payload(draft):
    return json.dumps({"draft": draft}, ensure_ascii=False)


def parse_development(draft):
    return parse_draft(payload(draft), "DEVELOPMENT", normalize_issues(ISSUES))


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

    def test_development_draft_keeps_only_what_the_chat_added(self):
        client = FakeClient(payload(DEVELOPMENT_DRAFT))
        result = draft_contract("DEVELOPMENT", MESSAGES, client, ISSUES, REJECTED)
        self.assertEqual(result, DEVELOPMENT_DRAFT)

    def test_prompt_shows_the_request_and_the_issues_left_out(self):
        client = FakeClient(payload(DEVELOPMENT_DRAFT))
        draft_contract("DEVELOPMENT", MESSAGES, client, ISSUES, REJECTED)
        content = client.messages[-1]["content"]
        self.assertIn("[요청서]\n312: 관리자 화면에 인증이 없다", content)
        self.assertIn("  설명: 로그인하지 않아도 /admin 이 열린다", content)
        self.assertIn("  확인 문장: 로그인하지 않고 관리자 주소로 들어가면 로그인 화면이 나타난다", content)
        self.assertIn("[고르지 않은 요구사항]\n313: 결제가 가끔 실패한다", content)

    def test_new_work_may_have_no_agreed_criteria(self):
        """대화에서 합의한 확인 문장만 적는다. 없으면 지어내지 않고 비워 둔다. (R-REQ-04)"""
        result = parse_development(DEVELOPMENT_DRAFT)
        self.assertEqual(result["newMilestones"][0]["qaCriteria"], [])

    def test_nothing_to_add_is_an_empty_draft(self):
        result = parse_development({"criteriaAdditions": [], "newMilestones": []})
        self.assertEqual(result, {"criteriaAdditions": [], "newMilestones": []})

    def test_developer_input_fields_are_rejected(self):
        cases = (
            ("CONSULTING", {**CONSULTING_DRAFT, "price": 50000}),
            ("CONSULTING", {**CONSULTING_DRAFT, "scheduledAt": "2026-08-29T14:00:00+09:00"}),
            ("DEVELOPMENT", {**DEVELOPMENT_DRAFT, "startDate": "2026-08-26"}),
            ("DEVELOPMENT", {**DEVELOPMENT_DRAFT, "deliverableTitles": ["소스 파일 전체"]}),
            ("DEVELOPMENT", {**DEVELOPMENT_DRAFT, "rejectedIssues": []}),
        )
        for target_type, draft in cases:
            with self.subTest(target_type=target_type, keys=sorted(draft)):
                with self.assertRaises(InvalidGmsResponseError):
                    parse_draft(payload(draft), target_type, normalize_issues(ISSUES))

        milestone = {**DEVELOPMENT_DRAFT["newMilestones"][0], "price": 400000}
        with self.assertRaises(InvalidGmsResponseError):
            parse_development({**DEVELOPMENT_DRAFT, "newMilestones": [milestone]})

    def test_missing_fields_and_mixed_contract_types(self):
        cases = (
            ("CONSULTING", {"topic": CONSULTING_DRAFT["topic"]}),
            ("CONSULTING", DEVELOPMENT_DRAFT),
            ("DEVELOPMENT", CONSULTING_DRAFT),
            ("DEVELOPMENT", {"criteriaAdditions": []}),
            ("DEVELOPMENT", {"newMilestones": []}),
        )
        for target_type, draft in cases:
            with self.subTest(target_type=target_type, keys=sorted(draft)):
                with self.assertRaises(InvalidGmsResponseError):
                    parse_draft(payload(draft), target_type, normalize_issues(ISSUES))

    def test_rejects_malformed_output(self):
        bad = [
            "not json",
            json.dumps({"proceed": True, "draft": CONSULTING_DRAFT}),
            payload(None),
            payload({**CONSULTING_DRAFT, "topic": "  "}),
            payload({**CONSULTING_DRAFT, "topic": "주" * 256}),
            payload({**CONSULTING_DRAFT, "detail": ""}),
        ]
        for raw in bad:
            with self.subTest(raw=raw[:40]), self.assertRaises(InvalidGmsResponseError):
                parse_draft(raw, "CONSULTING")

    def test_additions_must_point_at_the_request(self):
        bad = [
            [{"issueId": 999, "qaCriteria": ["막힌다"]}],
            [{"issueId": 313, "qaCriteria": ["결제가 된다"]}],
            [{"issueId": 312, "qaCriteria": ["막힌다"]}, {"issueId": 312, "qaCriteria": ["열린다"]}],
            [{"issueId": 312, "qaCriteria": []}],
            [{"issueId": 312}],
            [{"issueId": 312, "qaCriteria": ["  로그인하지 않고  관리자 주소로 들어가면 로그인 화면이 나타난다 "]}],
        ]
        for additions in bad:
            with self.subTest(additions=additions), self.assertRaises(InvalidGmsResponseError):
                parse_development({**DEVELOPMENT_DRAFT, "criteriaAdditions": additions})

    def test_rejects_broken_new_milestones(self):
        base = DEVELOPMENT_DRAFT["newMilestones"][0]
        bad = [
            {**base, "title": " "},
            {**base, "title": "가" * 201},
            {**base, "detail": ""},
            {**base, "detail": "가" * 1001},
            {**base, "qaCriteria": ["가" * 501]},
            {**base, "qaCriteria": ["검색이 된다", " 검색이  된다"]},
            {"title": base["title"], "qaCriteria": []},
        ]
        for milestone in bad:
            with self.subTest(milestone=sorted(milestone)), self.assertRaises(InvalidGmsResponseError):
                parse_development({**DEVELOPMENT_DRAFT, "newMilestones": [milestone]})

    def test_limits_are_inclusive(self):
        milestone = {"title": "가" * 200, "detail": "가" * 1000, "qaCriteria": ["가" * 500]}
        result = parse_development({"criteriaAdditions": [], "newMilestones": [milestone]})
        self.assertEqual(len(result["newMilestones"][0]["title"]), 200)

    def test_draft_uses_its_own_output_limit(self):
        """기본값(256)으로는 요구사항 여러 건짜리 초안이 잘린다. 작업별 한도를 넘겨야 한다."""
        client = FakeClient(payload(CONSULTING_DRAFT))
        draft_contract("CONSULTING", MESSAGES, client)
        self.assertEqual(client.max_tokens, DRAFT_MAX_OUTPUT_TOKENS)

    def test_string_issue_ids_are_told_apart_from_unknown_ones(self):
        """모델이 식별자를 "312" 처럼 문자열로 내놓는다. 목록에 없는 것과 갈라 말해야 한다."""
        as_text = {**DEVELOPMENT_DRAFT, "criteriaAdditions": [{"issueId": "312", "qaCriteria": ["막힌다"]}]}
        with self.assertRaises(InvalidGmsResponseError) as caught:
            parse_development(as_text)
        self.assertIn("숫자", str(caught.exception))

        unknown = {**DEVELOPMENT_DRAFT, "criteriaAdditions": [{"issueId": 999, "qaCriteria": ["막힌다"]}]}
        with self.assertRaises(InvalidGmsResponseError) as caught:
            parse_development(unknown)
        self.assertIn("요청서에 없는", str(caught.exception))

    def test_short_conversation_still_requires_a_draft(self):
        client = FakeClient(payload(None))
        with self.assertRaises(InvalidGmsResponseError):
            draft_contract("CONSULTING", MESSAGES[:1], client)

    def test_rejects_unknown_target_type(self):
        with self.assertRaises(ValueError):
            draft_contract("NONE", MESSAGES, FakeClient(payload(CONSULTING_DRAFT)))

    def test_rejects_broken_request_input(self):
        bad_issues = [
            [{"issueId": 312}],
            [{**ISSUES[0], "location": "/admin"}],
            [{**ISSUES[0], "qaCriteria": "로그인 화면"}],
            [{**ISSUES[0], "qaCriteria": [" "]}],
            [ISSUES[0], ISSUES[0]],
        ]
        for issues in bad_issues:
            with self.subTest(issues=issues), self.assertRaises(ValueError):
                normalize_issues(issues)

    def test_rejected_issues_must_not_overlap_the_request(self):
        chosen = normalize_issues(ISSUES)
        with self.assertRaises(ValueError):
            normalize_rejected([{"issueId": 312, "title": "관리자 화면에 인증이 없다"}], chosen)
        with self.assertRaises(ValueError):
            normalize_rejected([{"issueId": 313, "title": "결제", "reason": "빼겠습니다"}], chosen)
        self.assertEqual(normalize_rejected(REJECTED, chosen), REJECTED)

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
