package com.a501.service.domain.defaults.repository;

import java.util.Optional;

import com.a501.service.domain.defaults.entity.Default;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 예시 레코드 저장소. 단순 조회는 Spring Data 쿼리 메서드로, 정렬·페이지가 조합되는 목록은
 * {@link DefaultRepositoryCustom}(QueryDSL)로 나눕니다. (BACKEND_CONVENTIONS 13.3)
 */
public interface DefaultRepository extends JpaRepository<Default, Integer>, DefaultRepositoryCustom {

    /**
     * 소프트 삭제되지 않은 레코드 한 건을 찾습니다.
     *
     * @param id 레코드 id
     * @return 살아 있는 레코드. 없거나 삭제되었으면 비어 있습니다.
     */
    Optional<Default> findByIdAndDeletedFalse(Integer id);
}
