package com.a501.service.domain.code.entity;

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
import org.hibernate.annotations.ColumnDefault;

@Getter
@Entity
@Table(name = "dynamic_codes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DynamicCode extends AuditedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "code_id", nullable = false)
    private Integer codeId;

    @Column(name = "code_name", nullable = false, length = 100)
    private String codeName;

    @ColumnDefault("0")
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @ColumnDefault("TRUE")
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "group_id", nullable = false)
    private Integer groupId;
}
