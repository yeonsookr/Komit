package com.a501.service.domain.developer.repository;

import com.a501.service.domain.developer.entity.DeveloperProfile;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeveloperProfileRepository extends JpaRepository<DeveloperProfile, Integer> {
    Optional<DeveloperProfile> findByDeveloperIdAndDeletedFalse(Integer developerId);

    /** 여러 개발자의 프로필. 개발자마다 따로 읽으면 개발자 수만큼 쿼리가 늘어난다. */
    List<DeveloperProfile> findByDeveloperIdInAndDeletedFalse(Collection<Integer> developerIds);
}
