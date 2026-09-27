import json
import unittest

from app.routers import service


def _openapi():
    from fastapi import FastAPI

    app = FastAPI()
    app.include_router(service.router)
    return app.openapi()["paths"]


class MilestoneProgressTest(unittest.TestCase):
    def test_submission_takes_no_body(self):
        submission = _openapi()["/api/milestones/{milestoneId}/submission"]["post"]

        self.assertNotIn("requestBody", submission)
        errors = submission["responses"]["422"]["content"]["application/json"]["example"]["errors"]
        self.assertEqual(errors, [{"field": "milestoneId", "reason": "정수여야 합니다."}])

    def test_submission_reproduces_every_conflict(self):
        for milestone_id, code in [
            (service.CONFLICT_MILESTONE_ID, b"MILESTONE_STATE_CONFLICT"),
            (service.HANDOVER_NOT_OPEN_ID, b"HANDOVER_NOT_OPEN"),
            (service.MATERIALS_MISSING_MILESTONE_ID, b"HANDOVER_MATERIALS_MISSING"),
        ]:
            with self.subTest(milestone_id=milestone_id):
                result = service.submit_milestone(milestone_id)
                self.assertEqual(result.status_code, 409)
                self.assertIn(code, result.body)

    def test_submission_documents_every_conflict(self):
        conflicts = _openapi()["/api/milestones/{milestoneId}/submission"]["post"]["responses"]["409"]

        self.assertEqual(
            {example["value"]["code"] for example in conflicts["content"]["application/json"]["examples"].values()},
            {"MILESTONE_STATE_CONFLICT", "HANDOVER_NOT_OPEN", "HANDOVER_MATERIALS_MISSING"},
        )

    def test_partial_result_during_review_returns_the_work(self):
        result = service.save_qa_results(302, service.SaveQaResultsRequest(qaCriterionIds=[203]))

        self.assertEqual(result.status, "IN_PROGRESS")
        self.assertIsNone(result.submittedAt)
        self.assertIsNone(result.autoPassAt)
        self.assertEqual([qa.status for qa in result.qaCriteria], ["RESOLVED", "RESOLVED", "UNRESOLVED"])

    def test_full_result_keeps_the_submission(self):
        result = service.save_qa_results(302, service.SaveQaResultsRequest(qaCriterionIds=[205]))

        self.assertEqual(result.status, "SUBMITTED")
        self.assertIsNotNone(result.autoPassAt)

    def test_result_before_submission_keeps_the_status(self):
        result = service.save_qa_results(303, service.SaveQaResultsRequest(qaCriterionIds=[206]))

        self.assertEqual(result.status, "IN_PROGRESS")

    def test_confirmation_rejects_criteria_of_another_milestone(self):
        result = service.confirm_milestone(301, service.ConfirmMilestoneRequest(qaCriterionIds=[201, 202, 203]))

        self.assertEqual(result.status_code, 422)
        self.assertEqual(
            json.loads(result.body)["errors"],
            [{"field": "qaCriterionIds", "reason": "이 작업의 확인 문장만 보낼 수 있습니다."}],
        )

    def test_confirmation_needs_every_criterion(self):
        result = service.confirm_milestone(301, service.ConfirmMilestoneRequest(qaCriterionIds=[201]))

        self.assertEqual(result.status_code, 400)
        self.assertIn(b"QA_NOT_ALL_CHECKED", result.body)


if __name__ == "__main__":
    unittest.main()
