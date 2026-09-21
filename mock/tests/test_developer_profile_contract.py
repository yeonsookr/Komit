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
        request = _request(
            careers=[
                service.WriteCareerRequest(
                    company="A",
                    role="B",
                    startDate="2024-01-01",
                    endDate="2023-12-31",
                    evidenceFileId=None,
                )
            ]
        )
        response = service.write_my_developer_profile(request)
        self.assertIsInstance(response, JSONResponse)
        self.assertEqual(response.status_code, 422)
        body = json.loads(response.body)
        self.assertEqual(
            body["errors"],
            [{"field": "careers.0.endDate", "reason": service.CAREER_PERIOD_REASON}],
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
