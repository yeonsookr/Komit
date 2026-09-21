"use client";

import * as React from "react";
import { clientApi } from "@/lib/api/client";
import { INTERNAL } from "@/lib/api/endpoints";
import {
  REALTIME_VERSION,
  type RealtimeChannel,
  type RealtimeEnvelope,
  type RealtimeTicket,
  type RealtimeType,
} from "@/types/realtime";

/**
 * WebSocket 이 사는 곳. **자기 도메인입니다** — 웹서버가 이 경로만 따로 받아
 * 스프링으로 넘깁니다. 백엔드 주소는 브라우저가 알지 않습니다.
 */
const SOCKET_PATH = "/service/ws/realtime";

/** 다시 붙기까지 기다리는 시간. 회선이 한꺼번에 돌아와 서버를 때리지 않게 늘려 갑니다. */
const RETRY_BASE_MS = 500;
const RETRY_MAX_MS = 10_000;

/** 연결이 어디까지 왔는지. 화면은 이 값으로 무엇을 보여줄지 정합니다. */
export type RealtimeStatus = "IDLE" | "CONNECTING" | "OPEN" | "RETRYING" | "CLOSED";

export interface UseRealtimeConnectionOptions {
  channel: RealtimeChannel;
  channelId: string;
  /** 거짓이면 아무것도 열지 않습니다. 상대를 기다리는 동안 쓰는 자리입니다. */
  enabled?: boolean;
  /** 쪽지가 왔을 때. 연결마다 하나만 답니다. */
  onMessage?: (envelope: RealtimeEnvelope) => void;
}

export interface RealtimeConnection {
  status: RealtimeStatus;
  /** 상대에게 쪽지를 보냅니다. 아직 안 열렸으면 거짓을 돌려줍니다. */
  send: (type: RealtimeType, payload?: unknown) => boolean;
}

/**
 * 채널 하나에 연결 하나를 엽니다.
 *
 * 티켓은 한 번 쓰면 사라지므로 다시 붙을 때마다 새로 받습니다. 끊긴 이유를 묻지 않고
 * 늘 새 티켓으로 시작하는 편이, 「아직 살아 있나」를 따지다 틀리는 것보다 낫습니다.
 *
 * 세션 화면은 통화용과 채팅용을 따로 엽니다. 한 연결에 둘을 얹으면 한쪽이 끊길 때
 * 다른 쪽도 같이 끊깁니다.
 */
export function useRealtimeConnection({
  channel,
  channelId,
  enabled = true,
  onMessage,
}: UseRealtimeConnectionOptions): RealtimeConnection {
  const [status, setStatus] = React.useState<RealtimeStatus>("IDLE");
  const socketRef = React.useRef<WebSocket | null>(null);

  // 쪽지를 받을 때마다 effect 를 다시 돌리면 연결이 끊깁니다. 최신 함수만 갈아 끼웁니다.
  const handlerRef = React.useRef(onMessage);
  React.useEffect(() => {
    handlerRef.current = onMessage;
  }, [onMessage]);

  React.useEffect(() => {
    if (!enabled) {
      setStatus("IDLE");
      return;
    }

    let alive = true;
    let attempt = 0;
    let retryTimer: ReturnType<typeof setTimeout> | undefined;

    const connect = async () => {
      if (!alive) return;
      setStatus(attempt === 0 ? "CONNECTING" : "RETRYING");

      let ticket: string;
      try {
        const { data } = await clientApi.post<RealtimeTicket>(INTERNAL.REALTIME_TICKETS, {
          channel,
          channelId,
        });
        ticket = data.ticket;
      } catch {
        // 티켓을 못 받는 이유는 회선일 수도, 아직 들어갈 때가 아닌 것일 수도 있습니다.
        // 어느 쪽이든 화면이 할 일은 같아서 나누지 않고 다시 시도합니다.
        scheduleRetry();
        return;
      }

      if (!alive) return;

      const scheme = window.location.protocol === "https:" ? "wss:" : "ws:";
      const socket = new WebSocket(
        `${scheme}//${window.location.host}${SOCKET_PATH}?ticket=${encodeURIComponent(ticket)}`,
      );
      socketRef.current = socket;

      socket.onopen = () => {
        if (!alive) return;
        attempt = 0;
        setStatus("OPEN");
      };

      socket.onmessage = (event) => {
        if (!alive) return;
        let envelope: RealtimeEnvelope;
        try {
          envelope = JSON.parse(event.data as string) as RealtimeEnvelope;
        } catch {
          return;
        }

        // 연결을 돌보는 쪽지는 여기서 끝냅니다. 화면이 알 필요가 없습니다.
        if (envelope.type === "SYSTEM_PING") {
          reply(socket, "SYSTEM_PONG", envelope);
          return;
        }
        if (envelope.type === "SYSTEM_PONG") return;

        handlerRef.current?.(envelope);
      };

      socket.onclose = () => {
        socketRef.current = null;
        if (!alive) return;
        scheduleRetry();
      };

      // 닫힘이 뒤따라 오므로 여기서는 다시 붙이지 않습니다. 두 번 예약됩니다.
      socket.onerror = () => socket.close();
    };

    const scheduleRetry = () => {
      if (!alive) return;
      setStatus("RETRYING");
      // 같은 순간에 끊긴 사람들이 한꺼번에 돌아오지 않게 흔들어 둡니다.
      const backoff = Math.min(RETRY_BASE_MS * 2 ** attempt, RETRY_MAX_MS);
      const jitter = Math.random() * backoff * 0.5;
      attempt += 1;
      retryTimer = setTimeout(() => void connect(), backoff + jitter);
    };

    void connect();

    return () => {
      alive = false;
      clearTimeout(retryTimer);
      setStatus("CLOSED");
      const socket = socketRef.current;
      socketRef.current = null;
      if (socket) {
        socket.onclose = null;
        socket.close();
      }
    };
  }, [channel, channelId, enabled]);

  const send = React.useCallback(
    (type: RealtimeType, payload?: unknown) => {
      const socket = socketRef.current;
      if (!socket || socket.readyState !== WebSocket.OPEN) return false;

      const envelope: RealtimeEnvelope = {
        version: REALTIME_VERSION,
        type,
        channel,
        channelId,
        payload: payload ?? null,
      };
      socket.send(JSON.stringify(envelope));
      return true;
    },
    [channel, channelId],
  );

  return { status, send };
}

function reply(socket: WebSocket, type: RealtimeType, to: RealtimeEnvelope) {
  socket.send(
    JSON.stringify({
      version: REALTIME_VERSION,
      type,
      requestId: to.requestId ?? null,
      channel: to.channel,
      channelId: to.channelId,
      payload: null,
    } satisfies RealtimeEnvelope),
  );
}
