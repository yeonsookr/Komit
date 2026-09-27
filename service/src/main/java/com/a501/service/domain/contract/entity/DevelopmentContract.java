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

    /**
     * AI 초안 ver1 의 개발 쪽 내용. 날짜와 하자보수 기간은 개발자가 입력하는 값이라 비워 두고, 계약서를 보낼 때
     * 채운다. 컨설팅 초안이 금액을 0 으로 두는 것과 같다.
     */
    public static DevelopmentContract forDraft(Integer contractId) {
        DevelopmentContract entity = new DevelopmentContract();
        entity.contractId = Objects.requireNonNull(contractId, "contractId");
        entity.status = "SIGNED";
        entity.statusGroup = "DEVELOPMENT_CONTRACT_STATUS";
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

    /** 아직 착수하지 않았는지. 계약서를 쓸 때부터 착수 전까지 개발 쪽 상태는 {@code SIGNED} 다. */
    public boolean isBeforeStart() {
        return "SIGNED".equals(status);
    }

    /** 착수해 작업을 진행하는 중. 작업의 확인 요청·확인 결과·통과를 이때만 받는다. */
    public boolean isInProgress() {
        return "IN_PROGRESS".equals(status);
    }

    /** 대금 지급까지 끝난 계약. 리뷰와 마친 계약 수, 지급 뒤 해금 파일 열람이 이 값을 본다. */
    public boolean isCompleted() {
        return "COMPLETED".equals(status);
    }

    /**
     * Escrow 가 지급액과 수수료 이체를 모두 확정했다. 지급 기록의 원본은 Escrow 에 있고, 여기 남기는 것은 「더 볼 필요가
     * 없다」는 표시다. (`R-PAY-05`, `R-PAY-07`)
     */
    public void complete() {
        if (!isInProgress()) {
            throw new IllegalStateException("진행 중인 개발 계약만 완료할 수 있습니다.");
        }
        this.status = "COMPLETED";
    }

    /** 개발자가 착수했다. 착수일은 계약서 날짜 그대로 둔다. */
    public void start() {
        if (!isBeforeStart()) {
            throw new IllegalStateException("이미 착수한 개발 계약입니다.");
        }
        this.status = "IN_PROGRESS";
    }
}
