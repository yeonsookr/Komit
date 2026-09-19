"use client";

import { useRouter } from "next/navigation";
import * as React from "react";
import { Button } from "@/components/buttons";
import {
  ChatComposer,
  ChatMessage as ChatBubble,
  ChatRoom,
} from "@/components/domains/chat";
import { Callout, Modal, ModalBody, ModalFooter, ModalHeader, useToast } from "@/components/feedback";
import { clientApi } from "@/lib/api/client";
import { INTERNAL } from "@/lib/api/endpoints";
import { formatTime } from "@/lib/format";
import { ROUTES } from "@/lib/routes";
import { SESSION_PENDING_CONSENT_STATUS } from "@/lib/session";
import { ApiError } from "@/types/api";
import type { ChatMessage } from "@/types/chat";

/**
 * 장치 버튼.
 *
 * **아직 아무 일도 하지 않습니다.** 화상·마이크·화면 공유는 이 API가 다루지 않고
 * (목 명세 영역 4 — "화상 연결 자체는 이 API가 하지 않는다"), 무엇으로 이을지도
 * 정해지지 않았습니다. 자리만 두고 눌리지 않게 잠가 둡니다 — 눌리는데 아무 일도
 * 일어나지 않으면 고장으로 읽힙니다.
 */
const DEVICE_CONTROLS = ["마이크", "카메라", "화면 공유"] as const;

const NOT_CONNECTED_TITLE = "화상 연결이 아직 붙지 않았습니다";

/**
 * 입장이 어디까지 왔는지.
 *
 * - `JOINING` — 동의와 입장을 보내는 중
 * - `PENDING` — 내 동의는 남았고 상대를 기다리는 중 (`R-CON-13`)
 * - `ENTERED` — 양쪽이 모여 실제로 들어옴
 * - `FAILED` — 들어가지 못함
 */
type EntryState = "JOINING" | "PENDING" | "ENTERED" | "FAILED";

/** 상대가 들어왔는지 다시 묻는 주기. 실시간 채널이 붙으면 지웁니다. */
const PENDING_RECHECK_MS = 5_000;

/** 아직 무대를 열 수 없는 때에 적는 말. */
const ENTRY_NOTICE: Record<Exclude<EntryState, "ENTERED">, { title: string; body: string }> = {
  JOINING: {
    title: "세션에 들어가는 중입니다",
    body: "잠시만 기다려 주세요.",
  },
  PENDING: {
    title: "상대방의 동의를 기다리고 있습니다",
    body:
      "이 세션은 녹음해 글로 옮긴 뒤 요약을 만듭니다. 양쪽이 모두 동의해야 시작하며, " +
      "상대가 들어오면 화면이 저절로 바뀝니다.",
  },
  FAILED: {
    title: "세션에 들어가지 못했습니다",
    body: "잠시 후 다시 시도해 주세요. 이미 끝난 세션이거나 아직 이른 시각일 수 있습니다.",
  },
};

export interface SessionStageProps {
  sessionId: number;
  /** 끝낸 뒤 돌아갈 방. 세션 응답의 `contractId` 로 찾은 값입니다. */
  roomId: number;
  /** 상대 표시 이름. 화면 위 이름표에 씁니다. */
  counterpartName: string;
  /** 내 표시 이름. 보낸 말의 작성자로 씁니다. */
  myName: string;
  /** 세션 대화. 방 대화와 다른 `chatRefId` 를 씁니다. */
  chatRefId: string;
  initialMessages: ChatMessage[];
}

/**
 * SessionStage — 세션 화면의 무대·대화·컨트롤 바 (`4_의뢰인_세션중`).
 *
 * 화상이 붙기 전까지 무대는 비어 있습니다. **비어 있다는 사실을 화면에 적습니다** —
 * 검은 칸만 두면 연결이 끊긴 것인지 아직 만들지 않은 것인지 구분되지 않습니다.
 *
 * **무대 앞에 관문이 하나 있습니다.** 양쪽이 모두 동의해야 세션이 시작되므로
 * (`R-CON-13`), 먼저 들어온 사람에게는 무대 대신 기다리는 안내를 보여줍니다.
 *
 * 실제로 동작하는 것은 둘입니다. 동의하고 들어가는 것(`participations`)과
 * 「세션 종료」로 마치는 것. 마치면 되돌릴 수 없어 한 번 확인합니다.
 */
