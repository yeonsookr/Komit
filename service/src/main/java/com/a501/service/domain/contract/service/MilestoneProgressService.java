package com.a501.service.domain.contract.service;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.CommonErrorCode;
import com.a501.service.common.exception.InvalidFieldException;
import com.a501.service.domain.contract.ContractErrorCode;
import com.a501.service.domain.contract.dto.request.ConfirmMilestoneRequest;
import com.a501.service.domain.contract.dto.request.SaveQaResultsRequest;
import com.a501.service.domain.contract.dto.response.MilestoneResponse;
import com.a501.service.domain.contract.entity.Contract;
import com.a501.service.domain.contract.entity.DevelopmentContract;
import com.a501.service.domain.contract.entity.Milestone;
import com.a501.service.domain.contract.entity.MilestoneQaResult;
import com.a501.service.domain.contract.repository.ContractRepository;
import com.a501.service.domain.contract.repository.DevelopmentContractRepository;
import com.a501.service.domain.contract.repository.MilestoneQaResultRepository;
import com.a501.service.domain.contract.repository.MilestoneRepository;
import com.a501.service.domain.report.service.ReportQueryService;
import com.a501.service.domain.room.dto.RoomParties;
import com.a501.service.domain.room.service.RoomQueryService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 개발 작업의 확인 요청, 의뢰인의 확인 결과와 통과, 무응답 자동 통과. (R-QA-01~03, R-QA-06)
 *
 * <p><b>착수한 뒤 통과하지 않은 작업은 순서와 관계없이 확인을 요청한다.</b> 인계 작업만 일반 작업을 모두 통과한 뒤,
 * 계약 전체에 인수 시험 자료와 PAID 파일이 있을 때 요청한다. 인계 작업을 통과하면 최종 인수 시험 통과다.
 *
 * <p><b>의뢰인이 확인 문장 일부만 통과시키면 그것도 답이다.</b> 남은 문장은 아직 구현되지 않은 것이라 작업이
 * 개발자에게 돌아가고 자동 통과를 멈춘다. 개발자가 고쳐 다시 요청하면 기한을 새로 센다.
 *
 * <p>모든 변경은 계약 행을 잠그고 한다. 인계 자료 등록도 같은 행을 잠가, 확인 요청과 자료 변경, 여러 서버의 자동
 * 통과가 엇갈리지 않는다.
 */
@Service
@Transactional(readOnly = true)
public class MilestoneProgressService {
    static final String QA_CRITERION_IDS = "qaCriterionIds";
    static final String NOT_THIS_MILESTONE = "이 작업의 확인 문장만 보낼 수 있습니다.";

    private final ContractRepository contractRepository;
    private final DevelopmentContractRepository developmentContractRepository;
    private final MilestoneRepository milestoneRepository;
    private final MilestoneQaResultRepository qaResultRepository;
    private final RoomQueryService roomQueryService;
    private final ReportQueryService reportQueryService;
    private final DeliverableMaterialService deliverableMaterialService;
    private final ContractDetailService contractDetailService;
    private final Clock clock;

    @Autowired
    public MilestoneProgressService(
            ContractRepository contractRepository,
            DevelopmentContractRepository developmentContractRepository,
            MilestoneRepository milestoneRepository,
            MilestoneQaResultRepository qaResultRepository,
            RoomQueryService roomQueryService,
            ReportQueryService reportQueryService,
            DeliverableMaterialService deliverableMaterialService,
            ContractDetailService contractDetailService) {
        this(
                contractRepository,
                developmentContractRepository,
                milestoneRepository,
                qaResultRepository,
                roomQueryService,
                reportQueryService,
                deliverableMaterialService,
                contractDetailService,
                Clock.systemUTC());
    }

    /** 요청·통과 시각을 시험에서 고정하려고 시계를 받는다. */
    MilestoneProgressService(
            ContractRepository contractRepository,
            DevelopmentContractRepository developmentContractRepository,
            MilestoneRepository milestoneRepository,
            MilestoneQaResultRepository qaResultRepository,
            RoomQueryService roomQueryService,
            ReportQueryService reportQueryService,
            DeliverableMaterialService deliverableMaterialService,
            ContractDetailService contractDetailService,
            Clock clock) {
        this.contractRepository = contractRepository;
        this.developmentContractRepository = developmentContractRepository;
        this.milestoneRepository = milestoneRepository;
        this.qaResultRepository = qaResultRepository;
        this.roomQueryService = roomQueryService;
        this.reportQueryService = reportQueryService;
        this.deliverableMaterialService = deliverableMaterialService;
        this.contractDetailService = contractDetailService;
        this.clock = clock;
    }

