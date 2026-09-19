package com.a501.service.domain.developer.repository;

import com.a501.service.domain.developer.entity.DeveloperProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeveloperProfileRepository extends JpaRepository<DeveloperProfile, Integer> {}
