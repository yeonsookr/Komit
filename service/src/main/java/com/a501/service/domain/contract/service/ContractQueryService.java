package com.a501.service.domain.contract.service;

import com.a501.service.domain.contract.dto.ContractParties;
import com.a501.service.domain.contract.entity.ConsultingAgreement;
import com.a501.service.domain.contract.entity.Contract;
import com.a501.service.domain.contract.repository.ConsultingAgreementRepository;
import com.a501.service.domain.contract.repository.ContractRepository;
import com.a501.service.domain.room.dto.RoomParties;
import com.a501.service.domain.room.service.RoomQueryService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다른 도메인이 계약을 물어볼 때 쓰는 조회 창구.
 *
 * <p>계약에 매인 것들(컨설팅 세션 등)은 「이 사람이 당사자인가」를 계속 묻게 됩니다. 그 판정에
 * 필요한 방·프로젝트 단계를 여기서 넘어 두어, 묻는 쪽은 계약 도메인 하나만 알면 됩니다.
 * (BACKEND_CONVENTIONS 7.1)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContractQueryService {
    private final ConsultingAgreementRepository agreementRepository;
    private final ContractRepository contractRepository;
    private final RoomQueryService roomQueryService;

    /**
     * 컨설팅 합의가 달린 계약과 그 두 당사자.
     *
     * <p>합의 → 계약 → 방 → (개발자, 프로젝트) → 의뢰인 순으로 내려갑니다. 중간 어느 하나라도
     * 없거나 지워졌으면 비어 있습니다. <b>여기서 예외를 던지지 않습니다</b> — 없을 때 무엇으로
     * 답할지는 묻는 쪽이 정합니다.
     *
     * @param consultingAgreementId 컨설팅 합의 id
     * @return 계약 식별자와 두 당사자
     */
    public Optional<ContractParties> findPartiesByConsultingAgreementId(
            Integer consultingAgreementId) {
        return agreementRepository
                .findByIdAndDeletedFalse(consultingAgreementId)
                .map(ConsultingAgreement::getContractId)
                .flatMap(contractRepository::findByIdAndDeletedFalse)
                .flatMap(this::toParties);
    }

    /**
     * 이 방에 달린 계약. 아직 계약서를 쓰지 않았으면 비어 있습니다.
     *
     * @param roomId 방 id
     */
    public Optional<Integer> findContractIdByRoomId(Integer roomId) {
        return contractRepository.findByRoomIdAndDeletedFalse(roomId).map(Contract::getId);
    }

    private Optional<ContractParties> toParties(Contract contract) {
        return roomQueryService
                .findParties(contract.getRoomId())
                .map(parties -> toParties(contract, parties));
    }

    private ContractParties toParties(Contract contract, RoomParties parties) {
        return new ContractParties(
                contract.getId(), parties.clientId(), parties.developerId());
    }
}
