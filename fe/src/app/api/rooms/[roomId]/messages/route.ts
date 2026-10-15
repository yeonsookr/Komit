import { NextResponse } from "next/server";
import { getRoomMessages } from "@/lib/api/services";
import { parseNumericParam, toErrorResponse } from "@/lib/api/route";

const CHAT_NOT_FOUND = { code: "CHAT_NOT_FOUND", message: "대화를 찾을 수 없습니다." };

/**
 * GET /api/rooms/{roomId}/messages — 방의 지난 대화.
 *
 * 위로 올려 읽을 때 부릅니다. 첫 묶음은 서버 컴포넌트가 이미 내려보내므로 여기로
 * 오는 것은 `before` 가 붙은 요청입니다.
 */
export async function GET(
  request: Request,
  { params }: { params: Promise<{ roomId: string }> },
) {
  const { roomId } = await params;
  const id = parseNumericParam(roomId);

  if (id === null) {
    return NextResponse.json(CHAT_NOT_FOUND, { status: 404 });
  }

  try {
    const before = new URL(request.url).searchParams.get("before") ?? undefined;
    return NextResponse.json(await getRoomMessages(id, before));
  } catch (error) {
    return toErrorResponse(error);
  }
}
