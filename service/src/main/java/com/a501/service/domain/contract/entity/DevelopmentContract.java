package com.a501.service.domain.contract.entity;

import com.a501.service.common.persistence.SoftDeleteEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

@Getter
@Entity
@Table(name = "development_contracts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DevelopmentContract extends SoftDeleteEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "contract_id", nullable = false)
    private Integer contractId;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @ColumnDefault("0")
    @Column(name = "free_repair_days", nullable = false)
    private Integer freeRepairDays = 0;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "status_group", nullable = false, length = 50)
    private String statusGroup;

    @ColumnDefault("0")
    @Column(name = "total_amount", nullable = false)
    private Integer totalAmount = 0;

    /**
     * 개발 계약서의 개발 쪽 내용. 계약서를 처음 보낼 때부터 체결 상태다 — 개발 쪽 내용은 개발자가 정해 보낸
     * 것이고, 양쪽 서명으로 계약이 성립했는지는 공통 계약({@code contracts.status})이 가진다.
     */
    public static DevelopmentContract of(
            Integer contractId, LocalDate startDate, LocalDate dueDate, Integer freeRepairDays) {
        DevelopmentContract entity = new DevelopmentContract();
        entity.contractId = Objects.requireNonNull(contractId, "contractId");
        entity.status = "SIGNED";
        entity.statusGroup = "DEVELOPMENT_CONTRACT_STATUS";
        entity.rewrite(startDate, dueDate, freeRepairDays);
        return entity;
    }

    /** 협의 중에 다시 보낸 계약서의 날짜와 하자보수 기간으로 바꾼다. */
    public void rewrite(LocalDate startDate, LocalDate dueDate, Integer freeRepairDays) {
        this.startDate = Objects.requireNonNull(startDate, "startDate");
        this.dueDate = Objects.requireNonNull(dueDate, "dueDate");
        this.freeRepairDays = Objects.requireNonNull(freeRepairDays, "freeRepairDays");
    }

    /** 계약 금액. 작업 가격의 합이다. (R-CON-04) */
    public void changeTotalAmount(int totalAmount) {
        this.totalAmount = totalAmount;
    }
}
