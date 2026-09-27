package com.a501.service.domain.contract.service;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.domain.consulting.dto.response.ConsultingSessionResponse;
import com.a501.service.domain.consulting.service.ConsultingSessionQueryService;
import com.a501.service.domain.contract.ContractErrorCode;
import com.a501.service.domain.contract.dto.response.ConsultingAgreementResponse;
import com.a501.service.domain.contract.dto.response.ContractResponse;
import com.a501.service.domain.contract.dto.response.DeliverableResponse;
import com.a501.service.domain.contract.dto.response.DevelopmentAgreementResponse;
import com.a501.service.domain.contract.dto.response.MilestoneResponse;
import com.a501.service.domain.contract.dto.response.SignatureResponse;
import com.a501.service.domain.contract.dto.response.TempMessageResponse;
import com.a501.service.domain.contract.entity.Contract;
import com.a501.service.domain.contract.entity.ConsultingAgreement;
import com.a501.service.domain.contract.entity.DevelopmentContract;
import com.a501.service.domain.contract.entity.Deliverable;
import com.a501.service.domain.contract.entity.DeliverableMaterial;
import com.a501.service.domain.contract.entity.Milestone;
import com.a501.service.domain.contract.entity.MilestoneQaResult;
import com.a501.service.domain.contract.repository.ConsultingAgreementRepository;
import com.a501.service.domain.contract.repository.ContractRepository;
import com.a501.service.domain.contract.repository.ContractSignatureRepository;
import com.a501.service.domain.contract.repository.DeliverableRepository;
import com.a501.service.domain.contract.repository.DeliverableMaterialRepository;
import com.a501.service.domain.contract.repository.DevelopmentContractRepository;
import com.a501.service.domain.contract.repository.MilestoneQaResultRepository;
import com.a501.service.domain.contract.repository.MilestoneRepository;
import com.a501.service.domain.contract.repository.TempMessageRepository;
import com.a501.service.domain.diagnosis.dto.IssueBrief;
import com.a501.service.domain.diagnosis.dto.response.QaCriterionResponse;
import com.a501.service.domain.diagnosis.service.DiagnosisQueryService;
import com.a501.service.domain.room.dto.RoomParties;
import com.a501.service.domain.room.service.RoomQueryService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 계약서 한 건을 조회합니다.
 *
 * <p>계약서는 공통 부분(서명·원문·전달 메시지)과 갈래별 내용이 다른 테이블에 흩어져 있습니다.
 * 화면은 그것을 한 덩어리로 봅니다. 여기서 모읍니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContractDetailService {
    private static final String TYPE_CONSULTING = "CONSULTING";

    private final ContractRepository contractRepository;
    private final ConsultingAgreementRepository consultingAgreementRepository;
    private final DevelopmentContractRepository developmentContractRepository;
    private final MilestoneRepository milestoneRepository;
    private final MilestoneQaResultRepository milestoneQaResultRepository;
    private final DeliverableRepository deliverableRepository;
    private final DeliverableMaterialRepository deliverableMaterialRepository;
    private final ContractSignatureRepository signatureRepository;
    private final TempMessageRepository tempMessageRepository;
    private final RoomQueryService roomQueryService;
    private final ConsultingSessionQueryService sessionQueryService;
    private final DiagnosisQueryService diagnosisQueryService;

    /**
     * 계약서 한 건.
     *
     * <p>없는 계약과 남의 계약을 같은 답으로 다룹니다. 당사자가 아닌 사람에게 계약이 있다는
     * 사실조차 알리지 않습니다.
     *
     * @param userId 요청한 사람
     * @param contractId 계약 id
     * @return 계약서
     * @throws BusinessException 없는 계약이거나 당사자가 아닐 때
     */
    public ContractResponse getContract(Integer userId, Integer contractId) {
        Contract contract =
                contractRepository
                        .findByIdAndDeletedFalse(contractId)
                        .orElseThrow(() -> new BusinessException(ContractErrorCode.CONTRACT_NOT_FOUND));
        RoomParties parties =
                roomQueryService
                        .findParties(contract.getRoomId())
                        .orElseThrow(() -> new BusinessException(ContractErrorCode.CONTRACT_NOT_FOUND));
        if (!parties.clientId().equals(userId) && !parties.developerUserId().equals(userId)) {
            throw new BusinessException(ContractErrorCode.CONTRACT_NOT_FOUND);
        }

        boolean consulting = TYPE_CONSULTING.equals(contract.getContractType());
        return ContractResponse.of(
                contract,
                consulting ? consultingOf(contract) : null,
                consulting ? null : developmentOf(contract),
                signatureOf(contract.getClientSignatureId()),
                signatureOf(contract.getDeveloperSignatureId()),
                messagesOf(contract.getId()));
    }

    private ConsultingAgreementResponse consultingOf(Contract contract) {
        ConsultingAgreement agreement =
                consultingAgreementRepository
                        .findByContractIdAndDeletedFalse(contract.getId())
                        .orElse(null);
        if (agreement == null) {
            return null;
        }
        List<ConsultingSessionResponse> sessions =
                sessionQueryService.findByConsultingAgreementId(agreement.getId(), contract.getId());
        return ConsultingAgreementResponse.of(agreement, sessions);
    }

    private DevelopmentAgreementResponse developmentOf(Contract contract) {
        DevelopmentContract development =
                developmentContractRepository
                        .findByContractIdAndDeletedFalse(contract.getId())
                        .orElse(null);
        if (development == null) {
            return null;
        }
        List<Milestone> milestones =
                milestoneRepository.findAllByDevelopmentContractIdAndDeletedFalseOrderByIdAsc(
                        development.getId());
        List<Deliverable> deliverableRows =
                deliverableRepository
                        .findAllByDevelopmentContractIdAndDeletedFalseOrderByIdAsc(
                                development.getId());
        // 산출물마다 따로 읽지 않고 한 번에 읽어 묶는다.
        Map<Integer, List<DeliverableMaterial>> materialsByDeliverable = deliverableRows.isEmpty()
                ? Map.of()
                : deliverableMaterialRepository
                        .findAllByDeliverableIdInAndDeletedFalseOrderByIdAsc(
                                deliverableRows.stream().map(Deliverable::getId).toList())
                        .stream()
                        .collect(Collectors.groupingBy(DeliverableMaterial::getDeliverableId));
        List<DeliverableResponse> deliverables = deliverableRows.stream()
                .map(row -> DeliverableResponse.from(
                        row, materialsByDeliverable.getOrDefault(row.getId(), List.of())))
                .toList();
        return DevelopmentAgreementResponse.of(
                development, issueIdsOf(milestones), milestonesOf(milestones), deliverables);
    }

    /**
     * 이 계약에서 다루는 요구사항. 작업 하나가 요구사항 하나에 붙으므로 작업들이 가리키는 것을
     * 모읍니다. 붙은 요구사항이 없는 작업은 건너뜁니다.
     */
    private List<Integer> issueIdsOf(List<Milestone> milestones) {
        return milestones.stream().map(Milestone::getIssueId).filter(Objects::nonNull).distinct().toList();
    }

    private List<MilestoneResponse> milestonesOf(List<Milestone> milestones) {
        if (milestones.isEmpty()) {
            return List.of();
        }
        Map<Integer, IssueBrief> issues = diagnosisQueryService.findBriefs(issueIdsOf(milestones));
        Map<Integer, List<QaCriterionResponse>> criteria = qaCriteriaByMilestone(milestones);
        return milestones.stream()
                .map(
                        milestone ->
                                MilestoneResponse.of(
                                        milestone,
                                        issues.get(milestone.getIssueId()),
                                        criteria.getOrDefault(milestone.getId(), List.of())))
                .toList();
    }

    /**
     * 작업마다 걸린 확인 문장.
     *
     * <p>확인 문장은 요구사항 쪽에 있고 작업과는 {@code milestone_qa_results} 로 이어집니다. 이은
     * 줄을 먼저 모아 한 번에 문장을 가져옵니다.
     */
    private Map<Integer, List<QaCriterionResponse>> qaCriteriaByMilestone(List<Milestone> milestones) {
        List<Integer> milestoneIds = milestones.stream().map(Milestone::getId).toList();
        List<MilestoneQaResult> links =
                milestoneQaResultRepository.findAllByMilestoneIdInAndDeletedFalseOrderByIdAsc(
                        milestoneIds);
        if (links.isEmpty()) {
            return Map.of();
        }
        Map<Integer, QaCriterionResponse> views =
                diagnosisQueryService.findQaCriteria(
                        links.stream().map(MilestoneQaResult::getQaCriterionId).distinct().toList());
        Map<Integer, List<QaCriterionResponse>> byMilestone = new LinkedHashMap<>();
        for (MilestoneQaResult link : links) {
            QaCriterionResponse view = views.get(link.getQaCriterionId());
            if (view == null) {
                continue;
            }
            byMilestone
                    .computeIfAbsent(link.getMilestoneId(), key -> new ArrayList<>())
                    .add(view);
        }
        return byMilestone;
    }

    /** 계약서에 딸려 오간 전달 메시지. 화면은 상대가 마지막으로 남긴 것을 골라 씁니다. */
    private List<TempMessageResponse> messagesOf(Integer contractId) {
        return tempMessageRepository.findAllByContractIdAndDeletedFalseOrderByIdAsc(contractId).stream()
                .map(TempMessageResponse::from)
                .toList();
    }

    private SignatureResponse signatureOf(Integer signatureId) {
        if (signatureId == null) {
            return null;
        }
        return signatureRepository
                .findByIdAndDeletedFalse(signatureId)
                .map(SignatureResponse::from)
                .orElse(null);
    }
}
