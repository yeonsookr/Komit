package com.a501.service.domain.mypage.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** 목 명세 {@code WithdrawalBlockerResponse}와 필드 순서까지 같습니다. */
@Schema(description = "탈퇴 전에 정리해야 할 것 한 줄.")
public record WithdrawalBlockerResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "정리 대상. CONTRACT는 진행 중인 의뢰, DEPOSIT은 남은 예치금, INCOME은 아직 받지 못한 소득이다.",
                example = "CONTRACT") Type type,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "화면에 보여줄 줄. 갈래마다 모양이 정해져 있다.\n\n"
                + "- CONTRACT: `{프로젝트 이름} · 진행 중인 의뢰`\n"
                + "- DEPOSIT: `예치금 {금액}원` — 내가 의뢰인인 계약의 에스크로 잔액\n"
                + "- INCOME: `못 받은 소득 {금액}원` — 내가 개발자인 계약의 에스크로 잔액\n\n"
                + "금액은 세 자리마다 쉼표를 찍는다.",
                example = "수제 비누 가게 · 진행 중인 의뢰") String title,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "금액. 돈이 아닌 항목이면 null이다.") Long amount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "'이동'을 눌렀을 때 갈 **계약**의 식별자. 갈 곳이 없으면 null이다.", example = "130")
                Integer targetId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "**방**의 식별자다. 바로 위 `targetId`는 계약의 식별자이므로 서로 다른 값이다.\n\n"
                + "화면이 계약을 방 안에서 열기 때문에 함께 내려준다. `targetId`가 가리키는 계약이 달린 방이며"
                + "(`contracts.room_id`), `targetId`가 null이면 함께 null이다. 세 갈래 모두 정리하려면 결국 그 계약을 "
                + "끝내야 하므로 DEPOSIT과 INCOME도 같은 방을 가리킨다.", example = "93") Integer roomId) {

    /** 정리 대상 갈래. 상수 이름이 목의 값과 같습니다. */
    public enum Type {
        CONTRACT,
        DEPOSIT,
        INCOME
    }
}
