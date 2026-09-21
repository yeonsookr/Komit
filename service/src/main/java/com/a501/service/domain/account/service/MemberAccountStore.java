package com.a501.service.domain.account.service;

import java.util.UUID;
import com.a501.service.domain.account.entity.MemberAccountProvision;
import com.a501.service.domain.account.repository.MemberAccountProvisionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
                provision.getUserKeyEncrypted(), provision.getBankCode(),
                provision.getAccountNoEncrypted());
    }

    @Transactional
    public void complete(Integer userId, String encryptedUserKey, String bankCode,
            String encryptedAccountNo) {
        MemberAccountProvision provision = repository.findWithLockByUserId(userId).orElseThrow();
        if (!provision.isComplete()) {
            provision.complete(encryptedUserKey, bankCode, encryptedAccountNo);
        }
    }

    /** 발급 실패를 남깁니다. 실패를 알린 요청이 되돌아가도 상태는 남아야 하므로 별도 트랜잭션입니다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Integer userId) {
        repository.findWithLockByUserId(userId).ifPresent(MemberAccountProvision::fail);
    }

    public record Pending(String escrowUserId, String idempotencyKey, String encryptedUserKey,
            String bankCode, String encryptedAccountNo) {
        /** 에스크로가 발급을 마쳐 보관 중인 계좌가 있는지. */
        public boolean isIssued() {
            return encryptedUserKey != null && encryptedAccountNo != null;
        }
    }
}
