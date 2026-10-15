package com.a501.service.domain.review.service;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.CommonErrorCode;
import com.a501.service.domain.consulting.dto.SessionSchedule;
import com.a501.service.domain.consulting.service.ConsultingSessionQueryService;
import com.a501.service.domain.contract.ContractErrorCode;
import com.a501.service.domain.contract.dto.ReviewableContract;
import com.a501.service.domain.contract.service.ContractQueryService;
import com.a501.service.domain.developer.service.DeveloperLookupService;
import com.a501.service.domain.developer.service.DeveloperRatingService;
import com.a501.service.domain.review.ReviewErrorCode;
import com.a501.service.domain.review.dto.request.WriteReviewRequest;
import com.a501.service.domain.review.dto.response.ReviewResponse;
import com.a501.service.domain.review.entity.Review;
import com.a501.service.domain.review.repository.ReviewRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 끝난 거래에 의뢰인이 리뷰를 남기는 유스케이스({@code POST /api/contracts/{contractId}/review}).
 *
 * <p>유료 거래를 마친 의뢰인만 남기고, 계약 하나에 리뷰는 하나입니다. 컨설팅은 양쪽이 들어와 시작한 세션이
 * 끝나면 마친 것으로 봅니다. (R-REV-02, R-CON-12)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewWriteService {
    private static final String CONSULTING = "CONSULTING";

    private final ContractQueryService contractQueryService;
    private final ConsultingSessionQueryService sessionQueryService;
    private final DeveloperLookupService developerLookupService;
    private final DeveloperRatingService developerRatingService;
    private final ReviewRepository reviewRepository;
    private final ReviewQueryService reviewQueryService;

    /**
     * @throws BusinessException {@link ContractErrorCode#CONTRACT_NOT_FOUND} 없거나 내 계약이 아닐 때,
     *                           {@link CommonErrorCode#FORBIDDEN} 의뢰인이 아닐 때,
     *                           {@link ReviewErrorCode#CONTRACT_NOT_COMPLETED} 취소됐거나 거래를 마치지 않았을 때,
     *                           {@link ReviewErrorCode#REVIEW_ALREADY_WRITTEN} 이미 리뷰를 남겼을 때
     */
    @Transactional
    public ReviewResponse write(Integer userId, Integer contractId, WriteReviewRequest request) {
        ReviewableContract contract = contractQueryService.findReviewableContract(contractId)
                .filter(found -> found.parties().includes(userId))
                .orElseThrow(() -> new BusinessException(ContractErrorCode.CONTRACT_NOT_FOUND));
        if (!contract.parties().clientId().equals(userId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        if (contract.cancelled() || !isFinished(contract)) {
            throw new BusinessException(ReviewErrorCode.CONTRACT_NOT_COMPLETED);
        }

        Integer developerId = developerLookupService.findDeveloperId(contract.parties().developerId());
        if (developerId == null) {
            throw new IllegalStateException("리뷰를 받을 개발자를 찾을 수 없습니다.");
        }
        // 개발자 행을 먼저 잠급니다. 같은 계약에 두 번 들어와도 뒤의 것은 앞의 저장을 본 뒤에 확인합니다.
        developerRatingService.addReview(developerId, request.devScore(), request.expScore());
        if (reviewRepository.existsByContractIdAndDeletedFalse(contractId)) {
            throw new BusinessException(ReviewErrorCode.REVIEW_ALREADY_WRITTEN);
        }

        Review review = reviewRepository.save(Review.write(
                contractId, userId, developerId, request.devScore(), request.expScore(), blankToNull(request.content())));
        return reviewQueryService.toResponse(review);
    }

    private boolean isFinished(ReviewableContract contract) {
        if (!CONSULTING.equals(contract.contractType())) {
            return contract.developmentCompleted();
        }
        if (contract.consultingAgreementId() == null) {
            return false;
        }
        List<SessionSchedule> sessions = sessionQueryService.findSchedules(contract.consultingAgreementId());
        return !sessions.isEmpty() && sessions.stream().allMatch(SessionSchedule::ranToEnd);
    }

    private static String blankToNull(String content) {
        return content == null || content.isBlank() ? null : content.trim();
    }
}
