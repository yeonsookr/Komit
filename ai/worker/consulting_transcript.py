"""컨설팅 세션 녹음 두 건을 화자가 붙은 병합 전사로 만든다.

참가자마다 자기 마이크만 담으므로(`R-CON-14`) **파일이 곧 화자다.** 화자 분리를 하지 않는다.

여기서 나온 배열을 `consulting_summary.summarize` 가 그대로 먹는다. 요약은 `normalizedText`
만 읽는다.
"""

from __future__ import annotations

import json
import sys
from dataclasses import dataclass

from qa_suggestion import GmsUnavailableError, UnusableInputError

# 세그먼트가 아니라 **조각**으로 자르는 기준. 단어 사이가 이 시간보다 벌어지면 끊는다.
#
# Whisper 는 턴이 아니라 세그먼트를 준다. 한 세그먼트가 여러 대사를 묶어 70초를 넘기기도
# 하는데, 그러면 그 사이에 상대가 끼어든 말이 덩어리 안으로 들어가지 못해 병합이 깨진다.
# 실측에서 세그먼트 4개가 조각 36개로 풀렸고 주고받은 차례가 정답과 일치했다.
SPLIT_GAP_SECONDS = 0.6

# 앞 문장을 참고해 다음을 만드는 기능을 끈다.
#
# 켜 두면 침묵 구간에서 같은 말을 되풀이하며 없는 문장을 지어낸다. 실측에서 「괜찮아요」가
# 여섯 번 이어 나왔다. 화자별 트랙은 절반 이상이 침묵이라 이 조건에 정확히 들어맞는다.
CONDITION_ON_PREVIOUS_TEXT = False

SPEAKERS = ("CLIENT", "DEVELOPER")

# 전사는 GMS 를 부르지 않지만 실패 갈래는 `ai/AGENTS.md` 5 를 그대로 따른다.
#
#   모델을 올리지 못함  -> GmsUnavailableError  (상류에 닿지 못함. 다시 걸면 될 수 있다)
#   오디오를 읽지 못함  -> UnusableInputError   (다시 걸어도 소용없다. 사람이 봐야 한다)


@dataclass(frozen=True)
class Track:
    """전사할 트랙 하나.

    `offset_ms` 는 세션이 시작된 순간부터 이 녹음이 시작되기까지의 지연이다. 브라우저마다
    녹음을 켜는 시점이 달라(실측 4~5초) 이 값 없이 섞으면 대답이 질문보다 앞에 놓인다.
    """

    speaker: str
    path: str
    offset_ms: int = 0


def split_by_gap(segments, gap_seconds: float = SPLIT_GAP_SECONDS) -> list[dict]:
    """세그먼트를 단어 사이가 벌어진 자리에서 조각으로 다시 자른다.

    단어 시각이 없는 세그먼트는 그대로 둔다. 자를 근거가 없는데 나누면 없는 경계를 만든다.
    """
    pieces: list[dict] = []
    for segment in segments:
        words = getattr(segment, "words", None) or []
        if not words:
            text = segment.text.strip()
            if text:
                pieces.append({"start": segment.start, "end": segment.end, "text": text})
            continue

        chunk = [words[0]]
        for word in words[1:]:
            if word.start - chunk[-1].end > gap_seconds:
                pieces.append(_piece(chunk))
                chunk = [word]
            else:
                chunk.append(word)
        pieces.append(_piece(chunk))
    return [piece for piece in pieces if piece["text"]]


def _piece(words) -> dict:
    return {
        "start": words[0].start,
        "end": words[-1].end,
        "text": "".join(word.word for word in words).strip(),
    }


def clamp_offset(offset_ms: int, limit_ms: int) -> tuple[int, str | None]:
    """브라우저가 보고한 시작 지연을 그대로 믿지 않는다.

    음수이거나 녹음 길이를 넘는 값은 0 으로 본다. 그 사실을 경고로 남겨야 나중에 요약이
    이상할 때 순서가 어긋났을 가능성을 짚을 수 있다.
    """
    if 0 <= offset_ms <= limit_ms:
        return offset_ms, None
    return 0, f"시작 지연 {offset_ms}ms 가 범위를 벗어나 0 으로 보았습니다"


