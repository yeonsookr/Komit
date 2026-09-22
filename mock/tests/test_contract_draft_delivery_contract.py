import json
import unittest

from fastapi.responses import JSONResponse

from app.main import service_app
from app.routers.service import (
    CONVERTED_CONSULTING_ROOM_ID,
    CONVERTED_DEVELOPMENT_ROOM_ID,
    DRAFT_CONSULTING_CONTRACT_EXAMPLE,
    DRAFT_DEV_CONTRACT_EXAMPLE,
    ROOMS,
    ContractResponse,
    draft_contract,
)

# 갈래를 정하기 전의 무료 상담방. 초안을 만들 수 없다.
INQUIRY_ROOM_ID = 91


class ContractDraftDeliveryTest(unittest.TestCase):
    def test_draft_is_a_contract_in_the_pre_signed_state(self) -> None:
        """초안은 초안 전용 형식이 아니라 가계약 계약서다."""
        cases = (
            (CONVERTED_CONSULTING_ROOM_ID, "CONSULTING", "consulting", "development"),
            (CONVERTED_DEVELOPMENT_ROOM_ID, "DEVELOPMENT", "development", "consulting"),
        )

        for room_id, contract_type, filled, empty in cases:
            with self.subTest(contract_type=contract_type):
                result = draft_contract(room_id)

                self.assertIsInstance(result, ContractResponse)
                self.assertEqual(result.roomId, room_id)
                self.assertEqual(result.contractType, contract_type)
                self.assertEqual(getattr(result, filled).status, "PRE_SIGNED")
                self.assertIsNone(getattr(result, empty))

    def test_draft_is_not_signed_by_anyone(self) -> None:
        """AI는 계약서를 보내지도 서명하지도 않는다. (R-CON-01)"""
        result = draft_contract(CONVERTED_DEVELOPMENT_ROOM_ID)

        self.assertFalse(result.clientSignature.isSigned)
        self.assertFalse(result.developerSignature.isSigned)
        self.assertIsNone(result.clientSignature.signedAt)
        self.assertIsNone(result.developerSignature.signedAt)
        self.assertEqual(result.status, "NEGOTIATING")

    def test_developer_input_fields_are_left_empty(self) -> None:
        """일정·금액·인계물은 개발자가 채운다. 초안에는 없다."""
        result = draft_contract(CONVERTED_CONSULTING_ROOM_ID)

        self.assertEqual(result.consulting.totalAmount, 0)
        self.assertEqual(result.consulting.sessions, [])
        self.assertEqual(result.messages, [])
        self.assertIsNone(result.contractText)

    def test_same_room_always_gets_the_same_draft(self) -> None:
        """방마다 한 번만 만들어 둔다. 다시 불러도 문장이 달라지지 않는다."""
        before = json.dumps(ROOMS, ensure_ascii=False)
        first = draft_contract(CONVERTED_DEVELOPMENT_ROOM_ID)
        second = draft_contract(CONVERTED_DEVELOPMENT_ROOM_ID)

        self.assertEqual(first, second)
        self.assertEqual(json.dumps(ROOMS, ensure_ascii=False), before)

    def test_room_without_a_branch_cannot_have_a_draft(self) -> None:
        result = draft_contract(INQUIRY_ROOM_ID)

        self.assertIsInstance(result, JSONResponse)
        self.assertEqual(result.status_code, 409)
        self.assertEqual(
            json.loads(result.body),
            {
                "code": "ROOM_NOT_CONVERTED",
                "message": "갈래를 정하기 전에는 계약 초안을 만들 수 없습니다.",
            },
        )

    def test_missing_room_is_not_found(self) -> None:
        result = draft_contract(404)

        self.assertIsInstance(result, JSONResponse)
        self.assertEqual(result.status_code, 404)
        self.assertEqual(json.loads(result.body)["code"], "ROOM_NOT_FOUND")

    def test_openapi_documents_the_screen_and_every_case(self) -> None:
        operation = service_app.openapi()["paths"]["/api/rooms/{roomId}/contract-draft"]["post"]

        self.assertIn("초안은 가계약 계약서다", operation["description"])
        self.assertIn("방마다 한 번만 만든다", operation["description"])
        self.assertIn("개발자가 고친 내용은 서버가 들고 있지 않는다", operation["description"])
        self.assertEqual(
            operation["responses"]["200"]["content"]["application/json"]["example"],
            DRAFT_DEV_CONTRACT_EXAMPLE,
        )
        for status in ("401", "403", "404", "409", "422", "502", "503", "500"):
            self.assertIn(status, operation["responses"])

    def test_examples_match_the_contract_response_model(self) -> None:
        for example in (
            DRAFT_CONSULTING_CONTRACT_EXAMPLE,
            DRAFT_DEV_CONTRACT_EXAMPLE,
        ):
            with self.subTest(contract_type=example["contractType"]):
                self.assertEqual(ContractResponse(**example).model_dump(), example)


if __name__ == "__main__":
    unittest.main()
