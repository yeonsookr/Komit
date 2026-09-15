import { NextResponse } from "next/server";
import { updateIssueStatus } from "@/lib/api/services";
import { parseNumericParam, toErrorResponse } from "@/lib/api/route";

const STATUSES = ["UNRESOLVED", "RESOLVED"] as const;
type AllowedStatus = (typeof STATUSES)[number];

const INVALID_REQUEST = { code: "INVALID_REQUEST", message: "요청 값을 확인해 주세요." };
const NOT_FOUND = { code: "ISSUE_NOT_FOUND", message: "요구사항을 찾을 수 없습니다." };

/** PATCH /api/issues/{issueId} — 해결 처리 · 되돌리기. */
export async function PATCH(
  request: Request,
  { params }: { params: Promise<{ issueId: string }> },
) {
  const { issueId } = await params;
  const id = parseNumericParam(issueId);
  if (id === null) return NextResponse.json(NOT_FOUND, { status: 404 });

  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return NextResponse.json(INVALID_REQUEST, { status: 422 });
  }

  const status = (body as Record<string, unknown> | null)?.status;
  if (!STATUSES.includes(status as AllowedStatus)) {
    return NextResponse.json(INVALID_REQUEST, { status: 422 });
  }

  try {
    return NextResponse.json(await updateIssueStatus(id, { status: status as AllowedStatus }));
  } catch (error) {
    return toErrorResponse(error);
  }
}
