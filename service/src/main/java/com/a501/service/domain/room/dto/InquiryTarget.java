package com.a501.service.domain.room.dto;

/**
 * 상담방을 열 개발자 한 명. 추천이 방 엔티티를 직접 만들지 않기 위한 경계다. (BACKEND_CONVENTIONS 7.1)
 *
 * @param recommendationItemId 이 방이 시작된 추천 항목. 화면은 이 값으로 방을 개발자 카드에 붙인다
 */
public record InquiryTarget(Integer recommendationItemId, Integer developerId) {}
