package com.a501.service.domain.room.dto.request;

/** 방 개폐로 거르는 값. 허용된 값이 아니면 422 INVALID_REQUEST 로 걸러집니다. */
public enum RoomStatus {
    /** 열림. */
    OPEN,
    /** 닫힘. 갈래가 정해진 상담방이 여기 들어갑니다. */
    CLOSED
}
