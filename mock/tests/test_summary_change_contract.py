import unittest

from pydantic import ValidationError

from app.routers.service import SummaryChangeResponse


class SummaryChangeContractTest(unittest.TestCase):
    def make_change(self, **updates) -> SummaryChangeResponse:
        values = {
            "changeId": 1,
            "kind": "UPDATE",
            "issueId": 312,
            "title": "회원탈퇴 데이터는 30일 보관 후 삭제",
            "detail": None,
            "isApplied": False,
        }
        values.update(updates)
        return SummaryChangeResponse(**values)

    def test_update_requires_existing_issue_reference(self) -> None:
        change = self.make_change()
        self.assertEqual(change.kind, "UPDATE")
        self.assertEqual(change.issueId, 312)

        with self.assertRaises(ValidationError):
            self.make_change(issueId=None)

    def test_add_must_not_have_existing_issue_reference(self) -> None:
        change = self.make_change(kind="ADD", issueId=None)
        self.assertEqual(change.kind, "ADD")
        self.assertIsNone(change.issueId)

        with self.assertRaises(ValidationError):
            self.make_change(kind="ADD", issueId=312)

    def test_unknown_change_kind_is_rejected(self) -> None:
        with self.assertRaises(ValidationError):
            self.make_change(kind="DELETE")

    def test_empty_change_title_is_rejected(self) -> None:
        with self.assertRaises(ValidationError):
            self.make_change(title="")

    def test_persisted_response_keeps_service_owned_fields(self) -> None:
        self.assertEqual(
            set(SummaryChangeResponse.model_fields),
            {"changeId", "kind", "issueId", "title", "detail", "isApplied"},
        )


if __name__ == "__main__":
    unittest.main()
