package com.a501.service.domain.report.repository;

import com.a501.service.domain.report.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, Integer> {}
