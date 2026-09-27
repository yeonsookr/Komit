package com.a501.service.domain.contract.service;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.CommonErrorCode;
import com.a501.service.domain.contract.ContractErrorCode;
import com.a501.service.domain.contract.dto.request.AddDeliverableRequest;
import com.a501.service.domain.contract.dto.request.AddDeliverableUrlRequest;
import com.a501.service.domain.contract.dto.request.AttachDeliverableFileRequest;
import com.a501.service.domain.contract.dto.response.DeliverableResponse;
import com.a501.service.domain.contract.entity.Contract;
import com.a501.service.domain.contract.entity.Deliverable;
import com.a501.service.domain.contract.entity.DeliverableAccessStage;
import com.a501.service.domain.contract.entity.DeliverableMaterial;
import com.a501.service.domain.contract.entity.DevelopmentContract;
import com.a501.service.domain.contract.entity.Milestone;
import com.a501.service.domain.contract.repository.ContractRepository;
import com.a501.service.domain.contract.repository.DeliverableMaterialRepository;
import com.a501.service.domain.contract.repository.DeliverableRepository;
import com.a501.service.domain.contract.repository.DevelopmentContractRepository;
import com.a501.service.domain.contract.repository.MilestoneRepository;
import com.a501.service.domain.file.service.FileLookupService;
import com.a501.service.domain.room.dto.RoomParties;
import com.a501.service.domain.room.service.RoomQueryService;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 개발 계약의 산출물에 인계 자료(파일·URL)를 등록하고, 계약 뒤 산출물 항목을 더한다. (R-QA-06)
 *
 * <p><b>일반 작업을 모두 확정한 뒤부터 인계 작업의 확인을 요청하기 전까지만</b> 등록한다. 요청한 뒤 자료가
 * 바뀌면 의뢰인이 검수한 것과 지급 뒤 받는 것이 달라진다. 의뢰인이 확인 문장 일부만 통과시켜 인계 작업이
 * 개발자에게 돌아오면 다시 등록할 수 있다.
 *
 * <p><b>등록할 때마다 자료가 하나 는다.</b> 같은 산출물의 다른 URL·파일은 지우지 않는다. 같은 자료를 다시 보내면
 * 새로 만들지 않고 지금 상태를 돌려준다(재시도·두 번 누르기).
 *
 * <p>같은 계약 안에서 한 파일을 시험용(TEST)과 해금용(PAID)으로 함께 두지 않는다. 시험용으로 지급 전에 받을 수
 * 있기 때문이다. 동시 요청이 이 검사를 함께 지나가지 않도록 계약 행을 잠그고 본다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeliverableMaterialService {
    private final ContractRepository contractRepository;
    private final DevelopmentContractRepository developmentContractRepository;
    private final MilestoneRepository milestoneRepository;
    private final DeliverableRepository deliverableRepository;
    private final DeliverableMaterialRepository materialRepository;
    private final RoomQueryService roomQueryService;
    private final FileLookupService fileLookupService;

    /**
     * 개발자가 올린 파일을 인계물에 붙인다.
     *
     * @throws BusinessException {@link ContractErrorCode#DELIVERABLE_NOT_FOUND} 없는 인계물이거나 당사자가 아닐 때,
     *                           {@link CommonErrorCode#FORBIDDEN} 의뢰인일 때,
     *                           {@link ContractErrorCode#CONTRACT_CANCELLED} 취소된 계약일 때,
     *                           {@link ContractErrorCode#HANDOVER_NOT_OPEN} 서명 전이거나 일반 작업이 덜 끝났을 때,
     *                           {@link ContractErrorCode#FINAL_ACCEPTANCE_PASSED} 인계 작업을 확정한 뒤일 때,
     *                           {@link ContractErrorCode#HANDOVER_UNDER_REVIEW} 인계 작업의 확인을 요청한 뒤일 때,
     *                           {@code FILE_NOT_ATTACHABLE} 없거나 업로드를 마치지 않았거나 남의 파일일 때,
     *                           {@link ContractErrorCode#MATERIAL_STAGE_CONFLICT} 같은 계약에 같은 파일이 다른
     *                           단계로 있을 때
     */
    @Transactional
    public DeliverableResponse attachFile(Integer userId, Integer deliverableId, AttachDeliverableFileRequest request) {
        Handover handover = openHandover(userId, deliverableId);
        fileLookupService.assertAttachableLong(userId, List.of(request.fileId()));

        List<DeliverableMaterial> contractMaterials = contractMaterials(handover.development());
        Optional<DeliverableMaterial> sameFile = contractMaterials.stream()
                .filter(material -> request.fileId().equals(material.getFileId()))
                .filter(material -> material.getAccessStage() == request.accessStage())
                .filter(material -> material.getDeliverableId().equals(deliverableId))
                .findFirst();
        if (sameFile.isEmpty()) {
            boolean otherStage = contractMaterials.stream()
                    .filter(material -> request.fileId().equals(material.getFileId()))
                    .anyMatch(material -> material.getAccessStage() != request.accessStage());
            if (otherStage) {
                throw new BusinessException(ContractErrorCode.MATERIAL_STAGE_CONFLICT);
            }
            materialRepository.save(
                    DeliverableMaterial.ofFile(deliverableId, request.fileId(), request.accessStage()));
        }
        return response(handover.deliverable());
    }

    /**
     * 개발자가 인수 시험 URL 을 하나 더한다. URL 은 시험 중에 여는 자료라 TEST 다.
     *
     * @throws BusinessException 파일 등록과 같다. 파일 검사만 없다
     */
    @Transactional
    public DeliverableResponse addUrl(Integer userId, Integer deliverableId, AddDeliverableUrlRequest request) {
        Handover handover = openHandover(userId, deliverableId);
        boolean exists = materialRepository.findAllByDeliverableIdInAndDeletedFalseOrderByIdAsc(List.of(deliverableId))
                .stream()
                .anyMatch(material -> request.url().equals(material.getUrl()));
        if (!exists) {
            materialRepository.save(DeliverableMaterial.ofUrl(deliverableId, request.url()));
        }
        return response(handover.deliverable());
    }

    /**
     * 계약 뒤 개발자가 산출물 항목을 더한다. 서명한 계약서의 범위·금액·확인 문장은 그대로다.
     *
     * @throws BusinessException {@link ContractErrorCode#CONTRACT_NOT_FOUND} 없는 계약이거나 당사자가 아니거나 개발
     *                           계약이 아닐 때, 그 밖은 파일 등록과 같다
     */
    @Transactional
    public DeliverableResponse addDeliverable(Integer userId, Integer contractId, AddDeliverableRequest request) {
        Contract contract = contractRepository.findWithLockByIdAndDeletedFalse(contractId)
                .orElseThrow(() -> new BusinessException(ContractErrorCode.CONTRACT_NOT_FOUND));
        requireDeveloper(contract, userId, ContractErrorCode.CONTRACT_NOT_FOUND);
        DevelopmentContract development = developmentContractRepository.findByContractIdAndDeletedFalse(contractId)
                .orElseThrow(() -> new BusinessException(ContractErrorCode.CONTRACT_NOT_FOUND));
        requireOpen(contract, development);

        Deliverable deliverable = deliverableRepository.save(
                Deliverable.addedAfterSigning(development.getId(), request.title()));
        return DeliverableResponse.from(deliverable, List.of());
    }

    /** 인계물과 그 계약을 잠그고, 지금 인계 자료를 받을 수 있는지 본다. */
    private Handover openHandover(Integer userId, Integer deliverableId) {
        Deliverable deliverable = deliverableRepository.findByIdAndDeletedFalse(deliverableId)
                .orElseThrow(() -> new BusinessException(ContractErrorCode.DELIVERABLE_NOT_FOUND));
        DevelopmentContract development = developmentContractRepository
                .findByIdAndDeletedFalse(deliverable.getDevelopmentContractId())
                .orElseThrow(() -> new BusinessException(ContractErrorCode.DELIVERABLE_NOT_FOUND));
        Contract contract = contractRepository.findWithLockByIdAndDeletedFalse(development.getContractId())
                .orElseThrow(() -> new BusinessException(ContractErrorCode.DELIVERABLE_NOT_FOUND));
        requireDeveloper(contract, userId, ContractErrorCode.DELIVERABLE_NOT_FOUND);
        requireOpen(contract, development);
        return new Handover(deliverable, development);
    }

    /** 당사자가 아니면 존재를 알리지 않고, 의뢰인이면 권한이 없다고 답한다. */
    private void requireDeveloper(Contract contract, Integer userId, ContractErrorCode notFound) {
        RoomParties parties = roomQueryService.findParties(contract.getRoomId())
                .filter(found -> found.includes(userId))
                .orElseThrow(() -> new BusinessException(notFound));
        if (!parties.developerUserId().equals(userId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
    }

    /**
     * 인계 작업의 확인을 요청할 만큼 자료가 있는지. 계약 전체에 인수 시험 자료(URL 또는 TEST 파일)와 지급 뒤 받을
     * PAID 파일이 하나 이상씩 있어야 한다. 산출물 항목마다 보지는 않는다. (R-QA-06)
     *
     * <p>부르는 쪽이 계약 행을 잠근 채로 부른다. 자료 등록도 같은 행을 잠그므로 검사와 등록이 엇갈리지 않는다.
     *
     * @throws BusinessException {@link ContractErrorCode#HANDOVER_MATERIALS_MISSING} 어느 한쪽이라도 없을 때
     */
    public void requireFinalAcceptanceMaterials(DevelopmentContract development) {
        List<DeliverableMaterial> materials = contractMaterials(development);
        // URL 자료는 언제나 TEST 다. 그래서 TEST 하나로 URL 과 시험용 파일을 함께 센다.
        boolean testable = materials.stream()
                .anyMatch(material -> material.getAccessStage() == DeliverableAccessStage.TEST);
        boolean payable = materials.stream()
                .anyMatch(material -> material.getAccessStage() == DeliverableAccessStage.PAID);
        if (!testable || !payable) {
            throw new BusinessException(ContractErrorCode.HANDOVER_MATERIALS_MISSING);
        }
    }

    /**
     * 인계 자료를 받을 수 있는 때인지. 양쪽이 서명하고 일반 작업을 모두 확정한 뒤, 인계 작업의 확인을 요청하기
     * 전이다. 인계 작업은 금액이 아니라 표시로 가린다.
     */
    private void requireOpen(Contract contract, DevelopmentContract development) {
        if (contract.isCancelled()) {
            throw new BusinessException(ContractErrorCode.CONTRACT_CANCELLED);
        }
        List<Milestone> milestones =
                milestoneRepository.findAllByDevelopmentContractIdAndDeletedFalseOrderByIdAsc(development.getId());
        boolean regularDone = milestones.stream()
                .filter(milestone -> !milestone.isHandover())
                .allMatch(Milestone::isConfirmed);
        if (!contract.isConcluded() || !regularDone) {
            throw new BusinessException(ContractErrorCode.HANDOVER_NOT_OPEN);
        }
        List<Milestone> handovers = milestones.stream().filter(Milestone::isHandover).toList();
        if (handovers.stream().anyMatch(Milestone::isConfirmed)) {
            throw new BusinessException(ContractErrorCode.FINAL_ACCEPTANCE_PASSED);
        }
        if (handovers.stream().anyMatch(Milestone::isSubmitted)) {
            throw new BusinessException(ContractErrorCode.HANDOVER_UNDER_REVIEW);
        }
    }

    /** 이 계약의 모든 산출물에 붙은 자료. 같은 파일이 다른 단계로 있는지 계약 전체에서 본다. */
    private List<DeliverableMaterial> contractMaterials(DevelopmentContract development) {
        List<Integer> deliverableIds = deliverableRepository
                .findAllByDevelopmentContractIdAndDeletedFalseOrderByIdAsc(development.getId())
                .stream()
                .map(Deliverable::getId)
                .toList();
        return deliverableIds.isEmpty()
                ? List.of()
                : materialRepository.findAllByDeliverableIdInAndDeletedFalseOrderByIdAsc(deliverableIds);
    }

    private DeliverableResponse response(Deliverable deliverable) {
        return DeliverableResponse.from(
                deliverable,
                materialRepository.findAllByDeliverableIdInAndDeletedFalseOrderByIdAsc(List.of(deliverable.getId())));
    }

    private record Handover(Deliverable deliverable, DevelopmentContract development) {
        private Handover {
            Objects.requireNonNull(deliverable, "deliverable");
            Objects.requireNonNull(development, "development");
        }
    }
}
