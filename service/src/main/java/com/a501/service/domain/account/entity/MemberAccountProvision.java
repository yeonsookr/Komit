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
import org.hibernate.annotations.ColumnDefault;

@Getter
@Entity
@Table(name = "member_account_provisions", uniqueConstraints =
        @UniqueConstraint(name = "uk_member_account_provision_user", columnNames = "user_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberAccountProvision extends AuditedEntity {
    /** 코드북 그룹. 값은 data.sql의 system_enums에 있다. */
    public static final String STATUS_GROUP = "MEMBER_ACCOUNT_STATUS";
    public static final String PENDING = "PENDING";
    public static final String ISSUED = "ISSUED";
    public static final String FAILED = "FAILED";

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

    @ColumnDefault("'" + PENDING + "'")
    @Column(name = "status", nullable = false, length = 50)
    private String status = PENDING;

    @ColumnDefault("'" + STATUS_GROUP + "'")
    @Column(name = "status_group", nullable = false, length = 50)
    private String statusGroup = STATUS_GROUP;

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
        this.status = ISSUED;
    }

    /**
     * 발급이 실패한 채로 남았음을 적습니다. 행과 멱등 키는 그대로 두어
     * 다음 시도가 같은 발급 요청을 이어받습니다. (R-ACC-13)
     */
    public void fail() {
        if (!isComplete()) this.status = FAILED;
    }
}
