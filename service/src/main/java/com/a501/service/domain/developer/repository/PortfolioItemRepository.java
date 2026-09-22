package com.a501.service.domain.developer.repository;

import com.a501.service.domain.developer.entity.PortfolioItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioItemRepository extends JpaRepository<PortfolioItem, Integer> {
    /** 개발자의 살아 있는 작업물. 등록순({@code id} 오름차순)이며 순서 변경은 없습니다. */
    List<PortfolioItem> findByDeveloperIdAndDeletedFalseOrderByIdAsc(Integer developerId);
}
