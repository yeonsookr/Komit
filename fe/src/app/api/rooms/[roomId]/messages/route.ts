import { NextResponse } from "next/server";
import { getChatMessages, getRoom } from "@/lib/api/services";
import { parseNumericParam, toErrorResponse } from "@/lib/api/route";

const ROOM_NOT_FOUND = { code: "ROOM_NOT_FOUND", message: "방을 찾을 수 없습니다." };

/**
 * GET /api/rooms/{roomId}/messages — 방의 메시지.
 *
 * 방을 먼저 조회해 chatRefId 를 얻습니다. 없는 방이면 여기서 404 로 끊기므로
 * 채팅이 실제 서버로 바뀌어도 권한 판단 자리는 그대로입니다.
 */
export async function GET(
  _request: Request,
  { params }: { params: Promise<{ roomId: string }> },
) {
  const { roomId } = await params;
  const id = parseNumericParam(roomId);

  if (id === null) {
    return NextResponse.json(ROOM_NOT_FOUND, { status: 404 });
  }

  try {
    const room = await getRoom(id);
    return NextResponse.json({ content: await getChatMessages(room.chatRefId) });
  } catch (error) {
    return toErrorResponse(error);
  }
}
