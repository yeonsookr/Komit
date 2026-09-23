import os
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

os.environ.setdefault("GMS_API_KEY", "테스트용")

import app as service  # noqa: E402
from qa_suggestion import (  # noqa: E402
    GmsUnavailableError,
    InvalidGmsResponseError,
)


SUGGESTIONS = (
    '{"content": ['
    '{"testCase": "탈퇴한 계정으로 로그인하면 마이페이지 진입이 막히는지 확인한다"},'
    '{"testCase": "탈퇴 직후 기존 세션으로 마이페이지를 열면 거부되는지 확인한다"},'
    '{"testCase": "탈퇴한 계정의 마이페이지 주소를 직접 입력하면 막히는지 확인한다"}'
    "]}"
)
CONSULTING_DRAFT = (
    '{"proceed": true, "draft": {"topic": "관리자 인증을 함께 봅니다", "detail": null}}'
)
SUMMARY = (
    '{"discussed": [{"title": "관리자 화면 접근 오류 논의", "detail": null,'
    ' "evidenceUtteranceIndexes": [0], "reviewRequired": false}],'
    ' "decided": [], "nextActions": [], "changes": []}'
)

MESSAGES = [
    {
        "chatRefId": "room-91-chat",
        "authorRole": "CLIENT",
        "createdAt": "2026-08-20T10:00:00+09:00",
        "content": "관리자 화면이 아무나 들어가집니다.",
    }
]
UTTERANCES = [
    {
        "utteranceIndex": 0,
        "speaker": "CLIENT",
        "rawText": "관리자 화면 접근 오류를 확인했어요.",
        "normalizedText": "관리자 화면 접근 오류를 확인했어요.",
    }
]


class FakeClient:
    def __init__(self, raw):
        self.raw = raw

    def complete(self, messages, max_tokens=None):
        return self.raw


class FailingClient:
    def __init__(self, error):
        self.error = error

    def complete(self, messages, max_tokens=None):
        raise self.error


def use(client):
    service.app.state.config = {"GMS_API_KEY": "테스트용"}
    service.build_client = lambda: client


class AiServiceTest(unittest.IsolatedAsyncioTestCase):
    def tearDown(self):
        service.build_client = AiServiceTest._real_build_client

    @classmethod
    def setUpClass(cls):
        cls._real_build_client = service.build_client

    def test_기동_확인은_모델을_부르지_않는다(self):
        use(FailingClient(GmsUnavailableError("불러선 안 된다")))
        self.assertEqual(service.health(), {"status": "UP"})

    def test_확인_문장_후보를_돌려준다(self):
        use(FakeClient(SUGGESTIONS))
        result = service.qa_suggestions(
            service.QaSuggestionRequest(requirement="회원 탈퇴 후에도 마이페이지에 접근할 수 있다")
        )
        self.assertEqual(len(result["content"]), 3)
        self.assertIn("testCase", result["content"][0])

    def test_계약_초안을_돌려준다(self):
        use(FakeClient(CONSULTING_DRAFT))
        result = service.contract_draft(
            service.ContractDraftRequest(targetType="CONSULTING", messages=MESSAGES)
        )
        self.assertEqual(result["draft"]["topic"], "관리자 인증을 함께 봅니다")

    def test_상담이_안_끝나면_초안_없이_성공한다(self):
        use(FakeClient('{"proceed": false, "draft": null}'))
        result = service.contract_draft(
            service.ContractDraftRequest(targetType="CONSULTING", messages=MESSAGES)
        )
        self.assertIsNone(result["draft"])

    def test_컨설팅_요약을_돌려준다(self):
        use(FakeClient(SUMMARY))
        result = service.consulting_summary(
            service.ConsultingSummaryRequest(utterances=UTTERANCES)
        )
        self.assertEqual(set(result), {"discussed", "decided", "nextActions", "changes"})
        self.assertIs(result["discussed"][0]["reviewRequired"], False)

    async def test_상류에_닿지_못하면_503(self):
        response = await service.unavailable(None, GmsUnavailableError("접속 실패"))
        self.assertEqual(response.status_code, 503)
        self.assertIn(service.UNAVAILABLE_CODE.encode(), response.body)

    async def test_계약을_어긴_결과는_502(self):
        response = await service.invalid(None, InvalidGmsResponseError("형식 위반"))
        self.assertEqual(response.status_code, 502)
        self.assertIn(service.INVALID_CODE.encode(), response.body)

    async def test_없는_경로도_같은_형식으로_돌려준다(self):
        from starlette.exceptions import HTTPException

        response = await service.refused(None, HTTPException(404, "Not Found"))
        self.assertEqual(response.status_code, 404)
        self.assertIn(service.REQUEST_CODE.encode(), response.body)

    async def test_입력을_거부하면_422_이고_사유를_싣는다(self):
        response = await service.rejected(None, ValueError("전사 발화가 비어 있습니다"))
        self.assertEqual(response.status_code, 422)
        self.assertIn(service.REQUEST_CODE.encode(), response.body)
        self.assertIn("전사 발화가 비어 있습니다".encode(), response.body)

    def test_모듈이_거부하는_입력은_예외로_올라간다(self):
        use(FakeClient(SUMMARY))
        with self.assertRaises(ValueError):
            service.consulting_summary(
                service.ConsultingSummaryRequest(utterances=[])
            )

    def test_필수_필드가_빠지면_모델이_거부한다(self):
        """계약 필드를 dict 로 받으면 여기서 통과하고 모듈이 KeyError 로 터져 500 이 된다."""
        from pydantic import ValidationError

        with self.assertRaises(ValidationError):
            service.ConsultingSummaryRequest(
                utterances=[{"utteranceIdx": 0, "speaker": "CLIENT", "normalizedText": "x"}]
            )

    def test_시각은_있으면_받고_없어도_된다(self):
        body = service.ConsultingSummaryRequest(
            utterances=[{**UTTERANCES[0], "startMs": 0, "endMs": 1200}]
        )
        self.assertEqual(body.utterances[0].endMs, 1200)
        self.assertIsNone(
            service.ConsultingSummaryRequest(utterances=UTTERANCES).utterances[0].startMs
        )

    def test_모듈에는_평범한_dict_로_넘긴다(self):
        body = service.ConsultingSummaryRequest(utterances=UTTERANCES)
        rows = service.dump(body.utterances)
        self.assertIsInstance(rows[0], dict)
        self.assertNotIn("startMs", rows[0])
        self.assertEqual(service.dump(None), [])

    def test_계약에_없는_필드는_받지_않는다(self):
        from pydantic import ValidationError

        with self.assertRaises(ValidationError):
            service.QaSuggestionRequest(requirement="요구사항", priority="HIGH")

    def test_키가_없으면_기동하지_않는다(self):
        original = service.read_local_env
        service.read_local_env = lambda: {"GMS_MODEL": "gpt-4.1-nano"}
        try:
            with self.assertRaises(RuntimeError):
                service.require_config()
        finally:
            service.read_local_env = original

    def test_키가_있으면_설정을_돌려준다(self):
        original = service.read_local_env
        service.read_local_env = lambda: {"GMS_API_KEY": "있음"}
        try:
            self.assertEqual(service.require_config()["GMS_API_KEY"], "있음")
        finally:
            service.read_local_env = original


if __name__ == "__main__":
    unittest.main()
