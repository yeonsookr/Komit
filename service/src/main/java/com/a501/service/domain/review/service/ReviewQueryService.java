package com.a501.service.domain.review.service;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.domain.contract.ContractErrorCode;
import com.a501.service.domain.contract.service.ContractQueryService;
import com.a501.service.domain.review.ReviewErrorCode;
import com.a501.service.domain.review.dto.response.ReviewListResponse;
import com.a501.service.domain.review.dto.response.ReviewResponse;
import com.a501.service.domain.review.entity.Review;
import com.a501.service.domain.review.repository.ReviewRepository;
import com.a501.service.domain.user.service.UserService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 개발자가 받은 리뷰 조회. 작성자 이름 가리기와 계약 유형은 각 도메인의 서비스에 묻고 여기서 합칩니다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewQueryService {
    /** 최신순. 리뷰는 한 번 쓰면 고치지 않으므로 id 내림차순이 작성 시각 내림차순과 같습니다. */
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "id");

    private final ReviewRepository reviewRepository;
    private final UserService userService;
    private final ContractQueryService contractQueryService;

    /** 개발자가 받은 리뷰 전부. 새로운 것이 먼저입니다. 내 프로필 편집 화면이 씁니다. */
    public List<ReviewResponse> findByDeveloper(Integer developerId) {
        return toResponses(reviewRepository.findByDeveloperIdAndDeletedFalseOrderByIdDesc(developerId));
    }

    /**
     * 개발자가 받은 리뷰를 페이지로. 새로운 것이 먼저이며 계약의 {@code page}는 1부터입니다. 공개 프로필의 리뷰 칸이 씁니다.
     * 마지막 페이지를 넘어선 요청은 오류가 아니라 빈 목록입니다.
     */
    public ReviewListResponse findPageByDeveloper(Integer developerId, int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size, NEWEST_FIRST);
        Page<Review> found = reviewRepository.findByDeveloperIdAndDeletedFalse(developerId, pageable);
        return ReviewListResponse.of(new PageImpl<>(toResponses(found.getContent()), pageable, found.getTotalElements()), page);
    }

    public int countByDeveloper(Integer developerId) {
        return Math.toIntExact(reviewRepository.countByDeveloperIdAndDeletedFalse(developerId));
    }

    /**
     * 이 계약에 남긴 리뷰. 의뢰인은 자기가 남긴 것을, 개발자는 받은 것을 봅니다. 계약 하나에 리뷰는 하나입니다. (R-REV-02)
     *
     * @throws BusinessException {@link ContractErrorCode#CONTRACT_NOT_FOUND} 없거나 내 계약이 아닐 때,
     *                           {@link ReviewErrorCode#REVIEW_NOT_FOUND} 아직 리뷰가 없을 때
     */
    public ReviewResponse findByContract(Integer userId, Integer contractId) {
        contractQueryService.findReviewableContract(contractId)
                .filter(contract -> contract.parties().includes(userId))
                .orElseThrow(() -> new BusinessException(ContractErrorCode.CONTRACT_NOT_FOUND));
        return reviewRepository.findByContractIdAndDeletedFalse(contractId)
                .map(this::toResponse)
                .orElseThrow(() -> new BusinessException(ReviewErrorCode.REVIEW_NOT_FOUND));
    }

    /** 리뷰 하나를 계약 응답으로. 답글을 단 직후처럼 엔티티를 들고 있을 때 씁니다. */
    public ReviewResponse toResponse(Review review) {
        return toResponses(List.of(review)).get(0);
    }

    /** 순서를 지키며 응답으로 바꿉니다. 작성자 이름과 계약 유형은 도메인마다 한 번씩만 묻습니다. */
    private List<ReviewResponse> toResponses(List<Review> reviews) {
        if (reviews.isEmpty()) {
            return List.of();
        }
        Set<Integer> clientIds = reviews.stream().map(Review::getClientId).collect(Collectors.toSet());
        Set<Integer> contractIds = reviews.stream().map(Review::getContractId).collect(Collectors.toSet());
        Map<Integer, String> names = userService.findMaskedNames(clientIds);
        Map<Integer, String> types = contractQueryService.findContractTypes(contractIds);
        return reviews.stream()
                .map(review -> ReviewResponse.of(review, names.get(review.getClientId()), types.get(review.getContractId())))
                .toList();
    }
}
