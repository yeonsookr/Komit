"""전사할 세션을 Service 에서 받아 가 GPU 로 전사하고 결과를 올린다.

**부르는 방향이 다른 작업과 반대다.** 확인 문장·계약 초안·요약은 Service 가 AI 를 부르지만,
전사는 이 프로그램이 Service 에 묻는다. 모델이 도는 노트북이 공유기 뒤에 있어 Service 가
먼저 닿을 수 없기 때문이다. 나가는 HTTPS 만 쓰므로 인터넷만 되면 어느 망에서든 돈다.

한 번 도는 순서.

    1. Service 에 할 일이 있는지 묻는다          POST /api/transcription-jobs/claim
    2. 받은 주소로 녹음을 저장소에서 직접 받는다   GET  <읽기 주소>
    3. 전사한다                                  consulting_transcript.transcribe_session
    4. 결과나 실패를 올린다                       POST /api/transcription-jobs/{id}/result|failure

녹음은 Service 를 지나지 않는다. Service 는 저장소 읽기 주소만 내준다 (`ai/AGENTS.md` 4).
"""

from __future__ import annotations

import json
import sys
import tempfile
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import urlsplit

from consulting_transcript import (
    DEFAULT_DEVICE,
    DEFAULT_MODEL,
    SPEAKERS,
    Track,
    _default_compute,
    load_model,
    transcribe_session,
)
from qa_suggestion import GmsUnavailableError, UnusableInputError, read_local_env

POLL_SECONDS = 5

# 모델을 못 올렸거나 Service·저장소에 닿지 못했을 때 기다리는 간격. 같은 고장으로 5초마다
# 작업을 받아 갔다 돌려주기를 되풀이하지 않게 한다.
BACKOFF_SECONDS = 30

HTTP_TIMEOUT_SECONDS = 30

# Service 가 이 둘을 보고 작업을 되돌릴지 사람에게 넘길지 정한다.
RETRYABLE = "TRANSCRIBER_UNAVAILABLE"
UNUSABLE = "AUDIO_UNUSABLE"

# 로컬에서 Spring·MinIO 를 띄워 시험할 때만 http 를 받는다.
LOCAL_HOSTS = ("localhost", "127.0.0.1")


class ServiceUnreachableError(Exception):
    """Service 나 저장소에 닿지 못했다. 결과가 없으니 Service 는 이 세션을 다시 내준다."""


class TokenRejectedError(Exception):
    """Service 가 토큰을 받지 않았다. 다시 물어도 같으므로 멈춘다."""


class InvalidJobError(Exception):
    """Service 가 준 작업이 계약과 다르다. 이 작업은 건너뛴다."""


def check_url(url: str) -> str:
    """https 주소만 받는다. `urlopen` 은 `file://` 도 열어 이 노트북의 파일을 읽는다."""
    parts = urlsplit(url)
    if parts.scheme == "https" or (parts.scheme == "http" and parts.hostname in LOCAL_HOSTS):
        return url
    raise ValueError(f"https 주소가 아닙니다: {parts.scheme}://{parts.hostname}")


@dataclass(frozen=True)
class Job:
    session_id: int
    tracks: list[dict]


class ServiceClient:
    """전사 작업 경로 셋과 녹음 내려받기만 한다."""

    def __init__(self, base_url: str, token: str) -> None:
        if not base_url or not token:
            raise ValueError("SERVICE_API_BASE_URL 과 TRANSCRIBER_TOKEN 이 필요합니다")
        self._base = check_url(base_url).rstrip("/")
        self._token = token

    def claim(self) -> Job | None:
        status, body = self._post("/api/transcription-jobs/claim", {})
        if status == 204:
            return None
        return parse_job(body)

    def submit_result(self, job: Job, result: dict) -> None:
        self._post(f"/api/transcription-jobs/{job.session_id}/result", result)

    def report_failure(self, job: Job, code: str, message: str) -> None:
        self._post(
            f"/api/transcription-jobs/{job.session_id}/failure",
            {"code": code, "message": message},
        )

    def download(self, url: str, destination: Path) -> None:
        # 토큰을 싣지 않는다. 읽기 주소는 서명으로 통하고, 여기에 토큰을 실으면 저장소 쪽
        # 기록에 전사 토큰이 남는다.
        check_url(url)
        try:
            with urllib.request.urlopen(url, timeout=HTTP_TIMEOUT_SECONDS) as response:
                destination.write_bytes(response.read())
        except (urllib.error.URLError, TimeoutError) as error:
            raise ServiceUnreachableError(f"녹음을 받지 못했습니다: {error}") from error

    def _post(self, path: str, payload: dict) -> tuple[int, dict | None]:
        request = urllib.request.Request(
            self._base + path,
            data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
            method="POST",
            headers={
                "Authorization": f"Bearer {self._token}",
                "Content-Type": "application/json",
            },
        )
        try:
            with urllib.request.urlopen(request, timeout=HTTP_TIMEOUT_SECONDS) as response:
                raw = response.read()
                return response.status, json.loads(raw) if raw else None
        except urllib.error.HTTPError as error:
            if error.code in (401, 403):
                raise TokenRejectedError(f"{path}: {error.code}") from error
            raise ServiceUnreachableError(f"{path}: {error.code}") from error
        except (urllib.error.URLError, TimeoutError) as error:
            raise ServiceUnreachableError(f"{path} 에 닿지 못했습니다: {error}") from error


