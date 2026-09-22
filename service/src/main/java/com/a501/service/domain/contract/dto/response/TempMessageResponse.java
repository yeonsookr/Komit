package com.a501.service.domain.contract.dto.response;

import com.a501.service.domain.contract.entity.TempMessage;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * 계약서에 딸려 오가는 전달 메시지 하나.
 *
 * <p>채팅이 아니라 계약서에 붙는 한 줄입니다. 계약서를 보내거나 고쳐 보낼 때마다 쌓이며,
 * 화면은 상대가 마지막으로 남긴 것을 보여 줍니다.
 *
 * @param messageId 전달 메시지 id
 * @param content 남긴 내용
 * @param isClient 남긴 쪽이 의뢰인인지
 * @param createdAt 남긴 시각
 */
@Schema(description = "계약서에 딸려 오가는 전달 메시지 하나.")
public record TempMessageResponse(
        @Schema(description = "전달 메시지 식별자.", example = "31") Integer messageId,
        @Schema(description = "남긴 내용.", example = "세션 전에 확인하고 싶은 게 있으면 적어주세요.")
                String content,
        @Schema(description = "남긴 쪽이 의뢰인인지. 거짓이면 개발자가 남긴 것이다.", example = "false")
                boolean isClient,
        @Schema(description = "남긴 시각.") OffsetDateTime createdAt) {

    /**
     * 엔티티를 응답으로 바꿉니다.
     *
     * @param entity 전달 메시지
     * @return 응답 DTO
     */
    public static TempMessageResponse from(TempMessage entity) {
        return new TempMessageResponse(
                entity.getId(),
                entity.getContent(),
                entity.isClient(),
                entity.getCreatedAt() == null
                        ? null
                        : entity.getCreatedAt().atOffset(ZoneOffset.UTC));
    }
}
