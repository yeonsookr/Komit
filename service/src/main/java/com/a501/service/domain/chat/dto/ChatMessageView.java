package com.a501.service.domain.chat.dto;

import java.time.Instant;

/** 다른 도메인이 채팅 원문을 읽을 때 사용하는 내부 조회 값. */
public record ChatMessageView(Integer senderUserId, String content, Instant sentAt) {}
