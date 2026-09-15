import "server-only";

import { findChatMessages } from "@/mocks/chat";
import type { ChatMessage } from "@/types/chat";

/**
 * 한 방의 메시지.
 *
 * ⚠️ 지금은 목 데이터를 읽습니다. 채팅 계약이 정해지면 이 함수 본문만
 * 실제 호출로 바꾸면 되고, 화면과 Route Handler 는 그대로 둡니다.
 */
export async function getChatMessages(chatRefId: string): Promise<ChatMessage[]> {
  return findChatMessages(chatRefId);
}
