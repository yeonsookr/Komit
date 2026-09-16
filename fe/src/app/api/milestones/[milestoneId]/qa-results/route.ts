import { NextResponse } from "next/server";
import { saveQaResults } from "@/lib/api/services";
import { parseNumericParam, toErrorResponse } from "@/lib/api/route";

const INVALID_REQUEST = { code: "INVALID_REQUEST", message: "요청 값을 확인해 주세요." };
const NOT_FOUND = { code: "MILESTONE_NOT_FOUND", message: "작업을 찾을 수 없습니다." };

/** 보낸 값이 확인 문장 식별자 목록인지 봅니다. 비어 있으면 보낼 이유가 없습니다. */
function parseIds(body: unknown): number[] | null {
  if (typeof body !== "object" || body === null) return null;

  const ids = (body as Record<string, unknown>).qaCriterionIds;
  if (!Array.isArray(ids) || ids.length === 0) return null;
  if (!ids.every((id) => Number.isInteger(id))) return null;

  return ids as number[];
}

/**
 * PUT /api/milestones/{milestoneId}/qa-results — 확인 결과 저장.
 *
 * 다 체크하지 않아도 보낼 수 있습니다. 작업 상태는 바뀌지 않고, 개발자가
 * 무엇이 남았는지 봅니다. 체크는 되돌릴 수 없어 이번에 체크한 것만 보냅니다.
 */
export async function PUT(
  request: Request,
  { params }: { params: Promise<{ milestoneId: string }> },
) {
  const { milestoneId } = await params;
  const id = parseNumericParam(milestoneId);
  if (id === null) return NextResponse.json(NOT_FOUND, { status: 404 });

  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return NextResponse.json(INVALID_REQUEST, { status: 422 });
  }

  const qaCriterionIds = parseIds(body);
  if (qaCriterionIds === null) return NextResponse.json(INVALID_REQUEST, { status: 422 });

  try {
    return NextResponse.json(await saveQaResults(id, { qaCriterionIds }));
  } catch (error) {
    return toErrorResponse(error);
  }
}
