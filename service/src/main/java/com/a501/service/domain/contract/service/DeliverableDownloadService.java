package com.a501.service.domain.contract.service;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.domain.contract.ContractErrorCode;
import com.a501.service.domain.contract.entity.Contract;
import com.a501.service.domain.contract.entity.Deliverable;
import com.a501.service.domain.contract.entity.DeliverableAccessStage;
import com.a501.service.domain.contract.entity.DeliverableMaterial;
import com.a501.service.domain.contract.entity.DevelopmentContract;
import com.a501.service.domain.contract.repository.ContractRepository;
import com.a501.service.domain.contract.repository.DeliverableMaterialRepository;
import com.a501.service.domain.contract.repository.DeliverableRepository;
import com.a501.service.domain.contract.repository.DevelopmentContractRepository;
import com.a501.service.domain.file.service.FileLookupService;
import com.a501.service.domain.file.service.FileLookupService.FileContent;
import com.a501.service.domain.room.dto.RoomParties;
import com.a501.service.domain.room.service.RoomQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 계약 당사자가 인계 파일을 내려받는다. (R-QA-06)
 *
 * <p><b>권한은 파일마다 따로 본다.</b> 파일 식별자를 안다는 것은 권한이 아니다. 요청한 자료가 속한 계약의 당사자인지 보고,
 * 시험용(TEST)은 양쪽이 언제든, 지급 뒤 해금(PAID)은 올린 개발자는 언제든, 의뢰인은 대금 지급이 끝난 뒤에만 준다. 같은
 * 산출물에 URL 이나 TEST 파일이 있다고 PAID 를 열지 않는다.
 *
 * <p>지급이 끝났는지는 개발 계약의 완료 표시로 본다. 이 표시는 Escrow 가 지급액과 수수료 이체를 모두 확정한 뒤에만 남는다.
 * 지급을 부탁만 했거나 결과를 모르는 동안에는 열지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeliverableDownloadService {
    private final DeliverableMaterialRepository materialRepository;
    private final DeliverableRepository deliverableRepository;
    private final DevelopmentContractRepository developmentContractRepository;
    private final ContractRepository contractRepository;
    private final RoomQueryService roomQueryService;
    private final FileLookupService fileLookupService;

    /**
     * 인계 자료 하나의 파일.
     *
     * @throws BusinessException {@link ContractErrorCode#DELIVERABLE_NOT_FOUND} 없는 자료이거나 당사자가 아니거나 URL 자료일
     *                           때, {@link ContractErrorCode#DELIVERABLE_ACCESS_LOCKED} 지급 전 의뢰인이 PAID 파일을 받으려 할 때
     */
    public FileContent downloadMaterial(Integer userId, Integer deliverableMaterialId) {
        DeliverableMaterial material = materialRepository.findByIdAndDeletedFalse(deliverableMaterialId)
                .orElseThrow(DeliverableDownloadService::notFound);
        Access access = accessOf(userId, material.getDeliverableId());
        // URL 자료는 받을 파일이 없다.
        if (material.getFileId() == null) {
            throw notFound();
        }
        if (material.getAccessStage() == DeliverableAccessStage.PAID) {
            access.requirePaidOpen();
        }
        return open(material.getFileId());
    }

    /**
     * 기존 단일 파일 칸({@code deliverables.file_id})의 파일. TEST·PAID 구분이 없어 지급 뒤 해금 파일로 다룬다.
     *
     * @throws BusinessException {@link ContractErrorCode#DELIVERABLE_NOT_FOUND} 없는 인계물이거나 당사자가 아닐 때,
     *                           {@link ContractErrorCode#DELIVERABLE_NOT_UPLOADED} 올라온 파일이 없을 때,
     *                           {@link ContractErrorCode#DELIVERABLE_ACCESS_LOCKED} 지급 전 의뢰인일 때
     */
    public FileContent downloadLegacy(Integer userId, Integer deliverableId) {
        Access access = accessOf(userId, deliverableId);
        Integer fileId = access.deliverable().getFileId();
        if (fileId == null) {
            throw new BusinessException(ContractErrorCode.DELIVERABLE_NOT_UPLOADED);
        }
        access.requirePaidOpen();
        return open(fileId.longValue());
    }

    /** 인계물 → 개발 계약 → 계약 → 방 순으로 내려가 당사자인지 본다. 어디서 끊겨도 존재를 알리지 않는다. */
    private Access accessOf(Integer userId, Integer deliverableId) {
        Deliverable deliverable = deliverableRepository.findByIdAndDeletedFalse(deliverableId)
                .orElseThrow(DeliverableDownloadService::notFound);
        DevelopmentContract development = developmentContractRepository
                .findByIdAndDeletedFalse(deliverable.getDevelopmentContractId())
                .orElseThrow(DeliverableDownloadService::notFound);
        Contract contract = contractRepository.findByIdAndDeletedFalse(development.getContractId())
                .orElseThrow(DeliverableDownloadService::notFound);
        RoomParties parties = roomQueryService.findParties(contract.getRoomId())
                .filter(found -> found.includes(userId))
                .orElseThrow(DeliverableDownloadService::notFound);
        return new Access(deliverable, parties.developerUserId().equals(userId), development.isCompleted());
    }

    private FileContent open(Long fileId) {
        return fileLookupService.open(fileId).orElseThrow(DeliverableDownloadService::notFound);
    }

    private static BusinessException notFound() {
        return new BusinessException(ContractErrorCode.DELIVERABLE_NOT_FOUND);
    }

    private record Access(Deliverable deliverable, boolean developer, boolean paid) {
        /** 올린 개발자는 자기 자료를 언제든 받는다. 의뢰인은 지급이 끝난 뒤에만 받는다. */
        void requirePaidOpen() {
            if (!developer && !paid) {
                throw new BusinessException(ContractErrorCode.DELIVERABLE_ACCESS_LOCKED);
            }
        }
    }
}
