package com.a501.service.domain.user.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.a501.service.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Integer> {
    Optional<User> findByKakaoId(Long kakaoId);

    /**
     * 표시 이름에 검색어가 들어 있는 살아 있는 사용자. 개발자 찾기의 이름 검색이 씁니다.
     * 선행 와일드카드라 인덱스를 타지 않지만, 이름 검색은 부분 일치가 계약이라 피할 수 없습니다. (BACKEND_CONVENTIONS 13.3)
     */
    List<User> findByNameContainingAndDeletedFalse(String keyword);

    /** 개발자 프로필 전체 치환을 사용자별로 직렬화하기 위한 잠금 조회. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") Integer userId);

    /**
     * 기준 시각 이전에 탈퇴했는데 아직 식별 정보를 지우지 않은 사용자. 지우면 {@code kakaoId}가 음수가 되어 다시 걸리지 않습니다. (R-ACC-06)
     * 한 번에 {@code pageable} 크기만큼 식별자 순으로 가져옵니다.
     */
    @Query("""
            select u.id from User u
            where u.deleted = true and u.deletedAt <= :cutoff and u.kakaoId > 0
            order by u.id
            """)
    List<Integer> findAnonymizationTargets(@Param("cutoff") LocalDateTime cutoff, Pageable pageable);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update User u set u.recordStatus = 'PENDING' where u.id = :userId and u.recordStatus = 'ACTIVE'")
    int markSignupPending(@Param("userId") Integer userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update User u set u.recordStatus = 'ACTIVE' where u.id = :userId and u.recordStatus = 'PENDING' and u.deleted = false")
    int completeSignup(@Param("userId") Integer userId);
}
