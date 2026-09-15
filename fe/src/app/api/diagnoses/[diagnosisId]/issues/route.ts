import { NextResponse } from "next/server";
import { createIssue } from "@/lib/api/services";
import { parseNumericParam, toErrorResponse } from "@/lib/api/route";
import type { CreateIssueRequest, IssueSeverity } from "@/types/diagnosis";

const SEVERITIES: IssueSeverity[] = ["LOW", "MEDIUM", "HIGH"];

const INVALID_REQUEST = { code: "INVALID_REQUEST", message: "요청 값을 확인해 주세요." };
const NOT_FOUND = { code: "DIAGNOSIS_NOT_FOUND", message: "진단을 찾을 수 없습니다." };

/** 요구사항 문장과 분류·위험도만 통과시킵니다. */
function parseBody(body: unknown): CreateIssueRequest | null {
  if (typeof body !== "object" || body === null) return null;
  const input = body as Record<string, unknown>;

  const title = typeof input.title === "string" ? input.title.trim() : "";
  if (title.length === 0) return null;
  if (!Number.isInteger(input.categoryCodeId)) return null;
  if (!SEVERITIES.includes(input.severity as IssueSeverity)) return null;

  const detail = typeof input.detail === "string" ? input.detail.trim() : "";

  return {
    title,
    ...(detail ? { detail } : {}),
    categoryCodeId: input.categoryCodeId as number,
    severity: input.severity as IssueSeverity,
  };
}

/** POST /api/diagnoses/{id}/issues — 요구사항 직접 추가. */
export async function POST(
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

  const parsed = parseBody(body);
  if (parsed === null) return NextResponse.json(INVALID_REQUEST, { status: 422 });

  try {
    return NextResponse.json(await createIssue(id, parsed), { status: 201 });
  } catch (error) {
    return toErrorResponse(error);
  }
}
