import "server-only";

import { authenticatedBackendApi } from "../server";
import { BACKEND } from "../endpoints";
import type { ChatMessage, ChatMessageListResponse } from "@/types/chat";

/** 한 번에 읽어 오는 건수. 서버 상한은 100 입니다. */
const PAGE_SIZE = 30;

/**
 * 방의 지난 대화. 오래된 것부터 옵니다.
 *
 * 소켓은 붙은 뒤에 오는 것만 내려주므로 방에 들어갈 때 한 번 부릅니다.
 * 없거나 내가 들어가 있지 않은 방이면 404(CHAT_NOT_FOUND).
 */
export async function getRoomMessages(
  roomId: number,
  before?: string,
): Promise<ChatMessageListResponse> {
  const { data } = await authenticatedBackendApi().get<ChatMessageListResponse>(
    BACKEND.ROOM_MESSAGES(roomId),
    { params: { size: PAGE_SIZE, ...(before ? { before } : {}) } },
  );
  return data;
}

/**
 * 여러 방의 마지막 메시지. 사이드바 미리보기에 씁니다.
 *
 * ⚠️ 방마다 따로 부릅니다. 방이 많아지면 요청도 그만큼 늘어납니다. 서버에 여러 방의
 * 마지막 메시지를 한 번에 주는 길이 생기면 이 함수만 그쪽으로 바꿉니다.
 */
export async function getLastMessages(
  roomIds: number[],
): Promise<Record<number, ChatMessage | undefined>> {
  const entries = await Promise.all(
    roomIds.map(async (roomId) => {
      const { content } = await getRoomMessages(roomId);
      return [roomId, content.at(-1)] as const;
    }),
  );

  return Object.fromEntries(entries);
}

/**
 * 컨설팅 세션의 지난 대화.
 *
 * 화상 상담 옆에서 링크와 메모를 주고받는 대화입니다. **방 대화와 따로 쌓입니다.**
 * 끝난 세션도 읽을 수 있습니다.
 */
export async function getSessionMessages(
  sessionId: number,
  before?: string,
): Promise<ChatMessageListResponse> {
  const { data } = await authenticatedBackendApi().get<ChatMessageListResponse>(
    BACKEND.SESSION_MESSAGES(sessionId),
    { params: { size: PAGE_SIZE, ...(before ? { before } : {}) } },
  );
  return data;
}
