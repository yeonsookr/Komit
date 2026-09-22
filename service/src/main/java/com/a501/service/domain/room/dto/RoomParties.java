package com.a501.service.domain.room.dto;

/**
 * 방을 마주 보고 있는 두 사람.
 *
 * <p>도메인 사이에 오가는 결과 객체입니다. 엔티티 대신 id 만 담습니다. (BACKEND_CONVENTIONS 7.1)
 *
 * @param clientId 의뢰인. 방이 달린 프로젝트를 맡긴 사람
 * @param developerId 개발자. 방이 직접 들고 있는 값
 */
public record RoomParties(Integer clientId, Integer developerId) {

    /** 이 사람이 둘 중 하나인지. */
    public boolean includes(Integer userId) {
        return clientId.equals(userId) || developerId.equals(userId);
    }
}
