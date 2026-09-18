package com.a501.service.domain.recommendation.repository;

import com.a501.service.domain.recommendation.entity.RecommendationBatch;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationBatchRepository extends JpaRepository<RecommendationBatch, Integer> {}
