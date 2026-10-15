package com.a501.service.domain.chat.service;

/**
 * 한 대화에 누가 들어갈 수 있는지 가려 주는 곳.
 *
 * <p>대화는 방에도 붙고 컨설팅 세션에도 붙습니다. (`R-MATCH-06`) 누가 당사자인지는 그쪽 도메인만 알므로
 * 채팅은 묻기만 하고 판정은 각자에게 맡깁니다. (BACKEND_CONVENTIONS 7.1)
 */
public interface ChatRoster {
    /** 이 이름표를 맡는지. {@code "room-92-chat"} 의 {@code "room"} 처럼 앞자리로 가릅니다. */
    String prefix();

    /** 이 사람이 그 대화의 당사자인지. 대화가 없으면 거짓입니다. */
    boolean includes(Integer targetId, Integer userId);
}
