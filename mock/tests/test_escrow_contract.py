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
                referenceId="SETTLEMENT:9001",
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
                referenceId="SETTLEMENT:9001",
            )
        )
        self.assertEqual(exact_refund.REC.balance, 0)
        self.assertEqual(exact_refund.REC.status, "COMPLETED")

    async def test_refund_to_other_account_is_rejected_for_example_escrow(self) -> None:
        result = await escrow.refund_escrow(
            escrow.RefundEscrowRequest(
                Header=self.header("refundEscrow"),
                escrowId=escrow.ESCROW_ID,
                amount=250000,
                refundBankCode="001",
                refundAccountNo="0019999999999999",
                refundHolderName="김미미",
                referenceId="SETTLEMENT:9001",
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

    async def test_partial_settlement_reports_only_confirmed_outflow(self) -> None:
        for escrow_id, status in (
            (escrow.SETTLEMENT_PROCESSING_ESCROW_ID, "PROCESSING"),
            (escrow.SETTLEMENT_UNKNOWN_ESCROW_ID, "UNKNOWN"),
        ):
            request = escrow.ReleaseEscrowRequest(
                Header=self.header("releaseEscrow"),
                escrowId=escrow_id,
                referenceId=escrow.SETTLEMENT_FIXTURE_REFERENCE_ID,
                amount=400000,
                recipientBankCode="001",
                recipientAccountNo="0019876543210987",
                recipientName="이준",
            )
            released = await escrow.release_escrow(request)
            repeated = await escrow.release_escrow(request)
            lookup = await escrow.inquire_escrow(
                escrow.InquireEscrowRequest(
                    Header=self.header("inquireEscrow"), escrowId=escrow_id
                )
            )
            refund = await escrow.refund_escrow(
                escrow.RefundEscrowRequest(
                    Header=self.header("refundEscrow"),
                    escrowId=escrow_id,
                    amount=250000,
                    refundBankCode="001",
                    refundAccountNo=escrow.MOCK_ACCOUNT_NO,
                    refundHolderName="김미미",
                    referenceId=escrow.SETTLEMENT_FIXTURE_REFERENCE_ID,
                )
            )

            self.assertEqual(released.REC.status, status)
            self.assertEqual(repeated.REC, released.REC)
            self.assertEqual(released.REC.releasedAmount, 396000)
            self.assertEqual(released.REC.netAmount, 396000)
            self.assertEqual(released.REC.feeAmount, 0)
            self.assertEqual(released.REC.balance, 254000)
            self.assertEqual(lookup.REC.releasedAmount, released.REC.releasedAmount)
            self.assertEqual(lookup.REC.balance, released.REC.balance)
            self.assertEqual(lookup.REC.transactions[-1].transactionType, "FEE")
            self.assertEqual(lookup.REC.transactions[-1].status, status)
            self.assertEqual(json.loads(refund.body)["Header"]["responseCode"], "E5010")

            new_settlement = await escrow.release_escrow(
                request.model_copy(update={"referenceId": "SETTLEMENT:9002"})
            )
            self.assertEqual(
                json.loads(new_settlement.body)["Header"]["responseCode"], "E5010"
            )

    async def test_failed_settlement_has_no_outflow(self) -> None:
        result = await escrow.release_escrow(
            escrow.ReleaseEscrowRequest(
                Header=self.header("releaseEscrow"),
                escrowId=escrow.SETTLEMENT_FAILED_ESCROW_ID,
                referenceId=escrow.SETTLEMENT_FIXTURE_REFERENCE_ID,
                amount=400000,
                recipientBankCode="001",
                recipientAccountNo="0019876543210987",
                recipientName="이준",
            )
        )
        lookup = await escrow.inquire_escrow(
            escrow.InquireEscrowRequest(
                Header=self.header("inquireEscrow"),
                escrowId=escrow.SETTLEMENT_FAILED_ESCROW_ID,
            )
        )

        self.assertEqual(result.REC.status, "FAILED")
        self.assertEqual(result.REC.releasedAmount, 0)
        self.assertEqual(result.REC.feeAmount, 0)
        self.assertEqual(result.REC.netAmount, 0)
        self.assertEqual(result.REC.balance, 650000)
        self.assertEqual(lookup.REC.transactions[-1].status, "FAILED")

        listed = await escrow.inquire_escrow_list(
            escrow.InquireEscrowListRequest(Header=self.header("inquireEscrowList"))
        )
        self.assertEqual(listed.REC.totalCount, 2)

    async def test_reconciliation_uses_operator_key_and_all_services(self) -> None:
        operator_header = self.header("inquireReconciliation").model_copy(
            update={"clientApiKey": escrow.MOCK_OPERATOR_CLIENT_API_KEY}
        )
        result = await escrow.inquire_reconciliation(
            escrow.InquireReconciliationRequest(Header=operator_header, days=4)
        )

        self.assertEqual(result.REC.ledgerTotal, 1000000)
        self.assertEqual(result.REC.bankBalance, 1000000)
        self.assertEqual(result.REC.escrowCount, 2)
        self.assertEqual(result.REC.result, "MATCHED")
        self.assertEqual(result.REC.history[3].bankBalance, 950000)
        self.assertEqual(result.REC.history[3].result, "BANK_MISMATCH")

        service_result = await escrow.inquire_reconciliation(
            escrow.InquireReconciliationRequest(
                Header=self.header("inquireReconciliation")
            )
        )
        self.assertIsInstance(service_result, JSONResponse)
        self.assertEqual(service_result.status_code, 400)
        self.assertEqual(
            json.loads(service_result.body)["Header"]["responseCode"], "E5001"
        )

        operator_lookup = await escrow.inquire_escrow(
            escrow.InquireEscrowRequest(
                Header=self.header("inquireEscrow").model_copy(
                    update={"clientApiKey": escrow.MOCK_OPERATOR_CLIENT_API_KEY}
                ),
                escrowId=escrow.ESCROW_ID,
            )
        )
        self.assertIsInstance(operator_lookup, JSONResponse)
        self.assertEqual(operator_lookup.status_code, 400)
        self.assertEqual(
            json.loads(operator_lookup.body)["Header"]["responseCode"], "E5001"
        )


if __name__ == "__main__":
    unittest.main()
