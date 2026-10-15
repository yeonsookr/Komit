import { NextResponse } from "next/server";
import { writeRequirements } from "@/lib/api/services";
import { parseNumericParam, toErrorResponse } from "@/lib/api/route";
import type { IssueSeverity, WriteRequirement } from "@/types/diagnosis";

const SEVERITIES: IssueSeverity[] = ["LOW", "MEDIUM", "HIGH"];

const INVALID_REQUEST = { code: "INVALID_REQUEST", message: "요청 값을 확인해 주세요." };
const NOT_FOUND = { code: "DIAGNOSIS_NOT_FOUND", message: "진단을 찾을 수 없습니다." };

/** 요구사항 줄과 확인 문장만 통과시킵니다. 새로 적는 줄은 식별자가 null 입니다. */
function parseRequirements(body: unknown): WriteRequirement[] | null {
  if (typeof body !== "object" || body === null) return null;

  const rows = (body as Record<string, unknown>).requirements;
  if (!Array.isArray(rows)) return null;

  const parsed: WriteRequirement[] = [];
  for (const raw of rows) {
    if (typeof raw !== "object" || raw === null) return null;
    const row = raw as Record<string, unknown>;

    const title = typeof row.title === "string" ? row.title.trim() : "";
    if (title.length === 0) return null;
    if (!Number.isInteger(row.categoryCodeId)) return null;
    if (!SEVERITIES.includes(row.severity as IssueSeverity)) return null;
    if (row.issueId !== null && !Number.isInteger(row.issueId)) return null;

    const qaCriteria = Array.isArray(row.qaCriteria) ? row.qaCriteria : [];
    const parsedQa = [];
    for (const item of qaCriteria) {
      if (typeof item !== "object" || item === null) return null;
      const qa = item as Record<string, unknown>;
      const testCase = typeof qa.testCase === "string" ? qa.testCase.trim() : "";
      if (testCase.length === 0) return null;
      if (qa.qaCriterionId !== null && !Number.isInteger(qa.qaCriterionId)) return null;
      parsedQa.push({ qaCriterionId: (qa.qaCriterionId as number | null) ?? null, testCase });
    }

    parsed.push({
      issueId: (row.issueId as number | null) ?? null,
      title,
      ...(typeof row.detail === "string" ? { detail: row.detail } : {}),
      categoryCodeId: row.categoryCodeId as number,
      severity: row.severity as IssueSeverity,
      qaCriteria: parsedQa,
    });
  }

  return parsed;
}

/** PUT /api/diagnoses/{id}/requirements — 요청서 저장. */
export async function PUT(
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

  const requirements = parseRequirements(body);
  if (requirements === null) return NextResponse.json(INVALID_REQUEST, { status: 422 });

  try {
    return NextResponse.json({ content: await writeRequirements(id, { requirements }) });
  } catch (error) {
    return toErrorResponse(error);
  }
}
