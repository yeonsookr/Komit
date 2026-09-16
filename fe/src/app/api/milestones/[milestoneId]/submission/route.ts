import { NextResponse } from "next/server";
import { submitMilestone } from "@/lib/api/services";
import { parseNumericParam, toErrorResponse } from "@/lib/api/route";

const NOT_FOUND = { code: "MILESTONE_NOT_FOUND", message: "작업을 찾을 수 없습니다." };

/** 함께 남길 말. 적지 않아도 됩니다. */
function parseMessage(body: unknown): string | null {
  if (typeof body !== "object" || body === null) return null;
  const message = (body as Record<string, unknown>).message;
  return typeof message === "string" && message.trim() !== "" ? message.trim() : null;
}

/**
 * POST /api/milestones/{milestoneId}/submission — 작업 확인 요청.
 *
 * 개발자가 작업을 끝내고 의뢰인에게 확인을 부탁합니다. 보낸 시각부터 자동 통과
 * 기한을 세고, 다시 보내면 기한도 다시 셉니다.
 */
export async function POST(
  request: Request,
  { params }: { params: Promise<{ milestoneId: string }> },
) {
  const { milestoneId } = await params;
  const id = parseNumericParam(milestoneId);
  if (id === null) return NextResponse.json(NOT_FOUND, { status: 404 });

  // 본문이 없어도 됩니다. 비어 있으면 말 없이 보내는 것으로 봅니다.
  const body = await request.json().catch(() => null);
  const message = parseMessage(body);

  try {
    return NextResponse.json(await submitMilestone(id, message === null ? {} : { message }), {
      status: 201,
    });
  } catch (error) {
    return toErrorResponse(error);
  }
}
