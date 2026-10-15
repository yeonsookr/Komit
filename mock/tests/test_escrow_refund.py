import json
import unittest
from app.routers import escrow


class RefundContractTest(unittest.IsolatedAsyncioTestCase):
    def request(self, escrow_id, amount=250000):
        return escrow.RefundEscrowRequest(
            Header=escrow.EscrowRequestHeader(**escrow._escrow_header_example('refundEscrow')),
            escrowId=escrow_id, amount=amount, refundBankCode='001',
            refundAccountNo=escrow.MOCK_ACCOUNT_NO, refundHolderName='김미미',
            referenceId='SETTLEMENT:9001')

    async def test_refund_response_matches_ledger_and_list(self):
        for escrow_id, state in escrow.REFUND_FIXTURE_STATUSES.items():
            result = await escrow.refund_escrow(self.request(escrow_id))
            lookup = await escrow.inquire_escrow(escrow.InquireEscrowRequest(
                Header=escrow.EscrowRequestHeader(**escrow._escrow_header_example('inquireEscrow')),
                escrowId=escrow_id))
            listed = await escrow.inquire_escrow_list(escrow.InquireEscrowListRequest(
                Header=escrow.EscrowRequestHeader(**escrow._escrow_header_example('inquireEscrowList')),
                contractIds=[lookup.REC.contractId]))
            self.assertEqual(result.REC.status, state)
            self.assertEqual(result.REC.balance, lookup.REC.balance)
            self.assertEqual(result.REC.refundedAmount, lookup.REC.refundedAmount)
            self.assertEqual(result.REC.transactionId, lookup.REC.transactions[-1].transactionId)
            self.assertEqual(listed.REC.list[0].status, lookup.REC.status)
            self.assertEqual(lookup.REC.balance, lookup.REC.depositedAmount-lookup.REC.releasedAmount-lookup.REC.refundedAmount)

    async def test_partial_refund_and_new_settlement_are_rejected(self):
        partial = await escrow.refund_escrow(self.request(42))
        self.assertEqual(json.loads(partial.body)['Header']['responseCode'], 'E5013')
        request = self.request(48)
        request.referenceId = 'SETTLEMENT:other'
        result = await escrow.refund_escrow(request)
        self.assertEqual(json.loads(result.body)['Header']['responseCode'], 'E5010')

    async def test_mismatched_holder_does_not_report_transfer_success(self):
        request = self.request(46)
        request.refundHolderName = 'invalid-holder-name'
        result = await escrow.refund_escrow(request)
        self.assertEqual(json.loads(result.body)['Header']['responseCode'], 'E5016')
        self.assertNotIn('REC', json.loads(result.body))
