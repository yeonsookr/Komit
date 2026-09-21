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
import {
  usePeerConnection,
  type CallStatus,
  type PeerConnection,
} from "@/hooks/usePeerConnection";
import { useRealtimeConnection, type RealtimeStatus } from "@/hooks/useRealtimeConnection";
import { useSessionRecorder } from "@/hooks/useSessionRecorder";
import type { RealtimeEnvelope, RtcSignalType } from "@/types/realtime";
import { ApiError } from "@/types/api";
import type { ChatMessage } from "@/types/chat";

/** 통화가 어디까지 왔는지 사람 말로. */
const CALL_NOTICE: Record<string, string> = {
  NEED_PERMISSION: "카메라와 마이크를 쓸 수 있게 해 주세요",
  PERMISSION_DENIED: "카메라와 마이크가 막혀 있습니다",
  WAITING_PEER: "상대를 기다리는 중입니다",
  CONNECTING: "연결하는 중입니다",
  PEER_AWAY: "상대 연결이 끊겼습니다. 잠시 기다리는 중입니다",
  ENDED: "통화를 마쳤습니다",
};

/**
 * 화면 한가운데에 적을 말.
 *
 * 멈춘 때를 먼저 가립니다. 그러지 않으면 영영 오지 않을 연결을 「연결하는 중」이라고
 * 적게 됩니다.
 */
function stageNotice(
  call: CallStatus,
  realtime: RealtimeStatus,
  reason: string | undefined,
): string {
  if (realtime === "FAILED") return reason ?? "연결할 수 없습니다";
  if (CALL_NOTICE[call]) return CALL_NOTICE[call];
  return realtime === "RETRYING" ? "연결이 끊겨 다시 붙는 중입니다" : "연결하는 중입니다";
}

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

/**
 * 영상 한 칸.
 *
 * `srcObject` 는 속성으로 못 넘겨 element 를 직접 만져야 합니다. 내 화면은 스스로
 * 들으면 소리가 되울리므로 반드시 음소거합니다.
 */