    /**
     * 개발자가 확인을 요청한다. 다시 요청하면 자동 통과 기한도 다시 센다. (R-QA-02)
     *
     * @throws BusinessException {@link ContractErrorCode#MILESTONE_NOT_FOUND} 없는 작업이거나 당사자가 아닐 때,
     *                           {@link CommonErrorCode#FORBIDDEN} 의뢰인일 때,
     *                           {@link ContractErrorCode#MILESTONE_STATE_CONFLICT} 착수 전이거나 이미 통과했을 때,
     *                           {@link ContractErrorCode#HANDOVER_NOT_OPEN} 인계 작업인데 일반 작업이 덜 끝났을 때,
     *                           {@link ContractErrorCode#HANDOVER_MATERIALS_MISSING} 인계 작업인데 인수 시험 자료나
     *                           PAID 파일이 없을 때
     */
    @Transactional
    public MilestoneResponse submit(Integer milestoneId, Integer userId) {
        Target target = lock(milestoneId, userId, RoomParties::developerUserId);
        Milestone milestone = target.milestone();
        if (!milestone.isUnderway()) {
            throw new BusinessException(ContractErrorCode.MILESTONE_STATE_CONFLICT);
        }
        if (milestone.isHandover()) {
            boolean regularDone = milestoneRepository
                    .findAllByDevelopmentContractIdAndDeletedFalseOrderByIdAsc(target.development().getId())
                    .stream()
                    .filter(sibling -> !sibling.isHandover())
                    .allMatch(Milestone::isConfirmed);
            if (!regularDone) {
                throw new BusinessException(ContractErrorCode.HANDOVER_NOT_OPEN);
            }
            deliverableMaterialService.requireFinalAcceptanceMaterials(target.development());
        }
        milestone.submit(LocalDateTime.now(clock));
        return contractDetailService.getMilestone(milestone);
    }

    /**
     * 의뢰인이 체크한 확인 문장을 통과로 남긴다. 이미 통과한 것에 더하기만 한다. 확인 요청 중에 남은 문장이 있으면
     * 작업이 개발자에게 돌아간다.
     *
     * @throws BusinessException {@link ContractErrorCode#MILESTONE_NOT_FOUND} 없는 작업이거나 당사자가 아닐 때,
     *                           {@link CommonErrorCode#FORBIDDEN} 개발자일 때,
     *                           {@link ContractErrorCode#MILESTONE_STATE_CONFLICT} 착수 전이거나 이미 통과했을 때
     * @throws InvalidFieldException 이 작업의 확인 문장이 아닌 것이 섞였을 때
     */
    @Transactional
    public MilestoneResponse saveQaResults(Integer milestoneId, Integer userId, SaveQaResultsRequest request) {
        Target target = lock(milestoneId, userId, RoomParties::clientId);
        Milestone milestone = target.milestone();
        if (!milestone.isUnderway()) {
            throw new BusinessException(ContractErrorCode.MILESTONE_STATE_CONFLICT);
        }
        List<MilestoneQaResult> results = qaResultRepository.findAllByMilestoneIdAndDeletedFalseOrderByIdAsc(milestoneId);
        requireOwnCriteria(results, request.qaCriterionIds());

        results.stream()
                .filter(result -> request.qaCriterionIds().contains(result.getQaCriterionId()))
                .forEach(MilestoneQaResult::pass);
        if (milestone.isSubmitted() && !results.stream().allMatch(MilestoneQaResult::isPassed)) {
            milestone.returnToWork();
        }
        return contractDetailService.getMilestone(milestone);
    }

    /**
     * 의뢰인이 확인 문장을 모두 체크하고 통과시킨다. 되돌리지 않는다. (R-QA-01, R-QA-03)
     *
     * @throws BusinessException {@link ContractErrorCode#MILESTONE_NOT_FOUND} 없는 작업이거나 당사자가 아닐 때,
     *                           {@link CommonErrorCode#FORBIDDEN} 개발자일 때,
     *                           {@link ContractErrorCode#MILESTONE_STATE_CONFLICT} 확인 요청 중이 아닐 때,
     *                           {@link ContractErrorCode#QA_NOT_ALL_CHECKED} 체크하지 않은 문장이 있을 때
     * @throws InvalidFieldException 이 작업의 확인 문장이 아닌 것이 섞였을 때
     */
    @Transactional
    public MilestoneResponse confirm(Integer milestoneId, Integer userId, ConfirmMilestoneRequest request) {
        Target target = lock(milestoneId, userId, RoomParties::clientId);
        Milestone milestone = target.milestone();
        if (!milestone.isSubmitted()) {
            throw new BusinessException(ContractErrorCode.MILESTONE_STATE_CONFLICT);
        }
        List<MilestoneQaResult> results = qaResultRepository.findAllByMilestoneIdAndDeletedFalseOrderByIdAsc(milestoneId);
        requireOwnCriteria(results, request.qaCriterionIds());
        boolean allChecked = results.stream()
                .allMatch(result -> request.qaCriterionIds().contains(result.getQaCriterionId()));
        if (!allChecked) {
            throw new BusinessException(ContractErrorCode.QA_NOT_ALL_CHECKED);
        }

        results.forEach(MilestoneQaResult::pass);
        milestone.confirm(LocalDateTime.now(clock));
        return contractDetailService.getMilestone(milestone);
    }

