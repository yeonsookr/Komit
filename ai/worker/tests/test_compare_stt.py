import sys
from pathlib import Path
import tempfile
import unittest


SCRIPT_DIR = Path(__file__).resolve().parents[1] / "scripts"
sys.path.insert(0, str(SCRIPT_DIR))

from compare_stt import (  # noqa: E402
    calculate_cer,
    normalize_for_comparison,
    read_clova_text,
)


class CompareSttTest(unittest.TestCase):
    def test_reads_clova_utterances_before_five_minutes(self) -> None:
        content = """개발 상담

참석자 1 00:00
첫 번째 발화입니다.

참석자 2 04:59
API 연동을 확인합니다.

참석자 1 05:00
이 발화는 평가 구간 밖입니다.
"""
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "transcript.txt"
            path.write_text(content, encoding="utf-8")

            result = read_clova_text(path, seconds=300)

        self.assertEqual(result, "첫 번째 발화입니다. API 연동을 확인합니다.")

    def test_normalizes_case_whitespace_and_punctuation(self) -> None:
        self.assertEqual(
            normalize_for_comparison(" FastAPI에서, 테스트 1회! "),
            "fastapi에서테스트1회",
        )

    def test_calculates_character_error_rate(self) -> None:
        distance, cer, similarity = calculate_cer("가나다라", "가나다마")

        self.assertEqual(distance, 1)
        self.assertEqual(cer, 0.25)
        self.assertEqual(similarity, 0.75)

    def test_rejects_empty_reference_for_cer(self) -> None:
        with self.assertRaises(ValueError):
            calculate_cer("", "가")


if __name__ == "__main__":
    unittest.main()
