package com.a501.service.domain.contract.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigInteger;
import java.time.OffsetDateTime;

/**
 * 예치 한 건.
 *
 * <p>돈의 원장은 이 서버가 갖지 않습니다. 금액과 상태는 Escrow 에 물어 그대로 옮깁니다.
 * (`R-PAY-05`)
 */
@Schema(description = "계약 금액 예치.")
public record DepositResponse(
        @Schema(description = "예치를 건 계약.", example = "130") Integer contractId,
        @Schema(description = "예치한 금액. 원 단위다.", example = "650000") BigInteger amount,
        @Schema(
                        description = "예치 상태. 결제가 바로 확인되면 DEPOSITED, 결과를 기다리는 중이면 PENDING 이다.",
                        example = "DEPOSITED")
                String status,
        @Schema(description = "예치가 확인된 시각. 아직 기다리는 중이면 null 이다.")
                OffsetDateTime depositedAt) {}
