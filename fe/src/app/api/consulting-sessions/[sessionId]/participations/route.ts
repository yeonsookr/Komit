import { NextResponse } from "next/server";
import { getSessionParticipations, joinConsultingSession } from "@/lib/api/services";
import { parseNumericParam, toErrorResponse } from "@/lib/api/route";
import { SESSION_PENDING_CONSENT_STATUS } from "@/lib/session";

const SESSION_NOT_FOUND = { code: "SESSION_NOT_FOUND", message: "세션을 찾을 수 없습니다." };

/**
 * GET /api/consulting-sessions/{sessionId}/participations — 이 세션에 동의한 사람들.
 *
 * **목록에 있다는 것이 곧 동의했다는 뜻입니다** (`R-CON-17`). 세션 화면이 자기를 찾아
 * 보고, 없으면 방으로 돌려보냅니다.
 */
export async function GET(
  _request: Request,
  { params }: { params: Promise<{ sessionId: string }> },
) {
  const { sessionId } = await params;
  const id = parseNumericParam(sessionId);
  if (id === null) return NextResponse.json(SESSION_NOT_FOUND, { status: 404 });

  try {
    return NextResponse.json(await getSessionParticipations(id));
  } catch (error) {
    return toErrorResponse(error);
  }
}

/**
 * POST /api/consulting-sessions/{sessionId}/participations — 동의와 세션 입장.
 *
 * 화상을 잇는 요청이 아닙니다. 들어갈 수 있는 때인지 판정하고 동의와 입장을 기록합니다.
 *
 * **상태 코드를 그대로 넘깁니다.** 201 은 들어갔다는 뜻이고 202 는 상대의 동의를
 * 기다린다는 뜻이라(`R-CON-13`), 둘을 하나로 합치면 들어가지 못한 사람에게 세션
 * 화면이 열립니다. 아직 이르거나 이미 끝난 세션이면 백엔드가 409 를 주고 그대로
 * 전달합니다.
 */
export async function POST(
  _request: Request,
  { params }: { params: Promise<{ sessionId: string }> },
) {
  const { sessionId } = await params;
  const id = parseNumericParam(sessionId);
  if (id === null) return NextResponse.json(SESSION_NOT_FOUND, { status: 404 });

  try {
    const { entered, session } = await joinConsultingSession(id);
    return NextResponse.json(session, {
      status: entered ? 201 : SESSION_PENDING_CONSENT_STATUS,
    });
  } catch (error) {
    return toErrorResponse(error);
  }
}
