package com.a501.service.domain.recommendation.repository;

import com.a501.service.domain.recommendation.entity.RecommendationItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationItemRepository extends JpaRepository<RecommendationItem, Integer> {}