def merge(tracks: list[dict]) -> list[dict]:
    """트랙별 조각을 같은 시간축에 올려 시간순 한 줄로 세운다.

    사람은 서로 말을 겹쳐 하므로 **겹치면 시작이 빠른 쪽을 앞에** 둔다. 시작이 같으면 화자
    이름으로 정해 결과가 매번 같게 한다.

    `utteranceIndex` 는 이 배열 안에서만 유효하다. 다른 세션이나 재처리 결과를 가리키는
    영속 식별자가 아니다 (`transcript-evidence-contract.md`).
    """
    rows = [
        {
            "speaker": track["speaker"],
            "startMs": round(piece["start"] * 1000) + track["offsetMs"],
            "endMs": round(piece["end"] * 1000) + track["offsetMs"],
            "text": piece["text"],
        }
        for track in tracks
        for piece in track["pieces"]
    ]
    rows.sort(key=lambda row: (row["startMs"], row["speaker"]))
    return [
        {
            "utteranceIndex": index,
            "speaker": row["speaker"],
            "startMs": row["startMs"],
            "endMs": row["endMs"],
            "rawText": row["text"],
            # 표기 정리는 아직 하지 않는다. 요약이 이 필드를 읽으므로 비워 둘 수는 없다.
            # 숫자는 모델이 알아서 바꾸고(「오백」→500) 영어 고유명사만 남는데, 그 사전은
            # 요약이 실제로 용어를 놓치는 것을 본 뒤에 만든다.
            "normalizedText": row["text"],
        }
        for index, row in enumerate(rows)
    ]


def load_model(name: str, device: str, compute_type: str):
    """모델을 올린다. 올리지 못하면 전사 실패 갈래로 바꿔 던진다."""
    try:
        from faster_whisper import WhisperModel

        return WhisperModel(name, device=device, compute_type=compute_type)
    except Exception as error:  # 설치·장비 문제를 한 갈래로 모은다
        raise GmsUnavailableError(str(error)) from error


def transcribe_track(model, track: Track, gap_seconds: float = SPLIT_GAP_SECONDS) -> dict:
    """트랙 하나를 전사해 조각으로 돌려준다.

    **VAD 를 켠다.** 트랙의 절반 이상이 침묵이라 그대로 넣으면 느리고, 침묵 구간이 그대로
    모델에 들어간다. 다만 VAD 만으로 환각이 막히지는 않는다 — 그건 위 상수가 맡는다.
    """
    try:
        segments, info = model.transcribe(
            track.path,
            language="ko",
            beam_size=5,
            vad_filter=True,
            word_timestamps=True,
            condition_on_previous_text=CONDITION_ON_PREVIOUS_TEXT,
        )
        pieces = split_by_gap(segments, gap_seconds)
    except GmsUnavailableError:
        raise
    except Exception as error:
        raise UnusableInputError(f"{track.path} 를 읽지 못했습니다: {error}") from error

    limit_ms = round(info.duration * 1000)
    offset_ms, warning = clamp_offset(track.offset_ms, limit_ms)
    return {
        "speaker": track.speaker,
        "offsetMs": offset_ms,
        "durationMs": limit_ms,
        "pieces": pieces,
        "warning": None if warning is None else f"{track.speaker}: {warning}",
    }


def transcribe_session(
    tracks: list[Track],
    *,
    model_name: str = "small",
    device: str = "cpu",
    compute_type: str | None = None,
    gap_seconds: float = SPLIT_GAP_SECONDS,
) -> dict:
    """세션 하나의 트랙들을 전사해 병합 전사를 만든다.

    **한쪽 트랙이 비어도 정상이다.** 말을 하지 않은 참가자가 있을 수 있고, 그 경우에도
    나머지 한쪽으로 요약을 만든다 (`R-CON-16`).
    """
    if not tracks:
        raise ValueError("전사할 트랙이 없습니다")

    model = load_model(model_name, device, compute_type or _default_compute(device))
    done = [transcribe_track(model, track, gap_seconds) for track in tracks]
    return {
        "utterances": merge(done),
        "warnings": [row["warning"] for row in done if row["warning"]],
    }


def _default_compute(device: str) -> str:
    return "float16" if device == "cuda" else "int8"


if __name__ == "__main__":
    with open(sys.argv[1], encoding="utf-8") as handle:
        payload = json.load(handle)
    try:
        result = transcribe_session(
            [Track(**row) for row in payload["tracks"]],
            model_name=payload.get("model", "small"),
            device=payload.get("device", "cpu"),
        )
    except (GmsUnavailableError, UnusableInputError) as error:
        print(f"{type(error).__name__}: {error}", file=sys.stderr)
        raise SystemExit(1)
    print(json.dumps(result, ensure_ascii=False, indent=2))
