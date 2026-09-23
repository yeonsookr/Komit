package com.a501.service.domain.developer.service;

import com.a501.service.domain.developer.dto.MatchCandidate;
import com.a501.service.domain.developer.entity.Developer;
import com.a501.service.domain.developer.entity.DeveloperProfile;
import com.a501.service.domain.developer.repository.CareerRepository;
import com.a501.service.domain.developer.repository.DeveloperProfileRepository;
import com.a501.service.domain.developer.repository.DeveloperTechCategoryRepository;
import com.a501.service.domain.developer.repository.DeveloperRepository;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Set;
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
    private final DeveloperProfileRepository developerProfileRepository;
    private final DeveloperTechCategoryRepository developerTechCategoryRepository;
    private final CareerRepository careerRepository;

    /**
     * 추천 점수를 매길 개발자 전부. 점수 계산에 필요한 값만 담아 넘긴다.
     *
     * <p>모집단을 좁히지 않는다. 기술이 하나도 맞지 않아도 점수가 0 이 될 뿐이고, 몇 명을 견주었는지가 추천 회차의 기록이 된다. 개발자 수가 많아지면 그때 후보를 줄인다.
     */
    public List<MatchCandidate> findMatchCandidates() {
        List<Developer> developers = developerRepository.findAllByDeletedFalse();
        if (developers.isEmpty()) {
            return List.of();
        }

        List<Integer> ids = developers.stream().map(Developer::getId).toList();
        Map<Integer, Set<Integer>> techs = developerTechCategoryRepository
                .findByIdDeveloperIdInAndDeletedFalse(ids)
                .stream()
                .collect(Collectors.groupingBy(
                        tech -> tech.getId().getDeveloperId(),
                        Collectors.mapping(tech -> tech.getId().getTechCodeId(), Collectors.toSet())));
        Map<Integer, BigDecimal> years = developerProfileRepository
                .findByDeveloperIdInAndDeletedFalse(ids)
                .stream()
                .filter(profile -> profile.getYearsOfExperience() != null)
                .collect(Collectors.toMap(
                        DeveloperProfile::getDeveloperId, DeveloperProfile::getYearsOfExperience));
        Set<Integer> verified = Set.copyOf(careerRepository.findVerifiedDeveloperIds(ids));

        return developers.stream()
                .map(developer -> new MatchCandidate(
                        developer.getId(),
                        techs.getOrDefault(developer.getId(), Set.of()),
                        developer.getDevRating(),
                        developer.getExplanatoryRating(),
                        developer.getDevReviewCount() + developer.getExplanatoryReviewCount(),
                        years.get(developer.getId()) == null
                                ? null
                                : years.get(developer.getId()).doubleValue(),
                        verified.contains(developer.getId())))
                .toList();
    }

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
