package com.a501.service.domain.code.service;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.domain.code.CodeErrorCode;
import com.a501.service.domain.code.dto.CodeListResponse;
import com.a501.service.domain.code.dto.CodeResponse;
import com.a501.service.domain.code.entity.DynamicCodeGroup;
import com.a501.service.domain.code.repository.DynamicCodeGroupRepository;
import com.a501.service.domain.code.repository.DynamicCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CodeQueryService {
    private final DynamicCodeGroupRepository groups;
    private final DynamicCodeRepository codes;

    public CodeListResponse getCodes(String groupKey) {
        DynamicCodeGroup group = groups.findByGroupKeyAndActiveTrue(groupKey)
                .orElseThrow(() -> new BusinessException(CodeErrorCode.CODE_GROUP_NOT_FOUND));
        return new CodeListResponse(codes.findByGroupIdAndActiveTrueOrderBySortOrderAscCodeIdAsc(group.getGroupId())
                .stream().map(CodeResponse::from).toList());
    }
}
