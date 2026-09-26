package com.a501.service.domain.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.CommonErrorCode;
import com.a501.service.domain.consulting.dto.SessionSchedule;
import com.a501.service.domain.consulting.service.ConsultingSessionQueryService;
import com.a501.service.domain.contract.ContractErrorCode;
import com.a501.service.domain.contract.dto.ContractParties;
import com.a501.service.domain.contract.dto.ReviewableContract;
import com.a501.service.domain.contract.service.ContractQueryService;
import com.a501.service.domain.developer.service.DeveloperLookupService;
import com.a501.service.domain.developer.service.DeveloperRatingService;
import com.a501.service.domain.review.ReviewErrorCode;
import com.a501.service.domain.review.dto.request.WriteReviewRequest;
import com.a501.service.domain.review.entity.Review;
import com.a501.service.domain.review.repository.ReviewRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** 리뷰는 끝난 거래의 의뢰인만, 계약 하나에 한 번 남긴다 (R-REV-02). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReviewWriteServiceTest {
    private static final Integer CONTRACT_ID = 130;
    private static final Integer CLIENT_ID = 9001;
    private static final Integer DEVELOPER_USER_ID = 9101;
    private static final Integer DEVELOPER_ID = 31;
    private static final Integer AGREEMENT_ID = 70;
    private static final WriteReviewRequest REQUEST = new WriteReviewRequest(5, 4, "  말이 잘 통했습니다. ");
    private static final LocalDateTime AT = LocalDateTime.of(2026, 8, 29, 5, 0);

    @Mock
    private ContractQueryService contractQueryService;
    @Mock
    private ConsultingSessionQueryService sessionQueryService;
    @Mock
    private DeveloperLookupService developerLookupService;
    @Mock
    private DeveloperRatingService developerRatingService;
    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private ReviewQueryService reviewQueryService;

    @InjectMocks
    private ReviewWriteService service;

    @BeforeEach
    void setUp() {
        consulting(false);
        sessions(new SessionSchedule(80, AT, 30, AT, AT.plusMinutes(32)));
        when(developerLookupService.findDeveloperId(DEVELOPER_USER_ID)).thenReturn(DEVELOPER_ID);
        when(reviewRepository.save(any(Review.class))).thenAnswer(call -> call.getArgument(0));
    }

    private void consulting(boolean cancelled) {
        when(contractQueryService.findReviewableContract(CONTRACT_ID)).thenReturn(Optional.of(new ReviewableContract(
                new ContractParties(CONTRACT_ID, CLIENT_ID, DEVELOPER_USER_ID), "CONSULTING", cancelled, AGREEMENT_ID, false)));
    }

    private void sessions(SessionSchedule... schedules) {
        when(sessionQueryService.findSchedules(AGREEMENT_ID)).thenReturn(List.of(schedules));
    }

    private void assertRejected(Integer userId, Object errorCode) {
        assertThatThrownBy(() -> service.write(userId, CONTRACT_ID, REQUEST))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", errorCode);
        verify(reviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("시작한 세션이 끝난 컨설팅이면 리뷰를 남기고 개발자 평점에 더한다")
    void writesReviewForFinishedConsulting() {
        service.write(CLIENT_ID, CONTRACT_ID, REQUEST);

        ArgumentCaptor<Review> saved = ArgumentCaptor.forClass(Review.class);
        verify(reviewRepository).save(saved.capture());
        assertThat(saved.getValue().getDeveloperId()).isEqualTo(DEVELOPER_ID);
        assertThat(saved.getValue().getClientId()).isEqualTo(CLIENT_ID);
        assertThat(saved.getValue().getContent()).isEqualTo("말이 잘 통했습니다.");
        verify(developerRatingService).addReview(DEVELOPER_ID, 5, 4);
    }

    @Test
    @DisplayName("아무도 들어오지 않고 종료만 눌린 세션은 끝난 거래가 아니다 (R-CON-12)")
    void rejectsSessionThatNeverStarted() {
        sessions(new SessionSchedule(80, AT, 30, null, AT.plusMinutes(30)));

        assertRejected(CLIENT_ID, ReviewErrorCode.CONTRACT_NOT_COMPLETED);
        verify(developerRatingService, never()).addReview(any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("세션이 아직 끝나지 않았으면 남기지 않는다")
    void rejectsOngoingSession() {
        sessions(new SessionSchedule(80, AT, 30, AT, null));

        assertRejected(CLIENT_ID, ReviewErrorCode.CONTRACT_NOT_COMPLETED);
    }

    @Test
    @DisplayName("취소된 계약에는 남기지 않는다")
    void rejectsCancelledContract() {
        consulting(true);

        assertRejected(CLIENT_ID, ReviewErrorCode.CONTRACT_NOT_COMPLETED);
    }

    @Test
    @DisplayName("리뷰는 의뢰인만 남긴다")
    void onlyClientWrites() {
        assertRejected(DEVELOPER_USER_ID, CommonErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("당사자가 아니면 계약이 있다는 것도 알리지 않는다")
    void hidesContractFromStranger() {
        assertRejected(9999, ContractErrorCode.CONTRACT_NOT_FOUND);
    }

    @Test
    @DisplayName("이미 남긴 계약에는 다시 남기지 않는다")
    void rejectsSecondReview() {
        when(reviewRepository.existsByContractIdAndDeletedFalse(CONTRACT_ID)).thenReturn(true);

        assertRejected(CLIENT_ID, ReviewErrorCode.REVIEW_ALREADY_WRITTEN);
    }

    @Test
    @DisplayName("개발 계약은 개발 계약이 끝나야 남긴다")
    void developmentNeedsCompletedContract() {
        when(contractQueryService.findReviewableContract(CONTRACT_ID)).thenReturn(Optional.of(new ReviewableContract(
                new ContractParties(CONTRACT_ID, CLIENT_ID, DEVELOPER_USER_ID), "DEVELOPMENT", false, null, false)));

        assertRejected(CLIENT_ID, ReviewErrorCode.CONTRACT_NOT_COMPLETED);
        verify(sessionQueryService, never()).findSchedules(any());
    }
}
