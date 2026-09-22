package com.a501.service.domain.contract.service;

import com.a501.service.domain.contract.entity.Contract;
import com.a501.service.domain.contract.entity.ConsultingAgreement;
import com.a501.service.domain.contract.repository.ConsultingAgreementRepository;
import com.a501.service.domain.contract.repository.ContractRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 다른 도메인이 계약의 표시용 최소 정보를 가져가는 창구입니다. 엔티티는 밖으로 나가지 않습니다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContractQueryService {
    private final ContractRepository contractRepository;
    private final ConsultingAgreementRepository consultingAgreementRepository;

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
}
