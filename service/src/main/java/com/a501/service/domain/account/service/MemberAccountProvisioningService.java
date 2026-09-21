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
        return provision(userId).userKey();
    }

    /**
     * 회원 몫으로 발급한 계좌를 돌려줍니다. 아직 없으면 에스크로에 발급을 요청합니다.
     *
     * <p>회원마다 행이 하나이고 멱등 키를 계속 재사용하므로, 가입을 중간에 그만둔 회원이
     * 다시 들어와도 같은 계좌를 이어받습니다. 새로 발급하지 않습니다. (R-ACC-13)
     */
    public MemberAccount provision(Integer userId) {
        MemberAccountStore.Pending pending = store.prepare(userId);
        if (pending.isIssued()) {
            return new MemberAccount(crypto.decrypt(pending.encryptedUserKey()),
                    pending.bankCode(), crypto.decrypt(pending.encryptedAccountNo()));
        }

        EscrowAccountClient.MemberAccount issued;
        try {
            issued = escrow.createMemberAccount(pending.escrowUserId(), pending.idempotencyKey());
        } catch (RuntimeException exception) {
            store.fail(userId);
            throw exception;
        }
        store.complete(userId, crypto.encrypt(issued.userKey()), issued.bankCode(),
                crypto.encrypt(issued.accountNo()));
        return new MemberAccount(issued.userKey(), issued.bankCode(), issued.accountNo());
    }

    public record MemberAccount(String userKey, String bankCode, String accountNo) {}
}
