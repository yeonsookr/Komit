package com.a501.service.domain.user.repository;

import java.util.Optional;

import com.a501.service.domain.user.entity.User;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Integer> {
    Optional<User> findByKakaoId(Long kakaoId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update User u set u.recordStatus = 'PENDING' where u.id = :userId and u.recordStatus = 'ACTIVE'")
    int markSignupPending(@Param("userId") Integer userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update User u set u.recordStatus = 'ACTIVE' where u.id = :userId and u.recordStatus = 'PENDING' and u.deleted = false")
    int completeSignup(@Param("userId") Integer userId);
}
