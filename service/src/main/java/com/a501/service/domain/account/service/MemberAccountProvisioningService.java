package com.a501.service.domain.account.service;

import com.a501.service.domain.account.client.EscrowAccountClient;
import com.a501.service.domain.account.crypto.AccountDataCrypto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MemberAccountProvisioningService {
    private final MemberAccountStore store;
    private final EscrowAccountClient escrow;
    private final AccountDataCrypto crypto;

    public String ensureProvisioned(Integer userId) {
        MemberAccountStore.Pending pending = store.prepare(userId);
        if (pending.encryptedUserKey() != null) {
            return crypto.decrypt(pending.encryptedUserKey());
        }
        EscrowAccountClient.MemberAccount issued = escrow.createMemberAccount(
                pending.escrowUserId(), pending.idempotencyKey());
        store.complete(userId, crypto.encrypt(issued.userKey()), issued.bankCode(),
                crypto.encrypt(issued.accountNo()));
        return issued.userKey();
    }
}
