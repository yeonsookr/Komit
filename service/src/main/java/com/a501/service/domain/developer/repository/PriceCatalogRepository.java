package com.a501.service.domain.developer.repository;

import com.a501.service.domain.developer.dto.LowestPriceView;
import com.a501.service.domain.developer.entity.PriceCatalog;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PriceCatalogRepository extends JpaRepository<PriceCatalog, Integer> {
    /** 개발자의 살아 있는 가격표. 컨설팅({@code CONSULT})이 개발({@code DEVELOP})보다 먼저 오고, 그 안에서 싼 것부터입니다. */
    List<PriceCatalog> findByDeveloperIdAndDeletedFalseOrderByTaskTypeAscPriceAscIdAsc(Integer developerId);

    /** 여러 개발자의 작업 종류별 최저가. 목록 카드의 '컨설팅 N원부터 · 개발 M원부터'를 한 번의 조회로 채우기 위한 것입니다. */
    @Query("""
            select price.developerId as developerId, price.taskType as taskType, min(price.price) as lowestPrice
            from PriceCatalog price
            where price.developerId in :developerIds and price.deleted = false
            group by price.developerId, price.taskType
            order by price.developerId asc, price.taskType asc
            """)
    List<LowestPriceView> findLowestPrices(@Param("developerIds") Collection<Integer> developerIds);
}
