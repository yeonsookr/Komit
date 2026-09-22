package com.a501.service.domain.review.service;

import com.a501.service.domain.contract.service.ContractQueryService;
import com.a501.service.domain.review.dto.response.ReviewResponse;
import com.a501.service.domain.review.entity.Review;
import com.a501.service.domain.review.repository.ReviewRepository;
import com.a501.service.domain.user.service.UserService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 개발자가 받은 리뷰 조회. 작성자 이름 가리기와 계약 유형은 각 도메인의 서비스에 묻고 여기서 합칩니다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewQueryService {
    private final ReviewRepository reviewRepository;
    private final UserService userService;
    private final ContractQueryService contractQueryService;

    /** 개발자가 받은 리뷰 전부. 새로운 것이 먼저입니다. */
    public List<ReviewResponse> findByDeveloper(Integer developerId) {
        List<Review> reviews = reviewRepository.findByDeveloperIdAndDeletedFalseOrderByIdDesc(developerId);
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

    public int countByDeveloper(Integer developerId) {
        return Math.toIntExact(reviewRepository.countByDeveloperIdAndDeletedFalse(developerId));
    }
}
