"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/buttons";
import {
  ChatComposer,
  ChatDateDivider,
  ChatMessage as ChatBubble,
  ChatNotice,
  ChatRoom,
} from "@/components/domains/chat";
import { formatDateLabel, formatTime, toDayKey } from "@/lib/format";
import type { ChatMessage, ChatSender } from "@/types/chat";
import type { RoomDetail } from "@/types/room";

/** 갈래가 정해진 방에서 버튼에 적을 문구. */
const CONVERTED_LABEL = {
  CONSULTING: { notice: "컨설팅", action: "컨설팅 방으로" },
  DEVELOPMENT: { notice: "개발", action: "개발 방으로" },
} as const;

export interface RoomChatPanelProps {
  room: RoomDetail;
  /** 서버에서 읽어 온 메시지. 이후 보낸 말은 여기에 이어 붙습니다. */
  initialMessages: ChatMessage[];
  /** 내 표시 이름. 보낸 말의 작성자로 씁니다. */
  myName: string;
}

export function RoomChatPanel({ room, initialMessages, myName }: RoomChatPanelProps) {
  const router = useRouter();
  const [messages, setMessages] = React.useState(initialMessages);
  const bottomRef = React.useRef<HTMLDivElement>(null);

  // 서버에서 다시 읽어 오면(방 이동·새로고침) 그 값으로 맞춥니다.
  React.useEffect(() => setMessages(initialMessages), [initialMessages]);

  // 새 말이 붙으면 마지막 줄로 내립니다.
  React.useEffect(() => {
    bottomRef.current?.scrollIntoView({ block: "end" });
  }, [messages.length]);

  /**
   * 보내기.
   * ⚠️ 아직 서버에 저장하지 않습니다. 채팅 계약이 정해지면 여기서
   * POST 를 부르고 실패 시 되돌리는 처리를 더합니다.
   */
  const send = (body: string) => {
    const sender: ChatSender = "ME";
    setMessages((prev) => [
      ...prev,
      {
        messageId: `local-${Date.now()}`,
        chatRefId: room.chatRefId,
        sender,
        authorName: myName,
        body,
        sentAt: new Date().toISOString(),
      },
    ]);
  };

  const converted =
    room.convertedRoomId !== null && room.convertedRoomType !== null
      ? { id: room.convertedRoomId, ...CONVERTED_LABEL[room.convertedRoomType] }
      : null;

  const footer = converted ? (
    <ChatNotice
      action={
        <Button variant="dark" onClick={() => router.push(`/rooms/${converted.id}`)}>
          {converted.action}
        </Button>
      }
    >
      {converted.notice} 계약으로 진행해 이 상담은 마무리됐습니다. 이어지는 이야기는{" "}
      {converted.notice} 방에서 합니다.
    </ChatNotice>
  ) : room.status === "CLOSED" ? (
    <ChatNotice>이 방은 마무리되어 새 메시지를 보낼 수 없습니다.</ChatNotice>
  ) : (
    <ChatComposer onSend={send} />
  );

  return (
    <ChatRoom footer={footer} bodyClassName="max-h-[520px]">
      {messages.length === 0 ? (
        <p className="py-8 text-center text-sm text-fg-muted">아직 주고받은 말이 없습니다.</p>
      ) : (
        messages.map((message, index) => {
          const previous = index > 0 ? messages[index - 1] : null;
          const isNewDay = !previous || toDayKey(previous.sentAt) !== toDayKey(message.sentAt);
          const isMine = message.sender === "ME";

          return (
            <React.Fragment key={message.messageId}>
              {isNewDay && <ChatDateDivider date={formatDateLabel(message.sentAt)} />}
              <ChatBubble
                mine={isMine}
                author={message.authorName}
                time={formatTime(message.sentAt)}
                // 같은 사람이 연달아 말하면 아바타를 비웁니다.
                hideAvatar={!isNewDay && previous?.sender === message.sender}
              >
                {message.body}
              </ChatBubble>
            </React.Fragment>
          );
        })
      )}
      <div ref={bottomRef} />
    </ChatRoom>
  );
}
