package com.a501.service.domain.contract.entity;

import com.a501.service.common.persistence.CreatedAtEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** AI가 처음 만든 계약서 version 1. 학습·감사를 위해 생성 당시 JSON을 바꾸지 않고 보존한다. */
@Getter
@Entity
@Table(
        name = "ai_contract_draft_snapshots",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ai_contract_draft_snapshot_contract", columnNames = "contract_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiContractDraftSnapshot extends CreatedAtEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "contract_id", nullable = false, updatable = false)
    private Integer contractId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content_snapshot", nullable = false, updatable = false, columnDefinition = "json")
    private String contentSnapshot;

    public static AiContractDraftSnapshot of(Integer contractId, String contentSnapshot) {
        AiContractDraftSnapshot snapshot = new AiContractDraftSnapshot();
        snapshot.contractId = Objects.requireNonNull(contractId, "contractId");
        snapshot.contentSnapshot = Objects.requireNonNull(contentSnapshot, "contentSnapshot");
        return snapshot;
    }
}
