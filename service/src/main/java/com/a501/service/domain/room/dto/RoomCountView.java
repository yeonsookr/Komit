package com.a501.service.domain.room.dto;

/** 프로젝트별 방 수를 DB에서 집계한 조회 결과. */
public interface RoomCountView {
    Integer getProjectId();

    long getRoomCount();
}
