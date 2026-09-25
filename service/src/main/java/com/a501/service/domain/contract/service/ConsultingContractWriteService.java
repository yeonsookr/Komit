package com.a501.service.domain.contract.service;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.CommonErrorCode;
import com.a501.service.domain.consulting.entity.ConsultingSession;
import com.a501.service.domain.consulting.entity.Reservation;
import com.a501.service.domain.consulting.repository.ConsultingSessionRepository;
import com.a501.service.domain.consulting.repository.ReservationRepository;
import com.a501.service.domain.contract.ContractErrorCode;
import com.a501.service.domain.contract.dto.request.AmendConsultingContractRequest;
import com.a501.service.domain.contract.dto.request.WriteConsultingContractRequest;
import com.a501.service.domain.contract.dto.response.ContractResponse;
import com.a501.service.domain.contract.entity.ConsultingAgreement;
import com.a501.service.domain.contract.entity.Contract;
import com.a501.service.domain.contract.entity.TempMessage;
import com.a501.service.domain.contract.repository.ConsultingAgreementRepository;
import com.a501.service.domain.contract.repository.ContractRepository;
import com.a501.service.domain.contract.repository.TempMessageRepository;
import com.a501.service.domain.room.RoomErrorCode;
import com.a501.service.domain.room.dto.RoomParties;
import com.a501.service.domain.room.service.RoomQueryService;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 컨설팅 계약서를 쓰고 고친다.
 *
 * <p><b>계약서는 개발자가 쓴다.</b> 의뢰인은 받아서 서명하거나 수정을 제안한다. 방의 두 사람 모두 방이
 * 있다는 것을 알고 있으므로, 개발자가 아닌 사람에게는 감추지 않고 403 으로 답한다.
 *
 * <p>한 번 쓰면 네 줄이 함께 생긴다. 계약 · 컨설팅 계약 · 세션 · 예약이며, 세션과 예약은 아직 일어나지
 * 않은 약속이라 시각만 담는다.
 */
@Service
@Transactional(readOnly = true)
public class ConsultingContractWriteService {
    private final ContractRepository contractRepository;
    private final ConsultingAgreementRepository consultingAgreementRepository;
    private final ConsultingSessionRepository consultingSessionRepository;
    private final ReservationRepository reservationRepository;
    private final TempMessageRepository tempMessageRepository;
    private final RoomQueryService roomQueryService;
    private final ContractDetailService contractDetailService;
    private final Clock clock;

    @Autowired
    public ConsultingContractWriteService(
            ContractRepository contractRepository,
            ConsultingAgreementRepository consultingAgreementRepository,
            ConsultingSessionRepository consultingSessionRepository,
            ReservationRepository reservationRepository,
            TempMessageRepository tempMessageRepository,
            RoomQueryService roomQueryService,
            ContractDetailService contractDetailService) {
        this(
                contractRepository,
                consultingAgreementRepository,
                consultingSessionRepository,
                reservationRepository,
                tempMessageRepository,
                roomQueryService,
                contractDetailService,
                Clock.systemUTC());
    }

    /** 지난 시각인지 보는 기준을 시험에서 고정하려고 시계를 받습니다. */
    ConsultingContractWriteService(
            ContractRepository contractRepository,
            ConsultingAgreementRepository consultingAgreementRepository,
            ConsultingSessionRepository consultingSessionRepository,
            ReservationRepository reservationRepository,
            TempMessageRepository tempMessageRepository,
            RoomQueryService roomQueryService,
            ContractDetailService contractDetailService,
            Clock clock) {
        this.contractRepository = contractRepository;
        this.consultingAgreementRepository = consultingAgreementRepository;
        this.consultingSessionRepository = consultingSessionRepository;
        this.reservationRepository = reservationRepository;
        this.tempMessageRepository = tempMessageRepository;
        this.roomQueryService = roomQueryService;
        this.contractDetailService = contractDetailService;
        this.clock = clock;
    }

    /**
     * 개발자가 컨설팅 계약서를 써서 보낸다.
     *
     * @throws BusinessException {@link RoomErrorCode#ROOM_NOT_FOUND} 없는 방일 때,
     *                           {@link CommonErrorCode#FORBIDDEN} 이 방의 개발자가 아닐 때,
     *                           {@link CommonErrorCode#INVALID_REQUEST} 지난 시각일 때
     */
    @Transactional
    public ContractResponse write(
            Integer roomId, Integer userId, WriteConsultingContractRequest request) {
        requireDeveloperOf(roomId, userId);
        requireFuture(request.scheduledAt());

        Contract contract =
                contractRepository.save(Contract.of(roomId, Contract.ContractType.CONSULTING));
        ConsultingAgreement agreement = consultingAgreementRepository.save(
                ConsultingAgreement.of(
                        contract.getId(), request.topic(), request.price(), request.detail()));

        ConsultingSession session = consultingSessionRepository.save(ConsultingSession.of(
                agreement.getId(), request.price(), request.durationMinutes()));
        // 식별자가 있어야 지을 수 있는 값이라 저장한 뒤에 채운다.
        session.assignChatRef();

        reservationRepository.save(
                Reservation.of(session.getId(), toLocal(request.scheduledAt())));
        leaveMessage(contract.getId(), request.message());

        return contractDetailService.getContract(userId, contract.getId());
    }

