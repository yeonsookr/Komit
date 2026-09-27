import { NextResponse } from "next/server";
import { addDeliverableUrl } from "@/lib/api/services";
import { parseNumericParam, toErrorResponse } from "@/lib/api/route";

const INVALID_REQUEST = { code: "INVALID_REQUEST", message: "인수 시험 URL을 입력해 주세요." };
const NOT_FOUND = { code: "DELIVERABLE_NOT_FOUND", message: "인계물을 찾을 수 없습니다." };

/**
 * POST /api/deliverables/{id}/urls — 개발자가 인수 시험 URL 을 하나 더합니다.
 *
 * 주소 형식(http·https, 길이)은 백엔드가 검사하고 사유를 필드 오류로 돌려줍니다.
 * 여기서는 문자열이 왔는지만 봅니다.
 */
export async function POST(
  request: Request,
  { params }: { params: Promise<{ deliverableId: string }> },
) {
  const { deliverableId } = await params;
  const id = parseNumericParam(deliverableId);
  if (id === null) return NextResponse.json(NOT_FOUND, { status: 404 });

  let url: unknown;
  try {
    ({ url } = (await request.json()) as { url?: unknown });
  } catch {
    return NextResponse.json(INVALID_REQUEST, { status: 422 });
  }
  if (typeof url !== "string" || url.trim() === "") {
    return NextResponse.json(INVALID_REQUEST, { status: 422 });
  }

  try {
    return NextResponse.json(await addDeliverableUrl(id, url.trim()), { status: 201 });
  } catch (error) {
    return toErrorResponse(error);
  }
}
