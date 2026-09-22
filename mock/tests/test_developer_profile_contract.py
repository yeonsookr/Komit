import json
import unittest

from fastapi import FastAPI
from fastapi.responses import JSONResponse
from pydantic import ValidationError

from app.routers import service


def _request(**overrides):
    base = {
        "intro": "간단한 상담도 받습니다.",
        "description": None,
        "fieldCodeId": 42,
        "yearsOfExperience": 5.0,
        "activeFrom": "10:00",
        "activeTo": "19:00",
        "techCodeIds": [21],
        "careers": [],
        "portfolioItems": [],
        "prices": [],
    }
    base.update(overrides)
    return service.WriteDeveloperProfileRequest(**base)


class DeveloperProfileContractTest(unittest.TestCase):
    def setUp(self):
        app = FastAPI()
        app.include_router(service.router)
        self.specification = app.openapi()

    def test_put_documents_every_failure_case(self):
        put = self.specification["paths"]["/api/users/me/developer-profile"]["put"]
        self.assertEqual(
            set(put["responses"]), {"200", "201", "400", "401", "403", "404", "422", "500"}
        )

    def test_portfolio_item_lengths_follow_the_database(self):
        with self.assertRaises(ValidationError):
            service.WritePortfolioItemRequest(
                title="가" * 201, description=None, linkUrl=None, thumbnailFileId=None
            )
        with self.assertRaises(ValidationError):
            service.WritePortfolioItemRequest(
                title="제목", description=None, linkUrl="h" * 2049, thumbnailFileId=None
            )
        with self.assertRaises(ValidationError):
            _request(intro="")

    def test_reversed_career_period_is_a_field_error(self):
        with self.assertRaises(ValidationError) as raised:
            service.WriteCareerRequest(
                company="A",
                role="B",
                startDate="2024-01-01",
                endDate="2023-12-31",
                evidenceFileId=None,
            )
        error = raised.exception.errors()[0]
        self.assertEqual(error["loc"], ("endDate",))
        self.assertEqual(service._reason(error), service.CAREER_PERIOD_REASON)

    def test_impossible_calendar_date_is_a_field_error(self):
        with self.assertRaises(ValidationError) as raised:
            service.WriteCareerRequest(
                company="A",
                role="B",
                startDate="2024-02-31",
                endDate=None,
                evidenceFileId=None,
            )
        error = raised.exception.errors()[0]
        self.assertEqual(error["loc"], ("startDate",))
        self.assertEqual(service._reason(error), "형식이 올바르지 않습니다.")

    def test_period_days_must_fit_the_database_column(self):
        with self.assertRaises(ValidationError):
            service.WritePriceRequest(
                taskType="DEVELOP",
                taskName="작업",
                price=1,
                periodDays=32768,
            )

    def test_reserved_values_reproduce_file_and_code_failures(self):
        not_attachable = _request(
            portfolioItems=[
                service.WritePortfolioItemRequest(
                    title="제목",
                    description=None,
                    linkUrl=None,
                    thumbnailFileId=service.NOT_ATTACHABLE_FILE_ID,
                )
            ]
        )
        response = service.write_my_developer_profile(not_attachable)
        self.assertEqual(response.status_code, 400)
        self.assertEqual(json.loads(response.body)["code"], "FILE_NOT_ATTACHABLE")

        response = service.write_my_developer_profile(
            _request(techCodeIds=[service.NOT_FOUND_CODE_ID])
        )
        self.assertEqual(response.status_code, 404)
        self.assertEqual(json.loads(response.body)["code"], "CODE_NOT_FOUND")

    def test_valid_request_echoes_intro(self):
        response = service.write_my_developer_profile(_request(intro="새 소개"))
        self.assertIsInstance(response, service.MyDeveloperProfileResponse)
        self.assertEqual(response.intro, "새 소개")


if __name__ == "__main__":
    unittest.main()