function VideoTile({
  stream,
  muted,
  label,
  placeholder,
  className,
}: Readonly<{
  stream: MediaStream | null;
  muted?: boolean;
  label: string;
  placeholder?: string;
  className?: string;
}>) {
  const ref = React.useRef<HTMLVideoElement>(null);

  React.useEffect(() => {
    if (ref.current) ref.current.srcObject = stream;
  }, [stream]);

  return (
    <div className={`relative overflow-hidden rounded-xl bg-ink ${className ?? ""}`}>
      <video
        ref={ref}
        autoPlay
        playsInline
        muted={muted}
        className={stream ? "h-full w-full object-cover" : "hidden"}
      />
      {!stream && (
        <div className="flex h-full w-full items-center justify-center px-6 text-center">
          <p className="text-[13px] leading-6 text-on-ink/70">{placeholder}</p>
        </div>
      )}
      <p className="absolute bottom-2 left-2 rounded bg-ink/70 px-2 py-0.5 text-[11px] font-bold text-on-ink">
        {label}
      </p>
    </div>
  );
}

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
  /**
   * 통화 제안이 엇갈렸을 때 물러설 쪽인지.
   *
   * **두 사람이 서로 다른 값을 받아야 합니다.** 같으면 둘 다 물러서거나 둘 다 버팁니다.
   * 의뢰인·개발자로 가르므로 누가 먼저 들어오든 값이 바뀌지 않습니다.
   */
  polite: boolean;
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
  polite,
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

  /*
    양쪽이 모인 뒤에만 엽니다. 기다리는 동안에는 소켓도 카메라도 켜지 않습니다
    (`R-CON-17`).
  */
  const joined = entry === "ENTERED";

  // 훅끼리 서로를 참조해 순서가 꼬이지 않게, 받은 쪽지를 잠깐 맡아 두는 자리입니다.
  const callRef = React.useRef<PeerConnection | null>(null);

  const realtime = useRealtimeConnection({
    channel: "CONSULTING_SESSION",
    channelId: String(sessionId),
    enabled: joined,
    onMessage: React.useCallback((envelope: RealtimeEnvelope) => {
      const call = callRef.current;
      if (!call) return;

      // 누가 붙고 떨어졌는지는 서버가 압니다. 통화 쪽지는 상대가 직접 보낸 것입니다.
      if (envelope.type === "SYSTEM_PEER_JOINED") return call.peerPresent();
      if (envelope.type === "SYSTEM_PEER_LEFT") return call.peerAway();

      call.accept(envelope.type as RtcSignalType, envelope.payload);
    }, []),
  });

  /*
    소켓은 통화를 **시작할 때** 필요합니다. 한 번 붙고 나면 영상과 소리는 상대와 직접
    흐르므로, 회선이 잠깐 튀어 다시 붙는 동안에도 카메라를 놓지 않습니다.
  */
  const [socketOpened, setSocketOpened] = React.useState(false);
  React.useEffect(() => {
    if (!joined) {
      setSocketOpened(false);
      return;
    }
    if (realtime.status === "OPEN") setSocketOpened(true);
  }, [joined, realtime.status]);

  const call = usePeerConnection({
    enabled: joined && socketOpened,
    polite,
    send: realtime.send,
  });

  React.useEffect(() => {
    callRef.current = call;
  }, [call]);

  /*
    통화가 열리면 자기 마이크만 담기 시작합니다. 입장 자체가 동의라 따로 묻지 않습니다
    (`R-CON-17`). 파일은 세션을 마친 뒤에 올립니다.
  */
  const recorder = useSessionRecorder(call.localStream);

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

  /**
   * 담아 둔 녹음을 올립니다.
   *
   * 올리지 못해도 방을 떠나는 것을 막지 않습니다. 한쪽 녹음이 없어도 요약은 만들어지고
   * (`R-CON-16`), 여기서 붙잡으면 사용자가 나갈 방법이 없습니다.
   */
  const uploadRecording = async () => {
    const file = await recorder.stop();
    if (!file) return;

    const body = new FormData();
    body.append("recording", file);

    try {
      await clientApi.post(INTERNAL.SESSION_RECORDINGS(sessionId), body);
    } catch {
      showToast({
        tone: "danger",
        title: "녹음을 올리지 못했습니다",
        description: "요약이 내 이야기를 빼고 만들어질 수 있습니다.",
      });
    }
  };

  const end = async () => {
    if (ending) return;
    setEnding(true);

    try {
      await clientApi.post(INTERNAL.SESSION_END(sessionId));
      // 상대 화면이 얼어붙은 채 남지 않게, 방을 뜨기 전에 나간다고 알립니다.
      call.hangUp();
      setAsking(false);
      // 녹음은 세션이 끝난 뒤에만 받습니다. 순서를 바꾸면 400 입니다.
      await uploadRecording();
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
          <VideoTile
            className="min-h-[320px] lg:min-h-[480px]"
            stream={call.remoteStream}
            label={counterpartName}
            placeholder={stageNotice(call.status, realtime.status, realtime.error?.message)}
          />

          <div className="flex items-center gap-3">
            {/* 공유 중에는 내가 무엇을 보내고 있는지가 카메라보다 중요합니다. */}
            <VideoTile
              className="h-[108px] w-[220px] max-w-[45%] shrink-0"
              stream={call.screenStream ?? call.localStream}
              muted
              label={call.screenStream ? `${myName} (화면 공유 중)` : `${myName} (나)`}
              placeholder="카메라 꺼짐"
            />
            {call.status === "PERMISSION_DENIED" && (
              <p className="text-[13px] leading-6 text-fg-muted">
                브라우저 주소창의 자물쇠에서 카메라와 마이크를 허용한 뒤 새로고침해 주세요.
                허용하지 않아도 대화는 쓸 수 있습니다.
              </p>
            )}
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
          <Button
            variant="outline"
            disabled={!call.localStream}
            aria-pressed={!call.micOn}
            onClick={call.toggleMic}
          >
            {call.micOn ? "마이크 끄기" : "마이크 켜기"}
          </Button>
          <Button
            variant="outline"
            disabled={!call.localStream}
            aria-pressed={!call.cameraOn}
            onClick={call.toggleCamera}
          >
            {call.cameraOn ? "카메라 끄기" : "카메라 켜기"}
          </Button>
          <Button
            variant="outline"
            disabled={!call.localStream}
            aria-pressed={Boolean(call.screenStream)}
            onClick={() => void call.toggleScreenShare()}
          >
            {call.screenStream ? "화면 공유 중지" : "화면 공유"}
          </Button>
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
