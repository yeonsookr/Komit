package com.a501.service.domain.developer.entity;

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

@Getter
@Entity
@Table(name = "price_catalogs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PriceCatalog extends SoftDeleteEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "task_name", nullable = false, length = 50)
    private String taskName;

    @Column(name = "price", nullable = false)
    private Integer price;

    @Column(name = "period_days")
    private Short periodDays;

    @Column(name = "developer_id", nullable = false)
    private Integer developerId;

    @Column(name = "task_type", nullable = false, length = 50)
    private String taskType;

    @Column(name = "task_type_group", nullable = false, length = 50)
    private String taskTypeGroup;
}
