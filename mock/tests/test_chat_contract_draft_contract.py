import unittest

from fastapi.responses import JSONResponse
from pydantic import ValidationError

from app.routers.service import (
    CONVERTED_CONSULTING_ROOM_ID,
    CONVERTED_DEVELOPMENT_ROOM_ID,
    CONVERTED_ROOM_ID,
    ConvertRoomRequest,
    RoomDetailResponse,
    WriteConsultingContractRequest,
    WriteDevelopmentContractRequest,
    WriteMilestoneRequest,
    convert_room,
)


class ChatContractDraftContractTest(unittest.TestCase):
    def test_inquiry_conversion_opens_a_distinct_target_room(self) -> None:
        cases = (
            ("CONSULTING", CONVERTED_CONSULTING_ROOM_ID),
            ("DEVELOPMENT", CONVERTED_DEVELOPMENT_ROOM_ID),
        )

        for target_type, converted_room_id in cases:
            with self.subTest(target_type=target_type):
                result = convert_room(91, ConvertRoomRequest(targetType=target_type))

                self.assertIsInstance(result, RoomDetailResponse)
                self.assertEqual(result.status, "CLOSED")
                self.assertEqual(result.convertedRoomType, target_type)
                self.assertEqual(result.convertedRoomId, converted_room_id)
                self.assertNotEqual(result.convertedRoomId, result.roomId)

    def test_converted_inquiry_cannot_be_converted_again(self) -> None:
        result = convert_room(
            CONVERTED_ROOM_ID,
            ConvertRoomRequest(targetType="DEVELOPMENT"),
        )

        self.assertIsInstance(result, JSONResponse)
        self.assertEqual(result.status_code, 409)

    def test_conversion_accepts_only_contract_paths(self) -> None:
        with self.assertRaises(ValidationError):
            ConvertRoomRequest(targetType="NONE")

    def test_consulting_contract_keeps_existing_request_fields(self) -> None:
        self.assertEqual(
            set(WriteConsultingContractRequest.model_fields),
            {"topic", "scheduledAt", "durationMinutes", "price", "detail", "message"},
        )

    def test_development_contract_keeps_existing_request_fields(self) -> None:
        self.assertEqual(
            set(WriteDevelopmentContractRequest.model_fields),
            {
                "issueIds",
                "startDate",
                "dueDate",
                "freeRepairDays",
                "milestones",
                "rejectedIssues",
                "deliverableTitles",
                "detail",
                "message",
            },
        )
        self.assertEqual(
            set(WriteMilestoneRequest.model_fields),
            {"title", "price", "workDate", "qaCriteria"},
        )


if __name__ == "__main__":
    unittest.main()
