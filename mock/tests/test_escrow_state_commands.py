import json
import unittest
from app.routers import escrow


class StateCommandTest(unittest.IsolatedAsyncioTestCase):
    def header(self, name):
        return escrow.EscrowRequestHeader(**escrow._escrow_header_example(name))

    async def test_uncertain_deposit_cannot_be_cancelled(self):
        for escrow_id, state in [(203, 'PROCESSING'), (204, 'UNKNOWN')]:
            result = await escrow.cancel_escrow(escrow.CancelEscrowRequest(
                Header=self.header('cancelEscrow'), escrowId=escrow_id))
            self.assertEqual(json.loads(result.body)['Header']['responseCode'], 'E5015')
            lookup = await escrow.inquire_escrow(escrow.InquireEscrowRequest(
                Header=self.header('inquireEscrow'), escrowId=escrow_id))
            self.assertEqual(lookup.REC.transactions[0].status, state)
            self.assertEqual(lookup.REC.balance, 0)
        confirmed = await escrow.cancel_escrow(escrow.CancelEscrowRequest(
            Header=self.header('cancelEscrow'), escrowId=205))
        self.assertEqual(confirmed.REC.status, 'CANCELLED')

    async def test_report_mismatch_cannot_release_existing_hold(self):
        for name, model, handler in [
            ('holdEscrow', escrow.HoldEscrowRequest, escrow.hold_escrow),
            ('unholdEscrow', escrow.UnholdEscrowRequest, escrow.unhold_escrow),
        ]:
            result = await handler(model(Header=self.header(name), escrowId=409, reportId='REPORT-8'))
            self.assertEqual(json.loads(result.body)['Header']['responseCode'], 'E5014')
        lookup = await escrow.inquire_escrow(escrow.InquireEscrowRequest(
            Header=self.header('inquireEscrow'), escrowId=409))
        self.assertTrue(lookup.REC.isHeld)
