package com.a501.service.domain.user.repository;

import java.util.Optional;

import com.a501.service.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Integer> {
    Optional<User> findByKakaoId(Long kakaoId);

    /** 개발자 프로필 전체 치환을 사용자별로 직렬화하기 위한 잠금 조회. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") Integer userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update User u set u.recordStatus = 'PENDING' where u.id = :userId and u.recordStatus = 'ACTIVE'")
    int markSignupPending(@Param("userId") Integer userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update User u set u.recordStatus = 'ACTIVE' where u.id = :userId and u.recordStatus = 'PENDING' and u.deleted = false")
    int completeSignup(@Param("userId") Integer userId);
}
