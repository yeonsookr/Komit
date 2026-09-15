/**
 * 채팅 메시지 타입.
 *
 * ⚠️ 이 타입은 아직 합의된 계약이 아닙니다.
 * 코밋 목 서버(`/service`)는 메시지 본문을 다루지 않고 방 응답의 `chatRefId` 로
 * 채팅 쪽에 넘깁니다. 채팅을 어디서 가져올지 정해지기 전까지 FE 가 임시로 둡니다.
 * 계약이 나오면 이 파일과 `mocks/chat.ts` 만 고치면 됩니다.
 */

/** 보낸 사람이 나인지 상대인지. 화면의 좌우와 색을 가릅니다. */
export type ChatSender = "ME" | "COUNTERPART";

export interface ChatMessage {
  messageId: string;
  /** 어느 방의 메시지인지. 방 응답의 chatRefId 와 같은 값. */
  chatRefId: string;
  sender: ChatSender;
  /** 상대 메시지일 때 말풍선 옆에 붙는 이름. */
  authorName: string;
  body: string;
  sentAt: string;
}

export interface ChatMessageListResponse {
  content: ChatMessage[];
}
