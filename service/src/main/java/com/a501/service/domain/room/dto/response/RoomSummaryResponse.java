package com.a501.service.domain.room.dto.response;

import com.a501.service.domain.room.entity.Room;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * 사이드바에 한 줄로 나가는 방.
 *
 * <p><b>의뢰인 식별자는 담지 않습니다.</b> 내가 들어가 있는 방만 오고 한 방에서 나는 의뢰인이거나
 * 개발자 둘 중 하나이므로, {@code developerId} 하나로 보낸 의뢰와 받은 의뢰가 갈립니다.
 *
 * @param roomId 방 id
 * @param recommendationItemId 이 방이 시작된 추천 항목. 직접 탐색으로 열었으면 {@code null}
 * @param type 방 용도
 * @param status 방 개폐
 * @param projectId 이 방이 달린 프로젝트
 * @param projectName 프로젝트 이름
 * @param developerId 참여 개발자. 사용자 id 가 아니라 개발자 id 다
 * @param counterpartName 요청한 사람에게 보이는 상대 이름
 * @param chatRefId 채팅 참조 키
 * @param openedAt 개설 일시
 */
@Schema(description = "방 목록 사이드바에 한 줄로 나가는 방")
public record RoomSummaryResponse(
        @Schema(description = "방 식별자.", example = "92") Integer roomId,
        @Schema(description = "이 방이 시작된 추천 항목. 직접 탐색으로 열었으면 null 이다.", example = "501")
                Integer recommendationItemId,
        @Schema(description = "방 용도. INQUIRY · CONSULTING · DEVELOPMENT.", example = "INQUIRY")
                String type,
        @Schema(description = "방 개폐. OPEN · CLOSED.", example = "OPEN") String status,
        @Schema(description = "이 방이 달린 프로젝트.", example = "8") Integer projectId,
        @Schema(description = "프로젝트 이름.", example = "수제 비누 가게") String projectName,
        @Schema(
                        description =
                                "이 방에 참여한 개발자 식별자. 사용자 식별자가 아니다. 내 정보의 developerId 와"
                                        + " 같으면 받은 의뢰, 다르면 보낸 의뢰다.",
                        example = "3")
                Integer developerId,
        @Schema(description = "상대의 표시 이름. 상대가 탈퇴했으면 '탈퇴한 사용자' 가 들어온다.", example = "이준")
                String counterpartName,
        @Schema(description = "채팅 참조 키. 마지막 메시지와 미리보기는 이 값으로 채팅 쪽에서 가져온다.", example = "room-92-chat")
                String chatRefId,
        @Schema(description = "방이 열린 시각.") OffsetDateTime openedAt) {

    public static RoomSummaryResponse of(Room room, String projectName, String counterpartName) {
        return new RoomSummaryResponse(
                room.getId(),
                room.getRecommendationItemId(),
                room.getType(),
                room.getStatus(),
                room.getProjectId(),
                projectName,
                room.getDeveloperId(),
                counterpartName,
                room.getChatRefId(),
                room.getOpenedAt().atOffset(ZoneOffset.UTC));
    }
}
