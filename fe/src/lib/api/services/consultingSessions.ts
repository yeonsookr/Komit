import "server-only";

import { SESSION_PENDING_CONSENT_STATUS } from "@/lib/session";
import type { IceServersResponse } from "@/types/realtime";
import type {
  ApplySummaryChangesRequest,
  ConsultingSession,
  SessionRecording,
  SessionSummary,
} from "@/types/contract";
import { BACKEND } from "../endpoints";
import { authenticatedBackendApi } from "../server";

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
  const { data } = await authenticatedBackendApi().get<ConsultingSession>(
    BACKEND.CONSULTING_SESSION(sessionId),
  );
  return data;
}

/** 세션 입장 요청의 결과. */
export interface SessionJoinResult {
  /** 양쪽 동의가 모여 실제로 들어갔으면 참. 상대를 기다리는 중이면 거짓입니다. */
  entered: boolean;
  session: ConsultingSession;
}

/**
 * 세션 입장.
 *
 * **이 요청 하나가 동의와 입장을 겸합니다** (`R-CON-13`). 먼저 보낸 쪽은 아직 들어간
 * 것이 아니라 상대의 동의를 기다립니다. 양쪽이 모이면 그때 실제로 들어갑니다.
 *
 * 같은 사람이 기다리는 동안 다시 보내도 동의는 한 번만 기록되므로, 화면은 이 요청을
 * 되풀이해 201 로 바뀌는 때를 알 수 있습니다.
 *
 * 아직 열리지 않았거나 이미 끝난 세션이면 409 입니다.
 */
export async function joinConsultingSession(sessionId: number): Promise<SessionJoinResult> {
  const response = await authenticatedBackendApi().post<ConsultingSession>(
    BACKEND.SESSION_PARTICIPATIONS(sessionId),
    undefined,
  );

  // 202 만 대기로 봅니다. 그 밖의 2xx 는 들어간 것으로 다룹니다 — 이미 들어와 있는
  // 사람을 못 들어온 것으로 잘못 보면 세션 내내 대기 화면에 갇힙니다.
  return {
    entered: response.status !== SESSION_PENDING_CONSENT_STATUS,
    session: response.data,
  };
}

/**
 * 통화에 쓸 ICE 서버 목록.
 *
 * 브라우저가 상대를 찾고, 직접 못 찾으면 중계로 도는 데 쓰는 주소와 자격입니다.
 * 받은 값을 그대로 `RTCPeerConnection` 에 넣습니다.
 *
 * 발급한 뒤에는 취소할 수 없어서 수명이 곧 악용당할 수 있는 시간입니다. 그렇다고
 * 짧게 둘 수는 없습니다 — **통화 도중에 죽으면 중계를 다시 얻을 길이 없습니다.**
 * 그래서 수명을 한 통화보다 길게 두고 (`R-CON-19`), 화면은 통화를 열 때 한 번
 * 받아 그대로 씁니다.
 *
 * 세션 당사자가 아니면 404 입니다.
 */
export async function getSessionIceServers(sessionId: number): Promise<IceServersResponse> {
  const { data } = await authenticatedBackendApi().get<IceServersResponse>(
    BACKEND.SESSION_ICE_SERVERS(sessionId),
  );
  return data;
}

/**
 * 세션 종료.
 *
 * 끝내면 녹음을 바탕으로 요약이 만들어집니다. 만들어지기 전에 요약을 읽으면 409 입니다.
 */
export async function endConsultingSession(sessionId: number): Promise<ConsultingSession> {
  const { data } = await authenticatedBackendApi().post<ConsultingSession>(
    BACKEND.SESSION_END(sessionId),
    undefined,
  );
  return data;
}

/**
 * 세션 녹음 올리기.
 *
 * **자기 마이크 트랙만 올립니다** (`R-CON-14`). 누가 올렸는지는 토큰으로 정해지므로
 * 요청에 담지 않고, 서버가 화자를 붙여 돌려줍니다.
 *
 * 세션이 끝난 뒤에만 받습니다. 끝나기 전이면 400 입니다.
 */
export async function createSessionRecording(
  sessionId: number,
  body: { fileId: number },
): Promise<SessionRecording> {
  const { data } = await authenticatedBackendApi().post<SessionRecording>(
    BACKEND.SESSION_RECORDINGS(sessionId),
    body,
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
  const { data } = await authenticatedBackendApi().get<SessionSummary>(
    BACKEND.SESSION_SUMMARY(sessionId),
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
  const { data } = await authenticatedBackendApi().post<SessionSummary>(
    BACKEND.SESSION_SUMMARY_CHANGES(sessionId),
    body,
  );
  return data;
}
