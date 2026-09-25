package com.a501.service.domain.mypage.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 목 명세 {@code WithdrawalCheckResponse}와 같습니다. {@code canWithdraw}는 저장하지 않고 {@code blockers}로 판정합니다. */
@Schema(description = "탈퇴할 수 있는지.")
public record WithdrawalCheckResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "지금 탈퇴할 수 있는지. 저장된 값이 아니라 아래 `blockers`가 비었는지로 판정해 만드는 값이며, "
                + "거짓이면 화면이 탈퇴 버튼을 잠근다.", example = "false") boolean canWithdraw,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "정리해야 할 것. 계약·예치금·소득을 훑어 그때그때 만드는 목록이라 저장되지 않는다. "
                + "없으면 빈 배열이고 이때 `canWithdraw`가 참이다.\n\n"
                + "계약 식별자 순으로 늘어놓고, 한 계약 안에서는 CONTRACT, DEPOSIT, INCOME 순이다. "
                + "잔액이 0인 계약은 DEPOSIT·INCOME 줄을 만들지 않는다.") List<WithdrawalBlockerResponse> blockers) {

    public static WithdrawalCheckResponse of(List<WithdrawalBlockerResponse> blockers) {
        return new WithdrawalCheckResponse(blockers.isEmpty(), List.copyOf(blockers));
    }
}
