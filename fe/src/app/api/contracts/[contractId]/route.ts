import { NextResponse } from "next/server";
import { parseNumericParam, toErrorResponse } from "@/lib/api/route";
import { amendConsultingContract } from "@/lib/api/services";
import type { AmendConsultingContractRequest } from "@/types/contract";

const INVALID_REQUEST = { code: "INVALID_REQUEST", message: "요청 값을 확인해 주세요." };
const NOT_FOUND = { code: "CONTRACT_NOT_FOUND", message: "계약서를 찾을 수 없습니다." };

/** 목 명세가 정한 길이. ERD 의 `consulting_agreements.topic` 과 같습니다. */
const MAX_TOPIC_LENGTH = 255;
const MAX_MESSAGE_LENGTH = 300;

/**
 * **보낸 항목만 바꿉니다.** 빠진 항목은 그대로 두라는 뜻이라 `undefined` 로 남기고,
 * 빈 문자열을 0 이나 null 로 바꿔 보내지 않습니다 — 안 적은 것과 지운 것은 다릅니다.
 */
function parseBody(body: unknown): AmendConsultingContractRequest | null {
  if (typeof body !== "object" || body === null) return null;
  const input = body as Record<string, unknown>;
  const amend: AmendConsultingContractRequest = {};

  if (input.topic !== undefined) {
    if (typeof input.topic !== "string") return null;
    const topic = input.topic.trim();
    if (topic === "" || topic.length > MAX_TOPIC_LENGTH) return null;
    amend.topic = topic;
  }
  if (input.scheduledAt !== undefined) {
    if (typeof input.scheduledAt !== "string" || Number.isNaN(Date.parse(input.scheduledAt))) {
      return null;
    }
    amend.scheduledAt = input.scheduledAt;
  }
  if (input.durationMinutes !== undefined) {
    if (!Number.isInteger(input.durationMinutes) || (input.durationMinutes as number) <= 0) {
      return null;
    }
    amend.durationMinutes = input.durationMinutes as number;
  }
  if (input.price !== undefined) {
    if (!Number.isInteger(input.price) || (input.price as number) < 0) return null;
    amend.price = input.price as number;
  }
  if (input.detail !== undefined) {
    if (typeof input.detail !== "string") return null;
    amend.detail = input.detail.trim() === "" ? null : input.detail.trim();
  }
  if (input.message !== undefined) {
    if (typeof input.message !== "string") return null;
    if (input.message.trim().length > MAX_MESSAGE_LENGTH) return null;
    amend.message = input.message.trim() === "" ? null : input.message.trim();
  }

  return Object.keys(amend).length === 0 ? null : amend;
}

/**
 * PATCH /api/contracts/{contractId} — 컨설팅 계약서 수정 제안.
 *
 * **서명 전에만 됩니다.** 한쪽이라도 서명했으면 백엔드가 막습니다 (R-CON-02).
 * 개발 계약서는 이 경로를 쓰지 않습니다 — 그쪽은 다시 써서 덮어씁니다.
 */
export async function PATCH(
  request: Request,
  { params }: { params: Promise<{ contractId: string }> },
) {
  const { contractId } = await params;
  const id = parseNumericParam(contractId);
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
    return NextResponse.json(await amendConsultingContract(id, parsed));
  } catch (error) {
    return toErrorResponse(error);
  }
}
