import { NextResponse } from "next/server";
import { getRoom } from "@/lib/api/services";
import { parseNumericParam, toErrorResponse } from "@/lib/api/route";

/** GET /api/rooms/{roomId} — 방 머리와 요청서 요약. */
export async function GET(
  _request: Request,
  { params }: { params: Promise<{ roomId: string }> },
) {
  const { roomId } = await params;
  const id = parseNumericParam(roomId);

  if (id === null) {
    return NextResponse.json(
      { code: "ROOM_NOT_FOUND", message: "방을 찾을 수 없습니다." },
      { status: 404 },
    );
  }

  try {
    return NextResponse.json(await getRoom(id));
  } catch (error) {
    return toErrorResponse(error);
  }
}
