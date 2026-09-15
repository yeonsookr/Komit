import { NextResponse } from "next/server";
import { createConsultationRequests } from "@/lib/api/services";
import { parseNumericParam, toErrorResponse } from "@/lib/api/route";

const INVALID_REQUEST = { code: "INVALID_REQUEST", message: "요청 값을 확인해 주세요." };
const NOT_FOUND = { code: "RECOMMENDATION_NOT_FOUND", message: "추천 결과를 찾을 수 없습니다." };

/** 보낸 값이 추천 항목 식별자 배열인지 봅니다. 빈 배열은 서버가 422 로 거릅니다. */
function parseItemIds(body: unknown): number[] | null {
  if (typeof body !== "object" || body === null) return null;

  const ids = (body as Record<string, unknown>).recommendationItemIds;
  if (!Array.isArray(ids)) return null;
  if (!ids.every((id) => Number.isInteger(id) && (id as number) > 0)) return null;

  return ids as number[];
}

/**
 * POST /api/recommendations/{batchId}/consultation-requests
 *
 * 고른 개발자들에게 같은 요청서를 보냅니다. 멱등하므로 다시 눌러도 방이 늘지 않습니다.
 */
export async function POST(
  request: Request,
  { params }: { params: Promise<{ batchId: string }> },
) {
  const { batchId } = await params;
  const id = parseNumericParam(batchId);
  if (id === null) return NextResponse.json(NOT_FOUND, { status: 404 });

  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return NextResponse.json(INVALID_REQUEST, { status: 422 });
  }

  const recommendationItemIds = parseItemIds(body);
  if (recommendationItemIds === null) {
    return NextResponse.json(INVALID_REQUEST, { status: 422 });
  }

  try {
    const rooms = await createConsultationRequests(id, { recommendationItemIds });
    return NextResponse.json({ content: rooms }, { status: 201 });
  } catch (error) {
    return toErrorResponse(error);
  }
}
