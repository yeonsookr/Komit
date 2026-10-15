import { NextResponse } from "next/server";
import { parseNumericParam, toErrorResponse } from "@/lib/api/route";
import { generateContractDraft } from "@/lib/api/services";

const NOT_FOUND = { code: "ROOM_NOT_FOUND", message: "방을 찾을 수 없습니다." };

/** POST /api/rooms/{roomId}/contract-draft — 상담 대화로 방 갈래에 맞는 계약서 초안을 만듭니다. */
export async function POST(
  _request: Request,
  { params }: { params: Promise<{ roomId: string }> },
) {
  const { roomId } = await params;
  const id = parseNumericParam(roomId);
  if (id === null) return NextResponse.json(NOT_FOUND, { status: 404 });

  try {
    return NextResponse.json(await generateContractDraft(id));
  } catch (error) {
    return toErrorResponse(error);
  }
}
