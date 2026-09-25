import json
import unittest

from fastapi.responses import JSONResponse

from app.routers.service import (
    CONTRACT_NOT_SIGNED,
    DEV_CONTRACT_ID,
    DRAFT_CLIENT_CONTRACT_ID,
    DepositRequest,
    EscrowDepositResponse,
    deposit_contract,
)


class DepositContractTest(unittest.TestCase):
    """계약 금액 예치. 실서버 `ContractDepositService.deposit` 과 같은 갈래를 낸다."""

    REQUEST = DepositRequest(paymentMethodId=9, idempotencyKey="dep-test")

    def test_signed_contract_is_deposited(self) -> None:
        self.assertIsInstance(deposit_contract(DEV_CONTRACT_ID, self.REQUEST), EscrowDepositResponse)

    def test_unsigned_contract_is_rejected_with_409(self) -> None:
        # R-CON-01: 양쪽이 서명하기 전에는 계약이 성립하지 않아 돈을 맡기지 않는다.
        response = deposit_contract(DRAFT_CLIENT_CONTRACT_ID, self.REQUEST)

        self.assertIsInstance(response, JSONResponse)
        self.assertEqual(response.status_code, 409)
        self.assertEqual(
            json.loads(response.body),
            {"code": CONTRACT_NOT_SIGNED[0], "message": CONTRACT_NOT_SIGNED[1]},
        )


if __name__ == "__main__":
    unittest.main()
