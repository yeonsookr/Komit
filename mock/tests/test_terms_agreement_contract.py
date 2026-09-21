import json
import unittest

from fastapi import FastAPI
from fastapi.responses import JSONResponse

from app.routers import service


class TermsAgreementContractTest(unittest.TestCase):
    def setUp(self):
        app = FastAPI()
        app.include_router(service.router)
        self.specification = app.openapi()

    def test_openapi_exposes_get_and_put_with_required_fields(self):
        operations = self.specification["paths"]["/api/settings/terms"]
        self.assertEqual(set(operations), {"get", "put"})
        self.assertEqual(set(operations["put"]["responses"]), {"200", "401", "422", "500"})
        self.assertEqual(
            set(service.AgreeRequiredTermsRequest.model_fields),
            {
                "isEscrowTermsAgreed",
                "isPrivacyCollectAgreed",
                "isPrivacyThirdPartyAgreed",
                "isServiceTermsAgreed",
            },
        )

    def test_false_consent_fails_and_true_consent_succeeds(self):
        request = service.AgreeRequiredTermsRequest(
            isEscrowTermsAgreed=False,
            isPrivacyCollectAgreed=True,
            isPrivacyThirdPartyAgreed=True,
            isServiceTermsAgreed=True,
        )
        failure = service.agree_required_terms(request)
        self.assertIsInstance(failure, JSONResponse)
        self.assertEqual(failure.status_code, 422)
        self.assertEqual(
            json.loads(failure.body),
            service.REQUIRED_TERMS_INVALID_EXAMPLE,
        )

        success = service.agree_required_terms(
            service.AgreeRequiredTermsRequest(**service.REQUIRED_TERMS_AGREED_EXAMPLE)
        )
        self.assertEqual(success.model_dump(), service.REQUIRED_TERMS_AGREED_EXAMPLE)

    def test_signup_completion_contract_uses_bodyless_status_without_new_erd_fields(self):
        operations = self.specification["paths"]["/api/auth/signup/completion"]
        self.assertEqual(set(operations), {"get", "post"})
        self.assertEqual(set(operations["get"]["responses"]), {"204", "401", "409", "500"})
        self.assertEqual(set(operations["post"]["responses"]), {"204", "401", "409", "503", "500"})
        self.assertEqual(service.get_signup_completion().status_code, 204)
        self.assertEqual(service.complete_signup().status_code, 204)
