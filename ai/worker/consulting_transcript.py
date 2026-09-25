"""컨설팅 세션 녹음 두 건을 화자가 붙은 병합 전사로 만든다.

참가자마다 자기 마이크만 담으므로(`R-CON-14`) **파일이 곧 화자다.** 화자 분리를 하지 않는다.

여기서 나온 배열을 `consulting_summary.summarize` 가 그대로 먹는다.
"""

from __future__ import annotations

import json
import os
import sys
from dataclasses import dataclass
from pathlib import Path

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

# 전사 중 오류가 이 말을 담고 있으면 오디오가 아니라 장비 탓으로 본다. CTranslate2 는 GPU
# 오류를 모두 `RuntimeError` 로 던져 종류로는 가를 수 없다.
GPU_ERROR_WORDS = ("CUDA", "cuBLAS", "cublas", "cuDNN", "cudnn", "out of memory")

# 기준 장비가 GPU 로 정해져 모델을 다시 골랐다 (`README.md` 「GPU 기준으로 다시 고름」).
#
# `small` 은 CPU 조건에서 고른 것이다. GPU 에서 `large-v3-turbo` 는 절반으로 느려지는 대신
# 「에이터베이터 주소」를 「데이터베이스 주소」로, 「A P I T」를 「API키」로 받아 적는다.
# 90분 두 트랙이 8.5분이라 어차피 비동기인 이상 속도 손해가 값을 갖지 않는다.
DEFAULT_MODEL = "large-v3-turbo"

# 기준 장비가 GPU 다. CPU 로도 돌지만 훨씬 느리고, 그 조건은 다시 재야 한다.
DEFAULT_DEVICE = "cuda"

# 전사는 GMS 를 부르지 않지만 실패 갈래는 `ai/AGENTS.md` 5 를 그대로 따른다.
#
#   모델을 올리지 못함·GPU 가 멈춤  -> GmsUnavailableError  (장비 문제. 다시 걸면 될 수 있다)
#   오디오를 읽지 못함              -> UnusableInputError   (다시 걸어도 소용없다. 사람이 봐야 한다)


@dataclass(frozen=True)
class Track:
    """전사할 트랙 하나.

    두 녹음은 통화가 처음 붙은 순간에 함께 켜지므로 같은 시각에 시작한 것으로 본다.
    """

    speaker: str
    path: str


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
            "startMs": round(piece["start"] * 1000),
            "endMs": round(piece["end"] * 1000),
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
        }
        for index, row in enumerate(rows)
    ]


def load_model(name: str, device: str, compute_type: str):
    """모델을 올리고 한 번 돌려 본다. 어느 쪽이든 실패하면 전사 실패 갈래로 바꿔 던진다.

    **올리기만 해서는 GPU 가 되는지 모른다.** CUDA 라이브러리는 첫 전사 때 읽힌다. 확인하지
    않으면 라이브러리가 없는 것을 첫 녹음에서야 알게 되고, 그 오류가 오디오 탓으로 분류된다.
    """
    try:
        if device == "cuda":
            _add_cuda_dll_directories()
        import numpy
        from faster_whisper import WhisperModel

        model = WhisperModel(name, device=device, compute_type=compute_type)
        segments, _ = model.transcribe(numpy.zeros(16000, dtype=numpy.float32), language="ko")
        list(segments)
        return model
    except Exception as error:  # 설치·장비 문제를 한 갈래로 모은다
        raise GmsUnavailableError(str(error)) from error


def _add_cuda_dll_directories() -> None:
    """pip 로 깐 CUDA 라이브러리(`nvidia-cublas-cu12` 등)를 Windows 가 찾게 한다.

    Linux 는 패키지가 스스로 경로를 잡지만 Windows 는 `bin` 폴더를 알려 줘야 한다.
    """
    if sys.platform != "win32":
        return
    try:
        import nvidia
    except ImportError:
        return
    for root in nvidia.__path__:
        for folder in Path(root).glob("*/bin"):
            os.add_dll_directory(str(folder))
            os.environ["PATH"] = f"{folder}{os.pathsep}{os.environ['PATH']}"


def transcribe_track(model, track: Track, gap_seconds: float = SPLIT_GAP_SECONDS) -> dict:
    """트랙 하나를 전사해 조각으로 돌려준다.

    **VAD 를 켠다.** 트랙의 절반 이상이 침묵이라 그대로 넣으면 느리고, 침묵 구간이 그대로
    모델에 들어간다. 다만 VAD 만으로 환각이 막히지는 않는다 — 그건 위 상수가 맡는다.
    """
    try:
        segments, _ = model.transcribe(
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
        # GPU 메모리가 모자라거나 CUDA 라이브러리가 떨어진 것은 장비 문제라 다시 걸면 된다.
        if any(word in str(error) for word in GPU_ERROR_WORDS):
            raise GmsUnavailableError(f"{track.path} 를 전사하다 GPU 가 멈췄습니다: {error}") from error
        raise UnusableInputError(f"{track.path} 를 읽지 못했습니다: {error}") from error

    return {"speaker": track.speaker, "pieces": pieces}


def transcribe_session(
    tracks: list[Track],
    *,
    model=None,
    model_name: str = DEFAULT_MODEL,
    device: str = DEFAULT_DEVICE,
    compute_type: str | None = None,
    gap_seconds: float = SPLIT_GAP_SECONDS,
) -> dict:
    """세션 하나의 트랙들을 전사해 병합 전사를 만든다.

    **한쪽 트랙이 비어도 정상이다.** 말을 하지 않은 참가자가 있을 수 있고, 그 경우에도
    나머지 한쪽으로 요약을 만든다 (`R-CON-16`).

    `model` 을 주면 그것을 쓴다. 작업을 계속 받아 가는 쪽은 한 번 올린 모델을 되쓴다 —
    올리는 데만 몇 초가 걸린다.
    """
    if not tracks:
        raise ValueError("전사할 트랙이 없습니다")

    if model is None:
        model = load_model(model_name, device, compute_type or _default_compute(device))
    done = [transcribe_track(model, track, gap_seconds) for track in tracks]
    return {"utterances": merge(done)}


def _default_compute(device: str) -> str:
    return "float16" if device == "cuda" else "int8"


if __name__ == "__main__":
    with open(sys.argv[1], encoding="utf-8") as handle:
        payload = json.load(handle)
    try:
        result = transcribe_session(
            [Track(**row) for row in payload["tracks"]],
            model_name=payload.get("model", DEFAULT_MODEL),
            device=payload.get("device", DEFAULT_DEVICE),
        )
    except (GmsUnavailableError, UnusableInputError) as error:
        print(f"{type(error).__name__}: {error}", file=sys.stderr)
        raise SystemExit(1)
    print(json.dumps(result, ensure_ascii=False, indent=2))
