/**
 * 실시간 연결 타입. 서버의 `common/realtime/protocol` 과 같은 모양입니다.
 *
 * 영상·소리는 브라우저끼리 직접 오가고, 이 연결로는 그것을 트기 위한 쪽지만 오갑니다.
 */

/** 티켓 하나가 채널 하나를 엽니다. 다른 채널에는 쓸 수 없습니다. */
export type RealtimeChannel = "CONSULTING_SESSION" | "CHAT_ROOM";

/** 통화를 트기까지 주고받는 쪽지의 종류. 내용은 서버가 읽지 않습니다. */
export type RtcSignalType =
  | "RTC_PEER_READY"
  | "RTC_OFFER"
  | "RTC_ANSWER"
  | "RTC_ICE_CANDIDATE"
  | "RTC_ICE_RESTART"
  | "RTC_PEER_LEFT";

/** 연결 자체를 돌보는 쪽지. 서버가 보내는 것도 있습니다. */
export type RealtimeSystemType =
  | "SYSTEM_PING"
  | "SYSTEM_PONG"
  | "SYSTEM_ERROR"
  | "SYSTEM_REPLACED";

export type RealtimeType = RtcSignalType | RealtimeSystemType;

/** 지금 프로토콜 판. 서버가 다른 판을 받으면 연결을 닫습니다. */
export const REALTIME_VERSION = 1;

export interface RealtimeEnvelope<T = unknown> {
  version: number;
  type: RealtimeType;
  requestId?: string | null;
  channel: RealtimeChannel;
  channelId: string;
  payload?: T | null;
}

export interface CreateRealtimeTicketRequest {
  channel: RealtimeChannel;
  channelId: string;
}

export interface RealtimeTicket {
  ticket: string;
}
