import { NextResponse } from "next/server";
import { createSessionRecording } from "@/lib/api/services";
import { parseNumericParam, toErrorResponse } from "@/lib/api/route";

const INVALID_REQUEST = {
  code: "INVALID_REQUEST",
  message: "녹음 파일을 찾지 못했습니다.",
};

const SESSION_NOT_FOUND = { code: "SESSION_NOT_FOUND", message: "세션을 찾을 수 없습니다." };

/**
 * POST /api/consulting-sessions/[sessionId]/recordings — 올린 녹음을 세션에 붙입니다.
 *
 * **파일은 이 핸들러를 지나가지 않습니다.** 브라우저가 저장소로 직접 보내고 받은 `fileId` 만
 * 여기로 옵니다. 서버가 십수 MiB 를 받았다 다시 보내면 메모리를 그만큼 쓰고 두 번 이동합니다.
 *
 * 누가 올렸는지는 토큰으로 정해집니다. 요청에 화자를 담지 않습니다. (R-CON-14)
 */
export async function POST(
  request: Request,
  { params }: { params: Promise<{ sessionId: string }> },
) {
  const sessionId = parseNumericParam((await params).sessionId);
  if (sessionId === null) {
    return NextResponse.json(SESSION_NOT_FOUND, { status: 404 });
  }

  let fileId: unknown;
  try {
    ({ fileId } = (await request.json()) as { fileId?: unknown });
  } catch {
    return NextResponse.json(INVALID_REQUEST, { status: 422 });
  }

  if (typeof fileId !== "number" || !Number.isInteger(fileId) || fileId <= 0) {
    return NextResponse.json(INVALID_REQUEST, { status: 422 });
  }

  try {
    return NextResponse.json(await createSessionRecording(sessionId, { fileId }), {
      status: 201,
    });
  } catch (error) {
    return toErrorResponse(error);
  }
}
