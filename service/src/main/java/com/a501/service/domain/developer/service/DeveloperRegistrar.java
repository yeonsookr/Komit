package com.a501.service.domain.developer.service;

import com.a501.service.domain.developer.entity.Developer;
import com.a501.service.domain.developer.repository.DeveloperRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 개발자 행 생성을 별도 트랜잭션으로 떼어 둔 것입니다. 같은 사용자의 첫 저장 요청이 두 인스턴스에 동시에 닿으면
 * {@code developers.user_id} unique 제약이 한쪽을 거절하는데, PostgreSQL은 제약 위반이 난 트랜잭션을 더 쓸 수 없으므로
 * 바깥 트랜잭션을 살려 두고 여기서만 실패하게 합니다. 거절된 쪽은 바깥에서 다시 조회해 수정 경로로 갑니다. (BACKEND_CONVENTIONS 6.5)
 */
@Service
@RequiredArgsConstructor
public class DeveloperRegistrar {
    private final DeveloperRepository developerRepository;

    /**
     * @return 새로 만든 개발자 id
     * @throws org.springframework.dao.DataIntegrityViolationException 같은 사용자의 개발자 행이 이미 있을 때
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Integer register(Integer userId) {
        return developerRepository.saveAndFlush(Developer.register(userId)).getId();
    }
}
