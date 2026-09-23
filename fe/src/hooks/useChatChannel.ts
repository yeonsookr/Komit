"use client";

import * as React from "react";
import { useRealtimeConnection, type RealtimeStatus } from "@/hooks/useRealtimeConnection";
import type { ChatMessage } from "@/types/chat";
import type { RealtimeEnvelope } from "@/types/realtime";

export interface UseChatChannelOptions {
  /** 어느 대화인지. 방 응답이나 세션 응답의 chatRefId 를 그대로 넘깁니다. */
  chatRefId: string;
  /** 서버에서 읽어 온 지난 대화. */
  initialMessages: ChatMessage[];
}

export interface ChatChannel {
  messages: ChatMessage[];
  status: RealtimeStatus;
  /** 보냅니다. 연결이 열려 있지 않으면 아무 일도 하지 않습니다. */
  send: (content: string) => void;
}

/**
 * 대화 하나에 연결을 하나 엽니다.
 *
 * **보낸 말도 서버를 거쳐 돌아옵니다.** 화면에 먼저 그려 두지 않습니다. 저장된 뒤에야
 * 식별자와 확정된 시각이 생기고, 먼저 그려 두면 실패했을 때 없는 말이 남습니다.
 *
 * 방 대화와 세션 대화가 같은 채널을 씁니다. 가르는 것은 chatRefId 입니다.
 */
export function useChatChannel({
  chatRefId,
  initialMessages,
}: UseChatChannelOptions): ChatChannel {
  const [messages, setMessages] = React.useState(initialMessages);

  // 방을 옮기거나 새로고침해 다시 읽어 오면 그 값으로 맞춥니다.
  React.useEffect(() => setMessages(initialMessages), [initialMessages]);

  const receive = React.useCallback((envelope: RealtimeEnvelope) => {
    if (envelope.type !== "CHAT_MESSAGE" || !envelope.payload) return;

    const message = envelope.payload as ChatMessage;
    setMessages((previous) =>
      // 다시 붙는 사이에 같은 말이 두 번 올 수 있습니다.
      previous.some((item) => item.messageId === message.messageId)
        ? previous
        : [...previous, message],
    );
  }, []);

  const { status, send: publish } = useRealtimeConnection({
    channel: "CHAT_ROOM",
    channelId: chatRefId,
    onMessage: receive,
  });

  const send = React.useCallback(
    (content: string) => {
      const body = content.trim();
      if (body.length === 0) return;
      publish("CHAT_MESSAGE", { content: body });
    },
    [publish],
  );

  return { messages, status, send };
}
