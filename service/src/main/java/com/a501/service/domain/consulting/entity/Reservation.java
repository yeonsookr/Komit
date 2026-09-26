package com.a501.service.domain.consulting.entity;

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

@Getter
@Entity
@Table(name = "reservations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation extends SoftDeleteEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "status_group", nullable = false, length = 50)
    private String statusGroup;

    @Column(name = "consulting_session_id", nullable = false)
    private Integer consultingSessionId;

    /**
     * 개발자가 계약서에 적어 보낸 시각. 아직 의뢰인이 서명하지 않아 제안된 상태입니다.
     *
     * <p>한 세션에 예약을 여러 번 잡을 수 있어 확정된 것은 상태로 가립니다.
     */
    public static Reservation of(Integer consultingSessionId, LocalDateTime scheduledAt) {
        Reservation reservation = new Reservation();
        reservation.consultingSessionId =
                Objects.requireNonNull(consultingSessionId, "consultingSessionId");
        reservation.scheduledAt = Objects.requireNonNull(scheduledAt, "scheduledAt");
        reservation.status = "PROPOSED";
        reservation.statusGroup = "RESERVATION_STATUS";
        return reservation;
    }

    /** 협의 중에 다시 보낸 계약서로 시각을 바꿉니다. */
    public void reschedule(LocalDateTime scheduledAt) {
        this.scheduledAt = Objects.requireNonNull(scheduledAt, "scheduledAt");
    }

    /** 계약이 성립해 확정된 예약인지. 확정 전에는 세션에 들어가지 못합니다. (R-CON-01) */
    public boolean isConfirmed() {
        return "CONFIRMED".equals(status);
    }

    /** 양쪽이 서명해 계약이 성립했을 때 예약을 확정합니다. 이미 확정이면 그대로 둡니다. (R-CON-01) */
    public void confirm() {
        if ("PROPOSED".equals(status)) {
            this.status = "CONFIRMED";
        }
    }
}
