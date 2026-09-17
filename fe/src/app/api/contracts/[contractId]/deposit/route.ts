import { NextResponse } from "next/server";
import { depositContract } from "@/lib/api/services";
import { parseNumericParam, toErrorResponse } from "@/lib/api/route";
import type { DepositRequest } from "@/types/contract";

const INVALID_REQUEST = { code: "INVALID_REQUEST", message: "요청 값을 확인해 주세요." };
const NOT_FOUND = { code: "CONTRACT_NOT_FOUND", message: "계약을 찾을 수 없습니다." };

/**
 * 결제 수단과 멱등 키. 출금 계좌는 담지 않습니다 — 서버가 등록해 둔 수단으로
 * 뺍니다. 화면이 계좌번호를 보내면 남의 계좌를 지정할 수 있습니다.
 */
function parseBody(body: unknown): DepositRequest | null {
  if (typeof body !== "object" || body === null) return null;
  const input = body as Record<string, unknown>;

  if (!Number.isInteger(input.paymentMethodId)) return null;
  if (typeof input.idempotencyKey !== "string" || input.idempotencyKey.trim() === "") return null;

  return {
    paymentMethodId: input.paymentMethodId as number,
    idempotencyKey: input.idempotencyKey,
  };
}

/**
 * POST /api/contracts/{contractId}/deposit — 계약 금액 예치.
 *
 * 전액을 한 번에 맡깁니다. 같은 `idempotencyKey` 로 다시 보내면 결제가 두 번
 * 나가지 않습니다. 등록한 결제 수단이 없으면 백엔드가 409 를 주고 전달합니다.
 */
export async function POST(
  request: Request,
  { params }: { params: Promise<{ contractId: string }> },
) {
  const { contractId } = await params;
  const id = parseNumericParam(contractId);
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
    return NextResponse.json(await depositContract(id, parsed), { status: 201 });
  } catch (error) {
    return toErrorResponse(error);
  }
}
