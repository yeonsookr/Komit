import { NextResponse } from "next/server";
import { adminAuthorized } from "@/lib/admin/session";
import { toErrorResponse } from "@/lib/api/route";
import { writeAdminContractTemplate } from "@/lib/api/services";
import type { ContractTemplateType, WriteContractTemplateRequest } from "@/types/admin";

const INVALID_REQUEST = { code: "INVALID_REQUEST", message: "요청 값을 확인해 주세요." };

/**
 * 보낸 값이 저장해 볼 만한 모양인지만 봅니다.
 *
 * **유형 이름의 목록을 여기 적지 않습니다.** 고를 수 있는 유형은 서버가
 * `GET /api/admin/contract-templates` 의 `types` 로 정하고 화면은 그대로 그립니다.
 * 여기에 목록을 한 벌 더 두면 서버가 유형을 늘렸을 때 이 문이 먼저 막습니다 —
 * 모르는 유형은 백엔드가 400 으로 돌려주게 둡니다.
 */
function parseBody(body: unknown): WriteContractTemplateRequest | null {
  if (typeof body !== "object" || body === null) return null;
  const input = body as Record<string, unknown>;

  if (typeof input.type !== "string" || input.type.trim() === "") return null;
  if (typeof input.title !== "string" || typeof input.body !== "string") return null;

  const title = input.title.trim();
  if (title === "" || input.body.trim() === "") return null;

  const sourceTemplateId = input.sourceTemplateId;
  if (
    sourceTemplateId !== null &&
    (!Number.isInteger(sourceTemplateId) || (sourceTemplateId as number) <= 0)
  ) {
    return null;
  }

  return {
    type: input.type as ContractTemplateType,
    title,
    body: input.body,
    sourceTemplateId: sourceTemplateId as number | null,
  };
}

/**
 * POST /api/admin/contract-templates — 계약 양식 등록·개정.
 * `9_양식등록` 의 「등록」과 `9_양식편집` 의 「새 판으로 저장」이 같은 곳을 부릅니다.
 *
 * 언제나 새 판이 만들어지고 같은 유형에 사용 중이던 판은 보관으로 내려갑니다.
 * 둘을 가르는 것은 `sourceTemplateId` 하나입니다 — 개정이면 고칠 판, 등록이면 null.
 * 기존 판의 본문을 덮어쓰는 경로는 두지 않습니다. (`R-CON-05`)
 *
 * - 201: 새 판을 만들었을 때
 * - 400 / TEMPLATE_TYPE_MISMATCH: 개정인데 유형이 고칠 판과 다를 때
 * - 404 / TEMPLATE_NOT_FOUND: 고칠 판이 없을 때
 * - 422 / INVALID_REQUEST: 제목이나 본문이 비었을 때
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
    return NextResponse.json(await writeAdminContractTemplate(parsed), { status: 201 });
  } catch (error) {
    return toErrorResponse(error);
  }
}