    /**
     * 협의 중인 계약서를 고친다. 보낸 항목만 바뀐다.
     *
     * <p><b>한쪽이라도 서명한 뒤에는 고칠 수 없다.</b> 그때는 채팅으로 이야기한 뒤 다시 쓴다.
     * (`R-CON-02`)
     *
     * @throws BusinessException {@link ContractErrorCode#CONTRACT_NOT_FOUND} 없거나 내 계약이 아닐 때,
     *                           {@link ContractErrorCode#CONTRACT_ALREADY_SIGNED} 이미 서명했을 때,
     *                           {@link CommonErrorCode#INVALID_REQUEST} 지난 시각일 때
     */
    @Transactional
    public ContractResponse amend(
            Integer contractId, Integer userId, AmendConsultingContractRequest request) {
        Contract contract = contractRepository
                .findByIdAndDeletedFalse(contractId)
                .orElseThrow(() -> new BusinessException(ContractErrorCode.CONTRACT_NOT_FOUND));
        // 방의 두 사람만 고칠 수 있다. 수정 제안은 의뢰인도 보낸다.
        RoomParties parties = roomQueryService
                .findParties(contract.getRoomId())
                .filter(found -> found.includes(userId))
                .orElseThrow(() -> new BusinessException(ContractErrorCode.CONTRACT_NOT_FOUND));
        if (contract.isSigned()) {
            throw new BusinessException(ContractErrorCode.CONTRACT_ALREADY_SIGNED);
        }
        requireFuture(request.scheduledAt());

        ConsultingAgreement agreement = consultingAgreementRepository
                .findByContractIdAndDeletedFalse(contractId)
                .orElseThrow(() -> new BusinessException(ContractErrorCode.CONTRACT_NOT_FOUND));
        ConsultingSession session = consultingSessionRepository
                .findAllByConsultingAgreementIdAndDeletedFalseOrderByIdAsc(agreement.getId())
                .stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(ContractErrorCode.CONTRACT_NOT_FOUND));

        int price = request.price() == null ? session.getPrice() : request.price();
        agreement.rewrite(
                request.topic() == null ? agreement.getTopic() : request.topic(),
                price,
                request.detail() == null ? agreement.getDetail() : request.detail());
        session.rewrite(
                price,
                request.durationMinutes() == null
                        ? session.getDurationMinutes()
                        : request.durationMinutes());
        if (request.scheduledAt() != null) {
            reservationRepository
                    .findByConsultingSessionIdAndDeletedFalse(session.getId())
                    .ifPresent(reservation -> reservation.reschedule(toLocal(request.scheduledAt())));
        }

        leaveMessage(contractId, request.message(), Objects.equals(parties.clientId(), userId));
        return contractDetailService.getContract(userId, contractId);
    }

    /** 계약서는 개발자가 쓴다. 방의 당사자는 방이 있다는 것을 아므로 감추지 않는다. */
    private void requireDeveloperOf(Integer roomId, Integer userId) {
        RoomParties parties = roomQueryService
                .findParties(roomId)
                .orElseThrow(() -> new BusinessException(RoomErrorCode.ROOM_NOT_FOUND));
        if (!Objects.equals(parties.developerUserId(), userId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
    }

    /** 지난 시각으로는 약속을 잡을 수 없다. */
    private void requireFuture(OffsetDateTime scheduledAt) {
        if (scheduledAt != null && !scheduledAt.toInstant().isAfter(clock.instant())) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
    }

    private void leaveMessage(Integer contractId, String message) {
        leaveMessage(contractId, message, false);
    }

    private void leaveMessage(Integer contractId, String message, boolean client) {
        if (message == null || message.isBlank()) {
            return;
        }
        tempMessageRepository.save(TempMessage.of(contractId, message.trim(), client));
    }

    /** 저장은 시간대 없는 값으로 한다. 오가는 값은 시간대가 붙어 오므로 UTC 로 맞춰 떨어뜨린다. */
    private static java.time.LocalDateTime toLocal(OffsetDateTime value) {
        return value.atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }
}
