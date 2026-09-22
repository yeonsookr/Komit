package com.a501.service.domain.room.controller;

import com.a501.service.common.security.AuthenticatedUser;
import com.a501.service.domain.room.dto.request.ConvertRoomRequest;
import com.a501.service.domain.room.dto.request.RoomStatus;
import com.a501.service.domain.room.dto.request.RoomType;
import com.a501.service.domain.room.dto.response.RoomDetailResponse;
import com.a501.service.domain.room.dto.response.RoomListResponse;
import com.a501.service.domain.room.service.RoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "방", description = "상담·컨설팅·개발 대화방")
@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {
    private final RoomService roomService;

    @Operation(
            summary = "방 목록 조회",
            description =
                    """
                    내가 들어가 있는 방을 내려준다. 의뢰인이 보면 '보낸 의뢰', 개발자가 보면 \
                    '받은 의뢰'가 된다. 가르는 값은 developerId 하나다 — 내 정보의 \
                    developerId 와 같으면 받은 의뢰, 아니면 보낸 의뢰다.

                    한 사람이 양쪽일 수 있다. 역할별로 따로 부르는 API 는 두지 않으며, 이 \
                    API 가 둘 다 내려주고 화면이 가른다.

                    닫힌 방도 내려간다. 갈래가 정해져 닫힌 상담방은 나눈 이야기를 계속 볼 수 \
                    있어야 하므로 목록에 남는다. 거르려면 status 를 준다.

                    마지막 메시지와 미리보기는 이 응답에 없다. chatRefId 로 채팅 쪽에서 \
                    가져온다.

                    - 200: 항상. 들어가 있는 방이 없으면 content 가 빈 배열이다
                    - 401 UNAUTHORIZED: 로그인하지 않았을 때
                    - 422 INVALID_REQUEST: type 이나 status 가 허용된 값이 아닐 때""")
    @GetMapping
    RoomListResponse getRooms(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(description = "방 용도로 거른다. 비우면 전부 내려간다.") @RequestParam(required = false)
                    RoomType type,
            @Parameter(description = "열림 여부로 거른다. 비우면 전부 내려간다.") @RequestParam(required = false)
                    RoomStatus status) {
        return roomService.getRooms(user.userId(), type, status);
    }

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

    @Operation(
            summary = "상담에서 갈래 정하기",
            description =
                    """
                    무료 상담을 마치고 컨설팅과 개발 중 하나로 이어간다. 상담방은 닫히고 고른 \
                    갈래의 방이 새로 열린다. 닫힌 상담방도 지우지 않고 남겨서 나눈 이야기를 계속 \
                    볼 수 있다.

                    응답의 convertedRoomId 가 새로 열린 방이고 convertedRoomType 이 그 방의 \
                    용도다. 화면은 둘로 '컨설팅 방으로' 또는 '개발 방으로' 버튼을 그린다.

                    갈래는 의뢰인이 정한다. 개발자는 이 API 를 부를 수 없다 — 상담을 어디로 \
                    이어갈지는 돈을 내는 쪽이 고르기 때문이다. 개발자는 그 방의 당사자라 방이 \
                    있다는 것을 이미 아는 상태이므로, 없는 척 감추지 않고 권한이 없다고 답한다.

                    - 201: 갈래를 정해 새 방이 열렸을 때
                    - 401 UNAUTHORIZED: 로그인하지 않았을 때
                    - 403 FORBIDDEN: 이 방의 의뢰인이 아닐 때
                    - 404 ROOM_NOT_FOUND: 없거나 내가 들어가 있지 않은 방일 때
                    - 409 ROOM_ALREADY_CONVERTED: 갈래를 정할 수 없는 방일 때
                    - 422 INVALID_REQUEST: targetType 이 CONSULTING 도 DEVELOPMENT 도 아닐 때""")
    @PostMapping("/{roomId}/conversion")
    @ResponseStatus(HttpStatus.CREATED)
    RoomDetailResponse convert(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Integer roomId,
            @Valid @RequestBody ConvertRoomRequest request) {
        return roomService.convert(user.userId(), roomId, request);
    }
}
