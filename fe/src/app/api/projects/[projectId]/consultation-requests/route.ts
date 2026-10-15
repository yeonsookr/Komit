import { NextResponse } from "next/server";
import { createDirectConsultationRequests } from "@/lib/api/services";
import { parseNumericParam, toErrorResponse } from "@/lib/api/route";

const INVALID_REQUEST = { code: "INVALID_REQUEST", message: "요청 값을 확인해 주세요." };
const NOT_FOUND = { code: "PROJECT_NOT_FOUND", message: "프로젝트를 찾을 수 없습니다." };

/** 보낸 값이 개발자 식별자 배열인지 봅니다. 빈 배열은 서버가 422 로 거릅니다. */
function parseDeveloperIds(body: unknown): number[] | null {
  if (typeof body !== "object" || body === null) return null;

  const ids = (body as Record<string, unknown>).developerIds;
  if (!Array.isArray(ids)) return null;
  if (!ids.every((id) => Number.isInteger(id) && (id as number) > 0)) return null;

  return ids as number[];
}

/**
 * POST /api/projects/{projectId}/consultation-requests
 *
 * 직접 찾은 개발자에게 무료 상담방을 엽니다. 멱등하므로 다시 눌러도 방이 늘지 않습니다.
 */
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

  const developerIds = parseDeveloperIds(body);
  if (developerIds === null) {
    return NextResponse.json(INVALID_REQUEST, { status: 422 });
  }

  try {
    const rooms = await createDirectConsultationRequests(id, { developerIds });
    return NextResponse.json({ content: rooms }, { status: 201 });
  } catch (error) {
    return toErrorResponse(error);
  }
}
