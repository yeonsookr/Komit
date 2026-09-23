package com.a501.service.domain.recommendation.repository;

import com.a501.service.domain.recommendation.entity.RecommendationBatch;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationBatchRepository extends JpaRepository<RecommendationBatch, Integer> {
    Optional<RecommendationBatch> findByIdAndDeletedFalse(Integer id);

    /** 이 프로젝트의 마지막 회차. 열어보지 않았으면 새 회차를 만들지 않고 이것을 돌려준다. */
    Optional<RecommendationBatch> findFirstByProjectIdAndDeletedFalseOrderBySequenceNoDesc(Integer projectId);
}
