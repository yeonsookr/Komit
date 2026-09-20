"""클로바노트 전사와 로컬 Whisper 전사를 비교하는 N7용 실행 스크립트.

클로바노트 전사는 사람이 교정한 정답이 아니므로, 아래 CER는 모델의 절대 정확도가
아니라 두 자동 전사 결과가 얼마나 다른지를 나타낸다.
"""

from __future__ import annotations

import argparse
import csv
from datetime import datetime
from importlib.metadata import version
import json
import os
from pathlib import Path
import platform
import re
import shutil
import sys
import threading
import time
import unicodedata


# 클로바노트 txt의 발화 시작 줄 예: "참석자 1 00:50".
# 제목·생성 시각·빈 줄을 전사 내용에 섞지 않기 위해 이 줄을 기준으로 분리한다.
SPEAKER_LINE = re.compile(r"^참석자\s+\d+\s+(\d{1,2}:\d{2}(?::\d{2})?)$")


def timestamp_seconds(value: str) -> int:
    """클로바노트의 MM:SS 또는 HH:MM:SS 표시를 초 단위로 바꾼다."""
    parts = [int(part) for part in value.split(":")]
    if len(parts) == 2:
        return parts[0] * 60 + parts[1]
    return parts[0] * 3600 + parts[1] * 60 + parts[2]


def read_clova_text(path: Path, seconds: int) -> str:
    """참석자/시간 표시를 버리고 비교 구간의 발화 텍스트만 모은다.

    seconds=0이면 전체 발화를 읽는다. 짧은 구간을 시험할 때는 해당 시각 전에
    시작한 클로바 발화를 고른다. 경계에 걸친 마지막 발화는 일부가 비교 구간 밖에
    있을 수 있으므로 짧은 구간의 유사도는 대략적인 참고값이다.
    """
    utterances: list[tuple[int, str]] = []
    start: int | None = None
    lines: list[str] = []

    for raw_line in path.read_text(encoding="utf-8-sig").splitlines():
        line = raw_line.strip()
        match = SPEAKER_LINE.fullmatch(line)
        if match:
            if start is not None:
                utterances.append((start, " ".join(lines)))
            start = timestamp_seconds(match.group(1))
            lines = []
        elif start is not None and line:
            lines.append(line)

    if start is not None:
        utterances.append((start, " ".join(lines)))
    if not utterances:
        raise ValueError("클로바 txt에서 '참석자 1 00:00' 형식의 발화를 찾지 못했습니다.")

    selected = [text for start, text in utterances if seconds == 0 or start < seconds]
    return " ".join(selected)


def normalize_for_comparison(text: str) -> str:
    """표기 차이가 CER를 지배하지 않도록 공백·문장부호를 제외한다.

    이 정규화는 대조용으로만 사용한다. 실제 전사 결과 파일은 수정하지 않는다.
    Unicode 정규화와 소문자화를 적용한 뒤 `isalnum()`인 문자만 남긴다.
    따라서 한글·영문·숫자는 유지되고 공백과 마침표·쉼표 등 특수기호는 빠진다.
    개발 용어와 금액의 실질적인 글자 차이는 비교에 반영된다.
    """
    normalized = unicodedata.normalize("NFKC", text).casefold()
    return "".join(character for character in normalized if character.isalnum())


def calculate_cer(reference: str, hypothesis: str) -> tuple[int, float, float]:
    """정규화된 두 문자열의 편집 거리, CER, 보조 유사도를 반환한다."""
    if not reference:
        raise ValueError("CER 기준 문자열은 비어 있을 수 없습니다.")

    from rapidfuzz.distance import Levenshtein

    edit_distance = Levenshtein.distance(reference, hypothesis)
    cer = edit_distance / len(reference)
    similarity = Levenshtein.normalized_similarity(reference, hypothesis)
    return edit_distance, cer, similarity


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--input-dir", type=Path, help="같은 이름의 음성·txt가 있는 폴더")
    source.add_argument("--audio", type=Path, help="음성 파일 하나만 비교할 때 사용")
    parser.add_argument("--reference", type=Path, help="--audio와 함께 사용할 클로바 txt")
    parser.add_argument(
        "--list-only",
        action="store_true",
        help="짝이 맞는 파일 목록만 표시하고 모델은 실행하지 않음",
    )
    parser.add_argument(
        "--seconds",
        type=int,
        default=300,
        help="처음부터 비교할 길이(초). 기본 300초, 전체 파일은 0",
    )
    parser.add_argument(
        "--models",
        nargs="+",
        default=["base", "small", "medium"],
        help="비교할 모델 이름. 기본: base small medium",
    )
    return parser.parse_args()


