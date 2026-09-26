package com.a501.service.domain.contract.service;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.CommonErrorCode;
import com.a501.service.domain.contract.ContractErrorCode;
import com.a501.service.domain.contract.dto.request.WriteDevelopmentContractRequest;
import com.a501.service.domain.contract.dto.request.WriteMilestoneRequest;
import com.a501.service.domain.contract.dto.response.ContractResponse;
import com.a501.service.domain.contract.entity.Contract;
import com.a501.service.domain.contract.entity.Deliverable;
import com.a501.service.domain.contract.entity.DevelopmentContract;
import com.a501.service.domain.contract.entity.Milestone;
import com.a501.service.domain.contract.entity.MilestoneQaResult;
import com.a501.service.domain.contract.entity.TempMessage;
import com.a501.service.domain.contract.repository.ContractRepository;
import com.a501.service.domain.contract.repository.DeliverableRepository;
import com.a501.service.domain.contract.repository.DevelopmentContractRepository;
import com.a501.service.domain.contract.repository.MilestoneQaResultRepository;
import com.a501.service.domain.contract.repository.MilestoneRepository;
import com.a501.service.domain.contract.repository.TempMessageRepository;
import com.a501.service.domain.diagnosis.service.ContractRequirementService;
import com.a501.service.domain.room.RoomErrorCode;
import com.a501.service.domain.room.dto.ContractRoomView;
import com.a501.service.domain.room.dto.request.RoomType;
import com.a501.service.domain.room.service.RoomQueryService;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 개발 계약서를 쓰고, 협의 중에 다시 쓴다.
 *
 * <p><b>방 하나에 계약서 하나다.</b> 방에 계약서가 있으면 AI 초안이든 개발자가 고친 것이든 그 계약서를 이
 * 요청으로 바꾼다. 컨설팅 작성처럼 AI 원본이 있는 계약만 이어 쓰면, 원본 없이 쓴 계약서를 다시 보낼 때
 * 계약서가 둘 생긴다.
 *
 * <p><b>한쪽이라도 서명한 뒤에는 고칠 수 없다.</b> (R-CON-02)
 *
 * <p>계약서에 담는 요구사항은 요청서에 담긴 것과, 이 계약서에 이미 들어 있는 것이다. 개발자는 요청서에
 * 담긴 것만 볼 수 있고(R-REQ-05), 다시 쓸 때는 앞서 새로 만든 작업이 그 요구사항 번호를 달고 돌아온다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DevelopmentContractWriteService {
    private final ContractRepository contractRepository;
    private final DevelopmentContractRepository developmentContractRepository;
    private final MilestoneRepository milestoneRepository;
    private final MilestoneQaResultRepository milestoneQaResultRepository;
    private final DeliverableRepository deliverableRepository;
    private final TempMessageRepository tempMessageRepository;
    private final RoomQueryService roomQueryService;
    private final ContractRequirementService contractRequirementService;
    private final ContractDetailService contractDetailService;

    /**
     * 개발자가 개발 계약서를 써서 보낸다. 협의 중인 계약서가 있으면 그 내용을 이 요청으로 바꾼다.
     *
     * @throws BusinessException {@link RoomErrorCode#ROOM_NOT_FOUND} 없는 방이거나 방의 당사자가 아닐 때,
     *                           {@link CommonErrorCode#FORBIDDEN} 방의 의뢰인일 때,
     *                           {@link ContractErrorCode#NOT_DEVELOPMENT_ROOM} 개발방이 아닐 때,
     *                           {@link ContractErrorCode#DUE_DATE_BEFORE_START} 납기일이 착수일보다 이를 때,
     *                           {@link ContractErrorCode#CONTRACT_CANCELLED} 취소된 계약일 때,
     *                           {@link ContractErrorCode#CONTRACT_ALREADY_SIGNED} 한쪽이라도 서명했을 때,
     *                           {@link CommonErrorCode#INVALID_REQUEST} 계약서에 넣을 수 없는 요구사항이 섞였을 때
     */
    @Transactional
    public ContractResponse write(Integer roomId, Integer userId, WriteDevelopmentContractRequest request) {
        ContractRoomView room = requireDeveloperRoom(roomId, userId);
        if (request.dueDateBeforeStart()) {
            throw new BusinessException(ContractErrorCode.DUE_DATE_BEFORE_START);
        }

        Contract contract = contractRepository.findByRoomIdAndDeletedFalse(roomId).orElse(null);
        DevelopmentContract development = null;
        List<Milestone> previous = List.of();
        if (contract != null) {
            requireWritable(contract);
            development = developmentContractRepository.findByContractIdAndDeletedFalse(contract.getId()).orElse(null);
            if (development != null) {
                previous = milestoneRepository.findAllByDevelopmentContractIdAndDeletedFalseOrderByIdAsc(
                        development.getId());
            }
        }
        requireKnownIssues(room.projectId(), previous, request.milestones());

        if (contract == null) {
            contract = contractRepository.save(Contract.of(roomId, Contract.ContractType.DEVELOPMENT));
        }
        if (development == null) {
            development = developmentContractRepository.save(DevelopmentContract.of(
                    contract.getId(), request.startDate(), request.dueDate(), request.freeRepairDays()));
        } else {
            development.rewrite(request.startDate(), request.dueDate(), request.freeRepairDays());
        }

        LocalDateTime now = LocalDateTime.now();
        removePrevious(development.getId(), previous, now);
        long total = 0;
        for (WriteMilestoneRequest milestone : request.milestones()) {
            saveMilestone(room.projectId(), development.getId(), milestone);
            total += milestone.price();
        }
        // R-CON-04: 계약 금액은 작업 가격의 합이다.
        development.changeTotalAmount(Math.toIntExact(total));
        contractRequirementService.markContracted(request.milestones().stream()
                .map(WriteMilestoneRequest::issueId)
                .filter(Objects::nonNull)
                .toList());

        if (request.deliverableTitles() != null) {
            Integer developmentId = development.getId();
            request.deliverableTitles().forEach(title ->
                    deliverableRepository.save(Deliverable.of(developmentId, title)));
        }
        leaveMessage(contract.getId(), request.message());

        return contractDetailService.getContract(userId, contract.getId());
    }

    /**
     * 계약서는 개발자가 쓴다. 방의 당사자가 아니면 방이 있다는 것을 알리지 않는다. 의뢰인은 방이 있다는
     * 것을 알고 있으므로 감추지 않고 403 으로 답한다.
     */
    private ContractRoomView requireDeveloperRoom(Integer roomId, Integer userId) {
        ContractRoomView room = roomQueryService
                .findContractRoom(roomId)
                .filter(found -> found.parties().includes(userId))
                .orElseThrow(() -> new BusinessException(RoomErrorCode.ROOM_NOT_FOUND));
        if (!Objects.equals(room.parties().developerUserId(), userId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        if (!RoomType.DEVELOPMENT.name().equals(room.type())) {
            throw new BusinessException(ContractErrorCode.NOT_DEVELOPMENT_ROOM);
        }
        return room;
    }

    /** R-CON-02: 한쪽이라도 서명했으면 고칠 수 없다. R-CON-21: 취소된 계약서를 되살리는 길이 되면 안 된다. */
    private void requireWritable(Contract contract) {
        if (contract.isCancelled()) {
            throw new BusinessException(ContractErrorCode.CONTRACT_CANCELLED);
        }
        if (contract.isSigned()) {
            throw new BusinessException(ContractErrorCode.CONTRACT_ALREADY_SIGNED);
        }
        if (!Contract.ContractType.DEVELOPMENT.name().equals(contract.getContractType())) {
            // 개발방에 컨설팅 계약이 있다. 방 종류를 거르고 들어왔으므로 데이터가 어긋난 것이다.
            log.error("개발방에 개발 계약이 아닌 계약이 있다 contractId={} type={}",
                    contract.getId(), contract.getContractType());
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
        }
    }

    /**
     * 요청서에 담긴 요구사항과 이 계약서에 이미 들어 있는 요구사항만 받는다. 없는 요구사항, 다른 프로젝트의
     * 요구사항, 의뢰인이 고르지 않은 요구사항은 여기서 걸린다.
     */
    private void requireKnownIssues(
            Integer projectId, List<Milestone> previous, List<WriteMilestoneRequest> milestones) {
        Set<Integer> known = new HashSet<>(contractRequirementService.findRequestIssueIds(projectId));
        previous.stream().map(Milestone::getIssueId).filter(Objects::nonNull).forEach(known::add);
        boolean allKnown = milestones.stream()
                .map(WriteMilestoneRequest::issueId)
                .filter(Objects::nonNull)
                .allMatch(known::contains);
        if (!allKnown) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
    }

    /**
     * 앞서 보낸 작업·계약서 문장·인계물을 지운다. 서명 전이라 통과 기록이 없어 잃는 것이 없다.
     *
     * <p>작업과 확인 문장의 연결은 지우지 않는다. 계약서는 살아 있는 작업의 연결만 읽는다. 계약에서 빠진
     * 요구사항은 되돌리지 않는다 — 요청서에서 온 것은 계약 상태로, 새로 만든 것은 그대로 남는다(후속).
     */
    private void removePrevious(Integer developmentId, List<Milestone> previous, LocalDateTime now) {
        if (!previous.isEmpty()) {
            List<Integer> qaCriterionIds = milestoneQaResultRepository
                    .findAllByMilestoneIdInAndDeletedFalseOrderByIdAsc(
                            previous.stream().map(Milestone::getId).toList())
                    .stream()
                    .map(MilestoneQaResult::getQaCriterionId)
                    .toList();
            contractRequirementService.dropCriteria(qaCriterionIds);
            previous.forEach(milestone -> milestone.removeFromContract(now));
        }
        deliverableRepository.findAllByDevelopmentContractIdAndDeletedFalseOrderByIdAsc(developmentId)
                .forEach(deliverable -> deliverable.removeFromContract(now));
    }

    /**
     * 작업 하나를 저장한다. 새 작업이면 계약용 요구사항을 먼저 만들어 붙인다. 이름·설명은 요구사항에 있으니
     * 작업에는 적지 않고, 기존 요구사항이면 계약서에서 바꾼 값만 작업에 남긴다.
     */
    private void saveMilestone(Integer projectId, Integer developmentId, WriteMilestoneRequest request) {
        boolean newWork = request.issueId() == null;
        Integer issueId = newWork
                ? contractRequirementService.createIssue(projectId, request.title(), request.detail())
                : request.issueId();
        Milestone milestone = milestoneRepository.save(Milestone.of(
                developmentId,
                issueId,
                newWork ? null : request.title(),
                newWork ? null : request.detail(),
                request.price(),
                request.workDate()));
        contractRequirementService.writeCriteria(issueId, request.qaCriteria())
                .forEach(qaCriterionId ->
                        milestoneQaResultRepository.save(MilestoneQaResult.of(milestone.getId(), qaCriterionId)));
    }

    private void leaveMessage(Integer contractId, String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        tempMessageRepository.save(TempMessage.of(contractId, message.trim(), false));
    }
}
