package com.a501.service.domain.developer.repository;

import com.a501.service.domain.developer.entity.PriceCatalog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceCatalogRepository extends JpaRepository<PriceCatalog, Integer> {
    /** 개발자의 살아 있는 가격표. 컨설팅({@code CONSULT})이 개발({@code DEVELOP})보다 먼저 오고, 그 안에서 싼 것부터입니다. */
    List<PriceCatalog> findByDeveloperIdAndDeletedFalseOrderByTaskTypeAscPriceAscIdAsc(Integer developerId);
}
