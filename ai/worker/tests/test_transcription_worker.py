import io
import json
import sys
import unittest
import urllib.error
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from qa_suggestion import GmsUnavailableError, UnusableInputError  # noqa: E402
from transcription_worker import (  # noqa: E402
    RETRYABLE,
    UNUSABLE,
    InvalidJobError,
    Job,
    ServiceClient,
    ServiceUnreachableError,
    TokenRejectedError,
    parse_job,
    run_once,
)

JOB = Job(
    session_id=55,
    tracks=[
        {"speaker": "CLIENT", "audioUrl": "https://file.example/a"},
        {"speaker": "DEVELOPER", "audioUrl": "https://file.example/b"},
    ],
)

RESULT = {"utterances": [{"utteranceIndex": 0, "speaker": "CLIENT"}]}


class FakeClient:
    """Service 를 부르지 않는다. 무엇을 불렀는지만 적어 둔다."""

    def __init__(self, job=JOB, download_error=None):
        self.job = job
        self.download_error = download_error
        self.downloaded = []
        self.results = []
        self.failures = []

    def claim(self):
        return self.job

    def download(self, url, destination):
        if self.download_error:
            raise self.download_error
        destination.write_bytes(b"audio")
        self.downloaded.append((url, destination))

    def submit_result(self, job, result):
        self.results.append((job, result))

    def report_failure(self, job, code, message):
        self.failures.append((job, code, message))


class RunOnce(unittest.TestCase):
    def test_할_일이_없으면_아무것도_하지_않는다(self):
        client = FakeClient(job=None)

        self.assertEqual(run_once(client, lambda tracks: RESULT), "idle")
        self.assertEqual(client.downloaded, [])

    def test_받은_녹음을_전사해_결과를_올린다(self):
        client = FakeClient()
        seen = []

        def transcribe(tracks):
            seen.extend(tracks)
            return RESULT

        self.assertEqual(run_once(client, transcribe), "done")
        self.assertEqual([url for url, _ in client.downloaded], ["https://file.example/a", "https://file.example/b"])
        self.assertEqual([t.speaker for t in seen], ["CLIENT", "DEVELOPER"])
        self.assertEqual(client.results, [(JOB, RESULT)])

    def test_전사가_끝나면_받은_녹음을_남기지_않는다(self):
        client = FakeClient()
        run_once(client, lambda tracks: RESULT)

        for _, path in client.downloaded:
            self.assertFalse(path.exists())

    def test_모델을_못_올리면_다시_받아_가게_알린다(self):
        client = FakeClient()

        def transcribe(tracks):
            raise GmsUnavailableError("CUDA 를 찾지 못했습니다")

        self.assertEqual(run_once(client, transcribe), "retry")
        self.assertEqual(client.failures[0][1], RETRYABLE)
        self.assertEqual(client.results, [])

    def test_녹음을_못_받으면_다시_받아_가게_알린다(self):
        client = FakeClient(download_error=ServiceUnreachableError("주소가 만료됐습니다"))

        self.assertEqual(run_once(client, lambda tracks: RESULT), "retry")
        self.assertEqual(client.failures[0][1], RETRYABLE)

    def test_오디오를_못_쓰면_사람이_보게_알린다(self):
        client = FakeClient()

        def transcribe(tracks):
            raise UnusableInputError("디코딩되지 않습니다")

        self.assertEqual(run_once(client, transcribe), "unusable")
        self.assertEqual(client.failures[0][1], UNUSABLE)


def claim_body(**track):
    row = {"speaker": "CLIENT", "audioUrl": "https://file.example/a", **track}
    return {"sessionId": 55, "tracks": [row]}


class ParseJob(unittest.TestCase):
    def test_계약대로_오면_작업으로_읽는다(self):
        job = parse_job(claim_body())

        self.assertEqual((job.session_id, job.tracks[0]["speaker"]), (55, "CLIENT"))

    def test_필드가_빠지면_작업으로_읽지_않는다(self):
        with self.assertRaises(InvalidJobError):
            parse_job({"tracks": []})

    def test_모르는_화자는_받지_않는다(self):
        # 화자 이름으로 파일을 만든다. 경로가 섞이면 임시 폴더 밖에 쓴다.
        with self.assertRaises(InvalidJobError):
            parse_job(claim_body(speaker="../../CLIENT"))

    def test_같은_화자가_두_번_오면_받지_않는다(self):
        body = claim_body()
        body["tracks"].append(dict(body["tracks"][0]))
        with self.assertRaises(InvalidJobError):
            parse_job(body)

    def test_https_가_아닌_녹음_주소는_받지_않는다(self):
        for url in ("file:///C:/Users/secret.txt", "http://file.example/a", "ftp://file.example/a"):
            with self.subTest(url=url), self.assertRaises(InvalidJobError):
                parse_job(claim_body(audioUrl=url))

    def test_로컬_주소는_http_도_받는다(self):
        parse_job(claim_body(audioUrl="http://localhost:9000/recordings/a"))


