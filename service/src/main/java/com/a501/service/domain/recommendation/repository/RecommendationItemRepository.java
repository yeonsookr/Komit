package com.a501.service.domain.recommendation.repository;

import com.a501.service.domain.recommendation.entity.RecommendationItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationItemRepository extends JpaRepository<RecommendationItem, Integer> {
    List<RecommendationItem> findByBatchIdAndDeletedFalseOrderByRankAsc(Integer batchId);

    Optional<RecommendationItem> findByIdAndDeletedFalse(Integer id);
}
