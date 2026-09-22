package com.a501.service.domain.consulting.repository;

import com.a501.service.domain.consulting.entity.Reservation;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, Integer> {
    Optional<Reservation> findByConsultingSessionIdAndDeletedFalse(Integer consultingSessionId);

    /** 아직 오지 않은 예약 수. 마이페이지의 예정 세션 수입니다. */
    long countByConsultingSessionIdInAndDeletedFalseAndScheduledAtAfter(
            Collection<Integer> consultingSessionIds,
            LocalDateTime scheduledAt);

    List<Reservation> findAllByConsultingSessionIdInAndDeletedFalse(
            Collection<Integer> consultingSessionIds);
}
