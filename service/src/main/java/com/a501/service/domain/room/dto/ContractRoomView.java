package com.a501.service.domain.room.dto;

/**
 * 계약서를 쓰는 방의 정보. 계약 도메인이 방 엔티티 없이 방 종류·당사자·프로젝트를 확인하도록 넘긴다.
 * (BACKEND_CONVENTIONS 7.1)
 *
 * @param roomId 방 id
 * @param type 방 종류. {@code INQUIRY}·{@code CONSULTING}·{@code DEVELOPMENT}
 * @param projectId 방이 다루는 프로젝트 id. 계약서의 요구사항을 이 프로젝트 요청서에서 찾는다
 * @param parties 방의 의뢰인과 개발자 사용자 id
 */
public record ContractRoomView(Integer roomId, String type, Integer projectId, RoomParties parties) {}
