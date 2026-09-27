import { NextResponse } from "next/server";
import { addDeliverable } from "@/lib/api/services";
import { parseNumericParam, toErrorResponse } from "@/lib/api/route";

const INVALID_REQUEST = { code: "INVALID_REQUEST", message: "산출물 이름을 입력해 주세요." };
const NOT_FOUND = { code: "CONTRACT_NOT_FOUND", message: "계약서를 찾을 수 없습니다." };

/**
 * POST /api/contracts/{id}/deliverables — 계약이 성립한 뒤 개발자가 산출물 항목을 더합니다.
 *
 * 서명한 계약서의 범위·금액·확인 문장은 바뀌지 않습니다 (R-QA-06). 이름 길이는
 * 백엔드가 검사합니다.
 */
export async function POST(
  request: Request,
  { params }: { params: Promise<{ contractId: string }> },
) {
  const { contractId } = await params;
  const id = parseNumericParam(contractId);
  if (id === null) return NextResponse.json(NOT_FOUND, { status: 404 });

  let title: unknown;
  try {
    ({ title } = (await request.json()) as { title?: unknown });
  } catch {
    return NextResponse.json(INVALID_REQUEST, { status: 422 });
  }
  if (typeof title !== "string" || title.trim() === "") {
    return NextResponse.json(INVALID_REQUEST, { status: 422 });
  }

  try {
    return NextResponse.json(await addDeliverable(id, title.trim()), { status: 201 });
  } catch (error) {
    return toErrorResponse(error);
  }
}
