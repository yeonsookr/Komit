package com.a501.service.domain.developer.service;

import com.a501.service.domain.developer.repository.DeveloperRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 리뷰가 들어올 때 개발자의 평점과 리뷰 수를 갱신합니다. */
@Service
@RequiredArgsConstructor
public class DeveloperRatingService {
    private final DeveloperRepository developerRepository;

    /**
     * 개발자 행을 잠그고 리뷰 한 건의 점수를 더합니다.
     *
     * <p>잠금은 부르는 쪽의 트랜잭션이 끝날 때 풀립니다. 같은 개발자에게 들어오는 리뷰가 한 줄로 서므로
     * 평균이 겹쳐 쓰이지 않습니다.
     */
    @Transactional
    public void addReview(Integer developerId, int devScore, int expScore) {
        developerRepository.findWithLockByIdAndDeletedFalse(developerId)
                .orElseThrow(() -> new IllegalStateException("리뷰를 받을 개발자를 찾을 수 없습니다."))
                .addReview(devScore, expScore);
    }
}
