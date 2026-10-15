import json
import unittest

from app.routers import escrow


class AccountAuthContractTest(unittest.IsolatedAsyncioTestCase):
    async def test_retry_keeps_original_expiry(self):
        header = escrow.EscrowRequestHeader(**escrow._account_header_example('openAccountAuth'))
        first = await escrow.open_account_auth(escrow.OpenAccountAuthRequest(Header=header, accountNo=escrow.MOCK_ACCOUNT_NO))
        header.transmissionTime = '120500'
        header.institutionTransactionUniqueNo = '20260909120500000002'
        again = await escrow.open_account_auth(escrow.OpenAccountAuthRequest(Header=header, accountNo=escrow.MOCK_ACCOUNT_NO))
        self.assertEqual(first.REC, again.REC)
        header.idempotencyKey = None
        missing = await escrow.open_account_auth(escrow.OpenAccountAuthRequest(Header=header, accountNo=escrow.MOCK_ACCOUNT_NO))
        self.assertEqual(json.loads(missing.body)['Header']['responseCode'], 'E5002')
        header.idempotencyKey = 'pending-auth-key'
        pending = await escrow.open_account_auth(escrow.OpenAccountAuthRequest(Header=header, accountNo=escrow.MOCK_ACCOUNT_NO))
        self.assertEqual(pending.status_code, 503)
        self.assertNotIn('REC', json.loads(pending.body))

    async def test_verification_requires_matching_unexpired_request(self):
        for reference, code in [('1000000001', None), ('1000000408', 'E5011'), ('1000000404', 'E5012')]:
            request = escrow.CheckAuthCodeRequest(
                Header=escrow.EscrowRequestHeader(**escrow._account_header_example('checkAuthCode')),
                accountNo=escrow.MOCK_ACCOUNT_NO, authCode='9999', transactionUniqueNo=reference)
            result = await escrow.check_auth_code(request)
            if code:
                self.assertEqual(json.loads(result.body)['Header']['responseCode'], code)
            else:
                self.assertEqual(result.REC.transactionUniqueNo, reference)
                self.assertEqual(result.REC.status, 'SUCCESS')
                request.Header.userKey = 'another-user'
                denied = await escrow.check_auth_code(request)
                self.assertEqual(json.loads(denied.body)['Header']['responseCode'], 'E5012')
