package com.a501.service.domain.review.repository;

import com.a501.service.domain.review.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Integer> {
    /** 개발자가 받은 리뷰. 새로운 것이 먼저입니다. */
    List<Review> findByDeveloperIdAndDeletedFalseOrderByIdDesc(Integer developerId);
}
