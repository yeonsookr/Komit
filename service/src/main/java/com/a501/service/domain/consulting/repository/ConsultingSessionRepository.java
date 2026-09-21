package com.a501.service.domain.consulting.repository;

import com.a501.service.domain.consulting.entity.ConsultingSession;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsultingSessionRepository extends JpaRepository<ConsultingSession, Integer> {
    Optional<ConsultingSession> findByIdAndDeletedFalse(Integer id);
}
