import { NextResponse, type NextRequest } from "next/server";
import { getRooms } from "@/lib/api/services";
import { toErrorResponse } from "@/lib/api/route";
import type { RoomListParams, RoomStatus, RoomType } from "@/types/room";

const ROOM_TYPES: RoomType[] = ["INQUIRY", "CONSULTING", "DEVELOPMENT"];
const ROOM_STATUSES: RoomStatus[] = ["OPEN", "CLOSED"];

/** GET /api/rooms — 내가 들어가 있는 방 목록. */
export async function GET(request: NextRequest) {
  const search = request.nextUrl.searchParams;
  const type = search.get("type");
  const status = search.get("status");

  // 요청을 그대로 흘려보내지 않고 허용된 값만 통과시킵니다.
  const params: RoomListParams = {};
  if (type !== null) {
    if (!ROOM_TYPES.includes(type as RoomType)) {
      return NextResponse.json(
        { code: "INVALID_REQUEST", message: "요청 값을 확인해 주세요." },
        { status: 422 },
      );
    }
    params.type = type as RoomType;
  }
  if (status !== null) {
    if (!ROOM_STATUSES.includes(status as RoomStatus)) {
      return NextResponse.json(
        { code: "INVALID_REQUEST", message: "요청 값을 확인해 주세요." },
        { status: 422 },
      );
    }
    params.status = status as RoomStatus;
  }

  try {
    return NextResponse.json({ content: await getRooms(params) });
  } catch (error) {
    return toErrorResponse(error);
  }
}
