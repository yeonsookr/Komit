package com.a501.service.domain.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.CommonErrorCode;
import com.a501.service.domain.developer.service.DeveloperLookupService;
import com.a501.service.domain.review.ReviewErrorCode;
import com.a501.service.domain.review.dto.response.ReviewResponse;
import com.a501.service.domain.review.entity.Review;
import com.a501.service.domain.review.repository.ReviewRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** 답글 저장의 규칙(404 조건, 토큰 주인 대조, 덮어쓰기)을 DB 없이 검증합니다. */
@ExtendWith(MockitoExtension.class)
class ReviewCommandServiceTest {
    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private DeveloperLookupService developerLookupService;
    @Mock
    private ReviewQueryService reviewQueryService;
    @InjectMocks
    private ReviewCommandService service;

    private static Review review(int id, int developerId) {
        Review review = newReview();
        ReflectionTestUtils.setField(review, "id", id);
        ReflectionTestUtils.setField(review, "developerId", developerId);
        ReflectionTestUtils.setField(review, "clientId", 12);
        ReflectionTestUtils.setField(review, "contractId", 120);
        ReflectionTestUtils.setField(review, "devScore", 5);
        ReflectionTestUtils.setField(review, "expScore", 5);
        return review;
    }

    private static Review newReview() {
        try {
            var constructor = Review.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Test
    @DisplayName("토큰 주인이 받은 리뷰면 답글을 덮어쓰고 응답으로 돌려준다")
    void replyOverwritesOwnReview() {
        Review review = review(88, 3);
        review.reply("이전 답글");
        when(reviewRepository.findByIdAndDeletedFalse(88)).thenReturn(Optional.of(review));
        when(developerLookupService.findDeveloperId(12)).thenReturn(3);
        ReviewResponse expected = new ReviewResponse(88, "김O미", "DEVELOPMENT", 5, 5, null, "감사합니다.",
                OffsetDateTime.parse("2026-08-19T14:20:00+09:00"));
        when(reviewQueryService.toResponse(review)).thenReturn(expected);

        assertThat(service.reply(12, 88, "감사합니다.")).isEqualTo(expected);
        assertThat(review.getReply()).isEqualTo("감사합니다.");
    }

    @Test
    @DisplayName("없거나 삭제된 리뷰는 REVIEW_NOT_FOUND이고 개발자 확인 전에 끝난다")
    void missingReviewIsNotFound() {
        when(reviewRepository.findByIdAndDeletedFalse(404)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reply(12, 404, "x"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ReviewErrorCode.REVIEW_NOT_FOUND));
        verify(developerLookupService, never()).findDeveloperId(any());
    }

    @Test
    @DisplayName("남이 받은 리뷰이거나 개발자 프로필이 없으면 FORBIDDEN이고 답글은 바뀌지 않는다")
    void foreignReviewIsForbidden() {
        Review review = review(87, 3);
        when(reviewRepository.findByIdAndDeletedFalse(87)).thenReturn(Optional.of(review));
        when(developerLookupService.findDeveloperId(12)).thenReturn(5);
        when(developerLookupService.findDeveloperId(13)).thenReturn(null);

        for (int userId : new int[] {12, 13}) {
            assertThatThrownBy(() -> service.reply(userId, 87, "x"))
                    .isInstanceOfSatisfying(BusinessException.class,
                            exception -> assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));
        }
        assertThat(review.getReply()).isNull();
        verify(reviewQueryService, never()).toResponse(any());
    }
}
