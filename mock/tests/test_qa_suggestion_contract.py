import copy
import json
import unittest

from fastapi.responses import JSONResponse
from pydantic import ValidationError

from app.main import service_app
from app.routers.service import (
    ISSUES,
    QA_SUGGESTION_LIST_EXAMPLE,
    QaSuggestionListResponse,
    QaSuggestionResponse,
    suggest_qa_criteria,
)


class QaSuggestionContractTest(unittest.TestCase):
    def test_candidates_have_only_erd_test_case_and_are_not_saved(self) -> None:
        before = copy.deepcopy(ISSUES)
        result = suggest_qa_criteria(312)

        self.assertIsInstance(result, QaSuggestionListResponse)
        self.assertEqual(len(result.content), 3)
        self.assertEqual(result.page, 1)
        self.assertEqual(result.size, 3)
        self.assertEqual(result.totalElements, 3)
        self.assertEqual(result.totalPages, 1)
        self.assertEqual(
            len({item.testCase for item in result.content}),
            len(result.content),
        )
        self.assertTrue(all(set(item.model_dump()) == {"testCase"} for item in result.content))
        self.assertEqual(ISSUES, before)
        self.assertEqual(suggest_qa_criteria(312), result)

    def test_cannot_return_too_few_or_too_many_candidates(self) -> None:
        for count in (0, 2, 4):
            with self.subTest(count=count), self.assertRaises(ValidationError):
                QaSuggestionListResponse(
                    **{
                        **QA_SUGGESTION_LIST_EXAMPLE,
                        "content": QA_SUGGESTION_LIST_EXAMPLE["content"][:count]
                        if count < 3
                        else QA_SUGGESTION_LIST_EXAMPLE["content"]
                        + [{"testCase": "추가 문장"}],
                    }
                )

    def test_candidate_length_matches_qa_criterion_column(self) -> None:
        for text in ("", "가" * 501):
            with self.subTest(length=len(text)), self.assertRaises(ValidationError):
                QaSuggestionResponse(testCase=text)
        self.assertEqual(len(QaSuggestionResponse(testCase="가" * 500).testCase), 500)

    def test_missing_issue_is_hidden_as_not_found(self) -> None:
        result = suggest_qa_criteria(404)

        self.assertIsInstance(result, JSONResponse)
        self.assertEqual(result.status_code, 404)
        self.assertEqual(
            json.loads(result.body),
            {"code": "ISSUE_NOT_FOUND", "message": "요구사항을 찾을 수 없습니다."},
        )

    def test_openapi_documents_success_and_failure_contract(self) -> None:
        operation = service_app.openapi()["paths"]["/api/issues/{issueId}/qa-suggestions"]["post"]

        self.assertIn("의뢰인은 후보를 보고 채택하거나", operation["description"])
        self.assertIn("기존 요청서 저장 API로 저장한다", operation["description"])
        self.assertEqual(
            operation["responses"]["200"]["content"]["application/json"]["example"],
            QA_SUGGESTION_LIST_EXAMPLE,
        )
        for status in ("401", "404", "422", "502", "503", "500"):
            self.assertIn(status, operation["responses"])


class QaSuggestionHttpTest(unittest.IsolatedAsyncioTestCase):
    async def request(self, issue_id: str) -> tuple[int, dict]:
        path = f"/api/issues/{issue_id}/qa-suggestions"
        messages = []

        async def receive():
            return {"type": "http.request", "body": b"", "more_body": False}

        async def send(message):
            messages.append(message)

        await service_app(
            {
                "type": "http",
                "asgi": {"version": "3.0"},
                "http_version": "1.1",
                "method": "POST",
                "path": path,
                "raw_path": path.encode(),
                "root_path": "",
                "query_string": b"",
                "scheme": "http",
                "server": ("test", 80),
                "client": ("test", 1),
                "headers": [],
            },
            receive,
            send,
        )
        status = next(message["status"] for message in messages if message["type"] == "http.response.start")
        body = json.loads(b"".join(message.get("body", b"") for message in messages))
        return status, body

    async def test_http_status_and_body(self) -> None:
        status, body = await self.request("312")
        self.assertEqual(status, 200)
        self.assertEqual(body, QA_SUGGESTION_LIST_EXAMPLE)

        status, body = await self.request("404")
        self.assertEqual(status, 404)
        self.assertEqual(body["code"], "ISSUE_NOT_FOUND")

        status, body = await self.request("not-an-integer")
        self.assertEqual(status, 422)
        self.assertEqual(body["code"], "INVALID_REQUEST")


if __name__ == "__main__":
    unittest.main()
