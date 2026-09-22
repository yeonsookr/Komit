package com.a501.service.domain.developer.service;

import com.a501.service.domain.developer.entity.DeveloperTechCategory;
import com.a501.service.domain.developer.repository.DeveloperTechCategoryRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 개발자가 다룰 수 있는 기술. 저장은 전체 치환이며, 자연키가 겹치는 소프트 삭제 행은 새로 만들지 않고 되살립니다.
 * 코드가 살아 있는지는 호출하는 쪽이 코드 도메인에 먼저 확인합니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeveloperTechCategoryService {
    private static final Comparator<DeveloperTechCategory> REGISTRATION_ORDER =
            Comparator.comparing(DeveloperTechCategory::getCreatedAt).thenComparing(DeveloperTechCategory::getTechCodeId);

    private final DeveloperTechCategoryRepository repository;

    /** 살아 있는 기술 코드 식별자. 등록순입니다. */
    public List<Integer> listCodeIds(Integer developerId) {
        return repository.findByIdDeveloperId(developerId).stream()
                .filter(row -> !row.isDeleted())
                .sorted(REGISTRATION_ORDER)
                .map(DeveloperTechCategory::getTechCodeId)
                .toList();
    }

    /** 여러 개발자의 살아 있는 기술 코드 식별자. 개발자별로 등록순이며, 기술이 없는 개발자는 결과에 없습니다. */
    public Map<Integer, List<Integer>> listCodeIdsByDevelopers(Collection<Integer> developerIds) {
        if (developerIds == null || developerIds.isEmpty()) {
            return Map.of();
        }
        Map<Integer, List<Integer>> result = new HashMap<>();
        repository.findByIdDeveloperIdInAndDeletedFalse(developerIds).stream()
                .sorted(REGISTRATION_ORDER)
                .forEach(row -> result.computeIfAbsent(row.getId().getDeveloperId(), id -> new ArrayList<>())
                        .add(row.getTechCodeId()));
        return result;
    }

    @Transactional
    public void replaceAll(Integer developerId, List<Integer> codeIds, LocalDateTime now) {
        Set<Integer> wanted = new LinkedHashSet<>(codeIds);
        Map<Integer, DeveloperTechCategory> existing = repository.findByIdDeveloperId(developerId).stream()
                .collect(Collectors.toMap(DeveloperTechCategory::getTechCodeId, Function.identity()));
        for (Integer codeId : wanted) {
            DeveloperTechCategory row = existing.get(codeId);
            if (row == null) {
                repository.save(DeveloperTechCategory.of(developerId, codeId));
            } else if (row.isDeleted()) {
                row.reactivate();
            }
        }
        existing.values().stream()
                .filter(row -> !row.isDeleted() && !wanted.contains(row.getTechCodeId()))
                .forEach(row -> row.remove(now));
    }
}
