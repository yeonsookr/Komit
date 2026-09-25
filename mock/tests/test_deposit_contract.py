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

    def test_contract_the_client_has_not_signed_is_rejected_with_409(self) -> None:
        # R-CON-20: 의뢰인은 자기가 서명한 계약서에만 돈을 맡긴다. 132 는 아무도 서명하지 않은 계약서다.
        response = deposit_contract(DRAFT_CLIENT_CONTRACT_ID, self.REQUEST)

        self.assertIsInstance(response, JSONResponse)
        self.assertEqual(response.status_code, 409)
        self.assertEqual(
            json.loads(response.body),
            {"code": CONTRACT_NOT_SIGNED[0], "message": CONTRACT_NOT_SIGNED[1]},
        )


if __name__ == "__main__":
    unittest.main()