def gibibytes(byte_count: int) -> float:
    """바이트를 운영체제에서 흔히 쓰는 GiB 단위로 바꾼다."""
    return round(byte_count / (1024 ** 3), 2)


def audio_duration_seconds(path: Path) -> float:
    """faster-whisper의 의존성인 PyAV로 원본 음성 길이를 읽는다."""
    import av

    with av.open(str(path)) as container:
        if container.duration is None:
            raise ValueError("음성 길이 정보를 읽을 수 없습니다.")
        return container.duration / 1_000_000


def machine_environment(root: Path) -> dict[str, object]:
    """결과 재현에 필요한 장비·실행 환경을 실행 시점 값으로 기록한다.

    디스크가 SSD인지 HDD인지는 Python에서 신뢰성 있게 판별하지 못하므로 추측하지
    않고, 실제로 확인 가능한 전체/여유 공간과 입력 폴더 위치만 남긴다.
    """
    import psutil

    disk = shutil.disk_usage(root)
    memory = psutil.virtual_memory()
    return {
        "recorded_at": datetime.now().astimezone().isoformat(timespec="seconds"),
        "os": platform.platform(),
        "python": sys.version.split()[0],
        "processor": platform.processor() or os.environ.get("PROCESSOR_IDENTIFIER", "unknown"),
        "physical_cpu_cores": psutil.cpu_count(logical=False),
        "logical_cpu_cores": psutil.cpu_count(logical=True),
        "ram_total_gib": gibibytes(memory.total),
        "disk_path": str(root.resolve()),
        "disk_total_gib": gibibytes(disk.total),
        "disk_free_gib_before_test": gibibytes(disk.free),
        "packages": {
            "faster-whisper": version("faster-whisper"),
            "ctranslate2": version("ctranslate2"),
            "rapidfuzz": version("rapidfuzz"),
            "psutil": version("psutil"),
        },
    }


def monitor_resources(
    stop: threading.Event, memory_samples: list[int], cpu_samples: list[float]
) -> None:
    """전사 중 프로세스 메모리와 CPU 사용률을 약 0.1초마다 측정한다.

    psutil의 프로세스 CPU 사용률은 한 논리 코어를 완전히 사용하면 약 100%다.
    여러 코어를 동시에 사용하면 100%를 넘을 수 있으므로 값을 임의로 제한하지 않는다.
    """
    import psutil

    process = psutil.Process()
    process.cpu_percent(interval=None)
    while not stop.is_set():
        memory_samples.append(process.memory_info().rss)
        cpu_samples.append(process.cpu_percent(interval=None))
        stop.wait(0.1)
    memory_samples.append(process.memory_info().rss)
    cpu_samples.append(process.cpu_percent(interval=None))


def collect_cases(args: argparse.Namespace) -> tuple[Path, list[tuple[Path, Path, str]], list[str]]:
    """음성과 같은 이름의 txt를 짝지어 모델에 넣을 목록을 만든다.

    폴더 모드에서는 바로 아래 파일만 찾는다. 이전 실행의 stt_results 폴더나
    다른 중첩 폴더는 읽지 않는다. txt가 없는 음성은 전사만 하는 대신 건너뛰어
    모든 행이 동일한 '클로바와 비교' 조건을 갖게 한다.
    """
    if args.input_dir:
        if args.reference:
            raise SystemExit("--input-dir과 --reference는 함께 사용하지 않습니다.")
        if not args.input_dir.is_dir():
            raise SystemExit("--input-dir에 존재하는 폴더를 지정하세요.")
        root = args.input_dir
        audio_files = sorted(
            path for path in root.iterdir()
            if path.is_file() and path.suffix.lower() in {".m4a", ".mp3", ".wav", ".aac"}
        )
        if not audio_files:
            raise SystemExit("폴더에서 비교할 음성 파일을 찾지 못했습니다.")
        candidates = [(audio, audio.with_suffix(".txt")) for audio in audio_files]
    else:
        if not args.reference or not args.audio.is_file() or not args.reference.is_file():
            raise SystemExit("--audio와 --reference에 존재하는 파일을 지정하세요.")
        root = args.audio.parent
        candidates = [(args.audio, args.reference)]

    cases: list[tuple[Path, Path, str]] = []
    skipped: list[str] = []
    for audio, reference_path in candidates:
        if not reference_path.is_file():
            skipped.append(f"{audio.name}: 같은 이름의 txt 없음")
            continue
        try:
            reference = normalize_for_comparison(read_clova_text(reference_path, args.seconds))
        except (OSError, UnicodeError, ValueError) as error:
            skipped.append(f"{audio.name}: 클로바 txt를 읽을 수 없음 ({error})")
            continue
        if not reference:
            skipped.append(f"{audio.name}: 선택한 구간에 클로바 전사 없음")
            continue
        cases.append((audio, reference_path, reference))
    if not cases:
        raise SystemExit("클로바 txt와 짝이 맞는 비교 대상이 없습니다.")
    return root, cases, skipped


