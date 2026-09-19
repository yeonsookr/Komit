package com.a501.service.domain.setting.entity;

import com.a501.service.common.persistence.SoftDeleteEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

@Getter
@Entity
@Table(name = "settings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Setting extends SoftDeleteEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @ColumnDefault("TRUE")
    @Column(name = "notification_enabled", nullable = false)
    private boolean notificationEnabled = true;

    @ColumnDefault("FALSE")
    @Column(name = "is_service_terms_agreed", nullable = false)
    private boolean serviceTermsAgreed = false;

    @ColumnDefault("FALSE")
    @Column(name = "is_privacy_collect_agreed", nullable = false)
    private boolean privacyCollectAgreed = false;

    @ColumnDefault("FALSE")
    @Column(name = "is_privacy_third_party_agreed", nullable = false)
    private boolean privacyThirdPartyAgreed = false;

    @ColumnDefault("FALSE")
    @Column(name = "is_escrow_terms_agreed", nullable = false)
    private boolean escrowTermsAgreed = false;

    @ColumnDefault("FALSE")
    @Column(name = "is_seller_terms_agreed", nullable = false)
    private boolean sellerTermsAgreed = false;

    public static Setting agreeRequiredTerms(Integer userId) {
        Setting setting = new Setting();
        setting.userId = userId;
        setting.notificationEnabled = true;
        setting.serviceTermsAgreed = true;
        setting.privacyCollectAgreed = true;
        setting.privacyThirdPartyAgreed = true;
        setting.escrowTermsAgreed = true;
        setting.sellerTermsAgreed = false;
        return setting;
    }
}
