import json
import sys
import unittest
from pathlib import Path
from unittest.mock import patch
from urllib.error import HTTPError, URLError

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from qa_suggestion import (  # noqa: E402
    QA_MAX_OUTPUT_TOKENS,
    GmsChatClient,
    GmsUnavailableError,
    InvalidGmsResponseError,
    parse_suggestions,
    suggest,
)


GOOD = [
    {"testCase": "로그인하지 않고 관리자 화면을 열면 로그인 화면이 나타난다."},
    {"testCase": "일반 회원으로 관리자 화면을 열면 접근이 거부된다."},
    {"testCase": "관리자로 로그인해 관리자 화면을 열면 회원 목록이 나타난다."},
]


class FakeResponse:
    """`urlopen` 이 돌려주는 것처럼 행세한다. `json.load` 가 `read` 만 쓴다."""

    def __init__(self, body):
        self.body = json.dumps(body).encode()

    def read(self, *args):
        return self.body

    def __enter__(self):
        return self

    def __exit__(self, *args):
        return False


class FakeClient:
    def __init__(self, raw):
        self.raw = raw
        self.messages = None
        self.max_tokens = None

    def complete(self, messages, max_tokens=None):
        self.messages = messages
        self.max_tokens = max_tokens
        return self.raw


class QaSuggestionTest(unittest.TestCase):
    def test_valid_result_and_one_requirement_input(self):
        client = FakeClient(json.dumps({"content": GOOD}, ensure_ascii=False))
        self.assertEqual(suggest("관리자만 관리 화면을 사용할 수 있다.", client), GOOD)
        self.assertEqual(client.messages[-1]["content"], "관리자만 관리 화면을 사용할 수 있다.")

    def test_rejects_invalid_json_shape_and_count(self):
        bad = [
            "not json",
            json.dumps({"suggestions": GOOD}),
            json.dumps({"content": GOOD[:2]}),
            json.dumps({"content": GOOD + GOOD[:1]}),
            json.dumps({"content": [{"testCase": "문장", "id": 1}, *GOOD[1:]]}),
        ]
        for raw in bad:
            with self.subTest(raw=raw), self.assertRaises(InvalidGmsResponseError):
                parse_suggestions(raw)

    def test_rejects_empty_long_and_duplicate_sentences(self):
        for replacement in ("", " " * 3, "가" * 501, GOOD[1]["testCase"]):
            rows = [{"testCase": replacement}, *GOOD[1:]]
            with self.subTest(replacement=replacement[:20]), self.assertRaises(InvalidGmsResponseError):
                parse_suggestions(json.dumps({"content": rows}, ensure_ascii=False))

    def test_missing_key_never_sends_request(self):
        with self.assertRaises(GmsUnavailableError):
            GmsChatClient({})

    def test_network_and_server_errors(self):
        client = GmsChatClient({"GMS_API_KEY": "test-key"})
        for error in (
            URLError("offline"),
            HTTPError("https://example.invalid", 503, "unavailable", {}, None),
        ):
            with self.subTest(error=type(error).__name__):
                with patch("qa_suggestion.urlopen", side_effect=error):
                    with self.assertRaises(GmsUnavailableError):
                        client.complete([{"role": "user", "content": "test"}], max_tokens=16)

    def test_empty_requirement(self):
        with self.assertRaises(ValueError):
            suggest("   ", FakeClient(""))

    def test_suggest_uses_its_own_output_limit(self):
        """기본값(256)으로 부르면 긴 요구사항의 응답이 잘린다. 작업별 한도를 넘겨야 한다."""
        client = FakeClient(json.dumps({"content": GOOD}, ensure_ascii=False))
        suggest("관리자만 관리 화면을 사용할 수 있다.", client)
        self.assertEqual(client.max_tokens, QA_MAX_OUTPUT_TOKENS)

    def test_truncated_response_says_it_was_cut(self):
        """한도에 걸린 응답은 JSON 이 반만 온다. 모델 탓이 아니라 한도 탓임을 말해야 한다."""
        client = GmsChatClient({"GMS_API_KEY": "test-key"})
        body = {
            "choices": [{"finish_reason": "length", "message": {"content": '{"content":[{"te'}}]
        }
        with patch("qa_suggestion.urlopen", return_value=FakeResponse(body)):
            with self.assertRaises(InvalidGmsResponseError) as caught:
                client.complete([{"role": "user", "content": "test"}], max_tokens=16)
        self.assertIn("잘렸습니다", str(caught.exception))

    def test_출력_한도는_모든_모델이_받는_이름으로_싣는다(self):
        """gpt-5 계열은 `max_tokens` 를 400 으로 거절한다."""
        client = GmsChatClient({"GMS_API_KEY": "test-key", "GMS_MODEL": "gpt-4.1-nano"})
        body = {"choices": [{"finish_reason": "stop", "message": {"content": "{}"}}]}
        with patch("qa_suggestion.urlopen", return_value=FakeResponse(body)) as urlopen:
            client.complete([{"role": "user", "content": "test"}], max_tokens=16)

        sent = json.loads(urlopen.call_args.args[0].data)
        self.assertEqual(sent["max_completion_tokens"], 16)
        self.assertNotIn("max_tokens", sent)
        self.assertEqual(sent["model"], "gpt-4.1-nano")
        self.assertEqual(urlopen.call_args.kwargs["timeout"], 20)

    def test_JSON_객체를_요청하면_응답_형식을_싣는다(self):
        client = GmsChatClient({"GMS_API_KEY": "test-key"})
        body = {"choices": [{"finish_reason": "stop", "message": {"content": "{}"}}]}
        with patch("qa_suggestion.urlopen", return_value=FakeResponse(body)) as urlopen:
            client.complete([{"role": "user", "content": "JSON"}], max_tokens=16, json_object=True)
            client.complete([{"role": "user", "content": "JSON"}], max_tokens=16)

        first, second = (json.loads(call.args[0].data) for call in urlopen.call_args_list)
        self.assertEqual(first["response_format"], {"type": "json_object"})
        self.assertNotIn("response_format", second)

    def test_작업이_준_모델과_한도를_쓴다(self):
        client = GmsChatClient({"GMS_API_KEY": "test-key", "GMS_MODEL": "gpt-4.1-nano"})
        body = {"choices": [{"finish_reason": "stop", "message": {"content": "{}"}}]}
        with patch("qa_suggestion.urlopen", return_value=FakeResponse(body)) as urlopen:
            client.complete([{"role": "user", "content": "test"}], max_tokens=16, model="gpt-5-mini", timeout=120)

        self.assertEqual(json.loads(urlopen.call_args.args[0].data)["model"], "gpt-5-mini")
        self.assertEqual(urlopen.call_args.kwargs["timeout"], 120)


if __name__ == "__main__":
    unittest.main()
