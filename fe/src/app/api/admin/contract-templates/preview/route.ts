import { NextResponse } from "next/server";
import { adminAuthorized } from "@/lib/admin/session";
import { toErrorResponse } from "@/lib/api/route";
import { previewAdminContractTemplate } from "@/lib/api/services";
import type { ContractTemplateType, PreviewContractTemplateRequest } from "@/types/admin";

const INVALID_REQUEST = { code: "INVALID_REQUEST", message: "본문을 입력해 주세요." };

function parseBody(body: unknown): PreviewContractTemplateRequest | null {
  if (typeof body !== "object" || body === null) return null;
  const input = body as Record<string, unknown>;

  if (typeof input.type !== "string" || input.type.trim() === "") return null;
  if (typeof input.body !== "string" || input.body.trim() === "") return null;

  return { type: input.type as ContractTemplateType, body: input.body };
}

/**
 * POST /api/admin/contract-templates/preview — 저장 전 미리보기.
 *
 * 자리표시자를 보기용 값으로 채운 HTML과, **그 유형에서 채울 수 없는 이름**을
 * 서버가 가려 돌려줍니다. 화면이 본문을 따로 훑어 세지 않습니다 — 어떤 이름을
 * 채울 수 있는지는 서버가 아는 사실이고, 프론트가 같은 판정을 한 벌 더 가지면
 * 서버가 자리표시자를 늘린 날부터 둘이 다른 답을 냅니다.
 *
 * 아무것도 저장하지 않습니다.
 */
export async function POST(request: Request) {
  try {
    await adminAuthorized();
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
    return NextResponse.json(await previewAdminContractTemplate(parsed));
  } catch (error) {
    return toErrorResponse(error);
  }
}
