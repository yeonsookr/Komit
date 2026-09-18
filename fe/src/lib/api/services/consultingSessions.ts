import "server-only";

import { bearerHeaders } from "@/lib/auth/session";
import type {
  ApplySummaryChangesRequest,
  ConsultingSession,
  SessionSummary,
} from "@/types/contract";
import { BACKEND } from "../endpoints";
import { backendApi } from "../server";

/**
 * 컨설팅 세션(영역 4).
 *
 * 세션은 계약서 안에 딸린 값이면서 그 자체로 열리고 닫히는 것이라 방이 아니라
 * **세션 식별자**로 다룹니다. 계약서를 다시 읽지 않아도 지금 들어갈 수 있는지,
 * 끝났는지를 이 API 하나로 알 수 있습니다.
 *
 * 파일 이름이 `session.ts`(사용자 로그인 세션)와 헷갈리지 않게 `consultingSessions`
 * 입니다. 그쪽은 브라우저가 부르는 로그인 관련 요청이고 여기는 서버가 읽는 상담 세션입니다.
 */

/** 세션 하나. 잡힌 일정과 시작·종료 시각이 들어 있습니다. */
export async function getConsultingSession(sessionId: number): Promise<ConsultingSession> {
  const { data } = await backendApi().get<ConsultingSession>(
    BACKEND.CONSULTING_SESSION(sessionId),
    await bearerHeaders(),
  );
  return data;
}

/**
 * 세션 입장.
 *
 * 아직 열리지 않은 세션이면 409 입니다 — 시작 시각보다 한참 전에는 들어갈 수 없습니다.
 */
export async function joinConsultingSession(sessionId: number): Promise<ConsultingSession> {
  const { data } = await backendApi().post<ConsultingSession>(
    BACKEND.SESSION_PARTICIPATIONS(sessionId),
    undefined,
    await bearerHeaders(),
  );
  return data;
}

/**
 * 세션 종료.
 *
 * 끝내면 녹음을 바탕으로 요약이 만들어집니다. 만들어지기 전에 요약을 읽으면 409 입니다.
 */
export async function endConsultingSession(sessionId: number): Promise<ConsultingSession> {
  const { data } = await backendApi().post<ConsultingSession>(
    BACKEND.SESSION_END(sessionId),
    undefined,
    await bearerHeaders(),
  );
  return data;
}

/**
 * 세션 요약.
 *
 * 세션이 끝나도 요약이 바로 서지는 않습니다. 녹음을 글로 옮기고 개발자가 검토하는
 * 동안에는 **409 `SUMMARY_NOT_READY`** 입니다. 없는 세션(404)과 갈라 다뤄야 합니다.
 */
export async function getSessionSummary(sessionId: number): Promise<SessionSummary> {
  const { data } = await backendApi().get<SessionSummary>(
    BACKEND.SESSION_SUMMARY(sessionId),
    await bearerHeaders(),
  );
  return data;
}

/**
 * 요약의 변경 후보를 요청서에 반영.
 *
 * **요약을 읽는 것과 요청서를 고치는 것은 다른 일입니다.** 세션에서 정해진 것이 저절로
 * 요청서에 들어가지 않고, 의뢰인이 고른 것만 이 요청으로 반영됩니다.
 */
export async function applySummaryChanges(
  sessionId: number,
  body: ApplySummaryChangesRequest,
): Promise<SessionSummary> {
  const { data } = await backendApi().post<SessionSummary>(
    BACKEND.SESSION_SUMMARY_CHANGES(sessionId),
    body,
    await bearerHeaders(),
  );
  return data;
}
