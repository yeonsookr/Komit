import { NextResponse } from "next/server";
import { getSurvey } from "@/lib/api/services";
import { parseNumericParam, toErrorResponse } from "@/lib/api/route";

const NOT_FOUND = { code: "DIAGNOSIS_NOT_FOUND", message: "진단을 찾을 수 없습니다." };

/** GET /api/diagnoses/{id}/survey — 문항과 이미 고른 답. */
export async function GET(
  _request: Request,
  { params }: { params: Promise<{ diagnosisId: string }> },
) {
  const { diagnosisId } = await params;
  const id = parseNumericParam(diagnosisId);
  if (id === null) return NextResponse.json(NOT_FOUND, { status: 404 });

  try {
    return NextResponse.json(await getSurvey(id));
  } catch (error) {
    return toErrorResponse(error);
  }
}
