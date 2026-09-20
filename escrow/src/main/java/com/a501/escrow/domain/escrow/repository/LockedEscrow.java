package com.a501.escrow.domain.escrow.repository;

import com.a501.escrow.domain.common.Money;

public record LockedEscrow(
        long id,
        long serviceClientId,
        String externalContractId,
        Money expectedAmount,
        String status,
        String holdStatus,
        String externalReportId
) {
}
