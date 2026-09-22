package com.a501.service.domain.consulting.service;

import com.a501.service.domain.consulting.entity.ConsultingSession;
import com.a501.service.domain.consulting.repository.ConsultingSessionRepository;
import com.a501.service.domain.consulting.repository.ReservationRepository;
import com.a501.service.domain.contract.service.ContractQueryService;
import com.a501.service.domain.room.service.RoomQueryService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 마이페이지의 예정 컨설팅 수를 계산하는 조회 서비스입니다. */
@Service
@Transactional(readOnly = true)
public class ConsultingScheduleQueryService {
    private final RoomQueryService roomQueryService;
    private final ContractQueryService contractQueryService;
    private final ConsultingSessionRepository consultingSessionRepository;
    private final ReservationRepository reservationRepository;
    private final Clock clock;

    @Autowired
    public ConsultingScheduleQueryService(
            RoomQueryService roomQueryService,
            ContractQueryService contractQueryService,
            ConsultingSessionRepository consultingSessionRepository,
            ReservationRepository reservationRepository) {
        this(roomQueryService, contractQueryService, consultingSessionRepository, reservationRepository, Clock.systemUTC());
    }

    ConsultingScheduleQueryService(
            RoomQueryService roomQueryService,
            ContractQueryService contractQueryService,
            ConsultingSessionRepository consultingSessionRepository,
            ReservationRepository reservationRepository,
            Clock clock) {
        this.roomQueryService = roomQueryService;
        this.contractQueryService = contractQueryService;
        this.consultingSessionRepository = consultingSessionRepository;
        this.reservationRepository = reservationRepository;
        this.clock = clock;
    }

    public int countUpcomingByDeveloper(Integer developerId) {
        List<Integer> roomIds = roomQueryService.findIdsByDeveloper(developerId);
        List<Integer> agreementIds = contractQueryService.findConsultingAgreementIdsByRooms(roomIds);
        if (agreementIds.isEmpty()) {
            return 0;
        }
        List<Integer> sessionIds = consultingSessionRepository
                .findByConsultingAgreementIdInAndDeletedFalse(agreementIds).stream()
                .map(ConsultingSession::getId)
                .toList();
        if (sessionIds.isEmpty()) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        return Math.toIntExact(reservationRepository
                .countByConsultingSessionIdInAndDeletedFalseAndScheduledAtAfter(sessionIds, now));
    }
}
