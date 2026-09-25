package com.a501.service.domain.contract.service;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.domain.contract.ContractErrorCode;
import com.a501.service.domain.contract.dto.request.SignContractRequest;
import com.a501.service.domain.contract.dto.response.ContractResponse;
import com.a501.service.domain.contract.entity.Contract;
import com.a501.service.domain.contract.entity.ContractSignature;
import com.a501.service.domain.contract.entity.ContractTemplate;
import com.a501.service.domain.contract.repository.ContractRepository;
import com.a501.service.domain.contract.repository.ContractSignatureRepository;
import com.a501.service.domain.contract.repository.ContractTemplateRepository;
import com.a501.service.domain.room.dto.RoomParties;
import com.a501.service.domain.room.service.RoomQueryService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 계약서에 서명한다.
 *
 * <p><b>양쪽이 모두 서명해야 계약이 성립한다.</b> 한쪽만 서명한 동안에는 협의중으로 남는다.
 * (`R-CON-01`) 서명한 뒤에는 계약서를 고칠 수 없다. (`R-CON-02`)
 *
 * <p>서명판은 컨설팅과 개발이 같은 것을 쓰므로 이 길 하나가 둘을 다 받는다.
 */
@Service
@Transactional(readOnly = true)
public class ContractSignatureService {
    private final ContractRepository contractRepository;
    private final ContractSignatureRepository signatureRepository;
    private final ContractTemplateRepository templateRepository;
    private final RoomQueryService roomQueryService;
    private final ContractDetailService contractDetailService;
    private final Clock clock;

    @Autowired
    public ContractSignatureService(
            ContractRepository contractRepository,
            ContractSignatureRepository signatureRepository,
            ContractTemplateRepository templateRepository,
            RoomQueryService roomQueryService,
            ContractDetailService contractDetailService) {
        this(
                contractRepository,
                signatureRepository,
                templateRepository,
                roomQueryService,
                contractDetailService,
                Clock.systemUTC());
    }

    /** 서명 시각을 시험에서 고정하려고 시계를 받습니다. */
    ContractSignatureService(
            ContractRepository contractRepository,
            ContractSignatureRepository signatureRepository,
            ContractTemplateRepository templateRepository,
            RoomQueryService roomQueryService,
            ContractDetailService contractDetailService,
            Clock clock) {
        this.contractRepository = contractRepository;
        this.signatureRepository = signatureRepository;
        this.templateRepository = templateRepository;
        this.roomQueryService = roomQueryService;
        this.contractDetailService = contractDetailService;
        this.clock = clock;
    }

    /**
     * 서명판에 그린 그림으로 서명한다.
     *
     * @throws BusinessException {@link ContractErrorCode#CONTRACT_NOT_FOUND} 없거나 내 계약이 아닐 때,
     *                           {@link ContractErrorCode#CONTRACT_CANCELLED} 의뢰인이 취소한 계약일 때,
     *                           {@link ContractErrorCode#ALREADY_SIGNED_BY_ME} 이미 내가 서명했을 때
     */
    @Transactional
    public ContractResponse sign(Integer contractId, Integer userId, SignContractRequest request) {
        // 취소와 겹치지 않도록 행을 잠근다. 취소가 먼저 끝났으면 여기서 그 결과를 본다. (R-CON-21)
        Contract contract = contractRepository
                .findWithLockByIdAndDeletedFalse(contractId)
                .orElseThrow(() -> new BusinessException(ContractErrorCode.CONTRACT_NOT_FOUND));
        // 당사자가 아니면 계약이 있다는 사실도 알리지 않는다. (BACKEND_CONVENTIONS 12.1)
        RoomParties parties = roomQueryService
                .findParties(contract.getRoomId())
                .filter(found -> found.includes(userId))
                .orElseThrow(() -> new BusinessException(ContractErrorCode.CONTRACT_NOT_FOUND));
        if (contract.isCancelled()) {
            throw new BusinessException(ContractErrorCode.CONTRACT_CANCELLED);
        }

        boolean client = Objects.equals(parties.clientId(), userId);
        if (contract.isSignedBy(client)) {
            throw new BusinessException(ContractErrorCode.ALREADY_SIGNED_BY_ME);
        }

        ContractSignature signature = signatureRepository.save(ContractSignature.of(
                userId, request.signatureImageFileId(), LocalDateTime.now(clock)));
        contract.sign(client, signature.getId(), contractText(contract));

        return contractDetailService.getContract(userId, contractId);
    }

    /**
     * 굳혀 둘 계약 문구.
     *
     * <p>양식은 나중에 바뀔 수 있으므로 양쪽이 서명을 마치는 순간의 글을 계약에 그대로 박아 둔다.
     * 그래야 뒤에 양식이 바뀌어도 이 계약은 그때 합의한 글로 남는다.
     *
     * <p>이 계약이 가리키는 양식이 없으면 비워 둔다. 문구가 없다고 서명을 막지는 않는다.
     */
    private String contractText(Contract contract) {
        if (contract.getTemplateId() == null) {
            return null;
        }
        return templateRepository
                .findById(contract.getTemplateId())
                .map(ContractTemplate::getBody)
                .orElse(null);
    }
}
