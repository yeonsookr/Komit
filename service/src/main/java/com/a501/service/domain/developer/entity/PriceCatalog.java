package com.a501.service.domain.developer.entity;

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

/** 가격표 한 줄. {@code task_type}은 코드 분류 {@code TASK_TYPE}의 코드값({@code CONSULT}·{@code DEVELOP})입니다. */
@Getter
@Entity
@Table(name = "price_catalogs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PriceCatalog extends SoftDeleteEntity {
    /** {@code task_type_group}에 고정으로 들어가는 코드 분류 키. */
    public static final String TASK_TYPE_GROUP = "TASK_TYPE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "task_name", nullable = false, length = 50)
    private String taskName;

    @Column(name = "price", nullable = false)
    private Long price;

    @Column(name = "period_days")
    private Short periodDays;

    @Column(name = "developer_id", nullable = false)
    private Integer developerId;

    @Column(name = "task_type", nullable = false, length = 50)
    private String taskType;

    @Column(name = "task_type_group", nullable = false, length = 50)
    private String taskTypeGroup = TASK_TYPE_GROUP;

    public static PriceCatalog create(Integer developerId, String taskType, String taskName, Long price, Short periodDays) {
        PriceCatalog row = new PriceCatalog();
        row.developerId = developerId;
        row.update(taskType, taskName, price, periodDays);
        return row;
    }

    public void update(String taskType, String taskName, Long price, Short periodDays) {
        this.taskType = taskType;
        this.taskTypeGroup = TASK_TYPE_GROUP;
        this.taskName = taskName;
        this.price = price;
        this.periodDays = periodDays;
    }

    public void remove(LocalDateTime now) {
        markDeleted(now);
    }
}
