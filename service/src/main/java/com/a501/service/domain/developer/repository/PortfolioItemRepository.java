package com.a501.service.domain.developer.repository;

import com.a501.service.domain.developer.entity.PortfolioItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioItemRepository extends JpaRepository<PortfolioItem, Integer> {}
