package com.a501.service.domain.contract.entity;

import com.a501.service.common.persistence.SoftDeleteEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

@Getter
@Entity
@Table(name = "consulting_agreements")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConsultingAgreement extends SoftDeleteEntity {
    /** 지급까지 끝난 합의. {@code CONSULTING_AGREEMENT_STATUS} 코드값입니다. */
    public static final String COMPLETED = "COMPLETED";

    /** 파기된 합의. 지급 대상이 아닙니다. */
    public static final String TERMINATED = "TERMINATED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "contract_id", nullable = false)
    private Integer contractId;

    @Column(name = "topic", nullable = false, length = 255)
    private String topic;

    @Column(name = "detail", columnDefinition = "text")
    private String detail;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "status_group", nullable = false, length = 50)
    private String statusGroup;

    @ColumnDefault("0")
    @Column(name = "total_amount", nullable = false)
    private Integer totalAmount = 0;

    /**
     * 컨설팅 계약의 알맹이. 다룰 항목과 금액을 담습니다.
     *
     * <p>상태는 체결된 뒤의 진행을 담는 값이라 협의중에는 의미가 없습니다. 체결 여부는 바깥
     * {@code contracts.status} 로만 봅니다.
     */
    public static ConsultingAgreement of(Integer contractId, String topic, int totalAmount) {
        return of(contractId, topic, totalAmount, null);
    }

    public static ConsultingAgreement of(
            Integer contractId, String topic, int totalAmount, String detail) {
        ConsultingAgreement agreement = new ConsultingAgreement();
        agreement.contractId = Objects.requireNonNull(contractId, "contractId");
        agreement.topic = Objects.requireNonNull(topic, "topic");
        agreement.detail = detail;
        agreement.totalAmount = totalAmount;
        agreement.status = "SIGNED";
        agreement.statusGroup = "CONSULTING_AGREEMENT_STATUS";
        return agreement;
    }

    /** 대금이 개발자에게 지급되었는지. 지급이 끝나면 다시 지급 대상으로 고르지 않습니다. */
    public boolean isCompleted() {
        return COMPLETED.equals(status);
    }

    /**
     * 대금 지급이 끝났음을 적습니다. 같은 호출이 다시 와도 상태는 그대로입니다.
     *
     * <p>지급 기록의 원본은 Escrow 에 있습니다. 여기 남기는 것은 「더 볼 필요가 없다」는 표시뿐입니다. (`R-PAY-05`)
     */
    public void complete() {
        this.status = COMPLETED;
    }

    /** 계약이 취소되어 합의를 파기로 적습니다. 파기된 합의는 지급 대상으로 고르지 않습니다. (`R-CON-21`) */
    public void terminate() {
        this.status = TERMINATED;
    }

    /** 협의 중에 다시 보낸 계약서로 내용을 바꿉니다. */
    public void rewrite(String topic, int totalAmount, String detail) {
        this.topic = Objects.requireNonNull(topic, "topic");
        this.totalAmount = totalAmount;
        this.detail = detail;
    }
}
