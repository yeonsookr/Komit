package com.a501.service.domain.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.domain.contract.ContractErrorCode;
import com.a501.service.domain.contract.dto.ContractParties;
import com.a501.service.domain.contract.dto.ReviewableContract;
import com.a501.service.domain.contract.service.ContractQueryService;
import com.a501.service.domain.review.ReviewErrorCode;
import com.a501.service.domain.review.entity.Review;
import com.a501.service.domain.review.repository.ReviewRepository;
import com.a501.service.domain.user.service.UserService;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 계약에 남긴 리뷰는 그 계약의 두 당사자만 본다 (R-REV-02). */
class ReviewQueryServiceContractTest {
    private static final Integer CONTRACT_ID = 130;
    private static final Integer CLIENT_ID = 9001;
    private static final Integer DEVELOPER_USER_ID = 9101;

    private final ReviewRepository reviews = mock(ReviewRepository.class);
    private final UserService users = mock(UserService.class);
    private final ContractQueryService contracts = mock(ContractQueryService.class);
    private final ReviewQueryService service = new ReviewQueryService(reviews, users, contracts);

    @BeforeEach
    void setUp() {
        when(contracts.findReviewableContract(CONTRACT_ID)).thenReturn(Optional.of(new ReviewableContract(
                new ContractParties(CONTRACT_ID, CLIENT_ID, DEVELOPER_USER_ID), "CONSULTING", false, 70, false)));
        when(reviews.findByContractIdAndDeletedFalse(CONTRACT_ID))
                .thenReturn(Optional.of(Review.write(CONTRACT_ID, CLIENT_ID, 31, 5, 4, "좋았습니다.")));
        when(users.findMaskedNames(any())).thenReturn(Map.of(CLIENT_ID, "김O미"));
        when(contracts.findContractTypes(any())).thenReturn(Map.of(CONTRACT_ID, "CONSULTING"));
    }

    @Test
    @DisplayName("의뢰인은 자기가 남긴 리뷰를, 개발자는 받은 리뷰를 본다")
    void bothPartiesSeeTheReview() {
        assertThat(service.findByContract(CLIENT_ID, CONTRACT_ID).devScore()).isEqualTo(5);
        assertThat(service.findByContract(DEVELOPER_USER_ID, CONTRACT_ID).clientName()).isEqualTo("김O미");
    }

    @Test
    @DisplayName("당사자가 아니면 계약이 있다는 것도 알리지 않는다")
    void hidesFromStranger() {
        assertThatThrownBy(() -> service.findByContract(9999, CONTRACT_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ContractErrorCode.CONTRACT_NOT_FOUND);
    }

    @Test
    @DisplayName("아직 리뷰가 없으면 REVIEW_NOT_FOUND 다")
    void notYetWritten() {
        when(reviews.findByContractIdAndDeletedFalse(CONTRACT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByContract(CLIENT_ID, CONTRACT_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ReviewErrorCode.REVIEW_NOT_FOUND);
    }
}
