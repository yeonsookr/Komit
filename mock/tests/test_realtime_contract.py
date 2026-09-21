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
            set(operation["responses"]), {"200", "401", "403", "404", "409", "422"}
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

    def test_consulting_session_not_found_contract_matches_real_server(self):
        result = service.issue_realtime_ticket(
            service.RealtimeTicketRequest(
                channel="CONSULTING_SESSION", channelId="404"
            )
        )

        self.assertIsInstance(result, JSONResponse)
        self.assertEqual(result.status_code, 404)
        self.assertEqual(
            json.loads(result.body),
            {
                "code": "SESSION_NOT_FOUND",
                "message": "세션을 찾을 수 없습니다.",
            },
        )

    def test_consulting_session_conflict_contracts_match_real_server(self):
        cases = {
            "409": (
                "SESSION_ENDED",
                "이미 끝난 세션입니다.",
            ),
            "410": (
                "CONSENT_REQUIRED",
                "상대가 아직 들어오지 않았습니다. 양쪽이 모두 동의해야 시작할 수 있습니다.",
            ),
            "411": (
                "ENTRY_NOT_OPEN",
                "아직 입장할 수 있는 시각이 아닙니다.",
            ),
        }

        for channel_id, (code, message) in cases.items():
            with self.subTest(channel_id=channel_id):
                result = service.issue_realtime_ticket(
                    service.RealtimeTicketRequest(
                        channel="CONSULTING_SESSION", channelId=channel_id
                    )
                )

                self.assertIsInstance(result, JSONResponse)
                self.assertEqual(result.status_code, 409)
                self.assertEqual(
                    json.loads(result.body), {"code": code, "message": message}
                )


if __name__ == "__main__":
    unittest.main()
