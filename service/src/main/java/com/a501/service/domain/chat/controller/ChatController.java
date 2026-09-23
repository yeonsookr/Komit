package com.a501.service.domain.chat.controller;

import com.a501.service.common.security.AuthenticatedUser;
import com.a501.service.domain.chat.dto.response.ChatMessageListResponse;
import com.a501.service.domain.chat.service.ChatMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "방", description = "상담·컨설팅·개발 대화방")
@RestController
@RequestMapping("/api/rooms/{roomId}/messages")
@RequiredArgsConstructor
public class ChatController {
    private final ChatMessageService chatMessageService;

    @Operation(
            summary = "지난 대화 조회",
            description =
                    """
                    이 방에서 오간 메시지를 오래된 것부터 내려준다. 방에 들어갈 때 한 번 부른다.

                    소켓은 붙은 뒤에 오는 것만 내려준다. 어제 받은 메시지는 오지 않으므로 이전 \
                    대화는 이 API 로 가져온다.

                    위로 올려 읽을 때는 몇 쪽인지가 아니라 before 로 끊는다. 직전 응답의 \
                    nextBefore 를 그대로 넣으면 된다. 읽는 사이에 새 메시지가 와도 경계가 \
                    밀리지 않는다. nextBefore 가 null 이면 이 방의 첫 메시지까지 읽은 것이다.

                    상담방·컨설팅방·개발방을 가리지 않는다. 갈래가 정해져 닫힌 방도 읽을 수 \
                    있다 — 나눈 이야기를 계속 볼 수 있어야 한다.

                    - 200: 방에 들어가 있을 때. 대화가 없으면 content 가 빈 배열이다
                    - 401 UNAUTHORIZED: 로그인하지 않았을 때
                    - 404 ROOM_NOT_FOUND: 없거나 내가 들어가 있지 않은 방일 때""")
    @GetMapping
    ChatMessageListResponse getMessages(
            @Parameter(description = "방 식별자.", example = "92") @PathVariable Integer roomId,
            @Parameter(description = "이 시각보다 앞의 대화를 가져온다. 직전 응답의 nextBefore 를 넣는다. 비우면 가장 최근부터.")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    OffsetDateTime before,
            @Parameter(description = "한 번에 가져올 건수. 최대 100 이며 넘기면 100 으로 줄인다.", example = "30")
                    @RequestParam(defaultValue = "30")
                    int size,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return chatMessageService.getMessages(roomId, user.userId(), before, size);
    }
}
