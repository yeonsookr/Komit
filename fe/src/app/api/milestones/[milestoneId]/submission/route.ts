import { NextResponse } from "next/server";
import { submitMilestone } from "@/lib/api/services";
import { parseNumericParam, toErrorResponse } from "@/lib/api/route";

const NOT_FOUND = { code: "MILESTONE_NOT_FOUND", message: "작업을 찾을 수 없습니다." };

/**
 * POST /api/milestones/{milestoneId}/submission — 작업 확인 요청.
 *
 * 개발자가 작업을 끝내고 의뢰인에게 확인을 부탁합니다. 보낸 시각부터 자동 통과
 * 기한을 세고, 다시 보내면 기한도 다시 셉니다. 백엔드는 바디를 받지 않아 본문을 읽지
 * 않습니다.
 */
export async function POST(
  _request: Request,
  { params }: { params: Promise<{ milestoneId: string }> },
) {
  const { milestoneId } = await params;
  const id = parseNumericParam(milestoneId);
  if (id === null) return NextResponse.json(NOT_FOUND, { status: 404 });

  try {
    return NextResponse.json(await submitMilestone(id), { status: 201 });
  } catch (error) {
    return toErrorResponse(error);
  }
}
