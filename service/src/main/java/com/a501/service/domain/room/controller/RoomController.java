package com.a501.service.domain.room.controller;

import com.a501.service.common.security.AuthenticatedUser;
import com.a501.service.domain.room.dto.response.RoomDetailResponse;
import com.a501.service.domain.room.service.RoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "방", description = "상담·컨설팅·개발 대화방")
@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {
    private final RoomService roomService;

    @Operation(
            summary = "방 상세 조회",
            description =
                    """
                    방 머리에 붙는 상대 이름, 방 용도, 요청서 요약을 내려준다. 갈래가 이미 정해진 \
                    방이면 convertedRoomId 에 이어진 방이 담겨서, 화면이 '컨설팅 방으로' 같은 \
                    버튼을 보여줄 수 있다.

                    메시지는 이 응답에 없다. chatRefId 로 채팅 쪽에서 가져온다.

                    - 200: 방이 있고 내가 들어가 있을 때
                    - 401 UNAUTHORIZED: 로그인하지 않았을 때
                    - 404 ROOM_NOT_FOUND: 없거나 내가 들어가 있지 않은 방일 때. 남의 방도 존재를 \
                    감추기 위해 같은 응답을 준다""")
    @GetMapping("/{roomId}")
    RoomDetailResponse getRoom(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Integer roomId) {
        return roomService.getRoom(user.userId(), roomId);
    }
}
