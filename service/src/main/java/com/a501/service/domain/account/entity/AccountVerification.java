package com.a501.service.domain.account.entity;

import java.time.OffsetDateTime;
import com.a501.service.common.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "account_verifications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountVerification extends AuditedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "bank_code_id", nullable = false)
    private Integer bankCodeId;

    @Column(name = "bank_name", nullable = false, length = 100)
    private String bankName;

    @Column(name = "account_holder", nullable = false, length = 50)
    private String accountHolder;

    @Column(name = "account_no_encrypted", nullable = false, length = 255)
    private String accountNoEncrypted;

    @Column(name = "account_fingerprint", nullable = false, length = 64)
    private String accountFingerprint;

    @Column(name = "masked_number", nullable = false, length = 30)
    private String maskedNumber;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "transaction_unique_no", length = 100)
    private String transactionUniqueNo;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "confirmed_account_id")
    private Integer confirmedAccountId;

    public static AccountVerification pending(Integer userId, Integer bankCodeId, String bankName,
            String accountHolder, String encryptedNumber, String fingerprint, String maskedNumber,
            String idempotencyKey) {
        AccountVerification verification = new AccountVerification();
        verification.userId = userId;
        verification.bankCodeId = bankCodeId;
        verification.bankName = bankName;
        verification.accountHolder = accountHolder;
        verification.accountNoEncrypted = encryptedNumber;
        verification.accountFingerprint = fingerprint;
        verification.maskedNumber = maskedNumber;
        verification.idempotencyKey = idempotencyKey;
        return verification;
    }

    public boolean isOpen() { return transactionUniqueNo != null && confirmedAccountId == null; }
    public boolean isExpired(OffsetDateTime now) { return expiresAt != null && !expiresAt.isAfter(now); }

    public void opened(String transactionUniqueNo, OffsetDateTime expiresAt) {
        this.transactionUniqueNo = transactionUniqueNo;
        this.expiresAt = expiresAt;
    }

    public void confirmed(Integer accountId) { this.confirmedAccountId = accountId; }
}
