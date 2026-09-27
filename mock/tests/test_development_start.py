import unittest

from app.routers import service


class DevelopmentStartTest(unittest.TestCase):
    def test_start_moves_first_regular_work_and_schedules_the_rest(self):
        result = service.start_development(service.DEV_CONTRACT_ID)

        self.assertEqual(result.development.status, "IN_PROGRESS")
        statuses = [(milestone.isHandover, milestone.status) for milestone in result.development.milestones]
        self.assertEqual(statuses[0], (False, "IN_PROGRESS"))
        self.assertTrue(all(status == "SCHEDULED" for _handover, status in statuses[1:]))

    def test_start_reproduces_every_conflict(self):
        for contract_id, code in [
            (service.NOT_DEPOSITED_CONTRACT_ID, b"DEPOSIT_REQUIRED"),
            (service.STARTED_CONTRACT_ID, b"ALREADY_STARTED"),
            (service.DRAFT_CLIENT_CONTRACT_ID, b"CONTRACT_NOT_CONCLUDED"),
            (service.CANCELLED_CONTRACT_ID, b"CONTRACT_CANCELLED"),
        ]:
            with self.subTest(contract_id=contract_id):
                result = service.start_development(contract_id)
                self.assertEqual(result.status_code, 409)
                self.assertIn(code, result.body)

    def test_start_documents_every_conflict(self):
        from fastapi import FastAPI

        app = FastAPI()
        app.include_router(service.router)
        conflicts = app.openapi()["paths"]["/api/contracts/{contractId}/start"]["post"]["responses"]["409"]

        self.assertEqual(
            {example["value"]["code"] for example in conflicts["content"]["application/json"]["examples"].values()},
            {"DEPOSIT_REQUIRED", "ALREADY_STARTED", "CONTRACT_NOT_CONCLUDED", "CONTRACT_CANCELLED"},
        )


if __name__ == "__main__":
    unittest.main()
