package com.a501.escrow.domain.escrow.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigInteger;

import com.a501.escrow.common.api.EscrowHeaderFactory;
import com.a501.escrow.common.api.EscrowRequestHeader;
import com.a501.escrow.common.api.EscrowResponseHeader;
import com.a501.escrow.domain.escrow.dto.EscrowSettlementDtos;
import com.a501.escrow.domain.escrow.service.EscrowSettlementService;
import org.junit.jupiter.api.Test;

class EscrowSettlementControllerTest {
    @Test
    void delegatesReleaseAndRefundAndBuildsSuccessHeaders() {
        EscrowSettlementService service = mock(EscrowSettlementService.class);
        EscrowHeaderFactory headers = mock(EscrowHeaderFactory.class);
        EscrowSettlementController controller = new EscrowSettlementController(service, headers);
        EscrowRequestHeader releaseHeader = header("releaseEscrow", "release-key");
        EscrowRequestHeader refundHeader = header("refundEscrow", "refund-key");
        EscrowResponseHeader responseHeader = new EscrowResponseHeader(
                "H0000", "ok", "releaseEscrow", "20260920", "120000",
                "00100", "001", "releaseEscrow", "20260920120000000001");
        var releaseRequest = new EscrowSettlementDtos.ReleaseRequest(releaseHeader, 1, "SETTLEMENT:1",
                BigInteger.valueOf(100_000), "001", "0011111111111111", "worker");
        var refundRequest = new EscrowSettlementDtos.RefundRequest(refundHeader, 1, BigInteger.valueOf(50_000),
                "001", "0012222222222222", "payer", "SETTLEMENT:1");
        var releaseRecord = new EscrowSettlementDtos.ReleaseRecord(2, BigInteger.valueOf(100_000),
                BigInteger.valueOf(1_000), BigInteger.valueOf(99_000), BigInteger.valueOf(50_000), "COMPLETED");
        var refundRecord = new EscrowSettlementDtos.RefundRecord(3, BigInteger.valueOf(50_000),
                BigInteger.ZERO, "COMPLETED");
        when(headers.success(releaseHeader)).thenReturn(responseHeader);
        when(headers.success(refundHeader)).thenReturn(responseHeader);
        when(service.release(releaseRequest)).thenReturn(releaseRecord);
        when(service.refund(refundRequest)).thenReturn(refundRecord);

        assertThat(controller.release(releaseRequest).record()).isEqualTo(releaseRecord);
        assertThat(controller.refund(refundRequest).record()).isEqualTo(refundRecord);
    }

    @Test
    void rejectsAmountsOutsideNumericNineteenDigitRange() {
        assertThatThrownBy(() -> new com.a501.escrow.domain.escrow.dto.EscrowDtos.CreateRequest(
                header("createEscrow", "create-key"), "CONTRACT-1", "DEVELOPMENT", "payer",
                new BigInteger("10000000000000000000"), "001", "0011111111111111"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static EscrowRequestHeader header(String apiName, String key) {
        return new EscrowRequestHeader(apiName, "20260920", "120000", "00100", "001", apiName,
                "20260920120000000001", "client-key", key, null);
    }
}
