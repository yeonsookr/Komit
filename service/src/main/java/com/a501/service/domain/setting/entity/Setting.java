package com.a501.service.domain.setting.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "settings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Setting {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Integer userId;

    @Column(name = "notification_enabled", nullable = false)
    private boolean notificationEnabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "record_status", nullable = false, length = 50)
    private String recordStatus;

    @Column(name = "record_status_group", nullable = false, length = 50)
    private String recordStatusGroup;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    @Column(name = "is_service_terms_agreed", nullable = false)
    private boolean serviceTermsAgreed;

    @Column(name = "is_privacy_collect_agreed", nullable = false)
    private boolean privacyCollectAgreed;

    @Column(name = "is_privacy_third_party_agreed", nullable = false)
    private boolean privacyThirdPartyAgreed;

    @Column(name = "is_escrow_terms_agreed", nullable = false)
    private boolean escrowTermsAgreed;

    @Column(name = "is_seller_terms_agreed", nullable = false)
    private boolean sellerTermsAgreed;

    public static Setting agreeRequiredTerms(Integer userId) {
        Setting setting = new Setting();
        setting.userId = userId;
        setting.notificationEnabled = true;
        setting.recordStatus = "ACTIVE";
        setting.recordStatusGroup = "RECORD_STATUS";
        setting.deleted = false;
        setting.serviceTermsAgreed = true;
        setting.privacyCollectAgreed = true;
        setting.privacyThirdPartyAgreed = true;
        setting.escrowTermsAgreed = true;
        setting.sellerTermsAgreed = false;
        return setting;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
