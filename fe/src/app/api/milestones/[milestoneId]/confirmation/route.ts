import { NextResponse } from "next/server";
import { confirmMilestone } from "@/lib/api/services";
import { parseNumericParam, toErrorResponse } from "@/lib/api/route";

const INVALID_REQUEST = { code: "INVALID_REQUEST", message: "요청 값을 확인해 주세요." };
const NOT_FOUND = { code: "MILESTONE_NOT_FOUND", message: "작업을 찾을 수 없습니다." };

/** 체크한 확인 문장 식별자. 비어 있으면 통과시킬 근거가 없습니다. */
function parseIds(body: unknown): number[] | null {
  if (typeof body !== "object" || body === null) return null;

  const ids = (body as Record<string, unknown>).qaCriterionIds;
  if (!Array.isArray(ids) || ids.length === 0) return null;
  if (!ids.every((id) => Number.isInteger(id))) return null;

  return ids as number[];
}

/**
 * POST /api/milestones/{milestoneId}/confirmation — 작업 통과시키기.
 *
 * 확인 문장을 모두 체크한 작업만 완료로 확정합니다. 하나라도 남아 있으면
 * 백엔드가 400(QA_NOT_ALL_CHECKED)을 주고 그대로 전달합니다.
 * **통과 확정은 되돌릴 수 없습니다.**
 */
export async function POST(
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
    return NextResponse.json(await confirmMilestone(id, { qaCriterionIds }), { status: 201 });
  } catch (error) {
    return toErrorResponse(error);
  }
}