def main() -> None:
    args = parse_args()
    if args.seconds < 0:
        raise SystemExit("--seconds는 0 이상의 정수여야 합니다.")
    if not args.models or len(set(args.models)) != len(args.models):
        raise SystemExit("--models에는 중복되지 않은 모델 이름을 하나 이상 지정하세요.")
    root, cases, skipped = collect_cases(args)

    # 모델 로드 전에 실제 비교 대상부터 명확히 보여준다. 새 txt를 넣은 뒤
    # 담당자 개인 개발 관련 녹취본이 비교 대상에서 빠지지 않았는지 여기서 확인할 수 있다.
    scope = "파일 전체" if args.seconds == 0 else f"파일당 처음 {args.seconds}초"
    print(f"비교 대상: {len(cases)}쌍 ({scope})")
    for audio, reference, _ in cases:
        print(f"  {audio.name} ↔ {reference.name}")
    for reason in skipped:
        print(f"건너뜀: {reason}")
    if args.list_only:
        return

    # 경로는 코드에 고정하지 않는다. 결과는 입력 폴더 안의 로컬 결과 폴더에
    # 저장한다. 입력 폴더 자체를 저장소 밖에 두어 원문이 Git에 섞이지 않게 한다.
    result_dir = root / "stt_results" / datetime.now().strftime("%Y%m%d_%H%M%S_%f")
    result_dir.mkdir(parents=True, exist_ok=False)

    # 라이브러리는 여기서 불러와 인자·파일 형식 오류를 먼저 확인한다.
    # 첫 모델 로드에서는 Hugging Face에서 파일을 내려받을 수 있다.
    from faster_whisper import WhisperModel

    summary = {
        "environment": machine_environment(root),
        "models": args.models,
        "seconds": args.seconds,
        "settings": {"device": "cpu", "compute_type": "int8", "language": "ko", "beam_size": 5},
        "comparison": "특수기호·공백 제거 후 클로바 자동 전사 기준 CER; 정답 정확도 아님",
        "skipped": skipped,
        "files": [
            {"audio": audio.name, "reference": reference.name, "models": {}}
            for audio, reference, _ in cases
        ],
    }

    for model_name in args.models:
        # 모델은 파일마다 다시 다운로드/로드하지 않고 한 번만 연다.
        # 모든 모델에 CPU·INT8·언어·beam_size·입력 구간을 동일하게 적용한다.
        load_started = time.perf_counter()
        try:
            model = WhisperModel(model_name, device="cpu", compute_type="int8")
        except Exception as error:
            # 한 모델의 다운로드·로드 실패가 다른 모델 결과까지 없애지 않게 한다.
            load_seconds = time.perf_counter() - load_started
            message = f"{type(error).__name__}: {error}"
            print(f"{model_name} 모델 로드 실패: {message}")
            for file_result in summary["files"]:
                file_result["models"][model_name] = {
                    "status": "failed",
                    "stage": "model_load",
                    "model_load_seconds": round(load_seconds, 2),
                    "error": message,
                }
            continue
        load_seconds = time.perf_counter() - load_started

        for index, (audio, _, normalized_reference) in enumerate(cases):
            print(f"[{index + 1}/{len(cases)}] {audio.name} — {model_name} 전사 중...", flush=True)
            options = {"language": "ko", "beam_size": 5}
            if args.seconds:
                options["clip_timestamps"] = f"0,{args.seconds}"

            stop_monitor = threading.Event()
            memory_samples: list[int] = []
            cpu_samples: list[float] = []
            monitor = threading.Thread(
                target=monitor_resources,
                args=(stop_monitor, memory_samples, cpu_samples),
                daemon=True,
            )
            transcribe_started = time.perf_counter()
            monitor.start()
            try:
                segments, _ = model.transcribe(str(audio), **options)
                # segments는 지연 평가된다. 끝까지 순회해야 실제 전사가 끝나므로
                # 순회가 완료된 뒤 시간을 멈춘다.
                recognized = " ".join(segment.text.strip() for segment in segments)
                transcribe_seconds = time.perf_counter() - transcribe_started
            except Exception as error:
                transcribe_seconds = time.perf_counter() - transcribe_started
                message = f"{type(error).__name__}: {error}"
                summary["files"][index]["models"][model_name] = {
                    "status": "failed",
                    "stage": "transcription",
                    "model_load_seconds": round(load_seconds, 2),
                    "transcribe_seconds_before_failure": round(transcribe_seconds, 2),
                    "error": message,
                }
                print(f"{audio.name} / {model_name}: 실패 ({message})")
                continue
            finally:
                stop_monitor.set()
                monitor.join()

            total_audio_seconds = audio_duration_seconds(audio)
            evaluated_audio_seconds = (
                total_audio_seconds
                if args.seconds == 0
                else min(total_audio_seconds, float(args.seconds))
            )
            realtime_factor = transcribe_seconds / evaluated_audio_seconds
            processing_speed = evaluated_audio_seconds / transcribe_seconds

            # CER = (치환 + 삭제 + 삽입 횟수) / 클로바 기준 문자열의 글자 수.
            # 한국어 띄어쓰기와 구두점 표기 차이가 점수를 왜곡하지 않게 두 문자열은
            # 위 normalize_for_comparison()에서 한글·영문·숫자만 남긴다.
            # 클로바 역시 자동 전사이므로 이 CER는 절대 정확도가 아니라
            # '클로바 결과와의 차이'를 나타내는 비교 지표다.
            normalized_result = normalize_for_comparison(recognized)
            edit_distance, cer, similarity = calculate_cer(
                normalized_reference, normalized_result
            )

            # 전사 원문은 터미널이나 요약 표에 넣지 않는다. 파일별 폴더에만
            # 저장하므로 사용자가 필요한 결과를 로컬에서 직접 열어볼 수 있다.
            file_dir = result_dir / audio.stem
            file_dir.mkdir(exist_ok=True)
            (file_dir / f"{model_name}.txt").write_text(recognized, encoding="utf-8")
            summary["files"][index]["models"][model_name] = {
                "status": "success",
                "device": "cpu",
                "compute_type": "int8",
                "model_load_seconds": round(load_seconds, 2),
                "audio_file_bytes": audio.stat().st_size,
                "audio_total_seconds": round(total_audio_seconds, 2),
                "evaluated_audio_seconds": round(evaluated_audio_seconds, 2),
                "transcribe_seconds": round(transcribe_seconds, 2),
                "realtime_factor": round(realtime_factor, 4),
                "processing_speed_x_realtime": round(processing_speed, 2),
                "peak_process_memory_mib": round(max(memory_samples) / (1024 ** 2), 2),
                "average_process_cpu_percent": round(sum(cpu_samples) / len(cpu_samples), 2),
                "peak_process_cpu_percent": round(max(cpu_samples), 2),
                "reference_characters": len(normalized_reference),
                "edit_distance": edit_distance,
                "clova_reference_cer": round(cer, 4),
                "clova_reference_cer_percent": round(cer * 100, 2),
                "clova_character_similarity_percent": round(similarity * 100, 2),
            }
            print(
                f"{audio.name} / {model_name}: 전사 {transcribe_seconds:.1f}초, "
                f"실시간 대비 {processing_speed:.2f}배속, "
                f"최대 메모리 {max(memory_samples) / (1024 ** 2):.0f}MiB, "
                f"평균 CPU {sum(cpu_samples) / len(cpu_samples):.0f}%, "
                f"클로바 기준 CER {cer * 100:.1f}%"
            )

    # 한눈에 비교할 CSV와 설정까지 보존한 JSON을 함께 저장한다.
    # CSV에는 전사 원문을 넣지 않는다.
    with (result_dir / "summary.csv").open("w", encoding="utf-8-sig", newline="") as output:
        writer = csv.writer(output)
        writer.writerow([
            "audio", "model", "status", "evaluated_audio_seconds", "transcribe_seconds",
            "realtime_factor", "processing_speed_x_realtime", "peak_process_memory_mib",
            "average_process_cpu_percent", "peak_process_cpu_percent",
            "reference_characters", "edit_distance", "clova_reference_cer_percent",
            "clova_character_similarity_percent", "error",
        ])
        for file_result in summary["files"]:
            for model_name in args.models:
                result = file_result["models"][model_name]
                writer.writerow([
                    file_result["audio"], model_name, result["status"],
                    result.get("evaluated_audio_seconds", ""),
                    result.get("transcribe_seconds", result.get("transcribe_seconds_before_failure", "")),
                    result.get("realtime_factor", ""),
                    result.get("processing_speed_x_realtime", ""),
                    result.get("peak_process_memory_mib", ""),
                    result.get("average_process_cpu_percent", ""),
                    result.get("peak_process_cpu_percent", ""),
                    result.get("reference_characters", ""),
                    result.get("edit_distance", ""),
                    result.get("clova_reference_cer_percent", ""),
                    result.get("clova_character_similarity_percent", ""),
                    result.get("error", ""),
                ])

    disk_after = shutil.disk_usage(root)
    summary["environment"]["disk_free_gib_after_test"] = gibibytes(disk_after.free)

    (result_dir / "summary.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print(f"로컬 결과 폴더: {result_dir}")


if __name__ == "__main__":
    main()
