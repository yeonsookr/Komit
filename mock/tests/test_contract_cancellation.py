import json
import unittest

from fastapi.responses import JSONResponse

from app.routers.service import (
    CANCELLED_CONTRACT_ID,
    CANCELLED_SESSION_ID,
    CONTRACT_CANCELLED,
    CONTRACT_CONCLUDED,
    CONTRACT_ID,
    REFUND_PENDING,
    REFUND_PENDING_CONTRACT_ID,
    SESSION_CANCELLED,
    SIGNED_CONTRACT_ID,
    AmendConsultingContractRequest,
    ContractResponse,
    DepositRequest,
    RealtimeTicketRequest,
    SignContractRequest,
    amend_contract,
    cancel_contract,
    deposit_contract,
    get_contract,
    issue_realtime_ticket,
    join_consulting_session,
    sign_contract,
)


def _error(response: JSONResponse) -> tuple[int, dict]:
    return response.status_code, json.loads(response.body)


class ContractCancellationTest(unittest.TestCase):
    """개발자 서명 전 의뢰인의 계약 취소와 그 뒤의 차단 (R-CON-21)."""

    def test_cancel_returns_cancelled_contract(self) -> None:
        result = cancel_contract(CONTRACT_ID)

        self.assertIsInstance(result, ContractResponse)
        self.assertEqual(result.status, "CANCELLED")
        self.assertEqual(result.contractId, CONTRACT_ID)

    def test_concluded_contract_cannot_be_cancelled(self) -> None:
        self.assertEqual(
            _error(cancel_contract(SIGNED_CONTRACT_ID)),
            (409, {"code": CONTRACT_CONCLUDED[0], "message": CONTRACT_CONCLUDED[1]}),
        )

    def test_unknown_refund_is_503(self) -> None:
        self.assertEqual(
            _error(cancel_contract(REFUND_PENDING_CONTRACT_ID)),
            (503, {"code": REFUND_PENDING[0], "message": REFUND_PENDING[1]}),
        )

    def test_cancelled_contract_reads_as_cancelled(self) -> None:
        self.assertEqual(get_contract(CANCELLED_CONTRACT_ID).status, "CANCELLED")

    def test_cancelled_contract_rejects_sign_amend_and_deposit(self) -> None:
        expected = (409, {"code": CONTRACT_CANCELLED[0], "message": CONTRACT_CANCELLED[1]})
        self.assertEqual(
            _error(sign_contract(CANCELLED_CONTRACT_ID, SignContractRequest(signatureImageFileId=41))),
            expected,
        )
        self.assertEqual(
            _error(amend_contract(CANCELLED_CONTRACT_ID, AmendConsultingContractRequest())),
            expected,
        )
        self.assertEqual(
            _error(deposit_contract(
                CANCELLED_CONTRACT_ID, DepositRequest(paymentMethodId=9, idempotencyKey="dep"))),
            expected,
        )

    def test_session_of_cancelled_contract_cannot_be_entered(self) -> None:
        expected = (409, {"code": SESSION_CANCELLED[0], "message": SESSION_CANCELLED[1]})
        self.assertEqual(_error(join_consulting_session(CANCELLED_SESSION_ID)), expected)
        self.assertEqual(
            _error(issue_realtime_ticket(RealtimeTicketRequest(
                channel="CONSULTING_SESSION", channelId=str(CANCELLED_SESSION_ID)))),
            expected,
        )


if __name__ == "__main__":
    unittest.main()
