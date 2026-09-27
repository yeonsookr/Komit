package com.a501.service.domain.contract.service;

import com.a501.service.domain.contract.dto.DevelopmentPayoutTarget;
import com.a501.service.domain.contract.entity.Contract;
import com.a501.service.domain.contract.entity.DevelopmentContract;
import com.a501.service.domain.contract.entity.Milestone;
import com.a501.service.domain.contract.repository.ContractRepository;
import com.a501.service.domain.contract.repository.DevelopmentContractRepository;
import com.a501.service.domain.contract.repository.MilestoneRepository;
import com.a501.service.domain.room.service.RoomQueryService;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 정산 도메인이 지급할 개발 계약을 묻는 창구. 엔티티는 밖으로 나가지 않는다. (BACKEND_CONVENTIONS 7.1)
 *
 * <p><b>최종 인수 시험을 통과한 계약만 고른다.</b> 작업이 모두 통과 확정되고 그중 인계 작업이 있어야 한다. 대금은 한
 * 번에 나가고 작업마다 나가지 않는다. (R-PAY-01, R-QA-06) 지급을 마쳐 완료로 적힌 계약은 진행 중이 아니라 빠진다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DevelopmentPayoutQueryService {
    private static final String IN_PROGRESS = "IN_PROGRESS";

    private final DevelopmentContractRepository developmentContractRepository;
    private final MilestoneRepository milestoneRepository;
    private final ContractRepository contractRepository;
    private final RoomQueryService roomQueryService;

    /**
     * 최종 인수 시험을 통과했고 아직 지급을 마치지 않은 개발 계약.
     *
     * <p>신고·동결·예치·계좌는 보지 않는다. 묻는 쪽이 Escrow 와 함께 가린다. 방의 당사자를 찾지 못한 계약은 받을 사람을
     * 몰라 빠진다.
     */
    public List<DevelopmentPayoutTarget> findUnpaidDevelopmentContracts() {
        List<DevelopmentContract> developments = developmentContractRepository.findByStatusAndDeletedFalse(IN_PROGRESS);
        if (developments.isEmpty()) {
            return List.of();
        }
        Map<Integer, List<Milestone>> milestones = milestoneRepository
                .findAllByDevelopmentContractIdInAndDeletedFalse(developments.stream().map(DevelopmentContract::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(Milestone::getDevelopmentContractId));
        List<DevelopmentContract> passed = developments.stream()
                .filter(development -> finalAcceptancePassed(milestones.getOrDefault(development.getId(), List.of())))
                .toList();
        if (passed.isEmpty()) {
            return List.of();
        }
        Map<Integer, Contract> contracts = contractRepository
                .findAllById(passed.stream().map(DevelopmentContract::getContractId).toList())
                .stream()
                .filter(contract -> !contract.isDeleted() && contract.isConcluded())
                .collect(Collectors.toMap(Contract::getId, contract -> contract));

        return passed.stream()
                .filter(development -> contracts.containsKey(development.getContractId()))
                .flatMap(development -> roomQueryService.findParties(contracts.get(development.getContractId()).getRoomId())
                        .map(parties -> new DevelopmentPayoutTarget(
                                development.getContractId(),
                                development.getId(),
                                development.getTotalAmount(),
                                parties.clientId(),
                                parties.developerUserId()))
                        .stream())
                .toList();
    }

    /** 작업이 모두 통과했고 그중 인계 작업이 있다. 인계 작업은 일반 작업을 모두 통과한 뒤에만 확정되지만 둘 다 본다. */
    private static boolean finalAcceptancePassed(List<Milestone> milestones) {
        return milestones.stream().anyMatch(Milestone::isHandover) && milestones.stream().allMatch(Milestone::isConfirmed);
    }
}
