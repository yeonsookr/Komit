/**
 * 컨설팅 세션의 표시 규칙. 화면에서 분기하지 않도록 순수 함수로 빼 둡니다.
 */
import type { ConsultingSession, Contract } from "@/types/contract";

/** R-CON-08: 컨설팅 세션은 시작 전 정해진 시간부터 들어갈 수 있다. */
export const SESSION_OPEN_BEFORE_MINUTES = 10;

/**
 * 입장 요청이 「상대 동의를 기다리는 중」으로 돌아올 때의 상태 코드.
 *
 * R-CON-13: 양쪽이 모두 동의해야 실제로 들어갑니다. 먼저 보낸 쪽은 201 이 아니라
 * 이 값을 받습니다. **2xx 라서 오류로 잡히지 않으므로** 값을 잃으면 들어가지 못한
 * 사람에게 세션 화면이 그대로 열립니다.
 *
 * 서버가 읽는 곳(`api/services/consultingSessions.ts`)과 브라우저가 읽는
 * 곳(`SessionStage`)이 같은 값을 봐야 해서 `server-only` 가 아닌 여기 둡니다.
 */
export const SESSION_PENDING_CONSENT_STATUS = 202;

const MINUTE = 60 * 1000;

/**
 * 세션이 지금 어느 때인지.
 *
 * - `BEFORE` — 아직 이릅니다. 들어갈 수 없습니다.
 * - `OPEN` — 들어갈 수 있습니다. 시작 전 잠깐과 진행 중이 여기 듭니다.
 * - `ENDED` — 끝났습니다. 요약으로 넘어갑니다.
 */
export type SessionPhase = "BEFORE" | "OPEN" | "ENDED";

export function sessionPhase(session: ConsultingSession, now: Date = new Date()): SessionPhase {
  if (session.endedAt !== null) return "ENDED";

  const scheduledAt = new Date(session.reservation.scheduledAt).getTime();
  const opensAt = scheduledAt - SESSION_OPEN_BEFORE_MINUTES * MINUTE;
  return now.getTime() >= opensAt ? "OPEN" : "BEFORE";
}

/**
 * 실제로 얼마나 했는지(분). 끝나지 않았으면 null 입니다.
 *
 * 계약서에 적힌 `durationMinutes` 와 다를 수 있습니다 — 그쪽은 하기로 한 시간이고
 * 이것은 한 시간입니다. 끝난 뒤에는 실제로 한 시간을 적습니다.
 */
export function sessionElapsedMinutes(session: ConsultingSession): number | null {
  if (session.startedAt === null || session.endedAt === null) return null;

  const elapsed = new Date(session.endedAt).getTime() - new Date(session.startedAt).getTime();
  return elapsed <= 0 ? 0 : Math.round(elapsed / MINUTE);
}

/**
 * 이 계약의 세션. 지금은 계약 하나에 세션 하나입니다.
 *
 * **예약이 확정된 것만 돌려줍니다.** 협의 중인 일시는 아직 약속이 아니라 제안이라
 * 들어갈 자리가 없습니다. 확정 여부는 서명이 아니라 예약 상태로 봅니다 — 양쪽이
 * 서명하면 예약이 `CONFIRMED` 가 되고 그 뒤에는 일정을 바꾸지 않기 때문에, 그 한
 * 값이 이미 「양쪽이 합의한 일시」를 뜻합니다 (R-CON-01).
 */
export function confirmedSession(contract: Contract | null): ConsultingSession | null {
  if (contract === null || contract.contractType !== "CONSULTING") return null;

  const session = contract.consulting?.sessions[0] ?? null;
  return session?.reservation.status === "CONFIRMED" ? session : null;
}