def parse_job(body) -> Job:
    """받은 작업을 계약대로 읽는다. 어긋나면 `InvalidJobError` 다."""
    try:
        session_id, rows = body["sessionId"], body["tracks"]
        if not isinstance(session_id, int) or not isinstance(rows, list):
            raise TypeError("sessionId·tracks 의 형식이 다릅니다")
        tracks = []
        for row in rows:
            # 화자 이름으로 파일을 만든다. 경로가 섞이면 임시 폴더 밖에 쓴다.
            if row["speaker"] not in SPEAKERS:
                raise ValueError(f"모르는 화자입니다: {row['speaker']!r}")
            check_url(row["audioUrl"])
            tracks.append(row)
        if len({row["speaker"] for row in tracks}) != len(tracks):
            raise ValueError("같은 화자의 녹음이 두 번 왔습니다")
    except (KeyError, TypeError, ValueError) as error:
        raise InvalidJobError(f"받은 작업이 계약과 다릅니다: {error!r}") from error
    return Job(session_id=session_id, tracks=tracks)


def run_once(client, transcribe, workdir: Path | None = None) -> str:
    """작업 하나를 받아 끝까지 처리하고 무엇이 일어났는지 돌려준다.

    `idle` 할 일이 없었다 · `done` 결과를 올렸다 · `unusable` 사람이 봐야 한다고 알렸다 ·
    `retry` 다시 받아 가도록 알렸다
    """
    job = client.claim()
    if job is None:
        return "idle"

    # 녹음은 개인정보라 전사가 끝나면 이 노트북에 남기지 않는다.
    with tempfile.TemporaryDirectory(dir=workdir) as folder:
        try:
            tracks = []
            for row in job.tracks:
                path = Path(folder) / f"{row['speaker']}.webm"
                client.download(row["audioUrl"], path)
                tracks.append(Track(speaker=row["speaker"], path=str(path)))
            result = transcribe(tracks)
        except (GmsUnavailableError, ServiceUnreachableError) as error:
            client.report_failure(job, RETRYABLE, str(error))
            return "retry"
        except UnusableInputError as error:
            client.report_failure(job, UNUSABLE, str(error))
            return "unusable"

    client.submit_result(job, result)
    return "done"


def main() -> None:
    config = read_local_env()
    client = ServiceClient(config.get("SERVICE_API_BASE_URL", ""), config.get("TRANSCRIBER_TOKEN", ""))

    # 모델은 한 번만 올린다. 여기서 실패하면 받아 갈 일도 없으므로 바로 끝낸다.
    model = load_model(DEFAULT_MODEL, DEFAULT_DEVICE, _default_compute(DEFAULT_DEVICE))

    def transcribe(tracks: list[Track]) -> dict:
        return transcribe_session(tracks, model=model)

    while True:
        started = time.monotonic()
        try:
            outcome = run_once(client, transcribe)
        except (ServiceUnreachableError, InvalidJobError) as error:
            print(f"{type(error).__name__}: {error}", file=sys.stderr)
            time.sleep(BACKOFF_SECONDS)
            continue

        if outcome != "idle":
            print(json.dumps({"outcome": outcome, "seconds": round(time.monotonic() - started, 1)}), flush=True)
        time.sleep(BACKOFF_SECONDS if outcome == "retry" else POLL_SECONDS)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        pass
    except (TokenRejectedError, GmsUnavailableError) as error:
        print(f"{type(error).__name__}: {error}", file=sys.stderr)
        raise SystemExit(1)
