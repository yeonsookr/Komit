import { NextResponse } from "next/server";
import { parseNumericParam, toErrorResponse } from "@/lib/api/route";
import { writeConsultingContract } from "@/lib/api/services";
import type { WriteConsultingContractRequest } from "@/types/contract";

const INVALID_REQUEST = { code: "INVALID_REQUEST", message: "요청 값을 확인해 주세요." };
const NOT_FOUND = { code: "ROOM_NOT_FOUND", message: "방을 찾을 수 없습니다." };

/** 목 명세가 정한 길이. ERD 의 `consulting_agreements.topic` 과 같습니다. */
const MAX_TOPIC_LENGTH = 255;
const MAX_MESSAGE_LENGTH = 300;

function parseBody(body: unknown): WriteConsultingContractRequest | null {
  if (typeof body !== "object" || body === null) return null;
  const input = body as Record<string, unknown>;

  if (typeof input.topic !== "string") return null;
  const topic = input.topic.trim();
  if (topic === "" || topic.length > MAX_TOPIC_LENGTH) return null;

  // 일시는 서버가 그대로 저장하는 값이라 모양만 봅니다. 지난 날짜인지는 백엔드가 봅니다.
  if (typeof input.scheduledAt !== "string" || Number.isNaN(Date.parse(input.scheduledAt))) {
    return null;
  }
  if (!Number.isInteger(input.durationMinutes) || (input.durationMinutes as number) <= 0) {
    return null;
  }
  if (!Number.isInteger(input.price) || (input.price as number) < 0) return null;
  if (typeof input.message === "string" && input.message.trim().length > MAX_MESSAGE_LENGTH) {
    return null;
  }

  return {
    topic,
    scheduledAt: input.scheduledAt,
    durationMinutes: input.durationMinutes as number,
    price: input.price as number,
    ...(typeof input.detail === "string" && input.detail.trim() !== ""
      ? { detail: input.detail.trim() }
      : {}),
    ...(typeof input.message === "string" && input.message.trim() !== ""
      ? { message: input.message.trim() }
      : {}),
  };
}

/**
 * POST /api/rooms/{roomId}/consulting-contracts — 컨설팅 계약서 작성.
 *
 * 개발 계약과 달리 작업을 쪼개지 않습니다. 한 번 만나서 무엇을 다룰지·언제 얼마나·
 * 얼마에 하는지가 전부라 세션 하나가 곧 계약입니다.
 *
 * 계약서는 개발자가 씁니다. 이 방의 개발자가 아니면 백엔드가 403 을 주고 그대로 전달합니다.
 */
export async function POST(
  request: Request,
  { params }: { params: Promise<{ roomId: string }> },
) {
  const { roomId } = await params;
  const id = parseNumericParam(roomId);
  if (id === null) return NextResponse.json(NOT_FOUND, { status: 404 });

  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return NextResponse.json(INVALID_REQUEST, { status: 422 });
  }

  const parsed = parseBody(body);
  if (parsed === null) return NextResponse.json(INVALID_REQUEST, { status: 422 });

  try {
    return NextResponse.json(await writeConsultingContract(id, parsed), { status: 201 });
  } catch (error) {
    return toErrorResponse(error);
  }
}
