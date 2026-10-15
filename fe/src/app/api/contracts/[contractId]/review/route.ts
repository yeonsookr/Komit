import { NextResponse } from "next/server";
import { writeReview } from "@/lib/api/services";
import { parseNumericParam, toErrorResponse } from "@/lib/api/route";
import type { WriteReviewRequest } from "@/types/contract";

const INVALID_REQUEST = { code: "INVALID_REQUEST", message: "요청 값을 확인해 주세요." };
const NOT_FOUND = { code: "CONTRACT_NOT_FOUND", message: "계약을 찾을 수 없습니다." };

/** 리뷰 본문 길이 상한. R-REV-04. */
const MAX_CONTENT_LENGTH = 1000;

/** 1~5 점수인지. 점수는 두 축 다 필수입니다 (R-REV-01). */
function isScore(value: unknown): value is number {
  return Number.isInteger(value) && (value as number) >= 1 && (value as number) <= 5;
}

function parseBody(body: unknown): WriteReviewRequest | null {
  if (typeof body !== "object" || body === null) return null;
  const input = body as Record<string, unknown>;

  if (!isScore(input.devScore) || !isScore(input.expScore)) return null;

  const content = input.content;
  if (content !== undefined && content !== null && typeof content !== "string") return null;
  if (typeof content === "string" && content.length > MAX_CONTENT_LENGTH) return null;

  return {
    devScore: input.devScore,
    expScore: input.expScore,
    ...(typeof content === "string" && content.trim() !== ""
      ? { content: content.trim() }
      : {}),
  };
}

/**
 * POST /api/contracts/{contractId}/review — 리뷰 남기기.
 *
 * 유료 거래를 마친 의뢰인만 남길 수 있습니다. 개발 실력과 설명력을 나누어 받고
 * 본문은 비워도 됩니다.
 */
export async function POST(
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
    await writeReview(id, parsed);
    return new NextResponse(null, { status: 201 });
  } catch (error) {
    return toErrorResponse(error);
  }
}
