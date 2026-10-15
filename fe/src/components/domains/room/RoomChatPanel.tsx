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
import { useChatChannel } from "@/hooks/useChatChannel";
import { formatDateLabel, formatTime, toDayKey } from "@/lib/format";
import { ROUTES } from "@/lib/routes";
import type { ChatMessage } from "@/types/chat";
import type { RoomDetail } from "@/types/room";

/** 갈래가 정해진 방에서 버튼에 적을 문구. */
const CONVERTED_LABEL = {
  CONSULTING: { notice: "컨설팅", action: "컨설팅 방으로" },
  DEVELOPMENT: { notice: "개발", action: "개발 방으로" },
} as const;

export interface RoomChatPanelProps {
  room: RoomDetail;
  /** 서버에서 읽어 온 메시지. 이후 오간 말은 여기에 이어 붙습니다. */
  initialMessages: ChatMessage[];
  /** 내 표시 이름. 내가 보낸 말의 작성자로 씁니다. */
  myName: string;
  /** 내 사용자 식별자. 어느 말이 내 것인지 가릅니다. */
  myUserId: number;
  /** 대화 위에 붙는 안내. 컨설팅 방의 세션 배너가 여기 옵니다. */
  banner?: React.ReactNode;
}

export function RoomChatPanel({
  room,
  initialMessages,
  myName,
  myUserId,
  banner,
}: Readonly<RoomChatPanelProps>) {
  const router = useRouter();
  const bottomRef = React.useRef<HTMLDivElement>(null);

  const { messages, status, send } = useChatChannel({
    chatRefId: room.chatRefId,
    initialMessages,
  });

  // 새 말이 붙으면 마지막 줄로 내립니다.
  React.useEffect(() => {
    bottomRef.current?.scrollIntoView({ block: "end" });
  }, [messages.length]);

  const converted =
    room.convertedRoomId !== null && room.convertedRoomType !== null
      ? { id: room.convertedRoomId, ...CONVERTED_LABEL[room.convertedRoomType] }
      : null;

  const footer = converted ? (
    <ChatNotice
      action={
        <Button variant="dark" onClick={() => router.push(ROUTES.room(converted.id))}>
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
    /* 연결이 열려 있을 때만 보낼 수 있습니다. 끊긴 채 친 말은 아무 데도 닿지 않습니다. */
    <ChatComposer onSend={send} disabled={status !== "OPEN"} />
  );

  return (
    <ChatRoom banner={banner} footer={footer} bodyClassName="max-h-[520px]">
      {messages.length === 0 ? (
        <p className="py-8 text-center text-sm text-fg-muted">아직 주고받은 말이 없습니다.</p>
      ) : (
        messages.map((message, index) => {
          const previous = index > 0 ? messages[index - 1] : null;
          const isNewDay = !previous || toDayKey(previous.sentAt) !== toDayKey(message.sentAt);
          const isMine = message.senderUserId === myUserId;

          return (
            <React.Fragment key={message.messageId}>
              {isNewDay && <ChatDateDivider date={formatDateLabel(message.sentAt)} />}
              <ChatBubble
                mine={isMine}
                author={isMine ? myName : room.counterpartName}
                time={formatTime(message.sentAt)}
                // 같은 사람이 연달아 말하면 아바타를 비웁니다.
                hideAvatar={!isNewDay && previous?.senderUserId === message.senderUserId}
              >
                {message.content}
              </ChatBubble>
            </React.Fragment>
          );
        })
      )}
      <div ref={bottomRef} />
    </ChatRoom>
  );
}
