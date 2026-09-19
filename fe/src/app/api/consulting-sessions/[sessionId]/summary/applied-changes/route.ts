import { NextResponse } from "next/server";
import { applySummaryChanges } from "@/lib/api/services";
import { parseNumericParam, toErrorResponse } from "@/lib/api/route";
import type { ApplySummaryChangesRequest } from "@/types/contract";

const SESSION_NOT_FOUND = { code: "SESSION_NOT_FOUND", message: "세션을 찾을 수 없습니다." };

/**
 * POST /api/consulting-sessions/{sessionId}/summary/applied-changes
 * — 고른 변경 후보를 요청서에 반영.
 *
 * 요약을 읽는 것과 요청서를 고치는 것은 다른 일입니다. 여기 실린 후보만 들어가고,
 * 같은 후보를 다시 보내도 두 번 들어가지 않습니다.
 */
export async function POST(
  request: Request,
  { params }: { params: Promise<{ sessionId: string }> },
) {
  const { sessionId } = await params;
  const id = parseNumericParam(sessionId);
  if (id === null) return NextResponse.json(SESSION_NOT_FOUND, { status: 404 });

  try {
    const body = (await request.json()) as ApplySummaryChangesRequest;
    return NextResponse.json(await applySummaryChanges(id, body), { status: 201 });
  } catch (error) {
    return toErrorResponse(error);
  }
}
