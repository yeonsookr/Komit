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
import { uploadFromBrowser } from "@/lib/upload/browserUpload";
import {
  usePeerConnection,
  type CallStatus,
  type PeerConnection,
} from "@/hooks/usePeerConnection";
import { useRealtimeConnection, type RealtimeStatus } from "@/hooks/useRealtimeConnection";
import { useSessionRecorder } from "@/hooks/useSessionRecorder";
import type { IceServersResponse, RealtimeEnvelope, RtcSignalType } from "@/types/realtime";
import { ApiError } from "@/types/api";
import { useChatChannel } from "@/hooks/useChatChannel";
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
  /** 내 사용자 식별자. 어느 말이 내 것인지 가릅니다. */
  myUserId: number;
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
  myUserId,
  chatRefId,
  initialMessages,
  polite,
}: Readonly<SessionStageProps>) {
  const router = useRouter();
  const showToast = useToast();

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
    동의한 순간부터 채널에 들어갑니다. 상대가 들어온 것을 서버가 알려 줄 수 있어야 하기
    때문입니다. 실제 입장과 세션 시작은 입장 API 가 막습니다 (`R-CON-17`).
  */
  const consented = entry === "PENDING" || entry === "ENTERED";
  const joined = entry === "ENTERED";

  /*
    상대가 채널에 들어왔다는 알림을 받으면 그때 한 번만 다시 묻습니다.

    상대가 동의하는 순간 서버 안에서는 두 사람 모두 입장 처리가 끝나지만, 기다리던 쪽은
    그 응답을 받은 적이 없어 `201` 본문을 가져오려면 한 번은 물어야 합니다.
  */
  const enteredRef = React.useRef(false);
  React.useEffect(() => {
    enteredRef.current = joined;
  }, [joined]);

  const askAgain = React.useCallback(() => {
    if (enteredRef.current) return;
    void join().then(setEntry);
  }, [join]);

  // 쪽지 처리는 다시 만들어지지 않게 빈 의존성으로 두므로, 최신 함수를 여기 맡겨 둡니다.
  const askAgainRef = React.useRef(askAgain);
  React.useEffect(() => {
    askAgainRef.current = askAgain;
  }, [askAgain]);

  /*
    세션이 끝났다는 서버 알림을 받는 자리입니다.

    **누가 눌렀든 양쪽이 같은 쪽지를 받습니다.** 끝낸 사람 쪽은 자기 응답으로 이미
    마감했고, 쪽지도 회선이 튀면 두 번 올 수 있습니다. `finishedRef` 로 한 번만 돕니다.
  */
  const finishRef = React.useRef<() => void>(() => {});
  const finishedRef = React.useRef(false);

  // 훅끼리 서로를 참조해 순서가 꼬이지 않게, 받은 쪽지를 잠깐 맡아 두는 자리입니다.
  const callRef = React.useRef<PeerConnection | null>(null);

  const realtime = useRealtimeConnection({
    channel: "CONSULTING_SESSION",
    channelId: String(sessionId),
    enabled: consented,
    onMessage: React.useCallback(
      (envelope: RealtimeEnvelope) => {
        // 참가 알림은 통화보다 먼저 봅니다. 기다리는 동안에는 통화가 아직 없어서,
        // 아래 빗장에 걸리면 정작 기다리던 소식을 버리게 됩니다.
        if (envelope.type === "SYSTEM_PEER_JOINED") {
          askAgainRef.current();
          callRef.current?.peerPresent();
          return;
        }

        if (envelope.type === "SYSTEM_SESSION_ENDED") {
          finishRef.current();
          return;
        }

        const call = callRef.current;
        if (!call) return;

        // 누가 떨어졌는지는 서버가 압니다. 통화 쪽지는 상대가 직접 보낸 것입니다.
        if (envelope.type === "SYSTEM_PEER_LEFT") return call.peerAway();

        call.accept(envelope.type as RtcSignalType, envelope.payload);
      },
      [],
    ),
  });

  /*
    소켓은 통화를 **시작할 때** 필요합니다. 한 번 붙고 나면 영상과 소리는 상대와 직접
    흐르므로, 회선이 잠깐 튀어 다시 붙는 동안에도 카메라를 놓지 않습니다.
  */
  const [socketOpened, setSocketOpened] = React.useState(false);
  React.useEffect(() => {
    if (!consented) {
      setSocketOpened(false);
      return;
    }
    if (realtime.status === "OPEN") setSocketOpened(true);
  }, [consented, realtime.status]);

  /*
    통화에 쓸 주소와 자격. 비밀은 서버에만 있고 화면은 시한부 자격만 받아 씁니다
    (`R-CON-19`). 통화를 열 때 한 번 부릅니다 — 수명이 한 통화보다 길어 도중에
    다시 받을 일이 없습니다.
  */
  const loadIceServers = React.useCallback(async () => {
    const { data } = await clientApi.get<IceServersResponse>(
      INTERNAL.SESSION_ICE_SERVERS(sessionId),
    );
    return data.iceServers;
  }, [sessionId]);

  /*
    동의한 사람은 기다리는 동안에도 자기 카메라를 켭니다. 상대는 아직 통화가 없어 내
    화면도 소리도 받지 못하므로 감출 것이 없고, 들어가기 전에 장치를 확인할 수 있습니다.
  */
  const call = usePeerConnection({
    enabled: consented && socketOpened,
    polite,
    send: realtime.send,
    loadIceServers,
  });

  React.useEffect(() => {
    callRef.current = call;
  }, [call]);

  /*
    담기 시작하는 것은 **세션이 시작된 뒤**입니다. 카메라가 켜졌다고 담지 않습니다 —
    기다리는 동안의 미리보기까지 들어가면 안 됩니다. 입장 자체가 동의라 따로 묻지
    않습니다 (`R-CON-17`). 파일은 세션을 마친 뒤에 올립니다.
  */
  const recorder = useSessionRecorder(call.localStream, joined);

  /* 대화는 통화와 다른 연결로 엽니다. 한 연결에 둘을 얹으면 한쪽이 끊길 때 같이 끊깁니다. */
  const chat = useChatChannel({ chatRefId, initialMessages });

  React.useEffect(() => {
    bottomRef.current?.scrollIntoView({ block: "end" });
  }, [chat.messages.length]);


  /*
    올리지 못한 녹음을 쥐고 있는 자리입니다.

    **성공할 때까지 버리지 않습니다.** 조각은 멈추는 순간 파일 하나로 묶이고 그 뒤로는
    다시 만들 수 없어서, 여기서 놓으면 그 사람의 이야기가 요약에서 통째로 빠집니다.
  */
  const [pendingRecording, setPendingRecording] = React.useState<File | null>(null);
  const [uploading, setUploading] = React.useState(false);

  /**
   * 녹음을 저장소에 올리고 세션에 붙입니다.
   *
   * 파일 본문은 브라우저가 저장소로 직접 보냅니다. 우리 서버는 주소를 내주고 완료를 확인할
   * 뿐 십수 MiB 를 받았다 다시 보내지 않습니다.
   */
  const uploadRecording = React.useCallback(
    async (file: File) => {
      setUploading(true);
      try {
        const uploaded = await uploadFromBrowser(file, "SESSION_RECORDING");
        await clientApi.post(INTERNAL.SESSION_RECORDINGS(sessionId), {
          fileId: uploaded.fileId,
        });
        setPendingRecording(null);
        return true;
      } catch {
        setPendingRecording(file);
        showToast({
          tone: "danger",
          title: "녹음을 올리지 못했습니다",
          description: "다시 시도할 수 있습니다. 창을 닫으면 녹음이 사라집니다.",
        });
        return false;
      } finally {
        setUploading(false);
      }
    },
    [sessionId, showToast],
  );

  const leave = React.useCallback(() => {
    router.push(ROUTES.room(roomId));
    router.refresh();
  }, [roomId, router]);

  /*
    세션을 마칠 때 하는 일. 녹음을 마감해 올리고 방으로 돌아갑니다.

    **올리지 못했으면 나가지 않습니다.** 나가면 화면이 사라져 쥐고 있던 파일도 함께
    사라지고, 조각은 이미 묶여 다시 만들 수 없습니다. 남아 있어야 다시 시도할 수 있습니다.
  */
  const finish = React.useCallback(async () => {
    if (finishedRef.current) return;
    finishedRef.current = true;
    setEnding(true);

    // 상대 화면이 얼어붙은 채 남지 않게, 방을 뜨기 전에 나간다고 알립니다.
    call.hangUp();

    const file = await recorder.stop();
    if (file && !(await uploadRecording(file))) return;

    leave();
  }, [call, leave, recorder, uploadRecording]);

  React.useEffect(() => {
    finishRef.current = () => void finish();
  }, [finish]);

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

      /*
        쪽지를 기다리지 않고 여기서 바로 마감합니다. 쪽지는 상대를 위한 것이고,
        중계가 막히면 오지 않을 수도 있습니다. 그때 기다리기만 하면 끝낸 사람의 녹음이
        영영 올라가지 않습니다. 쪽지가 뒤이어 와도 `finish` 는 한 번만 돕니다.
      */
      await finish();
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
      <>
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

        {/*
          기다리는 동안 자기 모습을 미리 봅니다. 상대는 아직 통화가 없어 이 화면도 소리도
          받지 못합니다. 들어가기 전에 카메라가 제대로 잡혔는지 확인할 수 있습니다.
          녹음은 여기서 시작되지 않습니다 — 세션이 시작된 뒤입니다.
        */}
        {entry === "PENDING" && (
          <VideoTile
            className="mt-4 h-[200px] w-full max-w-[360px]"
            stream={call.localStream}
            label={`${myName} (나)`}
            muted
            placeholder="카메라를 켜는 중입니다"
          />
        )}
      </>
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
          footer={<ChatComposer onSend={chat.send} disabled={chat.status !== "OPEN"} />}
        >
          {chat.messages.map((message) => (
            <ChatBubble
              key={message.messageId}
              mine={message.senderUserId === myUserId}
              author={message.senderUserId === myUserId ? myName : counterpartName}
              time={formatTime(message.sentAt)}
            >
              {message.content}
            </ChatBubble>
          ))}
          <div ref={bottomRef} />
        </ChatRoom>
      </div>

      {pendingRecording && (
        <Callout
          className="mt-4"
          tone="danger"
          title="녹음을 올리지 못했습니다"
          action={
            <Button
              variant="outline"
              disabled={uploading}
              onClick={() =>
                void uploadRecording(pendingRecording).then((ok) => {
                  if (ok) leave();
                })
              }
            >
              {uploading ? "올리는 중" : "다시 시도"}
            </Button>
          }
        >
          이 창을 닫으면 녹음이 사라져 요약에 내 이야기가 빠집니다.
        </Callout>
      )}

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
