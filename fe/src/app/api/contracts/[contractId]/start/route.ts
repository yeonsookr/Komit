import { NextResponse } from "next/server";
import { startContract } from "@/lib/api/services";
import { parseNumericParam, toErrorResponse } from "@/lib/api/route";

const CONTRACT_NOT_FOUND = { code: "CONTRACT_NOT_FOUND", message: "계약을 찾을 수 없습니다." };

/**
 * POST /api/contracts/{contractId}/start — 개발 착수.
 *
 * 예치와 착수는 다른 단계입니다. 예치가 끝나지 않았으면 백엔드가 409 를 주고
 * 그대로 전달합니다. 착수는 개발자만 합니다.
 */
export async function POST(
  _request: Request,
  { params }: { params: Promise<{ contractId: string }> },
) {
  const { contractId } = await params;
  const id = parseNumericParam(contractId);
  if (id === null) return NextResponse.json(CONTRACT_NOT_FOUND, { status: 404 });

  try {
    return NextResponse.json(await startContract(id));
  } catch (error) {
    return toErrorResponse(error);
  }
}
