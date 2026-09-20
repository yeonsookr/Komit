import json
import unittest

from fastapi import FastAPI
from fastapi.responses import JSONResponse

from app.routers import service


class RealtimeTicketContractTest(unittest.TestCase):
    def setUp(self):
        app = FastAPI()
        app.include_router(service.router)
        self.specification = app.openapi()

    def test_openapi_documents_success_and_every_failure_contract(self):
        operation = self.specification["paths"]["/api/realtime/tickets"]["post"]

        self.assertEqual(
            set(operation["responses"]), {"200", "401", "403", "422"}
        )
        self.assertEqual(operation["tags"], ["공통 실시간 통신"])

    def test_success_returns_one_time_ticket_shape(self):
        result = service.issue_realtime_ticket(
            service.RealtimeTicketRequest(
                channel="CONSULTING_SESSION", channelId="55"
            )
        )

        self.assertIsInstance(result, service.RealtimeTicketResponse)
        self.assertEqual(result.model_dump(), {"ticket": "mock-realtime-ticket-once"})

    def test_reserved_channel_id_reproduces_permission_failure(self):
        result = service.issue_realtime_ticket(
            service.RealtimeTicketRequest(channel="CHAT_ROOM", channelId="403")
        )

        self.assertIsInstance(result, JSONResponse)
        self.assertEqual(result.status_code, 403)
        self.assertEqual(
            json.loads(result.body),
            {
                "code": "CHANNEL_FORBIDDEN",
                "message": "이 연결로는 다룰 수 없는 대상입니다.",
            },
        )


if __name__ == "__main__":
    unittest.main()
