import { NextResponse } from "next/server";
import { suggestContractQaCriteria } from "@/lib/api/services";
import { parseNumericParam, toErrorResponse } from "@/lib/api/route";
import type { ContractQaSuggestionRequest } from "@/types/contract";

const INVALID_REQUEST = { code: "INVALID_REQUEST", message: "요청 값을 확인해 주세요." };
const NOT_FOUND = { code: "ROOM_NOT_FOUND", message: "방을 찾을 수 없습니다." };

function parseBody(value: unknown): ContractQaSuggestionRequest | null {
  if (typeof value !== "object" || value === null) return null;
  const input = value as Record<string, unknown>;

  if (input.issueId !== null && !Number.isInteger(input.issueId)) return null;
  if (typeof input.title !== "string") return null;
  const title = input.title.trim();
  if (title.length < 1 || title.length > 200) return null;

  if (input.detail !== null && typeof input.detail !== "string") return null;
  const detail = typeof input.detail === "string" ? input.detail.trim() : null;
  if (detail !== null && (detail.length < 1 || detail.length > 1000)) return null;

  return { issueId: input.issueId as number | null, title, detail };
}

/** POST /api/rooms/{roomId}/contract-qa-suggestions — 현재 작업의 확인 문장 후보를 만듭니다. */
export async function POST(
  request: Request,
  { params }: { params: Promise<{ roomId: string }> },
) {
  const { roomId } = await params;
  const id = parseNumericParam(roomId);
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
    return NextResponse.json(await suggestContractQaCriteria(id, parsed));
  } catch (error) {
    return toErrorResponse(error);
  }
}
