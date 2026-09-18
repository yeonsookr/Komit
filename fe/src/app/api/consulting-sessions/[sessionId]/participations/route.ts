import { NextResponse } from "next/server";
import { joinConsultingSession } from "@/lib/api/services";
import { parseNumericParam, toErrorResponse } from "@/lib/api/route";

const SESSION_NOT_FOUND = { code: "SESSION_NOT_FOUND", message: "세션을 찾을 수 없습니다." };

/**
 * POST /api/consulting-sessions/{sessionId}/participations — 세션 입장.
 *
 * 화상을 잇는 요청이 아닙니다. 들어갈 수 있는 때인지 판정하고 들어간 시각을 남깁니다.
 * 아직 이르거나 이미 끝난 세션이면 백엔드가 409 를 주고 그대로 전달합니다.
 */
export async function POST(
  _request: Request,
  { params }: { params: Promise<{ sessionId: string }> },
) {
  const { sessionId } = await params;
  const id = parseNumericParam(sessionId);
  if (id === null) return NextResponse.json(SESSION_NOT_FOUND, { status: 404 });

  try {
    return NextResponse.json(await joinConsultingSession(id), { status: 201 });
  } catch (error) {
    return toErrorResponse(error);
  }
}
