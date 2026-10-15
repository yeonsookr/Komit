import { NextResponse } from "next/server";
import { cancelContract } from "@/lib/api/services";
import { parseNumericParam, toErrorResponse } from "@/lib/api/route";

const NOT_FOUND = { code: "CONTRACT_NOT_FOUND", message: "계약서를 찾을 수 없습니다." };

/**
 * POST /api/contracts/{contractId}/cancellation — 계약 취소.
 *
 * 개발자가 서명하기 전에 의뢰인이 계약을 취소하고 예치금을 돌려받습니다 (R-CON-21).
 * 본문은 받지 않습니다. 실패는 백엔드의 `code`·`message` 를 그대로 넘깁니다 —
 * 503 `REFUND_PENDING` 은 「취소는 됐고 환불을 확인하는 중」이라 화면이 그 문구를 보여줍니다.
 */
export async function POST(
  _request: Request,
  { params }: { params: Promise<{ contractId: string }> },
) {
  const { contractId } = await params;
  const id = parseNumericParam(contractId);
  if (id === null) return NextResponse.json(NOT_FOUND, { status: 404 });

  try {
    return NextResponse.json(await cancelContract(id), { status: 200 });
  } catch (error) {
    return toErrorResponse(error);
  }
}