class FakeResponse:
    def __init__(self, status, body=b""):
        self.status, self._body = status, body

    def read(self):
        return self._body

    def __enter__(self):
        return self

    def __exit__(self, *args):
        return False


def http_error(code):
    return urllib.error.HTTPError("https://x", code, "err", {}, io.BytesIO(b""))


class Client(unittest.TestCase):
    def setUp(self):
        self.client = ServiceClient("https://komit.example/service/", "secret-token")

    def test_주소와_토큰이_없으면_시작하지_않는다(self):
        with self.assertRaises(ValueError):
            ServiceClient("", "secret-token")
        with self.assertRaises(ValueError):
            ServiceClient("https://komit.example/service", "")

    def test_https_가_아닌_Service_주소로는_시작하지_않는다(self):
        # 토큰이 암호화 없이 나간다.
        with self.assertRaises(ValueError):
            ServiceClient("http://komit.example/service", "secret-token")

    def test_녹음을_받을_때도_https_만_연다(self):
        with mock.patch("urllib.request.urlopen") as urlopen:
            with self.assertRaises(ValueError):
                self.client.download("file:///C:/Users/secret.txt", mock.Mock())
        urlopen.assert_not_called()

    def test_할_일이_없으면_204_를_없음으로_읽는다(self):
        with mock.patch("urllib.request.urlopen", return_value=FakeResponse(204)):
            self.assertIsNone(self.client.claim())

    def test_받아_갈_때_토큰을_싣는다(self):
        body = json.dumps({"sessionId": 55, "tracks": []}).encode()
        with mock.patch("urllib.request.urlopen", return_value=FakeResponse(200, body)) as urlopen:
            job = self.client.claim()

        request = urlopen.call_args.args[0]
        self.assertEqual(request.full_url, "https://komit.example/service/api/transcription-jobs/claim")
        self.assertEqual(request.get_header("Authorization"), "Bearer secret-token")
        self.assertEqual(job, Job(session_id=55, tracks=[]))

    def test_녹음을_받을_때는_토큰을_싣지_않는다(self):
        destination = mock.Mock()
        with mock.patch("urllib.request.urlopen", return_value=FakeResponse(200, b"audio")) as urlopen:
            self.client.download("https://file.example/a?X-Amz-Signature=x", destination)

        # 서명된 주소를 문자열로만 넘긴다. 머리글을 붙일 자리가 없다.
        self.assertEqual(urlopen.call_args.args[0], "https://file.example/a?X-Amz-Signature=x")
        destination.write_bytes.assert_called_once_with(b"audio")

    def test_결과를_그_세션_경로로_올린다(self):
        with mock.patch("urllib.request.urlopen", return_value=FakeResponse(204)) as urlopen:
            self.client.submit_result(JOB, RESULT)

        request = urlopen.call_args.args[0]
        self.assertEqual(request.full_url, "https://komit.example/service/api/transcription-jobs/55/result")
        self.assertEqual(json.loads(request.data), RESULT)

    def test_토큰을_거절하면_멈추게_한다(self):
        with mock.patch("urllib.request.urlopen", side_effect=http_error(401)):
            with self.assertRaises(TokenRejectedError):
                self.client.claim()

    def test_서버가_고장나면_닿지_못한_것으로_본다(self):
        with mock.patch("urllib.request.urlopen", side_effect=http_error(503)):
            with self.assertRaises(ServiceUnreachableError):
                self.client.claim()

    def test_녹음_파일이_없으면_쓸_수_없는_것으로_본다(self):
        # 다시 받아 가게 두면 같은 세션이 계속 나와 뒤의 세션이 막힌다.
        with mock.patch("urllib.request.urlopen", side_effect=http_error(404)):
            with self.assertRaises(UnusableInputError):
                self.client.download("https://file.example/a", Path("CLIENT.webm"))

    def test_녹음_주소가_만료되면_닿지_못한_것으로_본다(self):
        with mock.patch("urllib.request.urlopen", side_effect=http_error(403)):
            with self.assertRaises(ServiceUnreachableError):
                self.client.download("https://file.example/a", mock.Mock())


if __name__ == "__main__":
    unittest.main()
