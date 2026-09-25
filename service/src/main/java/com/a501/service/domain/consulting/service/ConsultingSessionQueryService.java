package com.a501.service.domain.consulting.service;

import com.a501.service.domain.consulting.dto.SessionSchedule;
import com.a501.service.domain.consulting.dto.response.ConsultingSessionResponse;
import com.a501.service.domain.consulting.entity.ConsultingSession;
import com.a501.service.domain.consulting.entity.Reservation;
import com.a501.service.domain.consulting.repository.ConsultingSessionRepository;
import com.a501.service.domain.consulting.repository.ReservationRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다른 도메인이 세션 목록을 물어볼 때 쓰는 조회 창구.
 *
 * <p>계약서를 보여 줄 때 그 계약에 잡힌 세션이 함께 나갑니다. 묻는 쪽이 세션 엔티티를 직접 읽지
 * 않도록 여기서 응답 형태로 넘깁니다. (BACKEND_CONVENTIONS 7.1)
 *
 * <p>권한은 보지 않습니다. 계약을 볼 수 있는 사람은 그 계약의 세션도 볼 수 있고, 계약을 볼 수
 * 있는지는 묻는 쪽이 이미 가렸기 때문입니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsultingSessionQueryService {
    private final ConsultingSessionRepository sessionRepository;
    private final ReservationRepository reservationRepository;

    /**
     * 컨설팅 합의에 달린 세션. 예약이 없는 세션은 결과에서 빠집니다 — 예약 시각이 없으면 화면이
     * 보여 줄 것이 없습니다.
     *
     * @param consultingAgreementId 컨설팅 합의 id
     * @param contractId 세션 응답에 실어 보낼 계약 id
     * @return 세션 목록. 없으면 빈 목록
     */
    public List<ConsultingSessionResponse> findByConsultingAgreementId(
            Integer consultingAgreementId, Integer contractId) {
        List<ConsultingSession> sessions =
                sessionRepository.findAllByConsultingAgreementIdAndDeletedFalseOrderByIdAsc(
                        consultingAgreementId);
        if (sessions.isEmpty()) {
            return List.of();
        }
        Map<Integer, Reservation> reservations = reservationsOf(sessions);
        return sessions.stream()
                .filter(session -> reservations.containsKey(session.getId()))
                .map(
                        session ->
                                ConsultingSessionResponse.of(
                                        session, contractId, reservations.get(session.getId())))
                .toList();
    }

    /**
     * 컨설팅 합의에 달린 세션의 예약과 진행. 정산이 이의 제기 기간과 세션 예외를 가릴 때 씁니다.
     *
     * <p>예약이 없는 세션도 빼지 않고 {@code scheduledAt} 을 비워 돌려줍니다. 언제 끝날지 모르는 세션을
     * 조용히 빼면 나머지 세션만 보고 지급해 버릴 수 있기 때문입니다.
     *
     * @param consultingAgreementId 컨설팅 합의 id
     * @return 세션 순서대로. 세션이 없으면 빈 목록
     */
    public List<SessionSchedule> findSchedules(Integer consultingAgreementId) {
        List<ConsultingSession> sessions =
                sessionRepository.findAllByConsultingAgreementIdAndDeletedFalseOrderByIdAsc(
                        consultingAgreementId);
        if (sessions.isEmpty()) {
            return List.of();
        }
        Map<Integer, Reservation> reservations = reservationsOf(sessions);
        return sessions.stream()
                .map(session -> {
                    Reservation reservation = reservations.get(session.getId());
                    return new SessionSchedule(
                            session.getId(),
                            reservation == null ? null : reservation.getScheduledAt(),
                            session.getDurationMinutes(),
                            session.getStartedAt(),
                            session.getEndedAt());
                })
                .toList();
    }

    /** 세션마다 따로 묻지 않고 한 번에 가져옵니다. 한 세션에 예약은 하나입니다. */
    private Map<Integer, Reservation> reservationsOf(List<ConsultingSession> sessions) {
        List<Integer> sessionIds = sessions.stream().map(ConsultingSession::getId).toList();
        return reservationRepository.findAllByConsultingSessionIdInAndDeletedFalse(sessionIds).stream()
                .collect(
                        Collectors.toMap(
                                Reservation::getConsultingSessionId, reservation -> reservation));
    }
}
