import copy
import json
import unittest

from fastapi.responses import JSONResponse
from pydantic import ValidationError

from app.main import service_app
from app.routers import service


def _request(**overrides) -> service.ContractQaSuggestionRequest:
    body = {
        "issueId": 312,
        "title": "관리자 API에 인증 추가",
        "detail": "로그인하지 않은 요청은 로그인 화면으로 이동시킨다.",
    }
    body.update(overrides)
    return service.ContractQaSuggestionRequest(**body)


class ContractQaSuggestionRequestTest(unittest.TestCase):
    def test_accepts_existing_and_unsaved_tasks(self) -> None:
        self.assertEqual(_request().issueId, 312)
        self.assertIsNone(_request(issueId=None, detail=None).issueId)

    def test_matches_contract_task_text_limits(self) -> None:
        self.assertEqual(len(_request(title="가" * 200).title), 200)
        self.assertEqual(len(_request(detail="가" * 1000).detail), 1000)

        invalid_values = (
            ("title", ""),
            ("title", "가" * 201),
            ("detail", ""),
            ("detail", "가" * 1001),
        )
        for field, value in invalid_values:
            with self.subTest(field=field, length=len(value)), self.assertRaises(
                ValidationError
            ):
                _request(**{field: value})


class ContractQaSuggestionEndpointTest(unittest.TestCase):
    def test_returns_three_candidates_without_saving_contract_data(self) -> None:
        before_issues = copy.deepcopy(service.ISSUES)
        before_contract = copy.deepcopy(service.DRAFT_DEVELOPMENT_CONTRACT_EXAMPLE)

        result = service.suggest_contract_qa_criteria(93, _request(issueId=None))

        self.assertIsInstance(result, service.QaSuggestionListResponse)
        self.assertEqual(len(result.content), 3)
        self.assertEqual(service.ISSUES, before_issues)
        self.assertEqual(service.DRAFT_DEVELOPMENT_CONTRACT_EXAMPLE, before_contract)

    def test_missing_room_is_hidden_as_not_found(self) -> None:
        result = service.suggest_contract_qa_criteria(404, _request())

        self.assertIsInstance(result, JSONResponse)
        self.assertEqual(result.status_code, 404)
        self.assertEqual(
            json.loads(result.body),
            {"code": "ROOM_NOT_FOUND", "message": "방을 찾을 수 없습니다."},
        )

    def test_openapi_documents_flow_and_failures(self) -> None:
        operation = service_app.openapi()["paths"][
            "/api/rooms/{roomId}/contract-qa-suggestions"
        ]["post"]

        self.assertIn("현재 편집기에 적힌 `title`과 `detail`", operation["description"])
        self.assertIn("저장되지 않는다", operation["description"])
        self.assertIn("자동 호출하지 않는다", operation["description"])
        self.assertEqual(
            operation["responses"]["200"]["content"]["application/json"]["example"],
            service.QA_SUGGESTION_LIST_EXAMPLE,
        )
        self.assertEqual(
            set(operation["responses"]),
            {"200", "401", "403", "404", "409", "422", "500", "502", "503"},
        )
        conflicts = operation["responses"]["409"]["content"]["application/json"][
            "examples"
        ]
        self.assertEqual(
            set(conflicts),
            {"NOT_DEVELOPMENT_ROOM", "CONTRACT_ALREADY_SIGNED", "CONTRACT_CANCELLED"},
        )


class ContractQaSuggestionHttpTest(unittest.IsolatedAsyncioTestCase):
    async def request(self, room_id: str, body: dict) -> tuple[int, dict]:
        path = f"/api/rooms/{room_id}/contract-qa-suggestions"
        messages = []
        request_body = json.dumps(body).encode()
        received = False

        async def receive():
            nonlocal received
            if received:
                return {"type": "http.disconnect"}
            received = True
            return {"type": "http.request", "body": request_body, "more_body": False}

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
                "headers": [(b"content-type", b"application/json")],
            },
            receive,
            send,
        )
        status = next(
            message["status"]
            for message in messages
            if message["type"] == "http.response.start"
        )
        response_body = json.loads(
            b"".join(message.get("body", b"") for message in messages)
        )
        return status, response_body

    async def test_http_accepts_unsaved_task_and_rejects_invalid_text(self) -> None:
        status, body = await self.request(
            "93", {"issueId": None, "title": "새 작업", "detail": None}
        )
        self.assertEqual(status, 200)
        self.assertEqual(body, service.QA_SUGGESTION_LIST_EXAMPLE)

        status, body = await self.request(
            "93", {"issueId": None, "title": "", "detail": None}
        )
        self.assertEqual(status, 422)
        self.assertEqual(body["code"], "INVALID_REQUEST")


if __name__ == "__main__":
    unittest.main()
