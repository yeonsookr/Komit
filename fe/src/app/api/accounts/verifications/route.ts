import { NextResponse } from "next/server";
import { requestAccountVerification } from "@/lib/api/services";
import { toErrorResponse } from "@/lib/api/route";
import { authorized } from "@/lib/auth/session";
import type { RequestAccountVerificationBody } from "@/types/account";

const INVALID_REQUEST = { code: "INVALID_REQUEST", message: "요청 값을 확인해 주세요." };

function parseBody(body: unknown): RequestAccountVerificationBody | null {
  if (typeof body !== "object" || body === null) return null;
  const input = body as Record<string, unknown>;

  if (!Number.isInteger(input.bankCodeId) || (input.bankCodeId as number) <= 0) return null;
  if (typeof input.accountNumber !== "string" || !/^\d+$/.test(input.accountNumber)) return null;
  if (typeof input.accountHolder !== "string") return null;

  const accountHolder = input.accountHolder.trim();
  if (accountHolder === "") return null;

  return {
    bankCodeId: input.bankCodeId as number,
    accountNumber: input.accountNumber,
    accountHolder,
  };
}

/** POST /api/accounts/verifications — 계좌로 1원을 보내고 확인 건을 엽니다. */
export async function POST(request: Request) {
  try {
    await authorized();
  } catch (error) {
    return toErrorResponse(error);
  }

  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return NextResponse.json(INVALID_REQUEST, { status: 422 });
  }

  const parsed = parseBody(body);
  if (parsed === null) return NextResponse.json(INVALID_REQUEST, { status: 422 });

  try {
    return NextResponse.json(await requestAccountVerification(parsed), { status: 201 });
  } catch (error) {
    return toErrorResponse(error);
  }
}
