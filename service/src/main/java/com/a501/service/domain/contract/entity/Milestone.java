package com.a501.service.domain.contract.entity;

import com.a501.service.common.persistence.SoftDeleteEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

@Getter
@Entity
@Table(name = "milestones")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Milestone extends SoftDeleteEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "development_contract_id", nullable = false)
    private Integer developmentContractId;

    @Column(name = "issue_id")
    private Integer issueId;

    /**
     * 계약서에서 바꾼 작업 이름. 비어 있으면 붙은 요구사항의 이름을 쓴다.
     *
     * <p>요청서는 계약서가 건드리지 않는다. 합의해 바꾼 이름은 요구사항이 아니라 작업에 남긴다.
     */
    @Column(name = "title", length = 200)
    private String title;

    /** 계약서에서 바꾼 작업 설명. 비어 있으면 붙은 요구사항의 설명을 쓴다. */
    @Column(name = "detail", length = 1000)
    private String detail;

    @Column(name = "price", nullable = false)
    private Integer price;

    /**
     * 인계 작업이면 true. R-QA-06: 금액이 아니라 이 표시로 인계 작업을 가린다. 계약서마다 하나다. 일반 작업을 모두
     * 확정한 뒤 인계 작업을 확정하면 최종 인수 시험 통과다.
     */
    @ColumnDefault("false")
    @Column(name = "is_handover", nullable = false)
    private boolean handover;

    @Column(name = "reject_message", length = 300)
    private String rejectMessage;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "status_group", nullable = false, length = 50)
    private String statusGroup;

    @Column(name = "work_date")
    private Integer workDate;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    /**
     * 계약서에 담은 작업 하나. 요구사항 하나에 붙고, 서명 전이라 제안 상태로 시작한다.
     *
     * @param title 계약서에서 바꾼 이름. 요구사항 이름을 그대로 쓰면 {@code null}
     * @param detail 계약서에서 바꾼 설명. 요구사항 설명을 그대로 쓰면 {@code null}
     */
    public static Milestone of(
            Integer developmentContractId,
            Integer issueId,
            String title,
            String detail,
            Integer price,
            Integer workDate) {
        return of(developmentContractId, issueId, title, detail, price, workDate, false);
    }

    /**
     * 계약서에 담은 작업 하나. 인계 작업 여부를 함께 받는다.
     *
     * @param handover 인계 작업이면 {@code true}
     */
    public static Milestone of(
            Integer developmentContractId,
            Integer issueId,
            String title,
            String detail,
            Integer price,
            Integer workDate,
            boolean handover) {
        Milestone entity = new Milestone();
        entity.developmentContractId = Objects.requireNonNull(developmentContractId, "developmentContractId");
        entity.issueId = Objects.requireNonNull(issueId, "issueId");
        entity.title = title;
        entity.detail = detail;
        entity.price = Objects.requireNonNull(price, "price");
        entity.workDate = workDate;
        entity.handover = handover;
        entity.status = "PROPOSED";
        entity.statusGroup = "MILESTONE_STATUS";
        return entity;
    }

    /** 착수할 때 바로 시작하는 작업. */
    public void startWork() {
        this.status = "IN_PROGRESS";
    }

    /** 착수할 때 뒤로 잡아 두는 작업. */
    public void schedule() {
        this.status = "SCHEDULED";
    }

    /** 계약서를 다시 써서 빠진 작업. 서명 전이라 통과 기록이 없어 지워도 잃는 것이 없다. */
    public void removeFromContract(LocalDateTime removedAt) {
        markDeleted(removedAt);
    }
}
