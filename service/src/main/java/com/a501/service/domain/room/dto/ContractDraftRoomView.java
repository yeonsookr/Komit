package com.a501.service.domain.room.dto;

/**
 * 계약 초안 생성에 필요한 방 정보.
 *
 * @param roomId 갈래가 정해져 새로 열린 방 식별자
 * @param targetType 새 방의 용도. {@code CONSULTING} 또는 {@code DEVELOPMENT}
 * @param inquiryChatRefId 전환 전 무료 상담방의 채팅 참조 키. 정상 전환 출처가 없으면 {@code null}
 * @param parties 방의 의뢰인과 개발자 사용자 식별자
 * @param projectId 방이 다루는 프로젝트 식별자. 개발 계약 초안이 이 프로젝트 요청서를 읽는다
 */
public record ContractDraftRoomView(
        Integer roomId, String targetType, String inquiryChatRefId, RoomParties parties, Integer projectId) {}
