/** 이 계약에서 내가 맡은 쪽. 한 사용자가 계약마다 다른 역할로 참여할 수 있습니다. */
export type ScheduleRole = "CLIENT" | "DEVELOPER";

/** 내 일정에 표시하는 컨설팅 세션 한 건. */
export interface MySession {
  sessionId: number;
  contractId: number;
  /** 세션 방과 대화를 열 때 쓰는 방 식별자입니다. */
  roomId: number;
  role: ScheduleRole;
  /** CLIENT이면 개발자 이름, DEVELOPER이면 의뢰인 이름입니다. */
  counterpartName: string;
  /** 확정된 예약의 일시입니다. */
  scheduledAt: string;
  durationMinutes: number;
  price: number;
  endedAt: string | null;
  followUpUntil: string | null;
}

/** 잡힌 세션은 가까운 순, 지난 세션은 최근 순으로 옵니다. */
export interface MySchedule {
  upcoming: MySession[];
  past: MySession[];
}
