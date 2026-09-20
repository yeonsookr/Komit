package com.a501.service.domain.account.service;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.CommonErrorCode;
import com.a501.service.domain.account.AccountErrorCode;
import com.a501.service.domain.account.client.EscrowAccountClient;
import com.a501.service.domain.account.crypto.AccountDataCrypto;
import com.a501.service.domain.account.dto.request.ConfirmAccountVerificationRequest;
import com.a501.service.domain.account.dto.request.RequestAccountVerificationRequest;
import com.a501.service.domain.account.dto.response.AccountResponse;
import com.a501.service.domain.account.dto.response.AccountVerificationResponse;
import com.a501.service.domain.code.dto.CodeResponse;
import com.a501.service.domain.code.service.CodeQueryService;
import com.a501.service.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 외부 HTTP 호출 전후의 짧은 DB 트랜잭션은 AccountVerificationStore가 소유한다. */
@Service
@RequiredArgsConstructor
public class AccountVerificationService {
    private final UserService users;
    private final CodeQueryService codes;
    private final MemberAccountProvisioningService provisioning;
    private final AccountVerificationStore store;
    private final AccountDataCrypto crypto;
    private final EscrowAccountClient escrow;

    public AccountVerificationResponse request(Integer userId, RequestAccountVerificationRequest request) {
        users.getActiveUser(userId);
        String userKey = provisioning.ensureProvisioned(userId);
        String bankName = codes.getCodes("BANK_CODE").content().stream()
                .filter(code -> code.codeId().equals(request.bankCodeId()))
                .map(CodeResponse::codeName)
                .findFirst()
                .orElseThrow(() -> new BusinessException(CommonErrorCode.INVALID_REQUEST));
        String accountNo = request.accountNumber();
        String fingerprint = crypto.lookup(userId + "|" + request.bankCodeId() + "|"
                + accountNo + "|" + request.accountHolder());
        AccountVerificationStore.Verification verification = store.prepare(userId, request.bankCodeId(),
                bankName, request.accountHolder(), crypto.encrypt(accountNo), fingerprint,
                "···· " + accountNo.substring(accountNo.length() - 4));
        if (verification.isOpen()) return response(verification);

        EscrowAccountClient.OpenedAuth opened = escrow.openAccountAuth(userKey, accountNo,
                verification.idempotencyKey());
        OffsetDateTime expiresAt;
        try {
            expiresAt = OffsetDateTime.parse(opened.expiresAt());
        } catch (DateTimeParseException exception) {
            throw new BusinessException(AccountErrorCode.ESCROW_UNAVAILABLE);
        }
        return response(store.opened(verification.id(), opened.transactionUniqueNo(), expiresAt));
    }

    public AccountResponse confirm(Integer userId, Integer verificationId,
            ConfirmAccountVerificationRequest request) {
        users.getActiveUser(userId);
        AccountVerificationStore.Verification verification = store.get(verificationId, userId);
        if (verification.confirmedAccountId() != null) return store.confirm(verificationId, userId);
        if (!verification.isOpen() || !verification.expiresAt().isAfter(OffsetDateTime.now())) {
            throw new BusinessException(AccountErrorCode.VERIFICATION_NOT_FOUND);
        }
        String userKey = provisioning.ensureProvisioned(userId);
        escrow.checkAuthCode(userKey, verification.transactionUniqueNo(),
                crypto.decrypt(verification.encryptedNumber()), request.authCode());
        return store.confirm(verificationId, userId);
    }

    private static AccountVerificationResponse response(AccountVerificationStore.Verification value) {
        return new AccountVerificationResponse(value.id(), value.bankName(), value.maskedNumber(),
                value.accountHolder(), value.expiresAt());
    }
}
