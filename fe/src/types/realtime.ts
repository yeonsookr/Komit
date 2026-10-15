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

/** 브라우저를 다시 열었을 때 이전 연결의 SDP/ICE를 섞지 않기 위한 협상 세대입니다. */
export type RtcNegotiationId = string;

export interface RtcPeerPayload {
  negotiationId: RtcNegotiationId;
}

export interface RtcDescriptionPayload extends RtcPeerPayload {
  description: RTCSessionDescriptionInit;
}

export interface RtcIceCandidatePayload extends RtcPeerPayload {
  candidate: RTCIceCandidateInit;
}

export interface RtcSignalPayloadByType {
  RTC_PEER_READY: RtcPeerPayload;
  RTC_OFFER: RtcDescriptionPayload;
  RTC_ANSWER: RtcDescriptionPayload;
  RTC_ICE_CANDIDATE: RtcIceCandidatePayload;
  RTC_ICE_RESTART: RtcPeerPayload;
  RTC_PEER_LEFT: RtcPeerPayload;
}

export type RtcSignalPayload = RtcSignalPayloadByType[RtcSignalType];

/** 연결 자체를 돌보는 쪽지. 서버가 보내는 것도 있습니다. */
export type RealtimeSystemType =
  | "SYSTEM_PING"
  | "SYSTEM_PONG"
  | "SYSTEM_ERROR"
  | "SYSTEM_REPLACED"
  | "SYSTEM_PEER_JOINED"
  | "SYSTEM_PEER_LEFT"
  | "SYSTEM_SESSION_ENDED";

/**
 * 채널에 누가 붙고 떨어졌는지 알리는 쪽지의 내용.
 *
 * `SYSTEM_PEER_LEFT` 는 **나갔다는 뜻이 아닙니다.** 회선이 잠깐 끊겨도 옵니다.
 */
export interface RealtimePeerPayload {
  userId: number;
}

/** 대화에서 오가는 쪽지. 올릴 때도 받을 때도 같은 종류를 씁니다. */
export type ChatType = "CHAT_MESSAGE";

export type RealtimeType = RtcSignalType | RealtimeSystemType | ChatType;

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

/**
 * 통화를 열 때 받아 가는 ICE 서버 한 줄.
 *
 * 브라우저의 `RTCIceServer` 와 같은 모양이라 그대로 `RTCPeerConnection` 에 넣습니다.
 * STUN 은 자격을 받지 않아 아이디와 비밀번호가 없습니다.
 */
export interface IceServer {
  urls: string[];
  username?: string;
  credential?: string;
}

export interface IceServersResponse {
  iceServers: IceServer[];
  /**
   * 중계 자격이 죽는 시각. 중계 서버를 두지 않은 환경에서는 없습니다.
   *
   * 수명이 한 통화보다 길어서(`R-CON-19`) 화면은 이 값을 보고 움직이지 않습니다.
   * 그 전제가 깨졌는지 확인할 때 쓰는 값입니다.
   */
  expiresAt?: string;
}
