import { NextResponse } from "next/server";
import { saveSurveyAnswers } from "@/lib/api/services";
import { parseNumericParam, toErrorResponse } from "@/lib/api/route";
import type { SurveyAnswer } from "@/types/diagnosis";

const INVALID_REQUEST = { code: "INVALID_REQUEST", message: "요청 값을 확인해 주세요." };
const NOT_FOUND = { code: "DIAGNOSIS_NOT_FOUND", message: "진단을 찾을 수 없습니다." };

/** 문항 식별자와 고른 보기만 통과시킵니다. */
function parseAnswers(body: unknown): SurveyAnswer[] | null {
  if (typeof body !== "object" || body === null) return null;

  const answers = (body as Record<string, unknown>).answers;
  if (!Array.isArray(answers) || answers.length === 0) return null;

  const parsed: SurveyAnswer[] = [];
  for (const raw of answers) {
    if (typeof raw !== "object" || raw === null) return null;
    const answer = raw as Record<string, unknown>;

    if (!Number.isInteger(answer.questionId)) return null;
    const ids = answer.selectedOptionIds;
    if (!Array.isArray(ids) || !ids.every((id) => Number.isInteger(id))) return null;
    if (answer.text !== undefined && answer.text !== null && typeof answer.text !== "string") {
      return null;
    }

    parsed.push({
      questionId: answer.questionId as number,
      selectedOptionIds: ids as number[],
      text: (answer.text as string | null | undefined) ?? null,
    });
  }

  return parsed;
}

/** PATCH /api/diagnoses/{id}/survey/answers — 한 단계의 답 저장. */
export async function PATCH(
  request: Request,
  { params }: { params: Promise<{ diagnosisId: string }> },
) {
  const { diagnosisId } = await params;
  const id = parseNumericParam(diagnosisId);
  if (id === null) return NextResponse.json(NOT_FOUND, { status: 404 });

  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return NextResponse.json(INVALID_REQUEST, { status: 422 });
  }

  const answers = parseAnswers(body);
  if (answers === null) return NextResponse.json(INVALID_REQUEST, { status: 422 });

  try {
    return NextResponse.json(await saveSurveyAnswers(id, { answers }));
  } catch (error) {
    return toErrorResponse(error);
  }
}
