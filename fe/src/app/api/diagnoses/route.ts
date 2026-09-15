import { NextResponse } from "next/server";
import { startDiagnosis } from "@/lib/api/services";
import { toErrorResponse } from "@/lib/api/route";
import type { StartDiagnosisRequest } from "@/types/diagnosis";

const INVALID_REQUEST = { code: "INVALID_REQUEST", message: "요청 값을 확인해 주세요." };

/** 새 프로젝트는 이름이 있어야 하고, 다시 진단하려면 projectId 를 줍니다. */
function parseBody(body: unknown): StartDiagnosisRequest | null {
  if (typeof body !== "object" || body === null) return null;
  const input = body as Record<string, unknown>;

  const projectId = input.projectId;
  const name = input.name;
  const description = input.description;

  if (projectId !== undefined && !Number.isInteger(projectId)) return null;
  if (name !== undefined && typeof name !== "string") return null;
  if (description !== undefined && typeof description !== "string") return null;
  if (projectId === undefined && !String(name ?? "").trim()) return null;

  return {
    ...(projectId === undefined ? {} : { projectId: projectId as number }),
    ...(typeof name === "string" ? { name: name.trim() } : {}),
    ...(typeof description === "string" ? { description: description.trim() } : {}),
  };
}

/** POST /api/diagnoses — 사전점검 시작. 설문1의 「다음」에서 부릅니다. */
export async function POST(request: Request) {
  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return NextResponse.json(INVALID_REQUEST, { status: 422 });
  }

  const parsed = parseBody(body);
  if (parsed === null) return NextResponse.json(INVALID_REQUEST, { status: 422 });

  try {
    return NextResponse.json(await startDiagnosis(parsed), { status: 201 });
  } catch (error) {
    return toErrorResponse(error);
  }
}
