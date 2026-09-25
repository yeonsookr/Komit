import { NextResponse } from "next/server";
import { getSessionIceServers } from "@/lib/api/services";
import { parseNumericParam, toErrorResponse } from "@/lib/api/route";

const SESSION_NOT_FOUND = { code: "SESSION_NOT_FOUND", message: "세션을 찾을 수 없습니다." };

/**
 * GET /api/consulting-sessions/{sessionId}/ice-servers — 통화에 쓸 주소와 자격.
 *
 * **자격을 화면 코드에 두지 않는 이유가 이 경로입니다** (`R-CON-19`). 중계 서버의
 * 비밀은 서버에만 있고, 브라우저는 세션 당사자임을 확인받은 뒤 시한부 자격만
 * 받아 갑니다.
 */
export async function GET(
  _request: Request,
  { params }: { params: Promise<{ sessionId: string }> },
) {
  const { sessionId } = await params;
  const id = parseNumericParam(sessionId);
  if (id === null) return NextResponse.json(SESSION_NOT_FOUND, { status: 404 });

  try {
    return NextResponse.json(await getSessionIceServers(id));
  } catch (error) {
    return toErrorResponse(error);
  }
}
