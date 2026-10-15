package com.a501.service.domain.chat.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 한 대화의 지난 메시지.
 *
 * <p>위로 올려 읽는 목록이라 몇 쪽인지가 아니라 <b>어디까지 읽었는지</b>로 끊는다. 읽는 사이에 새 메시지가
 * 와도 경계가 밀리지 않는다.
 */
@Schema(description = "지난 대화. 오래된 것부터 내려간다.")
public record ChatMessageListResponse(
        @Schema(description = "메시지. 오래된 것부터 내려가므로 화면은 받은 순서대로 그리면 된다.")
                List<ChatMessageResponse> content,
        @Schema(
                        description =
                                "더 위로 읽을 때 before 에 넣을 값. null 이면 첫 메시지까지 읽은 것이다.")
                OffsetDateTime nextBefore) {}
