package com.a501.service.domain.developer.service;

import com.a501.service.domain.developer.entity.Developer;
import com.a501.service.domain.developer.repository.DeveloperRepository;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeveloperLookupService {
    private final DeveloperRepository developerRepository;

    public Integer findDeveloperId(Integer userId) {
        return developerRepository.findByUserIdAndDeletedFalse(userId)
                .map(Developer::getId)
                .orElse(null);
    }

    /**
     * 개발자를 맡고 있는 사용자.
     *
     * <p><b>개발자 식별자와 사용자 식별자는 다릅니다.</b> {@code rooms.developer_id} 같은 값은
     * {@code developers.id} 라, 사람을 가리키려면 이 한 단계를 거쳐야 합니다.
     *
     * @param developerId 개발자 id
     * @return 사용자 id
     */
    public Optional<Integer> findUserId(Integer developerId) {
        return developerRepository.findById(developerId).map(Developer::getUserId);
    }

    /**
     * 개발자 식별자별 사용자 식별자. 없는 개발자는 결과에서 빠집니다.
     *
     * @param developerIds 개발자 id 목록
     * @return 개발자 id → 사용자 id
     */
    public Map<Integer, Integer> findUserIds(Collection<Integer> developerIds) {
        if (developerIds.isEmpty()) {
            return Map.of();
        }
        return developerRepository.findAllByIdInAndDeletedFalse(developerIds).stream()
                .collect(Collectors.toMap(Developer::getId, Developer::getUserId));
    }
}
