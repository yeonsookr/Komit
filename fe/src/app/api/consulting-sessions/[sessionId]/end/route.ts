import { NextResponse } from "next/server";
import { endConsultingSession } from "@/lib/api/services";
import { parseNumericParam, toErrorResponse } from "@/lib/api/route";

const SESSION_NOT_FOUND = { code: "SESSION_NOT_FOUND", message: "세션을 찾을 수 없습니다." };

/**
 * POST /api/consulting-sessions/{sessionId}/end — 세션 종료.
 *
 * 끝낸 시각이 남고 요약 만들기가 시작됩니다. 같은 요청을 다시 보내도 결과가 같아,
 * 두 사람이 각자 끝내도 어긋나지 않습니다.
 */
export async function POST(
  _request: Request,
  { params }: { params: Promise<{ sessionId: string }> },
) {
  const { sessionId } = await params;
  const id = parseNumericParam(sessionId);
  if (id === null) return NextResponse.json(SESSION_NOT_FOUND, { status: 404 });

  try {
    return NextResponse.json(await endConsultingSession(id));
  } catch (error) {
    return toErrorResponse(error);
  }
}
