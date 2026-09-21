package com.a501.service.domain.contract.service;

import com.a501.service.domain.contract.entity.Contract;
import com.a501.service.domain.contract.repository.ContractRepository;
import java.util.Collection;
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

    /** 계약 식별자별 계약 유형 코드값({@code CONSULTING}·{@code DEVELOPMENT}). 없는 계약은 결과에 빠집니다. */
    public Map<Integer, String> findContractTypes(Collection<Integer> contractIds) {
        if (contractIds == null || contractIds.isEmpty()) {
            return Map.of();
        }
        return contractRepository.findAllById(contractIds).stream()
                .collect(Collectors.toMap(Contract::getId, Contract::getContractType));
    }
}
