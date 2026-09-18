package com.a501.service.domain.developer.repository;

import java.util.Optional;

import com.a501.service.domain.developer.entity.Developer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeveloperRepository extends JpaRepository<Developer, Integer> {
    Optional<Developer> findByUserIdAndDeletedFalse(Integer userId);
}
