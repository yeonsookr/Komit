import { NextResponse } from "next/server";
import { agreeRequiredTerms, getRequiredTerms } from "@/lib/api/services/terms";
import { toErrorResponse } from "@/lib/api/route";
import { authorized } from "@/lib/auth/session";
import type { RequiredTerms } from "@/types/terms";

const REQUIRED_FIELDS: (keyof RequiredTerms)[] = [
  "isEscrowTermsAgreed",
  "isPrivacyCollectAgreed",
  "isPrivacyThirdPartyAgreed",
  "isServiceTermsAgreed",
];

export async function GET() {
  try {
    return NextResponse.json(await getRequiredTerms());
  } catch (error) {
    return toErrorResponse(error);
  }
}

export async function PUT(request: Request) {
  try {
    await authorized();
  } catch (error) {
    return toErrorResponse(error);
  }

  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return NextResponse.json(
      { code: "INVALID_REQUEST", message: "요청 형식이 올바르지 않습니다.", errors: [{ field: "body", reason: "JSON 형식이 올바르지 않습니다." }] },
      { status: 422 },
    );
  }

  if (typeof body !== "object" || body === null) {
    return NextResponse.json(
      { code: "INVALID_REQUEST", message: "요청 형식이 올바르지 않습니다.", errors: [{ field: "body", reason: "값이 올바르지 않습니다." }] },
      { status: 422 },
    );
  }

  const values = body as Record<string, unknown>;
  const errors = REQUIRED_FIELDS.flatMap((field) => {
    const value = values[field];
    if (value === undefined) return [{ field, reason: "필수 항목입니다." }];
    if (typeof value !== "boolean") return [{ field, reason: "true 또는 false여야 합니다." }];
    if (!value) return [{ field, reason: "동의가 필요합니다." }];
    return [];
  });
  if (errors.length > 0) {
    return NextResponse.json(
      { code: "INVALID_REQUEST", message: "요청 형식이 올바르지 않습니다.", errors },
      { status: 422 },
    );
  }

  try {
    return NextResponse.json(await agreeRequiredTerms(values as unknown as RequiredTerms));
  } catch (error) {
    return toErrorResponse(error);
  }
}
