import json
import unittest

from fastapi.responses import JSONResponse

from app.routers import escrow


class EscrowContractTest(unittest.IsolatedAsyncioTestCase):
    def header(self, api_name: str, *, user_key: bool = False) -> escrow.EscrowRequestHeader:
        return escrow.EscrowRequestHeader(
            **escrow._escrow_header_example(api_name, with_user_key=user_key)
        )

    async def test_numeric_contract_keeps_its_identity_across_create_and_lookup(self) -> None:
        contract_id = "130"
        created = await escrow.create_escrow(
            escrow.CreateEscrowRequest(
                Header=self.header("createEscrow", user_key=True),
                contractId=contract_id,
                contractType="DEVELOPMENT",
                depositorName="김미미",
                amount=650000,
                withdrawalBankCode="001",
                withdrawalAccountNo=escrow.MOCK_ACCOUNT_NO,
            )
        )
        lookup = await escrow.inquire_escrow(
            escrow.InquireEscrowRequest(
                Header=self.header("inquireEscrow"), escrowId=created.REC.escrowId
            )
        )
        listed = await escrow.inquire_escrow_list(
            escrow.InquireEscrowListRequest(
                Header=self.header("inquireEscrowList"), contractIds=[contract_id]
            )
        )

        self.assertEqual(lookup.REC.contractId, contract_id)
        self.assertEqual(listed.REC.list[0].escrowId, created.REC.escrowId)
        self.assertEqual(lookup.REC.balance, created.REC.balance)

    async def test_text_contract_lookup_checks_escrow_pair(self) -> None:
        contract_id = "CLIENT-ORDER-ALPHA"
        created = await escrow.create_escrow(
            escrow.CreateEscrowRequest(
                Header=self.header("createEscrow", user_key=True),
                contractId=contract_id,
                contractType="DEVELOPMENT",
                depositorName="김미미",
                amount=650000,
                withdrawalBankCode="001",
                withdrawalAccountNo=escrow.MOCK_ACCOUNT_NO,
            )
        )
        lookup = await escrow.inquire_escrow(
            escrow.InquireEscrowRequest(
                Header=self.header("inquireEscrow"),
                escrowId=created.REC.escrowId,
                contractId=contract_id,
            )
        )
        mismatch = await escrow.inquire_escrow(
            escrow.InquireEscrowRequest(
                Header=self.header("inquireEscrow"),
                escrowId=created.REC.escrowId,
                contractId="OTHER-ORDER",
            )
        )

        self.assertEqual(lookup.REC.contractId, contract_id)
        self.assertIsInstance(mismatch, JSONResponse)
        self.assertEqual(json.loads(mismatch.body)["Header"]["responseCode"], "E5004")

    async def test_only_predeposit_escrow_can_be_cancelled(self) -> None:
        active = await escrow.cancel_escrow(
            escrow.CancelEscrowRequest(
                Header=self.header("cancelEscrow"), escrowId=escrow.ESCROW_ID
            )
        )
        pending = await escrow.cancel_escrow(
            escrow.CancelEscrowRequest(
                Header=self.header("cancelEscrow"),
                escrowId=escrow.DEPOSIT_PENDING_ESCROW_ID,
            )
        )

        self.assertIsInstance(active, JSONResponse)
        self.assertEqual(json.loads(active.body)["Header"]["responseCode"], "E5006")
        self.assertEqual(pending.REC.status, "CANCELLED")

    async def test_overdraw_is_rejected_for_release_and_refund(self) -> None:
        released = await escrow.release_escrow(
            escrow.ReleaseEscrowRequest(
                Header=self.header("releaseEscrow"),
                escrowId=escrow.ESCROW_ID,
                referenceId="SETTLEMENT:9001",
                amount=900000,
                recipientBankCode="001",
                recipientAccountNo=escrow.MOCK_ACCOUNT_NO,
                recipientName="이준",
            )
        )
        refunded = await escrow.refund_escrow(
            escrow.RefundEscrowRequest(
                Header=self.header("refundEscrow"),
                escrowId=escrow.ESCROW_ID,
                amount=900000,
                refundBankCode="001",
                refundAccountNo=escrow.MOCK_ACCOUNT_NO,
                refundHolderName="김미미",
            )
        )

        for result in (released, refunded):
            self.assertIsInstance(result, JSONResponse)
            self.assertEqual(json.loads(result.body)["Header"]["responseCode"], "E5008")

        exact_refund = await escrow.refund_escrow(
            escrow.RefundEscrowRequest(
                Header=self.header("refundEscrow"),
                escrowId=escrow.ESCROW_ID,
                amount=650000,
                refundBankCode="001",
                refundAccountNo=escrow.MOCK_ACCOUNT_NO,
                refundHolderName="김미미",
            )
        )
        self.assertEqual(exact_refund.REC.balance, 0)
        self.assertEqual(exact_refund.REC.status, "REFUNDED")

    async def test_refund_to_other_account_is_rejected_for_example_escrow(self) -> None:
        result = await escrow.refund_escrow(
            escrow.RefundEscrowRequest(
                Header=self.header("refundEscrow"),
                escrowId=escrow.ESCROW_ID,
                amount=250000,
                refundBankCode="001",
                refundAccountNo="0019999999999999",
                refundHolderName="김미미",
            )
        )

        self.assertIsInstance(result, JSONResponse)
        self.assertEqual(json.loads(result.body)["Header"]["responseCode"], "E5009")

    async def test_release_separates_gross_fee_and_net_without_charging_refund(self) -> None:
        def request(amount: int) -> escrow.ReleaseEscrowRequest:
            return escrow.ReleaseEscrowRequest(
                Header=self.header("releaseEscrow"),
                escrowId=escrow.ESCROW_ID,
                referenceId="SETTLEMENT:9001",
                amount=amount,
                recipientBankCode="001",
                recipientAccountNo="0019876543210987",
                recipientName="이준",
            )

        settled = await escrow.release_escrow(request(400000))
        rounded = await escrow.release_escrow(request(199))
        no_fee = await escrow.release_escrow(request(99))

        self.assertEqual(settled.REC.releasedAmount, 400000)
        self.assertEqual(settled.REC.feeAmount, 4000)
        self.assertEqual(settled.REC.netAmount, 396000)
        self.assertEqual(settled.REC.balance, 250000)
        self.assertEqual(rounded.REC.feeAmount, 1)
        self.assertEqual(rounded.REC.netAmount, 198)
        self.assertEqual(no_fee.REC.feeAmount, 0)
        self.assertEqual(no_fee.REC.netAmount, 99)


if __name__ == "__main__":
    unittest.main()
