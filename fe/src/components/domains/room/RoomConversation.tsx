"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/buttons";
import { Card } from "@/components/common";
import { Modal, ModalBody, ModalFooter, ModalHeader, useToast } from "@/components/feedback";
import { clientApi } from "@/lib/api/client";
import { INTERNAL } from "@/lib/api/endpoints";
import { ApiError } from "@/types/api";
import type { ChatMessage } from "@/types/chat";
import type { ConversionTarget, RoomDetail } from "@/types/room";
import { RoomChatPanel } from "./RoomChatPanel";

/** 갈래별 문구. 버튼·확인 모달·안내에 같은 말을 씁니다. */
const TARGET = {
  CONSULTING: { label: "컨설팅", action: "컨설팅 계약 진행" },
  DEVELOPMENT: { label: "개발", action: "개발 계약 진행" },
} as const;

export interface RoomConversationProps {
  room: RoomDetail;
  initialMessages: ChatMessage[];
  myName: string;
  /**
   * 갈래를 정할 수 있는지. 갈래는 의뢰인이 정하므로 개발자에게는 버튼을 보이지
   * 않습니다(백엔드도 403 으로 막습니다).
   */
  canConvert: boolean;
  /** 대화 위에 붙는 안내. 컨설팅 방의 세션 배너가 여기 옵니다. */
  banner?: React.ReactNode;
}

/**
 * RoomConversation — 방의 본문(갈래 전환 + 대화).
 *
 * 전환 결과를 여기서 들고 있습니다. 전환하면 응답이 그대로 갱신된 방 상세라
 * 그 값으로 다시 그리면 대화 아래 안내가 '개발 방으로'로 바뀝니다.
 */
export function RoomConversation({
  room: initialRoom,
  initialMessages,
  myName,
  canConvert,
  banner,
}: Readonly<RoomConversationProps>) {
  const router = useRouter();
  const showToast = useToast();

  const [room, setRoom] = React.useState(initialRoom);
  const [asking, setAsking] = React.useState<ConversionTarget | null>(null);
  const [converting, setConverting] = React.useState(false);

  // 다른 방으로 옮기면 그 방의 값으로 맞춥니다.
  React.useEffect(() => setRoom(initialRoom), [initialRoom]);

  /** 열린 무료 상담방이고, 아직 갈래가 안 정해졌고, 내가 의뢰인일 때만 고를 수 있습니다. */
  const canChooseTarget =
    canConvert &&
    room.type === "INQUIRY" &&
    room.status === "OPEN" &&
    room.convertedRoomId === null;

  const convert = async (targetType: ConversionTarget) => {
    setConverting(true);

    try {
      const { data } = await clientApi.post<RoomDetail>(
        INTERNAL.ROOM_CONVERSION(room.roomId),
        { targetType },
      );

      setRoom(data);
      setAsking(null);
      showToast({
        tone: "success",
        title: `${TARGET[targetType].label} 계약으로 이어갑니다.`,
        description: "상담은 마무리되었고 새 방이 열렸습니다.",
      });
    } catch (error) {
      const apiError = error instanceof ApiError ? error : null;

      // 이미 갈래가 정해진 방이면 서버가 정한 곳으로 보냅니다.
      if (apiError?.code === "ROOM_ALREADY_CONVERTED") {
        showToast({ tone: "warning", title: apiError.message });
        setAsking(null);
        router.refresh();
        return;
      }

      showToast({
        tone: "danger",
        title: apiError?.message ?? "갈래를 정하지 못했습니다.",
      });
    } finally {
      setConverting(false);
    }
  };

  return (
    <>
      {canChooseTarget && (
        <Card variant="outline" className="mt-6">
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            {(["CONSULTING", "DEVELOPMENT"] as const).map((target) => (
              <Button
                key={target}
                variant="outline"
                size="lg"
                fullWidth
                onClick={() => setAsking(target)}
              >
                {TARGET[target].action}
              </Button>
            ))}
          </div>
        </Card>
      )}

      <section className="mt-10">
        <h2 className="text-xl font-bold text-fg">채팅</h2>
        <div className="mt-4">
          <RoomChatPanel
            room={room}
            initialMessages={initialMessages}
            myName={myName}
            banner={banner}
          />
        </div>
      </section>

      {/* 상담이 닫히는 되돌릴 수 없는 동작이라 한 번 확인합니다. */}
      <Modal
        open={asking !== null}
        onClose={() => !converting && setAsking(null)}
        size="sm"
        dismissible={!converting}
      >
        <ModalHeader title={asking ? `${TARGET[asking].label} 계약으로 진행할까요?` : ""} />
        <ModalBody>
          <p className="leading-6">
            이 상담은 마무리되고 {asking ? TARGET[asking].label : ""} 방이 새로 열립니다. 나눈
            이야기는 계속 볼 수 있지만 이 방에서는 더 이야기할 수 없습니다.
          </p>
        </ModalBody>
        <ModalFooter>
          <Button variant="ghost" onClick={() => setAsking(null)} disabled={converting}>
            취소
          </Button>
          <Button variant="dark" loading={converting} onClick={() => asking && convert(asking)}>
            진행하기
          </Button>
        </ModalFooter>
      </Modal>
    </>
  );
}
