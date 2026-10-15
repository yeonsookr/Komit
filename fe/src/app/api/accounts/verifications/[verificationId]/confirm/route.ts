import { NextResponse } from "next/server";
import { parseNumericParam, toErrorResponse } from "@/lib/api/route";
import { confirmAccountVerification } from "@/lib/api/services";
import { authorized } from "@/lib/auth/session";
import type { ConfirmAccountVerificationBody } from "@/types/account";

const INVALID_REQUEST = { code: "INVALID_REQUEST", message: "요청 값을 확인해 주세요." };
const NOT_FOUND = {
  code: "VERIFICATION_NOT_FOUND",
  message: "계좌 확인 건을 찾을 수 없습니다.",
};

function parseBody(body: unknown): ConfirmAccountVerificationBody | null {
  if (typeof body !== "object" || body === null) return null;

  const authCode = (body as Record<string, unknown>).authCode;
  return typeof authCode === "string" && /^\d{4}$/.test(authCode) ? { authCode } : null;
}

/** POST /api/accounts/verifications/{verificationId}/confirm — 계좌 등록을 마칩니다. */
export async function POST(
  request: Request,
  { params }: { params: Promise<{ verificationId: string }> },
) {
  const { verificationId } = await params;
  const id = parseNumericParam(verificationId);
  if (id === null) return NextResponse.json(NOT_FOUND, { status: 404 });

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
    return NextResponse.json(await confirmAccountVerification(id, parsed));
  } catch (error) {
    return toErrorResponse(error);
  }
}