export function SessionStage({
  sessionId,
  roomId,
  counterpartName,
  myName,
  chatRefId,
  initialMessages,
}: Readonly<SessionStageProps>) {
  const router = useRouter();
  const showToast = useToast();
  const [messages, setMessages] = React.useState(initialMessages);
  const [asking, setAsking] = React.useState(false);
  const [ending, setEnding] = React.useState(false);
  const bottomRef = React.useRef<HTMLDivElement>(null);
  const [entry, setEntry] = React.useState<EntryState>("JOINING");

  /*
    동의하고 들어갑니다.

    **이 요청은 기록이 아니라 관문입니다.** 같은 요청 하나가 녹음·전사·요약 동의와
    입장을 겸하고, 양쪽이 모두 보내야 실제로 들어갑니다 (`R-CON-13`). 먼저 보낸 쪽은
    201 이 아니라 202 를 받고 상대를 기다립니다.

    202 는 2xx 라 오류로 잡히지 않습니다. 결과를 버리면 들어가지도 않은 사람에게
    무대가 그대로 열리므로 여기서 기다려 갈라야 합니다.
  */
  const join = React.useCallback(async () => {
    try {
      const { status } = await clientApi.post(INTERNAL.SESSION_PARTICIPATIONS(sessionId));
      return status === SESSION_PENDING_CONSENT_STATUS ? "PENDING" : "ENTERED";
    } catch {
      return "FAILED";
    }
  }, [sessionId]);

  React.useEffect(() => {
    let alive = true;
    void join().then((next) => {
      if (alive) setEntry(next);
    });
    return () => {
      alive = false;
    };
  }, [join]);

  /*
    기다리는 동안 같은 요청을 되풀이해 201 로 바뀌는 때를 봅니다. 동의는 한 번만
    기록되므로 여러 번 보내도 덧나지 않습니다.

    ⚠️ 임시입니다. 상대가 들어온 것은 원래 실시간 채널이 알려줄 일이고, 그것이 붙으면
    이 되풀이를 지웁니다. 그때까지 이것이 없으면 먼저 동의한 사람은 상대가 들어왔는지
    영영 알 수 없습니다.
    간격은 정책 값이 아니라 화면이 다시 묻는 주기라 `RULES.md` 에 두지 않습니다.
  */
  React.useEffect(() => {
    if (entry !== "PENDING") return;

    let alive = true;
    const timer = setInterval(() => {
      void join().then((next) => {
        if (alive) setEntry(next);
      });
    }, PENDING_RECHECK_MS);

    return () => {
      alive = false;
      clearInterval(timer);
    };
  }, [entry, join]);

  React.useEffect(() => {
    bottomRef.current?.scrollIntoView({ block: "end" });
  }, [messages.length]);

  /**
   * 보내기.
   * ⚠️ 아직 서버에 저장하지 않습니다. 방 대화와 같습니다(`RoomChatPanel`) — 채팅
   * 계약이 정해지면 여기서 POST 를 부르고 실패 시 되돌리는 처리를 더합니다.
   */
  const send = (body: string) => {
    setMessages((prev) => [
      ...prev,
      {
        messageId: `local-${Date.now()}`,
        chatRefId,
        sender: "ME",
        authorName: myName,
        body,
        sentAt: new Date().toISOString(),
      },
    ]);
  };

  const end = async () => {
    if (ending) return;
    setEnding(true);

    try {
      await clientApi.post(INTERNAL.SESSION_END(sessionId));
      setAsking(false);
      showToast({
        tone: "success",
        title: "세션을 마쳤습니다",
        description: "녹음을 바탕으로 요약을 만들고 있습니다.",
      });
      router.push(ROUTES.room(roomId));
      router.refresh();
    } catch (error) {
      showToast({
        tone: "danger",
        title: error instanceof ApiError ? error.message : "세션을 마치지 못했습니다.",
      });
      setEnding(false);
    }
  };

  /*
    들어가기 전에는 무대를 그리지 않습니다. 대화도 컨트롤도 아직 내 것이 아니고,
    무엇보다 카메라·마이크는 양쪽이 동의한 뒤에만 켭니다 (`R-CON-13`).
  */
  if (entry !== "ENTERED") {
    return (
      <Callout
        className="mt-4"
        tone={entry === "FAILED" ? "danger" : "info"}
        title={ENTRY_NOTICE[entry].title}
        action={
          entry === "FAILED" ? (
            <Button variant="outline" onClick={() => window.location.reload()}>
              다시 시도
            </Button>
          ) : undefined
        }
      >
        {ENTRY_NOTICE[entry].body}
      </Callout>
    );
  }

  return (
    <>
      <div className="mt-4 flex flex-col gap-4 lg:flex-row">
        <div className="flex min-w-0 flex-1 flex-col gap-3">
          <div className="flex min-h-[320px] flex-col items-center justify-center rounded-xl bg-ink px-6 py-16 text-center lg:min-h-[480px]">
            <p className="text-sm font-semibold text-on-ink">{NOT_CONNECTED_TITLE}</p>
            <p className="mt-2 max-w-md text-[13px] leading-6 text-on-ink/70">
              {counterpartName}님과의 화면과 소리는 이 화면이 아직 잇지 못합니다. 무엇으로
              이을지 정해지면 이 자리에 붙습니다.
            </p>
          </div>

          <div className="flex gap-3">
            {[counterpartName, myName].map((name) => (
              <div
                key={name}
                className="flex h-[108px] w-[220px] max-w-[45%] flex-col justify-end rounded-lg bg-surface-2 p-3"
              >
                <p className="truncate text-[11px] font-bold text-fg">{name}</p>
                <p className="text-[11px] text-fg-subtle">연결 전</p>
              </div>
            ))}
          </div>
        </div>

        <ChatRoom
          className="h-[420px] w-full shrink-0 lg:h-auto lg:w-[380px]"
          footer={<ChatComposer onSend={send} />}
        >
          {messages.map((message) => (
            <ChatBubble
              key={message.messageId}
              mine={message.sender === "ME"}
              author={message.authorName}
              time={formatTime(message.sentAt)}
            >
              {message.body}
            </ChatBubble>
          ))}
          <div ref={bottomRef} />
        </ChatRoom>
      </div>

      <div className="mt-4 flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap gap-2">
          {DEVICE_CONTROLS.map((label) => (
            <Button key={label} variant="outline" disabled title={NOT_CONNECTED_TITLE}>
              {label}
            </Button>
          ))}
        </div>

        {/*
          와이어프레임의 「채팅」은 두지 않았습니다. 대화 패널을 접어도 화상이 좌우로
          늘어날 뿐 크게 볼 수 있는 것이 늘지 않아, 누를 값이 없는 버튼이었습니다.
        */}
        <Button variant="danger" onClick={() => setAsking(true)}>
          세션 종료
        </Button>
      </div>

      <Modal
        open={asking}
        onClose={() => setAsking(false)}
        dismissible={!ending}
        aria-labelledby="end-session-title"
      >
        <ModalHeader
          id="end-session-title"
          title="세션을 마칠까요?"
          description={`${counterpartName}님과의 컨설팅 세션`}
        />
        <ModalBody>
          마치면 녹음을 바탕으로 요약이 만들어지고 방으로 돌아갑니다.{" "}
          <strong className="font-semibold text-fg">다시 들어올 수 없습니다.</strong>
        </ModalBody>
        <ModalFooter>
          <Button variant="outline" onClick={() => setAsking(false)} disabled={ending}>
            취소
          </Button>
          <Button variant="danger" onClick={end} loading={ending}>
            세션 종료
          </Button>
        </ModalFooter>
      </Modal>
    </>
  );
}
