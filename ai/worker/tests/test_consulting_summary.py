import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from consulting_summary import (  # noqa: E402
    MAX_OUTPUT_TOKENS,
    InvalidGmsResponseError,
    build_messages,
    parse_summary,
    summarize,
)


REQS = [{"issueId": 7, "title": "결제 실패 로그 남기기"}]

UTTERANCES = [
    {
        "utteranceIndex": 0,
        "speaker": "CLIENT",
        "rawText": "관리자 화면 접근 오류를 확인했어요.",
        "normalizedText": "관리자 화면 접근 오류를 확인했어요.",
    },
    {
        "utteranceIndex": 1,
        "speaker": "DEVELOPER",
        "rawText": "로그인하지 않으면 관리자 화면을 막는 것으로 정하죠.",
        "normalizedText": "로그인하지 않으면 관리자 화면을 막는 것으로 정하죠.",
    },
]


def item(**over):
    row = {
        "title": "관리자 화면 접근 오류 논의",
        "detail": None,
        "evidenceUtteranceIndexes": [0],
        "reviewRequired": False,
    }
    row.update(over)
    return row


def summary(**over):
    data = {"discussed": [item()], "decided": [], "nextActions": [], "changes": []}
    data.update(over)
    return json.dumps(data, ensure_ascii=False)


class FakeClient:
    def __init__(self, raw):
        self.raw = raw
        self.max_tokens = None

    def complete(self, messages, max_tokens=None):
        self.max_tokens = max_tokens
        return self.raw


class ConsultingSummaryTest(unittest.TestCase):
    def test_네_배열을_돌려준다(self):
        result = parse_summary(summary())
        self.assertEqual(
            set(result), {"discussed", "decided", "nextActions", "changes"}
        )
        self.assertEqual(result["discussed"][0]["evidenceUtteranceIndexes"], [0])

    def test_네_배열이_모두_비어도_통과한다(self):
        result = parse_summary(summary(discussed=[]))
        self.assertEqual(result["discussed"], [])

    def test_키가_모자라면_실패한다(self):
        raw = json.dumps({"discussed": [], "decided": [], "nextActions": []})
        with self.assertRaises(InvalidGmsResponseError):
            parse_summary(raw)

    def test_항목에_없는_필드가_있으면_실패한다(self):
        with self.assertRaises(InvalidGmsResponseError):
            parse_summary(summary(discussed=[item(extra="x")]))

    def test_UPDATE_는_입력에_있는_issueId_만_가리킨다(self):
        change = item(kind="UPDATE", issueId=7)
        parse_summary(summary(changes=[change]), REQS)
        with self.assertRaises(InvalidGmsResponseError):
            parse_summary(summary(changes=[change]), [{"issueId": 9, "title": "다른 것"}])

    def test_ADD_는_issueId_가_null_이어야_한다(self):
        parse_summary(summary(changes=[item(kind="ADD", issueId=None)]))
        with self.assertRaises(InvalidGmsResponseError):
            parse_summary(summary(changes=[item(kind="ADD", issueId=3)]))

    def test_kind_가_다른_값이면_실패한다(self):
        with self.assertRaises(InvalidGmsResponseError):
            parse_summary(summary(changes=[item(kind="DELETE", issueId=None)]))

    def test_순번_내용이_틀려도_형식_오류로_보지_않는다(self):
        # 실존 검증은 evidence_validator 몫이다. 여기서 막으면 요약 전체가 사라진다.
        for indexes in ([0, 0], [-1], [99], []):
            with self.subTest(indexes=indexes):
                result = parse_summary(
                    summary(discussed=[item(evidenceUtteranceIndexes=indexes)])
                )
                self.assertEqual(
                    result["discussed"][0]["evidenceUtteranceIndexes"], indexes
                )

    def test_근거가_목록이_아니면_실패한다(self):
        with self.assertRaises(InvalidGmsResponseError):
            parse_summary(summary(discussed=[item(evidenceUtteranceIndexes=0)]))

    def test_미완성_JSON_은_형식_오류다(self):
        with self.assertRaises(InvalidGmsResponseError):
            parse_summary('{"discussed": [')

    def test_제목이_비면_실패한다(self):
        with self.assertRaises(InvalidGmsResponseError):
            parse_summary(summary(discussed=[item(title="  ")]))

    def test_프롬프트에_발화와_화자가_들어간다(self):
        messages = build_messages(UTTERANCES, REQS)
        body = messages[1]["content"]
        self.assertIn("0 [CLIENT]", body)
        self.assertIn("1 [DEVELOPER]", body)
        self.assertIn("7  결제 실패 로그 남기기", body)

    def test_요약은_네_배열용_토큰_한도로_부른다(self):
        client = FakeClient(summary())
        summarize(UTTERANCES, client)
        self.assertEqual(client.max_tokens, MAX_OUTPUT_TOKENS)

    def test_요약은_근거_검증까지_거쳐_돌아온다(self):
        raw = summary(
            discussed=[item(evidenceUtteranceIndexes=[0]), item(evidenceUtteranceIndexes=[5])]
        )
        result = summarize(UTTERANCES, FakeClient(raw))
        rows = result["discussed"]
        self.assertEqual(rows[0]["evidenceUtteranceIndexes"], [0])
        self.assertIs(rows[0]["reviewRequired"], False)
        self.assertEqual(rows[1]["evidenceUtteranceIndexes"], [])
        self.assertIs(rows[1]["reviewRequired"], True)

    def test_발화가_없으면_부르지_않는다(self):
        with self.assertRaises(ValueError):
            summarize([], FakeClient(summary()))


if __name__ == "__main__":
    unittest.main()
