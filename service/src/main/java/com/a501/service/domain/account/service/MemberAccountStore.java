package com.a501.service.domain.account.service;

import java.util.UUID;
import com.a501.service.domain.account.entity.MemberAccountProvision;
import com.a501.service.domain.account.repository.MemberAccountProvisionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberAccountStore {
    private final MemberAccountProvisionRepository repository;

    @Transactional
    public Pending prepare(Integer userId) {
        MemberAccountProvision provision = repository.findByUserId(userId)
                .orElseGet(() -> repository.saveAndFlush(
                        MemberAccountProvision.pending(userId, UUID.randomUUID().toString())));
        return new Pending(provision.getEscrowUserId(), provision.getIdempotencyKey(),
                provision.getUserKeyEncrypted());
    }

    @Transactional
    public void complete(Integer userId, String encryptedUserKey, String bankCode,
            String encryptedAccountNo) {
        MemberAccountProvision provision = repository.findWithLockByUserId(userId).orElseThrow();
        if (!provision.isComplete()) {
            provision.complete(encryptedUserKey, bankCode, encryptedAccountNo);
        }
    }

    public record Pending(String escrowUserId, String idempotencyKey, String encryptedUserKey) {}
}
