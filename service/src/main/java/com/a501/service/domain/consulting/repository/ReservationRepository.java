package com.a501.service.domain.consulting.repository;

import com.a501.service.domain.consulting.entity.Reservation;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, Integer> {
    Optional<Reservation> findByConsultingSessionIdAndDeletedFalse(Integer consultingSessionId);

    List<Reservation> findAllByConsultingSessionIdInAndDeletedFalse(
            Collection<Integer> consultingSessionIds);
}
