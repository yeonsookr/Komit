package com.a501.service.domain.review.repository;

import com.a501.service.domain.review.entity.Review;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Integer> {
    boolean existsByContractIdAndDeletedFalse(Integer contractId);

    Optional<Review> findByContractIdAndDeletedFalse(Integer contractId);

    /** 개발자가 받은 리뷰. 새로운 것이 먼저입니다. */
    List<Review> findByDeveloperIdAndDeletedFalseOrderByIdDesc(Integer developerId);

    /** 개발자가 받은 리뷰를 페이지로. 정렬은 호출하는 쪽이 {@code Pageable}에 담습니다(최신순은 id 내림차순). */
    Page<Review> findByDeveloperIdAndDeletedFalse(Integer developerId, Pageable pageable);

    long countByDeveloperIdAndDeletedFalse(Integer developerId);

    Optional<Review> findByIdAndDeletedFalse(Integer id);
}
