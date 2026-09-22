package com.a501.service.domain.room.dto.response;

import com.a501.service.domain.room.entity.Room;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * 방 상세. 방 머리에 붙는 상대 이름·용도와 요청서 요약을 담습니다.
 *
 * <p><b>메시지는 여기에 없습니다.</b> {@code chatRefId} 로 채팅 쪽에서 가져옵니다.
 *
 * @param roomId 방 id
 * @param recommendationItemId 이 방이 시작된 추천 항목. 직접 탐색으로 열었으면 {@code null}
 * @param type 방 용도
 * @param status 방 개폐
 * @param projectId 이 방이 달린 프로젝트
 * @param projectName 프로젝트 이름
 * @param developerId 참여 개발자
 * @param counterpartName 요청한 사람에게 보이는 상대 이름
 * @param chatRefId 채팅 참조 키
 * @param openedAt 개설 일시
 * @param request 요청서 요약. 아직 진단이 없으면 {@code null}
 * @param contractId 이 방에 달린 계약. 아직 계약서를 쓰지 않았으면 {@code null}
 * @param convertedRoomId 갈래를 정해 새로 열린 방. 아직 정하지 않았으면 {@code null}
 * @param convertedRoomType 새로 열린 방의 용도. {@code convertedRoomId} 와 늘 짝으로 차고 빕니다
 */
@Schema(description = "방 상세. 공통 래퍼 없이 리소스를 그대로 반환한다.")
public record RoomDetailResponse(
        @Schema(description = "방 식별자.", example = "92") Integer roomId,
        @Schema(description = "이 방이 시작된 추천 항목. 직접 탐색으로 열었으면 null 이다.", example = "17")
                Integer recommendationItemId,
        @Schema(description = "방 용도. INQUIRY · CONSULTING · DEVELOPMENT.", example = "CONSULTING")
                String type,
        @Schema(description = "방 개폐. OPEN · CLOSED.", example = "OPEN") String status,
        @Schema(description = "이 방이 달린 프로젝트.", example = "5") Integer projectId,
        @Schema(description = "프로젝트 이름.", example = "수제 비누 가게") String projectName,
        @Schema(description = "참여 개발자.", example = "9") Integer developerId,
        @Schema(description = "요청한 사람에게 보이는 상대 이름.", example = "이준") String counterpartName,
        @Schema(description = "채팅 참조 키. 메시지는 이 키로 채팅 쪽에서 가져온다.", example = "room-92-chat")
                String chatRefId,
        @Schema(description = "개설 일시.") OffsetDateTime openedAt,
        @Schema(description = "요청서 요약. 아직 진단이 없으면 null 이다.")
                RoomRequestSummaryResponse request,
        @Schema(description = "이 방에 달린 계약. 아직 계약서를 쓰지 않았으면 null 이다.", example = "120")
                Integer contractId,
        @Schema(description = "갈래를 정해 새로 열린 방. 아직 정하지 않았으면 null 이다.", example = "104")
                Integer convertedRoomId,
        @Schema(description = "새로 열린 방의 용도. convertedRoomId 와 늘 짝으로 차고 빈다.", example = "CONSULTING")
                String convertedRoomType) {

    /**
     * 방과 다른 도메인에서 받아 온 값들을 응답으로 묶습니다. 엔티티는 밖으로 나가지 않습니다.
     *
     * <p>{@code convertedRoomType} 은 가리키는 방의 용도입니다. 그 방을 찾지 못하면 식별자와
     * 함께 비웁니다 — 화면이 둘을 짝으로 보고 버튼을 그리기 때문입니다.
     */
    public static RoomDetailResponse of(
            Room room,
            String projectName,
            String counterpartName,
            RoomRequestSummaryResponse request,
            Integer contractId,
            Room convertedRoom) {
        return new RoomDetailResponse(
                room.getId(),
                room.getRecommendationItemId(),
                room.getType(),
                room.getStatus(),
                room.getProjectId(),
                projectName,
                room.getDeveloperId(),
                counterpartName,
                room.getChatRefId(),
                room.getOpenedAt().atOffset(ZoneOffset.UTC),
                request,
                contractId,
                convertedRoom == null ? null : convertedRoom.getId(),
                convertedRoom == null ? null : convertedRoom.getType());
    }
}
