import { NextResponse } from "next/server";
import { convertRoom } from "@/lib/api/services";
import { parseNumericParam, toErrorResponse } from "@/lib/api/route";
import type { ConversionTarget } from "@/types/room";

const TARGETS: ConversionTarget[] = ["CONSULTING", "DEVELOPMENT"];

const INVALID_REQUEST = { code: "INVALID_REQUEST", message: "요청 값을 확인해 주세요." };
const ROOM_NOT_FOUND = { code: "ROOM_NOT_FOUND", message: "방을 찾을 수 없습니다." };

/** 보낸 값이 허용된 갈래인지 봅니다. */
function parseTarget(body: unknown): ConversionTarget | null {
  if (typeof body !== "object" || body === null) return null;

  const target = (body as Record<string, unknown>).targetType;
  return TARGETS.includes(target as ConversionTarget) ? (target as ConversionTarget) : null;
}

/**
 * POST /api/rooms/{roomId}/conversion — 상담에서 갈래 정하기.
 *
 * 갈래는 의뢰인이 정합니다. 개발자가 부르면 백엔드가 403 을 주고 그대로 전달합니다.
 */
export async function POST(
  request: Request,
  { params }: { params: Promise<{ roomId: string }> },
) {
  const { roomId } = await params;
  const id = parseNumericParam(roomId);
  if (id === null) return NextResponse.json(ROOM_NOT_FOUND, { status: 404 });

  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return NextResponse.json(INVALID_REQUEST, { status: 422 });
  }

  const targetType = parseTarget(body);
  if (targetType === null) return NextResponse.json(INVALID_REQUEST, { status: 422 });

  try {
    const room = await convertRoom(id, { targetType });
    return NextResponse.json(room, { status: 201 });
  } catch (error) {
    return toErrorResponse(error);
  }
}
