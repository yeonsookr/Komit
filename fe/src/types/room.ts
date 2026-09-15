/**
 * 방 관련 타입. 목 Swagger `/service/docs` 영역 3 의 RoomSummaryResponse ·
 * RoomDetailResponse 와 필드가 같습니다. 명세에 없는 필드를 더하지 않습니다.
 */

/** 방 용도. INQUIRY 무료 상담 · CONSULTING 컨설팅 · DEVELOPMENT 개발. */
export type RoomType = "INQUIRY" | "CONSULTING" | "DEVELOPMENT";

/** 갈래가 정해지면 상담방은 닫힙니다. */
export type RoomStatus = "OPEN" | "CLOSED";

/** 사이드바에 한 줄로 나가는 방. */
export interface RoomSummary {
  roomId: number;
  /** 추천을 거치지 않고 직접 찾아 연 방이면 null. */
  recommendationItemId: number | null;
  type: RoomType;
  status: RoomStatus;
  projectId: number;
  projectName: string;
  /**
   * 이 방의 개발자 식별자(사용자 식별자가 아님).
   * 내 developerId 와 같으면 '맡은 의뢰', 다르거나 내가 개발자가 아니면 '맡긴 의뢰'.
   */
  developerId: number;
  /** 상대 표시 이름. 탈퇴했으면 `탈퇴한 사용자`. */
  counterpartName: string;
  /** 메시지는 이 API 가 다루지 않습니다. 채팅 쪽에서 이 키로 가져옵니다. */
  chatRefId: string;
  openedAt: string;
}

/** 방 머리에 붙는 요청서 요약. 요청서는 방이 아니라 프로젝트에 달립니다. */
export interface RoomRequestSummary {
  diagnosisId: number;
  issueCount: number;
  updatedAt: string;
}

/** 방 상세. 목록에 쓰는 값에 요청서 요약과 이어진 방을 더합니다. */
export interface RoomDetail extends RoomSummary {
  /** 아직 진단하지 않은 프로젝트의 방이면 null. */
  request: RoomRequestSummary | null;
  /** 아직 계약서를 쓰지 않았으면 null. 방 하나에 계약은 하나. */
  contractId: number | null;
  /** 갈래를 정해 새로 열린 방. '개발 방으로' 버튼이 여기로 보냅니다. */
  convertedRoomId: number | null;
  /** convertedRoomId 와 늘 짝으로 차고 빕니다. 버튼 문구를 이 값으로 고릅니다. */
  convertedRoomType: "CONSULTING" | "DEVELOPMENT" | null;
}

export interface RoomListResponse {
  content: RoomSummary[];
}

/** 방 목록 조회 조건. 사이드바는 전부 보여주므로 거르지 않습니다. */
export interface RoomListParams {
  type?: RoomType;
  status?: RoomStatus;
}
