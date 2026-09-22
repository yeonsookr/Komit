package com.a501.service.domain.room.dto.request;

/** 방 용도로 거르는 값. 허용된 값이 아니면 422 INVALID_REQUEST 로 걸러집니다. */
public enum RoomType {
    /** 무료 상담. */
    INQUIRY,
    /** 컨설팅. */
    CONSULTING,
    /** 개발. */
    DEVELOPMENT
}
