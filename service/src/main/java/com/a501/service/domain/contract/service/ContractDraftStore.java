package com.a501.service.domain.contract.service;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.domain.contract.client.ContractDraftClient.GeneratedConsultingDraft;
import com.a501.service.domain.contract.client.ContractDraftClient.GeneratedDevelopmentDraft;
import com.a501.service.domain.contract.client.ContractDraftClient.NewMilestone;
import com.a501.service.domain.contract.entity.AiContractDraftSnapshot;
import com.a501.service.domain.contract.entity.ConsultingAgreement;
import com.a501.service.domain.contract.entity.Contract;
import com.a501.service.domain.contract.entity.DevelopmentContract;
import com.a501.service.domain.contract.entity.Milestone;
import com.a501.service.domain.contract.entity.MilestoneQaResult;
import com.a501.service.domain.contract.repository.AiContractDraftSnapshotRepository;
import com.a501.service.domain.contract.repository.ConsultingAgreementRepository;
import com.a501.service.domain.contract.repository.ContractRepository;
import com.a501.service.domain.contract.repository.DevelopmentContractRepository;
import com.a501.service.domain.contract.repository.MilestoneQaResultRepository;
import com.a501.service.domain.contract.repository.MilestoneRepository;
import com.a501.service.domain.diagnosis.dto.DraftRequirements;
import com.a501.service.domain.diagnosis.service.ContractRequirementService;
import com.a501.service.domain.room.RoomErrorCode;
import com.a501.service.domain.room.dto.ContractRoomView;
import com.a501.service.domain.room.service.RoomQueryService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** AI 호출이 끝난 뒤 계약서 version 1과 원본만 짧은 트랜잭션으로 저장한다. */
@Service
@RequiredArgsConstructor
public class ContractDraftStore {
    private final ContractRepository contractRepository;
    private final ConsultingAgreementRepository consultingAgreementRepository;
    private final AiContractDraftSnapshotRepository snapshotRepository;
    private final DevelopmentContractRepository developmentContractRepository;
    private final MilestoneRepository milestoneRepository;
    private final MilestoneQaResultRepository milestoneQaResultRepository;
    private final ContractRequirementService contractRequirementService;
    private final RoomQueryService roomQueryService;

    @Transactional
    public Integer saveConsultingIfAbsent(Integer roomId, GeneratedConsultingDraft draft) {
        return contractRepository.findByRoomIdAndDeletedFalse(roomId)
                .map(Contract::getId)
                .orElseGet(() -> saveConsulting(roomId, draft));
    }

    /**
     * 개발 계약서 version 1 을 저장한다. 방에 계약서가 이미 있으면 그것을 돌려준다.
     *
     * <p>요청서의 요구사항은 모두 작업으로 넣고 요청서의 확인 문장을 계약서 문장으로 옮긴다. 거기에 AI 가 채팅에서
     * 합의한 것으로 돌려준 확인 문장과 새 작업을 더한다. 금액은 0, 날짜와 인계물은 비워 두고 개발자가 계약서를
     * 보낼 때 채운다. 컨설팅 초안의 금액 0 과 같다.
     *
     * <p>요청서 요구사항의 상태는 바꾸지 않는다. 초안은 개발자가 아직 보내지 않은 것이라 의뢰인의 요청서를 묶지
     * 않는다. 개발자가 계약서를 보낼 때 계약 상태로 바뀐다.
     */
    @Transactional
    public Integer saveDevelopmentIfAbsent(
            Integer roomId, List<DraftRequirements.Requested> requested, GeneratedDevelopmentDraft draft) {
        return contractRepository.findByRoomIdAndDeletedFalse(roomId)
                .map(Contract::getId)
                .orElseGet(() -> saveDevelopment(roomId, requested, draft));
    }

    private Integer saveDevelopment(
            Integer roomId, List<DraftRequirements.Requested> requested, GeneratedDevelopmentDraft draft) {
        Integer projectId = roomQueryService.findContractRoom(roomId)
                .map(ContractRoomView::projectId)
                .orElseThrow(() -> new BusinessException(RoomErrorCode.ROOM_NOT_FOUND));
        Contract contract =
                contractRepository.save(Contract.of(roomId, Contract.ContractType.DEVELOPMENT));
        Integer developmentId =
                developmentContractRepository.save(DevelopmentContract.forDraft(contract.getId())).getId();

        Map<Integer, List<String>> additions = draft.additionsByIssue();
        for (DraftRequirements.Requested requirement : requested) {
            List<String> criteria = new ArrayList<>(requirement.qaCriteria());
            criteria.addAll(additions.getOrDefault(requirement.issueId(), List.of()));
            saveMilestone(developmentId, requirement.issueId(), criteria);
        }
        for (NewMilestone milestone : draft.newMilestones()) {
            Integer issueId = contractRequirementService.createIssue(projectId, milestone.title(), milestone.detail());
            saveMilestone(developmentId, issueId, milestone.qaCriteria());
        }

        snapshotRepository.save(AiContractDraftSnapshot.of(contract.getId(), draft.contentSnapshot()));
        return contract.getId();
    }

    private void saveMilestone(Integer developmentId, Integer issueId, List<String> criteria) {
        Milestone milestone = milestoneRepository.save(Milestone.of(developmentId, issueId, null, null, 0, null));
        contractRequirementService.writeCriteria(issueId, criteria).forEach(qaCriterionId ->
                milestoneQaResultRepository.save(MilestoneQaResult.of(milestone.getId(), qaCriterionId)));
    }

    private Integer saveConsulting(Integer roomId, GeneratedConsultingDraft draft) {
        Contract contract =
                contractRepository.save(Contract.of(roomId, Contract.ContractType.CONSULTING));
        consultingAgreementRepository.save(
                ConsultingAgreement.of(contract.getId(), draft.topic(), 0, draft.detail()));
        snapshotRepository.save(
                AiContractDraftSnapshot.of(contract.getId(), draft.contentSnapshot()));
        return contract.getId();
    }
}