    /**
     * 기한이 지나도록 답이 없는 작업을 통과시킨다. 그 작업의 확인 문장도 모두 통과로 남긴다. (R-QA-02)
     *
     * <p>잠근 뒤 상태와 기한을 다시 본다. 다른 서버가 먼저 통과시켰거나, 그 사이 의뢰인이 답했거나 개발자가 다시
     * 요청했으면 건너뛴다. 해결되지 않은 신고가 있으면 통과시키지 않는다. 자금 동결도 신고로 생긴다.
     *
     * @param now 지금(UTC)
     * @return 이번에 통과시켰으면 {@code true}
     */
    @Transactional
    public boolean autoPass(Integer milestoneId, LocalDateTime now) {
        Optional<Contract> contract = milestoneRepository.findContractIdByMilestoneId(milestoneId)
                .flatMap(contractRepository::findWithLockByIdAndDeletedFalse);
        if (contract.isEmpty()) {
            return false;
        }
        Milestone milestone = milestoneRepository.findByIdAndDeletedFalse(milestoneId).orElse(null);
        boolean inProgress = milestone != null && developmentContractRepository
                .findByIdAndDeletedFalse(milestone.getDevelopmentContractId())
                .filter(DevelopmentContract::isInProgress)
                .isPresent();
        LocalDateTime due = inProgress ? milestone.autoPassAt() : null;
        if (due == null || now.isBefore(due)) {
            return false;
        }
        if (reportQueryService.hasOpenReport(contract.get().getId())) {
            return false;
        }

        qaResultRepository.findAllByMilestoneIdAndDeletedFalseOrderByIdAsc(milestoneId)
                .forEach(MilestoneQaResult::pass);
        milestone.confirm(now);
        return true;
    }

    /**
     * 계약 행부터 잠그고 작업을 읽는다. 작업을 먼저 읽고 잠그면 기다리는 동안 바뀐 상태를 보지 못한다. 당사자가
     * 아니면 존재를 알리지 않고, 다른 쪽 당사자면 권한이 없다고 답한다. 착수해 진행 중인 계약만 받는다.
     */
    private Target lock(Integer milestoneId, Integer userId, Function<RoomParties, Integer> actor) {
        Contract contract = milestoneRepository.findContractIdByMilestoneId(milestoneId)
                .flatMap(contractRepository::findWithLockByIdAndDeletedFalse)
                .orElseThrow(() -> new BusinessException(ContractErrorCode.MILESTONE_NOT_FOUND));
        RoomParties parties = roomQueryService.findParties(contract.getRoomId())
                .filter(found -> found.includes(userId))
                .orElseThrow(() -> new BusinessException(ContractErrorCode.MILESTONE_NOT_FOUND));
        if (!actor.apply(parties).equals(userId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        Milestone milestone = milestoneRepository.findByIdAndDeletedFalse(milestoneId)
                .orElseThrow(() -> new BusinessException(ContractErrorCode.MILESTONE_NOT_FOUND));
        DevelopmentContract development = developmentContractRepository
                .findByIdAndDeletedFalse(milestone.getDevelopmentContractId())
                .orElseThrow(() -> new BusinessException(ContractErrorCode.MILESTONE_NOT_FOUND));
        if (!development.isInProgress()) {
            throw new BusinessException(ContractErrorCode.MILESTONE_STATE_CONFLICT);
        }
        return new Target(milestone, development);
    }

    /** 보낸 식별자가 모두 이 작업의 확인 문장인지. 다른 작업이나 요구사항의 문장은 받지 않는다. */
    private static void requireOwnCriteria(List<MilestoneQaResult> results, List<Integer> qaCriterionIds) {
        List<Integer> own = results.stream().map(MilestoneQaResult::getQaCriterionId).toList();
        if (!own.containsAll(qaCriterionIds)) {
            throw new InvalidFieldException(QA_CRITERION_IDS, NOT_THIS_MILESTONE);
        }
    }

    private record Target(Milestone milestone, DevelopmentContract development) {}
}
