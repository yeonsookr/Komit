import sys
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from consulting_transcript import (  # noqa: E402
    Track,
    load_model,
    merge,
    split_by_gap,
    transcribe_session,
    transcribe_track,
)
from qa_suggestion import GmsUnavailableError, UnusableInputError  # noqa: E402


class FakeWord:
    def __init__(self, start, end, word):
        self.start, self.end, self.word = start, end, word


class FakeSegment:
    """단어 시각을 가진 세그먼트. 모델을 부르지 않고 자르는 규칙만 본다."""

    def __init__(self, words, text=None):
        self.words = [FakeWord(*row) for row in words] if words else None
        self.start = self.words[0].start if self.words else 0.0
        self.end = self.words[-1].end if self.words else 0.0
        self.text = text if text is not None else "".join(w.word for w in self.words or [])


def track(speaker, pieces):
    return {"speaker": speaker, "pieces": pieces}


def piece(start, end, text):
    return {"start": start, "end": end, "text": text}


class SplitByGap(unittest.TestCase):
    def test_말이_끊기는_자리에서_자른다(self):
        segment = FakeSegment([
            (0.0, 0.5, "배포가"), (0.5, 1.0, " 실패해요"),
            (3.0, 3.4, "로그는"), (3.4, 3.9, " 보셨어요"),
        ])

        pieces = split_by_gap([segment], gap_seconds=0.6)

        self.assertEqual([p["text"] for p in pieces], ["배포가 실패해요", "로그는 보셨어요"])
        self.assertEqual(pieces[0]["end"], 1.0)
        self.assertEqual(pieces[1]["start"], 3.0)

    def test_붙어_있으면_자르지_않는다(self):
        segment = FakeSegment([(0.0, 0.5, "네"), (0.7, 1.2, " 알겠습니다")])

        self.assertEqual(len(split_by_gap([segment], gap_seconds=0.6)), 1)

    def test_단어_시각이_없으면_그대로_둔다(self):
        """자를 근거가 없는데 나누면 없는 경계를 만든다."""
        segment = FakeSegment(None, text=" 아 네네 ")

        pieces = split_by_gap([segment])

        self.assertEqual([p["text"] for p in pieces], ["아 네네"])

    def test_빈_조각은_버린다(self):
        self.assertEqual(split_by_gap([FakeSegment(None, text="   ")]), [])


class Merge(unittest.TestCase):
    def test_두_트랙을_시각순으로_세운다(self):
        merged = merge([track("CLIENT", [piece(10.0, 11.0, "로컬에선 되고요?")]),
                        track("DEVELOPER", [piece(11.3, 12.5, "네 로컬은 멀쩡해요")])])

        self.assertEqual([row["speaker"] for row in merged], ["CLIENT", "DEVELOPER"])
        self.assertEqual(merged[1]["startMs"], 11_300)

    def test_끼어든_말이_상대_말_사이에_들어간다(self):
        client = track("CLIENT", [piece(3.0, 3.5, "아 네네")])
        developer = track("DEVELOPER", [piece(0.0, 2.0, "문제가 두 개예요"),
                                        piece(5.0, 7.0, "하나는 키가 올라가 있고요")])

        merged = merge([client, developer])

        self.assertEqual([row["rawText"] for row in merged],
                         ["문제가 두 개예요", "아 네네", "하나는 키가 올라가 있고요"])

    def test_겹치면_시작이_빠른_쪽이_앞이다(self):
        client = track("CLIENT", [piece(1.0, 3.0, "먼저 시작한 말")])
        developer = track("DEVELOPER", [piece(2.0, 4.0, "겹쳐 들어온 말")])

        merged = merge([client, developer])

        self.assertEqual(merged[0]["rawText"], "먼저 시작한 말")

    def test_시작이_같으면_화자_이름으로_정해_매번_같게_한다(self):
        client = track("CLIENT", [piece(1.0, 2.0, "이쪽")])
        developer = track("DEVELOPER", [piece(1.0, 2.0, "저쪽")])

        first = merge([client, developer])
        again = merge([developer, client])

        self.assertEqual([r["rawText"] for r in first], [r["rawText"] for r in again])

    def test_순번은_영부터_차례로_붙인다(self):
        client = track("CLIENT", [piece(0.0, 1.0, "하나"), piece(2.0, 3.0, "둘")])

        merged = merge([client])

        self.assertEqual([row["utteranceIndex"] for row in merged], [0, 1])

    def test_한쪽이_비어도_나머지로_만든다(self):
        """말을 하지 않은 참가자가 있을 수 있다. (R-CON-16)"""
        merged = merge([track("CLIENT", [piece(0.0, 1.0, "혼자 말함")]),
                        track("DEVELOPER", [])])

        self.assertEqual(len(merged), 1)


class Failures(unittest.TestCase):
    """갈리는 기준은 다시 시도하면 되는가다. (`ai/AGENTS.md` 5)"""

    def test_트랙이_없으면_부르는_쪽_실수로_다룬다(self):
        """밖으로 나가는 실패가 아니다. `consulting_summary` 와 같게 `ValueError` 다."""
        with self.assertRaises(ValueError):
            transcribe_session([])

    def test_모델을_올리지_못하면_다시_걸어_볼_갈래로_던진다(self):
        """설치·장비 문제다. 라이브러리를 가짜로 바꿔 외부 요청 없이 본다."""
        class 못올리는라이브러리:
            @staticmethod
            def WhisperModel(*args, **kwargs):
                raise RuntimeError("cublas64_12.dll is not found")

        with mock.patch.dict(sys.modules, {"faster_whisper": 못올리는라이브러리}):
            with self.assertRaises(GmsUnavailableError):
                load_model("small", "cuda", "float16")

    def test_오디오를_읽지_못하면_다시_걸어도_소용없는_갈래로_던진다(self):
        """깨진 파일은 입력이 잘못된 것이라 재시도가 답이 아니다."""
        class 못읽는모델:
            def transcribe(self, *args, **kwargs):
                raise RuntimeError("Invalid data found when processing input")

        with self.assertRaises(UnusableInputError):
            transcribe_track(못읽는모델(), Track("CLIENT", "깨진파일.webm"))

    def test_전사하다_GPU_가_멈추면_다시_걸어_볼_갈래로_던진다(self):
        """오디오는 멀쩡하다. 사람에게 넘기면 다시 받아 가지 않는다."""
        for message in ("CUDA failed with error out of memory", "Library cublas64_12.dll is not found"):
            class 멈추는모델:
                def transcribe(self, *args, **kwargs):
                    raise RuntimeError(message)

            with self.subTest(message=message), self.assertRaises(GmsUnavailableError):
                transcribe_track(멈추는모델(), Track("CLIENT", "멀쩡한파일.webm"))


if __name__ == "__main__":
    unittest.main()
