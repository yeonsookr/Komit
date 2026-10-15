package com.a501.service.domain.contract.service;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.CommonErrorCode;
import com.a501.service.domain.contract.ContractErrorCode;
import com.a501.service.domain.contract.entity.Contract;
import com.a501.service.domain.contract.entity.Milestone;
import com.a501.service.domain.diagnosis.service.ContractRequirementService;
import com.a501.service.domain.room.RoomErrorCode;
import com.a501.service.domain.room.dto.ContractRoomView;
import com.a501.service.domain.room.dto.request.RoomType;
import com.a501.service.domain.room.service.RoomQueryService;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 개발 계약 편집과 그 보조 기능이 공유하는 접근·상태·요구사항 범위 정책. */
@Slf4j
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DevelopmentContractEditingPolicy {
    private final RoomQueryService roomQueryService;
    private final ContractRequirementService contractRequirementService;

    /** 개발방의 개발자만 계약 편집 기능을 사용할 수 있다. */
    public ContractRoomView requireDeveloperRoom(Integer roomId, Integer userId) {
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

    /** R-CON-02·R-CON-21: 서명하거나 취소한 계약은 편집하지 않는다. */
    public void requireWritable(Contract contract) {
        if (contract.isCancelled()) {
            throw new BusinessException(ContractErrorCode.CONTRACT_CANCELLED);
        }
        if (contract.isSigned()) {
            throw new BusinessException(ContractErrorCode.CONTRACT_ALREADY_SIGNED);
        }
        if (!Contract.ContractType.DEVELOPMENT.name().equals(contract.getContractType())) {
            log.error("개발방에 개발 계약이 아닌 계약이 있다 contractId={} type={}",
                    contract.getId(), contract.getContractType());
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
        }
    }

    /** 요청서 또는 현재 계약에 속한 요구사항만 계약 편집에 사용한다. */
    public void requireKnownIssues(
            Integer projectId, List<Milestone> previous, Collection<Integer> issueIds) {
        Set<Integer> known = new HashSet<>(contractRequirementService.findRequestIssueIds(projectId));
        previous.stream().map(Milestone::getIssueId).filter(Objects::nonNull).forEach(known::add);
        if (!known.containsAll(issueIds)) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
    }
}
