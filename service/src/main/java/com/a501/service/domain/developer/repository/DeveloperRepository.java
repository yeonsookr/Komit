package com.a501.service.domain.developer.repository;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.a501.service.domain.developer.entity.Developer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

/**
 * 개발자 저장소. 단순 조회는 Spring Data 쿼리 메서드로, 조건·정렬·페이지가 조합되는 공개 목록은
 * {@link DeveloperRepositoryCustom}(QueryDSL)으로 나눕니다. (BACKEND_CONVENTIONS 13.3)
 */
public interface DeveloperRepository extends JpaRepository<Developer, Integer>, DeveloperRepositoryCustom {
    Optional<Developer> findByUserIdAndDeletedFalse(Integer userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Developer> findWithLockByIdAndDeletedFalse(Integer id);

    List<Developer> findAllByIdInAndDeletedFalse(Collection<Integer> ids);

    /** 추천 점수를 매길 모집단. 개발자 수가 많아지면 후보를 줄이는 조건을 여기 건다. */
    List<Developer> findAllByDeletedFalse();
}
