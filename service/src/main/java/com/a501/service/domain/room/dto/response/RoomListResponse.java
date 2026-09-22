package com.a501.service.domain.room.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 방 목록.
 *
 * <p><b>페이지 정보로 감싸지 않습니다.</b> 사이드바가 프로젝트별로 묶어 한 번에 늘어놓는 구조라
 * 페이지를 넘길 자리가 없습니다. 방이 몇 개든 전부 나가며 상한을 두지 않습니다.
 *
 * @param content 조건에 맞는 방. 없으면 빈 배열
 */
@Schema(description = "방 목록. 페이지 정보로 감싸지 않는다.")
public record RoomListResponse(
        @Schema(description = "조건에 맞는 방. 마지막으로 움직인 것이 먼저다. 없으면 빈 배열이다.")
                List<RoomSummaryResponse> content) {}
