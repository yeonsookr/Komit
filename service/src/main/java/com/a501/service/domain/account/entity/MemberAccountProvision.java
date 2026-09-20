package com.a501.service.domain.account.entity;

import com.a501.service.common.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "member_account_provisions", uniqueConstraints =
        @UniqueConstraint(name = "uk_member_account_provision_user", columnNames = "user_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberAccountProvision extends AuditedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "escrow_user_id", nullable = false, length = 40)
    private String escrowUserId;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "user_key_encrypted", length = 255)
    private String userKeyEncrypted;

    @Column(name = "bank_code", length = 10)
    private String bankCode;

    @Column(name = "account_no_encrypted", length = 255)
    private String accountNoEncrypted;

    public static MemberAccountProvision pending(Integer userId, String idempotencyKey) {
        MemberAccountProvision provision = new MemberAccountProvision();
        provision.userId = userId;
        provision.escrowUserId = "komit-" + userId + "@komit.local";
        provision.idempotencyKey = idempotencyKey;
        return provision;
    }

    public boolean isComplete() { return userKeyEncrypted != null && accountNoEncrypted != null; }

    public void complete(String userKeyEncrypted, String bankCode, String accountNoEncrypted) {
        this.userKeyEncrypted = userKeyEncrypted;
        this.bankCode = bankCode;
        this.accountNoEncrypted = accountNoEncrypted;
    }
}
