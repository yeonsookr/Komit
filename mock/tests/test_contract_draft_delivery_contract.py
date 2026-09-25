import json
import unittest

from fastapi.responses import JSONResponse

from app.main import service_app
from app.routers.service import (
    CONVERTED_CONSULTING_ROOM_ID,
    CONVERTED_DEVELOPMENT_ROOM_ID,
    DRAFT_CONSULTING_CONTRACT_EXAMPLE,
    DRAFT_DEV_CONTRACT_ID,
    DRAFT_DEVELOPMENT_CONTRACT_EXAMPLE,
    ROOMS,
    ContractResponse,
    WriteConsultingContractRequest,
    draft_contract,
    get_contract,
    write_consulting_contract,
)

# 갈래를 정하기 전의 무료 상담방. 초안을 만들 수 없다.
INQUIRY_ROOM_ID = 91


class ContractDraftDeliveryTest(unittest.TestCase):
    def test_draft_is_contract_version_one_in_the_signed_branch_state(self) -> None:
        """초안은 초안 전용 형식이 아니라 계약서 version 1이다."""
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
                self.assertEqual(getattr(result, filled).status, "SIGNED")
                self.assertIsNone(getattr(result, empty))

    def test_draft_is_not_signed_by_anyone(self) -> None:
        """AI는 계약서를 보내지도 서명하지도 않는다. (R-CON-01)"""
        result = draft_contract(CONVERTED_DEVELOPMENT_ROOM_ID)

        self.assertFalse(result.clientSignature.isSigned)
        self.assertFalse(result.developerSignature.isSigned)
        self.assertIsNone(result.clientSignature.signedAt)
        self.assertIsNone(result.developerSignature.signedAt)
        self.assertEqual(result.status, "NEGOTIATING")

    def test_consulting_draft_leaves_developer_input_empty(self) -> None:
        """컨설팅은 다룰 항목만 온다. 일시·시간·금액은 개발자가 채운다."""
        result = draft_contract(CONVERTED_CONSULTING_ROOM_ID)

        self.assertNotEqual(result.consulting.topic, "")
        self.assertEqual(result.consulting.totalAmount, 0)
        self.assertEqual(result.consulting.sessions, [])
        self.assertEqual(
            result.consulting.detail,
            "회원탈퇴 처리 흐름과 결제 실패 재현 방법을 화면 공유로 봅니다.",
        )
        self.assertEqual(result.messages, [])
        self.assertIsNone(result.contractText)

    def test_development_draft_leaves_developer_input_empty(self) -> None:
        """개발은 작업 이름·확인 문장·맡을 요구사항만 온다. 일정·금액·인계물은 비어 있다."""
        result = draft_contract(CONVERTED_DEVELOPMENT_ROOM_ID)
        development = result.development

        self.assertNotEqual(development.issueIds, [])
        self.assertNotEqual(development.milestones, [])
        for milestone in development.milestones:
            self.assertNotEqual(milestone.title, "")
            self.assertNotEqual(milestone.qaCriteria, [])
            self.assertEqual(milestone.price, 0)
            self.assertIsNone(milestone.workDate)

        self.assertIsNone(development.startDate)
        self.assertIsNone(development.dueDate)
        self.assertEqual(development.freeRepairDays, 0)
        self.assertEqual(development.totalAmount, 0)
        self.assertEqual(development.deliverables, [])
        self.assertEqual(result.messages, [])

    def test_sent_contract_keeps_the_amounts_the_developer_filled(self) -> None:
        """초안과 가계약은 다르다. 개발자가 써서 보낸 계약서에는 금액과 일정이 있다."""
        sent = get_contract(DRAFT_DEV_CONTRACT_ID)

        self.assertIsInstance(sent, ContractResponse)
        self.assertNotEqual(sent.development.totalAmount, 0)
        self.assertIsNotNone(sent.development.startDate)

    def test_written_consulting_detail_stays_in_the_consulting_branch(self) -> None:
        result = write_consulting_contract(
            CONVERTED_CONSULTING_ROOM_ID,
            WriteConsultingContractRequest(
                topic="회원탈퇴 경로를 봅니다",
                scheduledAt="2026-08-29T14:00:00+09:00",
                durationMinutes=30,
                price=50000,
                detail="탈퇴 데이터 보관 방법을 확인합니다",
                message=None,
            ),
        )

        self.assertIsInstance(result, ContractResponse)
        self.assertEqual(result.consulting.detail, "탈퇴 데이터 보관 방법을 확인합니다")
        self.assertNotIn("detail", result.model_dump().keys())

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

        self.assertIn("초안은 계약서 version 1이다", operation["description"])
        self.assertIn("방마다 한 번만 만든다", operation["description"])
        self.assertIn("개발자가 고친 내용은 서버가 들고 있지 않는다", operation["description"])
        self.assertEqual(
            operation["responses"]["200"]["content"]["application/json"]["example"],
            DRAFT_DEVELOPMENT_CONTRACT_EXAMPLE,
        )
        for status in ("401", "403", "404", "409", "422", "502", "503", "500"):
            self.assertIn(status, operation["responses"])

    def test_examples_match_the_contract_response_model(self) -> None:
        for example in (
            DRAFT_CONSULTING_CONTRACT_EXAMPLE,
            DRAFT_DEVELOPMENT_CONTRACT_EXAMPLE,
        ):
            with self.subTest(contract_type=example["contractType"]):
                self.assertEqual(ContractResponse(**example).model_dump(), example)


if __name__ == "__main__":
    unittest.main()
