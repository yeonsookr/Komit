package com.a501.service.domain.contract.service;

import com.a501.service.domain.contract.dto.ConsultingPayoutTarget;
import com.a501.service.domain.contract.dto.ContractParties;
import com.a501.service.domain.contract.dto.ContractProgressView;
import com.a501.service.domain.contract.entity.ConsultingAgreement;
import com.a501.service.domain.contract.entity.Contract;
import com.a501.service.domain.contract.entity.DevelopmentContract;
import com.a501.service.domain.contract.repository.ConsultingAgreementRepository;
import com.a501.service.domain.contract.repository.ContractRepository;
import com.a501.service.domain.contract.repository.DevelopmentContractRepository;
import com.a501.service.domain.room.dto.RoomParties;
import com.a501.service.domain.room.service.RoomQueryService;
import java.util.Collection;
import java.util.Comparator;
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
    /** 파기된 계약의 상태 코드값. 컨설팅 합의와 개발 계약이 각자 가집니다. */
    static final String TERMINATED = "TERMINATED";

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

    /**
     * 컨설팅 합의가 달린 계약이 의뢰인에게 취소됐는지. 합의나 계약이 없으면 거짓입니다.
     *
     * <p>취소된 계약의 세션에는 들어가지 못하게 세션 쪽이 묻습니다. (`R-CON-21`)
     *
     * @param consultingAgreementId 컨설팅 합의 id
     */
    public boolean isCancelledByConsultingAgreementId(Integer consultingAgreementId) {
        return agreementRepository
                .findByIdAndDeletedFalse(consultingAgreementId)
                .map(ConsultingAgreement::getContractId)
                .flatMap(contractRepository::findByIdAndDeletedFalse)
                .map(Contract::isCancelled)
                .orElse(false);
    }

    /**
     * 이 방에 달린 계약. 아직 계약서를 쓰지 않았으면 비어 있습니다.
     *
     * @param roomId 방 id
     */
    public Optional<Integer> findContractIdByRoomId(Integer roomId) {
        return contractRepository.findByRoomIdAndDeletedFalse(roomId).map(Contract::getId);
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

    /**
     * 양측이 서명했고 아직 대금을 지급하지 않은 컨설팅 계약.
     *
     * <p>지급을 마쳤거나 파기된 합의는 빠집니다. 예치가 걸렸는지, 이의 제기 기간이 지났는지는 보지
     * 않습니다 — 예치는 Escrow 가, 기간은 세션이 압니다. 묻는 쪽이 가립니다.
     *
     * <p>방의 당사자를 찾지 못한 계약은 빠집니다. 대금을 받을 사람을 모르면 지급할 수 없습니다.
     */
    public List<ConsultingPayoutTarget> findUnpaidConsultingContracts() {
        List<ConsultingAgreement> agreements = agreementRepository.findByStatusNotInAndDeletedFalse(
                List.of(ConsultingAgreement.COMPLETED, ConsultingAgreement.TERMINATED));
        if (agreements.isEmpty()) {
            return List.of();
        }
        Map<Integer, Contract> contracts = contractRepository
                .findAllById(agreements.stream().map(ConsultingAgreement::getContractId).toList())
                .stream()
                .filter(contract -> !contract.isDeleted() && contract.isConcluded())
                .collect(Collectors.toMap(Contract::getId, contract -> contract));

        return agreements.stream()
                .filter(agreement -> contracts.containsKey(agreement.getContractId()))
                .flatMap(agreement -> toParties(contracts.get(agreement.getContractId()))
                        .map(parties -> new ConsultingPayoutTarget(
                                parties.contractId(),
                                agreement.getId(),
                                agreement.getTotalAmount(),
                                parties.clientId(),
                                parties.developerId()))
                        .stream())
                .toList();
    }

    /**
     * 방들에 달린 계약마다 예치가 있을 수 있는지와 마쳤는지. 협의 중이라 아직 체결하지 않은 계약도 듭니다.
     * 의뢰인이 서명하면 개발자 서명 전에도 예치할 수 있으므로(R-CON-20) 의뢰인 서명이나 성립을 예치 가능으로 봅니다.
     * 상담 합의나 개발 계약이 완료·파기됐거나, 의뢰인이 계약을 취소했으면 마친 것으로 봅니다. (R-ACC-05)
     * 취소는 되돌릴 수 없고 개발 계약의 상태는 바꾸지 않으므로 계약 상태로 따로 봅니다. (R-CON-21) 계약 식별자 순입니다.
     */
    public List<ContractProgressView> findProgressByRooms(Collection<Integer> roomIds) {
        if (roomIds == null || roomIds.isEmpty()) {
            return List.of();
        }
        List<Contract> contracts = contractRepository.findByRoomIdInAndDeletedFalse(roomIds);
        if (contracts.isEmpty()) {
            return List.of();
        }
        List<Integer> contractIds = contracts.stream().map(Contract::getId).toList();
        Set<String> closed = Set.of(COMPLETED, TERMINATED);
        Set<Integer> finished = new HashSet<>();
        agreementRepository.findByContractIdInAndDeletedFalseAndStatusIn(contractIds, closed).stream()
                .map(ConsultingAgreement::getContractId)
                .forEach(finished::add);
        developmentContractRepository.findByContractIdInAndDeletedFalseAndStatusIn(contractIds, closed).stream()
                .map(DevelopmentContract::getContractId)
                .forEach(finished::add);
        return contracts.stream()
                .sorted(Comparator.comparing(Contract::getId))
                .map(contract -> new ContractProgressView(
                        contract.getId(), contract.getRoomId(), contract.isSignedBy(true) || contract.isConcluded(),
                        finished.contains(contract.getId()) || contract.isCancelled()))
                .toList();
    }

    private Optional<ContractParties> toParties(Contract contract) {
        return roomQueryService
                .findParties(contract.getRoomId())
                .map(parties -> toParties(contract, parties));
    }

    private ContractParties toParties(Contract contract, RoomParties parties) {
        return new ContractParties(
                contract.getId(), parties.clientId(), parties.developerUserId());
    }
}
