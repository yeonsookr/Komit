package com.a501.service.domain.report.service;

import com.a501.service.domain.report.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 다른 도메인이 신고 엔티티 없이 신고 여부만 묻는 창구. (BACKEND_CONVENTIONS 7.1) */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportQueryService {
    /** 접수되고 아직 종결되지 않은 신고. {@code REPORT_STATUS} 코드값입니다. */
    private static final String OPEN = "OPEN";

    private final ReportRepository reportRepository;

    /**
     * 이 계약에 해결되지 않은 신고가 있는지.
     *
     * <p>있으면 자동 지급을 하지 않습니다. 신고가 종결될 때까지 대금은 예치금에 남습니다.
     * (`R-CON-11`, `R-QA-02`, `R-RPT-03`)
     *
     * @param contractId 계약 id
     */
    public boolean hasOpenReport(Integer contractId) {
        return reportRepository.existsByContractIdAndStatusAndDeletedFalse(contractId, OPEN);
    }
}
