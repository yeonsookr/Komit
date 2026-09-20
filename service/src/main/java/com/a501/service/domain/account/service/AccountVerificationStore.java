package com.a501.service.domain.account.service;

import java.time.OffsetDateTime;
import java.util.UUID;
import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.CommonErrorCode;
import com.a501.service.domain.account.AccountErrorCode;
import com.a501.service.domain.account.dto.response.AccountResponse;
import com.a501.service.domain.account.entity.Account;
import com.a501.service.domain.account.entity.AccountVerification;
import com.a501.service.domain.account.repository.AccountRepository;
import com.a501.service.domain.account.repository.AccountVerificationRepository;
import com.a501.service.domain.account.repository.MemberAccountProvisionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountVerificationStore {
    private final AccountVerificationRepository verifications;
    private final AccountRepository accounts;
    private final MemberAccountProvisionRepository provisions;

    @Transactional
    public Verification prepare(Integer userId, Integer bankCodeId, String bankName,
            String accountHolder, String encryptedNumber, String fingerprint, String maskedNumber) {
        // 회원 발급 행을 잠가 같은 회원의 재요청이 각각 다른 멱등키를 만들지 않게 한다.
        provisions.findWithLockByUserId(userId).orElseThrow();
        AccountVerification verification = verifications
                .findFirstByUserIdAndAccountFingerprintAndConfirmedAccountIdIsNullOrderByIdDesc(
                        userId, fingerprint)
                .filter(found -> !found.isExpired(OffsetDateTime.now()))
                .orElseGet(() -> verifications.saveAndFlush(AccountVerification.pending(userId,
                        bankCodeId, bankName, accountHolder, encryptedNumber, fingerprint,
                        maskedNumber, UUID.randomUUID().toString())));
        return view(verification);
    }

    @Transactional
    public Verification opened(Integer verificationId, String transactionUniqueNo, OffsetDateTime expiresAt) {
        AccountVerification verification = verifications.findWithLockById(verificationId).orElseThrow();
        if (!verification.isOpen()) verification.opened(transactionUniqueNo, expiresAt);
        return view(verification);
    }

    public Verification get(Integer verificationId, Integer userId) {
        AccountVerification verification = verifications.findById(verificationId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.VERIFICATION_NOT_FOUND));
        if (!verification.getUserId().equals(userId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        return view(verification);
    }

    @Transactional
    public AccountResponse confirm(Integer verificationId, Integer userId) {
        provisions.findWithLockByUserId(userId).orElseThrow();
        AccountVerification verification = verifications.findWithLockById(verificationId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.VERIFICATION_NOT_FOUND));
        if (!verification.getUserId().equals(userId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        if (verification.getConfirmedAccountId() != null) {
            Account existing = accounts.findById(verification.getConfirmedAccountId()).orElseThrow();
            return response(existing, verification.getBankName());
        }
        if (!verification.isOpen() || verification.isExpired(OffsetDateTime.now())) {
            throw new BusinessException(AccountErrorCode.VERIFICATION_NOT_FOUND);
        }
        Account account = accounts.findFirstByUserIdAndDeletedFalseOrderByIdDesc(userId)
                .orElseGet(() -> Account.register(userId, verification.getAccountHolder(),
                        verification.getMaskedNumber(), verification.getBankCodeId(),
                        verification.getAccountNoEncrypted()));
        account.replace(verification.getAccountHolder(), verification.getMaskedNumber(),
                verification.getBankCodeId(), verification.getAccountNoEncrypted());
        account = accounts.saveAndFlush(account);
        verification.confirmed(account.getId());
        return response(account, verification.getBankName());
    }

    private static Verification view(AccountVerification value) {
        return new Verification(value.getId(), value.getUserId(), value.getBankName(),
                value.getMaskedNumber(), value.getAccountHolder(), value.getAccountNoEncrypted(),
                value.getIdempotencyKey(), value.getTransactionUniqueNo(), value.getExpiresAt(),
                value.getConfirmedAccountId());
    }

    private static AccountResponse response(Account account, String bankName) {
        return new AccountResponse(account.getId(), bankName, account.getMaskedNumber(),
                account.getAccountHolder());
    }

    public record Verification(Integer id, Integer userId, String bankName,
            String maskedNumber, String accountHolder, String encryptedNumber, String idempotencyKey,
            String transactionUniqueNo, OffsetDateTime expiresAt, Integer confirmedAccountId) {
        public boolean isOpen() { return transactionUniqueNo != null; }
    }
}
