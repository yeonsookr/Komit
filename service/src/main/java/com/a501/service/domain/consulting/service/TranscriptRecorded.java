package com.a501.service.domain.consulting.service;

/**
 * 세션에 전사가 새로 남았다는 사실. 전사가 커밋된 뒤에 전해집니다.
 *
 * @param sessionId 전사가 남은 세션
 */
public record TranscriptRecorded(Integer sessionId) {}
