package com.a501.service.domain.report.repository;

import com.a501.service.domain.report.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, Integer> {
    /** 이 계약에 주어진 상태의 신고가 있는지. 지급 전에 해결되지 않은 신고({@code OPEN})를 볼 때 씁니다. */
    boolean existsByContractIdAndStatusAndDeletedFalse(Integer contractId, String status);
}
