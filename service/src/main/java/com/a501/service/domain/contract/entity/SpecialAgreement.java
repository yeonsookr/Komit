package com.a501.service.domain.contract.entity;

import com.a501.service.common.persistence.SoftDeleteEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

@Getter
@Entity
@Table(name = "special_agreements")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SpecialAgreement extends SoftDeleteEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "development_contract_id", nullable = false)
    private Integer developmentContractId;

    @Column(name = "issue_id", nullable = false)
    private Integer issueId;

    @Column(name = "changed_price")
    private Integer changedPrice;

    @Column(name = "changed_period_days")
    private Integer changedPeriodDays;

    @Column(name = "content", columnDefinition = "text")
    private String content;

    @Column(name = "proposed_at", nullable = false)
    private LocalDateTime proposedAt;

    @ColumnDefault("FALSE")
    @Column(name = "is_client_approved", nullable = false)
    private boolean clientApproved = false;

    @ColumnDefault("FALSE")
    @Column(name = "is_developer_approved", nullable = false)
    private boolean developerApproved = false;

    @Column(name = "client_approved_at")
    private LocalDateTime clientApprovedAt;

    @Column(name = "developer_approved_at")
    private LocalDateTime developerApprovedAt;

    @Column(name = "type", nullable = false, length = 50)
    private String type;

    @Column(name = "type_group", nullable = false, length = 50)
    private String typeGroup;

    @Column(name = "proposed_by_user_id", nullable = false)
    private Integer proposedByUserId;

    @Column(name = "contract_text", columnDefinition = "text")
    private String contractText;

    @Column(name = "template_id")
    private Integer templateId;
}
