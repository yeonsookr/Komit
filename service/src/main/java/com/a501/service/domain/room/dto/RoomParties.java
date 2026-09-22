package com.a501.service.domain.room.dto;

/**
 * 방을 마주 보고 있는 두 사람. <b>둘 다 사용자 식별자입니다.</b>
 *
 * <p>방이 들고 있는 {@code developer_id} 는 {@code developers.id} 라 사람을 바로 가리키지
 * 않습니다. 여기서 한 단계를 넘어 두어야 묻는 쪽이 두 식별자를 헷갈리지 않습니다.
 *
 * @param clientId 의뢰인. 방이 달린 프로젝트를 맡긴 사람
 * @param developerUserId 개발자를 맡고 있는 사람
 */
public record RoomParties(Integer clientId, Integer developerUserId) {

    /** 이 사람이 둘 중 하나인지. */
    public boolean includes(Integer userId) {
        return clientId.equals(userId) || developerUserId.equals(userId);
    }
}
