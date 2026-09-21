import json
import unittest

from fastapi.responses import JSONResponse
from pydantic import ValidationError

from app.main import service_app
from app.routers.service import (
    CONSULTING_CONTRACT_DRAFT_EXAMPLE,
    CONVERTED_CONSULTING_ROOM_ID,
    CONVERTED_DEVELOPMENT_ROOM_ID,
    DEVELOPMENT_CONTRACT_DRAFT_EXAMPLE,
    ROOMS,
    ConsultingContractDraftResponse,
    ContractDraftMilestoneResponse,
    ContractDraftResponse,
    DevelopmentContractDraftResponse,
    WriteConsultingContractRequest,
    WriteDevelopmentContractRequest,
    WriteMilestoneRequest,
    draft_contract,
)

# 갈래를 정하기 전의 무료 상담방. 초안을 만들 수 없다.
INQUIRY_ROOM_ID = 91


class ContractDraftDeliveryTest(unittest.TestCase):
    def test_draft_follows_the_room_branch(self) -> None:
        cases = (
            (CONVERTED_CONSULTING_ROOM_ID, "CONSULTING", "consulting", "development"),
            (CONVERTED_DEVELOPMENT_ROOM_ID, "DEVELOPMENT", "development", "consulting"),
        )

        for room_id, contract_type, filled, empty in cases:
            with self.subTest(contract_type=contract_type):
                result = draft_contract(room_id)

                self.assertIsInstance(result, ContractDraftResponse)
                self.assertEqual(result.roomId, room_id)
                self.assertEqual(result.contractType, contract_type)
                self.assertIsNotNone(getattr(result, filled))
                self.assertIsNone(getattr(result, empty))

    def test_draft_only_carries_fields_the_ai_fills(self) -> None:
        """일정·기간·금액·인계물은 개발자 입력 필드라 초안에 없다."""
        self.assertEqual(
            set(ConsultingContractDraftResponse.model_fields),
            set(WriteConsultingContractRequest.model_fields)
            - {"scheduledAt", "durationMinutes", "price", "message"},
        )
        self.assertEqual(
            set(DevelopmentContractDraftResponse.model_fields),
            set(WriteDevelopmentContractRequest.model_fields)
            - {
                "startDate",
                "dueDate",
                "freeRepairDays",
                "deliverableTitles",
                "detail",
                "message",
            },
        )
        self.assertEqual(
            set(ContractDraftMilestoneResponse.model_fields),
            set(WriteMilestoneRequest.model_fields) - {"price", "workDate"},
        )

    def test_same_room_always_gets_the_same_draft(self) -> None:
        """방마다 한 번만 만들어 보관한다. 다시 불러도 문장이 달라지지 않는다."""
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

    def test_draft_keeps_the_qa_criterion_contract(self) -> None:
        """확인 문장은 작업마다 하나 이상, 문장마다 1~500자다. (R-REQ-04)"""
        for criteria in ([], [""], ["가" * 501]):
            with self.subTest(criteria=str(criteria)[:20]), self.assertRaises(ValidationError):
                ContractDraftMilestoneResponse(title="작업", qaCriteria=criteria)

        with self.assertRaises(ValidationError):
            DevelopmentContractDraftResponse(issueIds=[], milestones=[], rejectedIssues=[])

        with self.assertRaises(ValidationError):
            ConsultingContractDraftResponse(topic="주" * 256, detail=None)

    def test_openapi_documents_the_screen_and_every_case(self) -> None:
        operation = service_app.openapi()["paths"]["/api/rooms/{roomId}/contract-draft"]["post"]

        self.assertIn("방마다 한 번만 만든다", operation["description"])
        self.assertIn("개발자가 고친 내용은 서버가 들고 있지 않는다", operation["description"])
        self.assertIn("초안은 개발자가 본다", operation["description"])
        self.assertEqual(
            operation["responses"]["200"]["content"]["application/json"]["example"],
            DEVELOPMENT_CONTRACT_DRAFT_EXAMPLE,
        )
        for status in ("401", "403", "404", "409", "422", "502", "503", "500"):
            self.assertIn(status, operation["responses"])

    def test_examples_match_the_response_model(self) -> None:
        for example in (
            CONSULTING_CONTRACT_DRAFT_EXAMPLE,
            DEVELOPMENT_CONTRACT_DRAFT_EXAMPLE,
        ):
            with self.subTest(contract_type=example["contractType"]):
                self.assertEqual(
                    ContractDraftResponse(**example).model_dump(),
                    example,
                )


if __name__ == "__main__":
    unittest.main()
