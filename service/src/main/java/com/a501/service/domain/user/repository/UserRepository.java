package com.a501.service.domain.user.repository;

import java.util.Optional;

import com.a501.service.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Integer> {
    Optional<User> findByKakaoId(Long kakaoId);
}
