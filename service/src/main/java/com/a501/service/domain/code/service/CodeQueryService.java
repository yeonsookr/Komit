package com.a501.service.domain.code.service;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.domain.code.CodeErrorCode;
import com.a501.service.domain.code.dto.CodeListResponse;
import com.a501.service.domain.code.dto.CodeResponse;
import com.a501.service.domain.code.entity.DynamicCode;
import com.a501.service.domain.code.entity.DynamicCodeGroup;
import com.a501.service.domain.code.repository.DynamicCodeGroupRepository;
import com.a501.service.domain.code.repository.DynamicCodeRepository;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
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

    /**
     * 요청한 코드가 전부 그 분류의 살아 있는 코드인지 확인하고 표시 정보를 돌려줍니다. 다른 도메인이 코드 식별자를 저장하기 전에 부릅니다.
     *
     * @return 코드 식별자별 표시 정보. 요청이 비었으면 빈 맵
     * @throws BusinessException {@link CodeErrorCode#CODE_NOT_FOUND} 하나라도 없거나 비활성이거나 다른 분류의 코드일 때,
     *                           {@link CodeErrorCode#CODE_GROUP_NOT_FOUND} 분류 자체가 없을 때
     */
    /**
     * 이름으로 코드 식별자를 찾습니다. 밖에서 이름으로만 오는 값을 저장하기 전에 부릅니다.
     *
     * @return 이름별 코드 식별자. 시드에 없는 이름은 결과에 빠지므로 부르는 쪽이 건너뛸지 거절할지 정합니다
     */
    public Map<String, Integer> findCodeIdsByName(String groupKey, Collection<String> codeNames) {
        if (codeNames == null || codeNames.isEmpty()) {
            return Map.of();
        }
        DynamicCodeGroup group = groups.findByGroupKeyAndActiveTrue(groupKey)
                .orElseThrow(() -> new BusinessException(CodeErrorCode.CODE_GROUP_NOT_FOUND));
        return codes.findByGroupIdAndCodeNameInAndActiveTrue(group.getGroupId(), Set.copyOf(codeNames)).stream()
                .collect(Collectors.toMap(DynamicCode::getCodeName, DynamicCode::getCodeId, (first, ignored) -> first));
    }

    /** 코드 식별자별 표시 정보. 없는 코드는 결과에 빠집니다. 저장된 식별자를 화면에 보여줄 때 씁니다. */
    public Map<Integer, CodeResponse> findCodes(Collection<Integer> codeIds) {
        if (codeIds == null || codeIds.isEmpty()) {
            return Map.of();
        }
        return codes.findAllById(codeIds).stream()
                .collect(Collectors.toMap(DynamicCode::getCodeId, CodeResponse::from));
    }

    public Map<Integer, CodeResponse> requireCodes(String groupKey, Collection<Integer> codeIds) {
        Set<Integer> wanted = codeIds == null ? Set.of()
                : codeIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (wanted.isEmpty()) {
            return Map.of();
        }
        DynamicCodeGroup group = groups.findByGroupKeyAndActiveTrue(groupKey)
                .orElseThrow(() -> new BusinessException(CodeErrorCode.CODE_GROUP_NOT_FOUND));
        Map<Integer, CodeResponse> found = codes.findByCodeIdInAndGroupIdAndActiveTrue(wanted, group.getGroupId()).stream()
                .collect(Collectors.toMap(DynamicCode::getCodeId, CodeResponse::from));
        if (found.size() != wanted.size()) {
            throw new BusinessException(CodeErrorCode.CODE_NOT_FOUND);
        }
        return found;
    }
}
