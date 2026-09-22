package com.a501.service.domain.review.service;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.CommonErrorCode;
import com.a501.service.domain.developer.service.DeveloperLookupService;
import com.a501.service.domain.review.ReviewErrorCode;
import com.a501.service.domain.review.dto.response.ReviewResponse;
import com.a501.service.domain.review.entity.Review;
import com.a501.service.domain.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 개발자가 받은 리뷰에 답글을 남기는 유스케이스({@code PUT /api/reviews/{reviewId}/reply}).
 *
 * <p>저장되는 곳은 토큰 주인의 리뷰입니다. 컨트롤러가 넘긴 사용자 식별자로 개발자 도메인에 개발자 식별자를 물어 리뷰의 개발자와 대조하고,
 * 다르면 저장하지 않습니다. 리뷰는 공개 프로필에 이미 나가는 리소스라 존재를 감출 이유가 없어 404가 아니라 403입니다.
 * (BACKEND_CONVENTIONS 12.1)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewCommandService {
    private final ReviewRepository reviewRepository;
    private final DeveloperLookupService developerLookupService;
    private final ReviewQueryService reviewQueryService;

    /**
     * 답글을 달거나 고칩니다. 리뷰 하나에 답글은 하나뿐이라 다시 보내면 덮어씁니다. (R-REV-03)
     *
     * @throws BusinessException {@link ReviewErrorCode#REVIEW_NOT_FOUND} 리뷰가 없거나 삭제되었을 때,
     *                           {@code FORBIDDEN} 토큰 주인이 받은 리뷰가 아닐 때(개발자 프로필이 없는 경우 포함)
     */
    @Transactional
    public ReviewResponse reply(Integer userId, Integer reviewId, String reply) {
        Review review = reviewRepository.findByIdAndDeletedFalse(reviewId)
                .orElseThrow(() -> new BusinessException(ReviewErrorCode.REVIEW_NOT_FOUND));
        Integer developerId = developerLookupService.findDeveloperId(userId);
        if (developerId == null || !developerId.equals(review.getDeveloperId())) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        review.reply(reply);
        return reviewQueryService.toResponse(review);
    }
}
