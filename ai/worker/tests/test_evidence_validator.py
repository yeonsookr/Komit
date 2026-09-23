import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from evidence_validator import has_valid_evidence, validate  # noqa: E402


SECTIONS = ("discussed", "decided", "nextActions", "changes")
UTTERANCE_COUNT = 3


def item(indexes, **over):
    row = {
        "title": "관리자 화면 접근 오류 논의",
        "detail": None,
        "evidenceUtteranceIndexes": indexes,
        "reviewRequired": False,
    }
    row.update(over)
    return row


def summary(**over):
    data = {section: [] for section in SECTIONS}
    data.update(over)
    return data


class EvidenceValidatorTest(unittest.TestCase):
    def test_계약_4절_예시표를_그대로_따른다(self):
        cases = (
            ([0], [0], False),
            ([0, 2], [0, 2], False),
            ([], [], True),
            ([1, 1], [], True),
            ([0, 3], [], True),
            ([-1], [], True),
        )
        for indexes, kept, review in cases:
            with self.subTest(indexes=indexes):
                result = validate(
                    summary(discussed=[item(indexes)]), UTTERANCE_COUNT
                )
                row = result["discussed"][0]
                self.assertEqual(row["evidenceUtteranceIndexes"], kept)
                self.assertIs(row["reviewRequired"], review)

    def test_네_배열에_모두_적용한다(self):
        data = summary(**{section: [item([9])] for section in SECTIONS})
        result = validate(data, UTTERANCE_COUNT)
        for section in SECTIONS:
            with self.subTest(section=section):
                self.assertEqual(result[section][0]["evidenceUtteranceIndexes"], [])
                self.assertIs(result[section][0]["reviewRequired"], True)

    def test_어긴_항목만_내리고_나머지는_그대로_둔다(self):
        data = summary(discussed=[item([0]), item([5]), item([1, 2])])
        result = validate(data, UTTERANCE_COUNT)
        kept = [row["evidenceUtteranceIndexes"] for row in result["discussed"]]
        review = [row["reviewRequired"] for row in result["discussed"]]
        self.assertEqual(kept, [[0], [], [1, 2]])
        self.assertEqual(review, [False, True, False])

    def test_전사가_비면_어떤_항목도_근거를_갖지_못한다(self):
        result = validate(summary(discussed=[item([0])]), 0)
        self.assertEqual(result["discussed"][0]["evidenceUtteranceIndexes"], [])
        self.assertIs(result["discussed"][0]["reviewRequired"], True)

    def test_정수가_아닌_순번은_근거로_보지_않는다(self):
        for indexes in ([True], ["1"], [1.0], [None], [[0]]):
            with self.subTest(indexes=indexes):
                self.assertFalse(has_valid_evidence(indexes, UTTERANCE_COUNT))

    def test_목록이_아니면_근거로_보지_않는다(self):
        for indexes in (None, 0, "0", {"0": 1}):
            with self.subTest(indexes=indexes):
                self.assertFalse(has_valid_evidence(indexes, UTTERANCE_COUNT))

    def test_검토_표시는_검증만이_정한다(self):
        data = summary(discussed=[item([0], reviewRequired=True)])
        result = validate(data, UTTERANCE_COUNT)
        self.assertIs(result["discussed"][0]["reviewRequired"], False)

    def test_다른_필드는_건드리지_않는다(self):
        row = item([7], title="결제 실패 로그", detail="재현 절차 확인", kind="ADD")
        result = validate(summary(changes=[row]), UTTERANCE_COUNT)
        checked = result["changes"][0]
        self.assertEqual(checked["title"], "결제 실패 로그")
        self.assertEqual(checked["detail"], "재현 절차 확인")
        self.assertEqual(checked["kind"], "ADD")

    def test_입력을_바꾸지_않는다(self):
        row = item([9])
        data = summary(discussed=[row])
        validate(data, UTTERANCE_COUNT)
        self.assertEqual(row["evidenceUtteranceIndexes"], [9])
        self.assertIs(row["reviewRequired"], False)

    def test_발화_수가_음수면_실패한다(self):
        with self.assertRaises(ValueError):
            validate(summary(), -1)


if __name__ == "__main__":
    unittest.main()
