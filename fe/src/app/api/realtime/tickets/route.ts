import { NextResponse } from "next/server";
import { createRealtimeTicket } from "@/lib/api/services";
import { toErrorResponse } from "@/lib/api/route";
import type { RealtimeChannel } from "@/types/realtime";

const INVALID_REQUEST = {
  code: "INVALID_REQUEST",
  message: "요청 형식이 올바르지 않습니다.",
};

const CHANNELS: RealtimeChannel[] = ["CONSULTING_SESSION", "CHAT_ROOM"];

/**
 * POST /api/realtime/tickets — 실시간 연결 입장권.
 *
 * 브라우저는 쿠키의 토큰을 직접 읽을 수 없어 WebSocket 에 인증 헤더를 못 붙입니다.
 * 그래서 인증이 붙는 이 경로로 먼저 짧게 사는 티켓을 받고, 연결할 때 그것만 넘깁니다.
 */
export async function POST(request: Request) {
  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return NextResponse.json(INVALID_REQUEST, { status: 422 });
  }

  const { channel, channelId } = (body ?? {}) as {
    channel?: unknown;
    channelId?: unknown;
  };

  if (
    typeof channel !== "string" ||
    !CHANNELS.includes(channel as RealtimeChannel) ||
    typeof channelId !== "string" ||
    channelId.trim() === ""
  ) {
    return NextResponse.json(INVALID_REQUEST, { status: 422 });
  }

  try {
    const ticket = await createRealtimeTicket({
      channel: channel as RealtimeChannel,
      channelId,
    });
    return NextResponse.json(ticket, { status: 200 });
  } catch (error) {
    return toErrorResponse(error);
  }
}
