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

/**
 * 여러 방의 마지막 메시지. 사이드바의 미리보기에 씁니다.
 *
 * ⚠️ 지금은 목 데이터라 한 번에 읽습니다. 실제 채팅이 붙으면 방마다 따로
 * 부르지 말고 이 함수 하나를 목록 조회 API 로 바꿉니다.
 */
export async function getLastMessages(
  chatRefIds: string[],
): Promise<Record<string, ChatMessage | undefined>> {
  const entries = await Promise.all(
    chatRefIds.map(async (chatRefId) => {
      const messages = await getChatMessages(chatRefId);
      return [chatRefId, messages.at(-1)] as const;
    }),
  );

  return Object.fromEntries(entries);
}
