import unittest

from fastapi.responses import JSONResponse

from app import scan_result
from app.routers.service import (
    CreateRecommendationRequest,
    RecommendationBatchResponse,
    WriteRequirementRequest,
    WriteRequirementsRequest,
    create_recommendation,
    write_requirements,
)


class RecommendationValidationTest(unittest.TestCase):
    diagnosis_id = 987654

    def setUp(self) -> None:
        scan_result.remember(
            self.diagnosis_id,
            {
                "issues": [
                    {
                        "title": "검사에서 찾은 보안 문제",
                        "category": "SECURITY",
                        "severity": "HIGH",
                    },
                    {
                        "title": "검사에서 찾았지만 고르지 않은 문제",
                        "category": "ETC",
                        "severity": "LOW",
                    },
                ]
            },
        )
        write_requirements(
            self.diagnosis_id,
            WriteRequirementsRequest(
                requirements=[
                    WriteRequirementRequest(
                        issueId=90000,
                        title="검사에서 찾은 보안 문제",
                        detail=None,
                        categoryCodeId=71,
                        severity="HIGH",
                        qaCriteria=[],
                    )
                ]
            ),
        )

    def test_uploaded_scan_issue_can_create_recommendation(self) -> None:
        result = create_recommendation(
            8,
            CreateRecommendationRequest(
                diagnosisId=self.diagnosis_id,
                issueIds=[90000],
            ),
        )

        self.assertIsInstance(result, RecommendationBatchResponse)

    def test_issue_from_another_diagnosis_is_rejected(self) -> None:
        result = create_recommendation(
            8,
            CreateRecommendationRequest(
                diagnosisId=self.diagnosis_id,
                issueIds=[311],
            ),
        )

        self.assertIsInstance(result, JSONResponse)
        self.assertEqual(result.status_code, 422)

    def test_unselected_issue_is_rejected(self) -> None:
        result = create_recommendation(
            8,
            CreateRecommendationRequest(
                diagnosisId=self.diagnosis_id,
                issueIds=[90001],
            ),
        )

        self.assertIsInstance(result, JSONResponse)
        self.assertEqual(result.status_code, 422)


if __name__ == "__main__":
    unittest.main()
