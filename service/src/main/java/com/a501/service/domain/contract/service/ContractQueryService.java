package com.a501.service.domain.contract.service;

import com.a501.service.domain.contract.dto.ContractParties;
import com.a501.service.domain.contract.entity.ConsultingAgreement;
import com.a501.service.domain.contract.entity.Contract;
import com.a501.service.domain.contract.entity.DevelopmentContract;
import com.a501.service.domain.contract.repository.ConsultingAgreementRepository;
import com.a501.service.domain.contract.repository.ContractRepository;
import com.a501.service.domain.contract.repository.DevelopmentContractRepository;
import com.a501.service.domain.room.dto.RoomParties;
import com.a501.service.domain.room.service.RoomQueryService;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다른 도메인이 계약을 물어볼 때 쓰는 조회 창구. 엔티티는 밖으로 나가지 않습니다.
 *
 * <p>계약에 매인 것들(컨설팅 세션 등)은 「이 사람이 당사자인가」를 계속 묻게 됩니다. 그 판정에
 * 필요한 방·프로젝트 단계를 여기서 넘어 두어, 묻는 쪽은 계약 도메인 하나만 알면 됩니다.
 * 표시용 최소 정보(계약 유형, 마친 계약 수)도 같은 창구로 내줍니다. (BACKEND_CONVENTIONS 7.1)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContractQueryService {
    /**
     * 마친 계약의 상태 코드값. 공통 계약({@code CONTRACT_STATUS})에는 완료 상태가 없고, 컨설팅 합의({@code CONSULTING_AGREEMENT_STATUS})와
     * 개발 계약({@code DEVELOPMENT_CONTRACT_STATUS})이 각자 {@code COMPLETED}를 가집니다.
     */
    static final String COMPLETED = "COMPLETED";

    private final ConsultingAgreementRepository agreementRepository;
    private final ContractRepository contractRepository;
    private final DevelopmentContractRepository developmentContractRepository;
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

    /** 계약 식별자별 계약 유형 코드값({@code CONSULTING}·{@code DEVELOPMENT}). 없는 계약은 결과에 빠집니다. */
    public Map<Integer, String> findContractTypes(Collection<Integer> contractIds) {
        if (contractIds == null || contractIds.isEmpty()) {
            return Map.of();
        }
        return contractRepository.findAllById(contractIds).stream()
                .collect(Collectors.toMap(Contract::getId, Contract::getContractType));
    }

    /** 방 식별자들에 연결된 컨설팅 합의 식별자. */
    public List<Integer> findConsultingAgreementIdsByRooms(Collection<Integer> roomIds) {
        if (roomIds == null || roomIds.isEmpty()) {
            return List.of();
        }
        List<Integer> contractIds = contractRepository.findByRoomIdInAndDeletedFalse(roomIds).stream()
                .map(Contract::getId)
                .toList();
        if (contractIds.isEmpty()) {
            return List.of();
        }
        return agreementRepository.findByContractIdInAndDeletedFalse(contractIds).stream()
                .map(ConsultingAgreement::getId)
                .toList();
    }

    /**
     * 방 식별자별 마친 계약 수. 컨설팅 합의나 개발 계약이 {@code COMPLETED}인 계약만 세며, 진행 중이거나 파기된 계약은 들지 않습니다.
     * 마친 계약이 없는 방은 결과에 없습니다. 개발자 카드의 {@code contractCount}가 이 값을 방의 개발자별로 합쳐 씁니다.
     */
    public Map<Integer, Long> countCompletedByRooms(Collection<Integer> roomIds) {
        if (roomIds == null || roomIds.isEmpty()) {
            return Map.of();
        }
        List<Contract> contracts = contractRepository.findByRoomIdInAndDeletedFalse(roomIds);
        if (contracts.isEmpty()) {
            return Map.of();
        }
        List<Integer> contractIds = contracts.stream().map(Contract::getId).toList();
        Set<Integer> completed = new HashSet<>();
        agreementRepository.findByContractIdInAndDeletedFalseAndStatus(contractIds, COMPLETED).stream()
                .map(ConsultingAgreement::getContractId)
                .forEach(completed::add);
        developmentContractRepository.findByContractIdInAndDeletedFalseAndStatus(contractIds, COMPLETED).stream()
                .map(DevelopmentContract::getContractId)
                .forEach(completed::add);

        Map<Integer, Long> result = new HashMap<>();
        contracts.stream()
                .filter(contract -> completed.contains(contract.getId()))
                .forEach(contract -> result.merge(contract.getRoomId(), 1L, Long::sum));
        return result;
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
