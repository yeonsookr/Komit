import { NextResponse } from "next/server";
import { createRecommendation } from "@/lib/api/services";
import { parseNumericParam, toErrorResponse } from "@/lib/api/route";

const INVALID_REQUEST = { code: "INVALID_REQUEST", message: "요청 값을 확인해 주세요." };
const NOT_FOUND = { code: "PROJECT_NOT_FOUND", message: "프로젝트를 찾을 수 없습니다." };

/** POST /api/projects/{projectId}/recommendations — 고른 요구사항으로 추천 받기. */
export async function POST(
  request: Request,
  { params }: { params: Promise<{ projectId: string }> },
) {
  const { projectId } = await params;
  const id = parseNumericParam(projectId);
  if (id === null) return NextResponse.json(NOT_FOUND, { status: 404 });

  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return NextResponse.json(INVALID_REQUEST, { status: 422 });
  }

  const input = body as Record<string, unknown> | null;
  const diagnosisId = input?.diagnosisId;
  const issueIds = input?.issueIds;

  // 진단 없이 부를 수 없고, 요구사항은 한 건 이상 골라야 합니다.
  if (!Number.isInteger(diagnosisId)) return NextResponse.json(INVALID_REQUEST, { status: 422 });
  if (!Array.isArray(issueIds) || issueIds.length === 0) {
    return NextResponse.json(INVALID_REQUEST, { status: 422 });
  }
  if (!issueIds.every((issueId) => Number.isInteger(issueId))) {
    return NextResponse.json(INVALID_REQUEST, { status: 422 });
  }

  try {
    const batch = await createRecommendation(id, {
      diagnosisId: diagnosisId as number,
      issueIds: issueIds as number[],
    });
    return NextResponse.json(batch, { status: 201 });
  } catch (error) {
    return toErrorResponse(error);
  }
}
