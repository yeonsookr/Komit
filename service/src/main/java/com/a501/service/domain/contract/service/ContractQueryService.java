package com.a501.service.domain.contract.service;

import com.a501.service.domain.contract.entity.Contract;
import com.a501.service.domain.contract.entity.ConsultingAgreement;
import com.a501.service.domain.contract.entity.DevelopmentContract;
import com.a501.service.domain.contract.repository.ConsultingAgreementRepository;
import com.a501.service.domain.contract.repository.ContractRepository;
import com.a501.service.domain.contract.repository.DevelopmentContractRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 다른 도메인이 계약의 표시용 최소 정보를 가져가는 창구입니다. 엔티티는 밖으로 나가지 않습니다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContractQueryService {
    /**
     * 마친 계약의 상태 코드값. 공통 계약({@code CONTRACT_STATUS})에는 완료 상태가 없고, 컨설팅 합의({@code CONSULTING_AGREEMENT_STATUS})와
     * 개발 계약({@code DEVELOPMENT_CONTRACT_STATUS})이 각자 {@code COMPLETED}를 가집니다.
     */
    static final String COMPLETED = "COMPLETED";

    private final ContractRepository contractRepository;
    private final ConsultingAgreementRepository consultingAgreementRepository;
    private final DevelopmentContractRepository developmentContractRepository;

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
        return consultingAgreementRepository.findByContractIdInAndDeletedFalse(contractIds).stream()
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
        consultingAgreementRepository.findByContractIdInAndDeletedFalseAndStatus(contractIds, COMPLETED).stream()
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
}
