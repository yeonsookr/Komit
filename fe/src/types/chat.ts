/**
 * 채팅 메시지 타입. 서버의 `domain/chat` 과 같은 모양입니다.
 *
 * 대화는 방에도 붙고 컨설팅 세션에도 붙습니다. 둘은 따로 쌓이며 서버는 `chatRefId`
 * 라는 한 이름 공간으로 가리킵니다(`room-92-chat` · `session-31-chat`).
 * 화면은 그 값을 직접 만들지 않고 방·세션 식별자로 부릅니다.
 */

/** 서버가 내려주는 메시지 한 건. */
export interface ChatMessage {
  messageId: string;
  /** 보낸 사람. 내 식별자와 같으면 내가 쓴 것입니다. */
  senderUserId: number;
  content: string;
  sentAt: string;
}

export interface ChatMessageListResponse {
  content: ChatMessage[];
  /**
   * 더 위로 읽을 때 `before` 에 넣을 값. `null` 이면 첫 메시지까지 읽은 것입니다.
   *
   * 몇 쪽인지가 아니라 어디까지 읽었는지로 끊습니다. 읽는 사이에 새 메시지가 와도
   * 경계가 밀리지 않습니다.
   */
  nextBefore: string | null;
}

/** 소켓으로 올려 보내는 말. 보낸 사람은 티켓으로 증명되므로 담지 않습니다. */
export interface ChatMessagePayload {
  content: string;
}
