import "server-only";

import { authenticatedBackendApi } from "../server";
import { BACKEND } from "../endpoints";
import type { CreateRealtimeTicketRequest, RealtimeTicket } from "@/types/realtime";

/**
 * 실시간 연결 입장권을 받습니다.
 *
 * 브라우저는 쿠키의 토큰을 읽을 수 없어 WebSocket 에 인증 헤더를 붙이지 못합니다.
 * 그래서 인증된 요청으로 짧게 사는 티켓을 먼저 받고, 연결할 때 그 티켓만 넘깁니다.
 *
 * 당사자인지·양쪽이 동의했는지·입장 시각이 됐는지는 이 요청에서 모두 판정합니다.
 * 연결이 붙은 뒤에는 다시 묻지 않습니다.
 */
export async function createRealtimeTicket(
  body: CreateRealtimeTicketRequest,
): Promise<RealtimeTicket> {
  const { data } = await authenticatedBackendApi().post<RealtimeTicket>(
    BACKEND.REALTIME_TICKETS,
    body,
  );
  return data;
}
