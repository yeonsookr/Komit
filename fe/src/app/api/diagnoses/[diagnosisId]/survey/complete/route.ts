import { NextResponse } from "next/server";
import { completeSurvey } from "@/lib/api/services";
import { parseNumericParam, toErrorResponse } from "@/lib/api/route";

const NOT_FOUND = { code: "DIAGNOSIS_NOT_FOUND", message: "진단을 찾을 수 없습니다." };

/**
 * POST /api/diagnoses/{id}/survey/complete — 설문 완료.
 *
 * 설문4까지 마쳤다고 알립니다. 이 호출을 받아야 설문1의 「진행 중인 설문」
 * 목록에서 빠집니다. 아직 답하지 않은 문항이 남았으면 백엔드가 409 를 줍니다.
 */
export async function POST(
  _request: Request,
  { params }: { params: Promise<{ diagnosisId: string }> },
) {
  const { diagnosisId } = await params;
  const id = parseNumericParam(diagnosisId);
  if (id === null) return NextResponse.json(NOT_FOUND, { status: 404 });

  try {
    return NextResponse.json(await completeSurvey(id));
  } catch (error) {
    return toErrorResponse(error);
  }
}
