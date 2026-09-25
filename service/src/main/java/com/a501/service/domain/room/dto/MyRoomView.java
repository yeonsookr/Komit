package com.a501.service.domain.room.dto;

/**
 * 내가 든 방 하나와 그 방에서의 내 자리.
 *
 * @param roomId      방 식별자
 * @param projectId   방이 달린 프로젝트 식별자
 * @param asClient    내가 맡긴 프로젝트의 방인지
 * @param asDeveloper 내가 개발자로 들어간 방인지
 */
public record MyRoomView(Integer roomId, Integer projectId, boolean asClient, boolean asDeveloper) {
}
