import { NextResponse } from "next/server";
import { writeDevelopmentContract } from "@/lib/api/services";
import { parseNumericParam, toErrorResponse } from "@/lib/api/route";
import type {
  WriteDevelopmentContractRequest,
  WriteMilestoneRequest,
} from "@/types/contract";

const INVALID_REQUEST = { code: "INVALID_REQUEST", message: "요청 값을 확인해 주세요." };
const NOT_FOUND = { code: "ROOM_NOT_FOUND", message: "방을 찾을 수 없습니다." };

/** 비어 있지 않은 글. 없으면 undefined, 글이 아니거나 비어 있으면 null 입니다. */
function optionalText(value: unknown): string | null | undefined {
  if (value === undefined || value === null) return undefined;
  if (typeof value !== "string" || value.trim() === "") return null;
  return value.trim();
}

/**
 * 작업 하나. 확인 문장이 하나 이상 있어야 완료 조건이 생깁니다.
 * 요구사항에 붙지 않은 새 작업은 이름이 있어야 서버가 요구사항을 만들 수 있습니다.
 */
function parseMilestone(value: unknown): WriteMilestoneRequest | null {
  if (typeof value !== "object" || value === null) return null;
  const input = value as Record<string, unknown>;

  const issueId = input.issueId;
  if (issueId !== undefined && issueId !== null && !Number.isInteger(issueId)) return null;
  const title = optionalText(input.title);
  const detail = optionalText(input.detail);
  if (title === null || detail === null) return null;
  if (!Number.isInteger(issueId) && title === undefined) return null;
  if (!Number.isInteger(input.price) || (input.price as number) < 0) return null;
  const isHandover = input.isHandover;
  if (isHandover !== undefined && typeof isHandover !== "boolean") return null;
  if (!Array.isArray(input.qaCriteria) || input.qaCriteria.length === 0) return null;
  if (!input.qaCriteria.every((qa) => typeof qa === "string" && qa.trim() !== "")) return null;

  const workDate = input.workDate;
  if (workDate !== undefined && workDate !== null && !Number.isInteger(workDate)) return null;

  return {
    ...(Number.isInteger(issueId) ? { issueId: issueId as number } : {}),
    ...(title === undefined ? {} : { title }),
    ...(detail === undefined ? {} : { detail }),
    ...(isHandover === true ? { isHandover: true } : {}),
    price: input.price as number,
    ...(Number.isInteger(workDate) ? { workDate: workDate as number } : {}),
    qaCriteria: (input.qaCriteria as string[]).map((qa) => qa.trim()),
  };
}

function parseBody(body: unknown): WriteDevelopmentContractRequest | null {
  if (typeof body !== "object" || body === null) return null;
  const input = body as Record<string, unknown>;

  if (typeof input.startDate !== "string" || typeof input.dueDate !== "string") return null;
  if (!Number.isInteger(input.freeRepairDays)) return null;
  if (!Array.isArray(input.milestones) || input.milestones.length === 0) return null;

  const milestones = input.milestones.map(parseMilestone);
  if (milestones.some((milestone) => milestone === null)) return null;

  const titles = input.deliverableTitles;
  if (titles !== undefined && !Array.isArray(titles)) return null;

  return {
    startDate: input.startDate,
    dueDate: input.dueDate,
    freeRepairDays: input.freeRepairDays as number,
    milestones: milestones as WriteMilestoneRequest[],
    ...(Array.isArray(titles)
      ? { deliverableTitles: (titles as string[]).filter((t) => t.trim() !== "") }
      : {}),
    ...(typeof input.message === "string" && input.message.trim() !== ""
      ? { message: input.message.trim() }
      : {}),
  };
}

/**
 * POST /api/rooms/{roomId}/development-contracts — 개발 계약서 작성.
 *
 * 협의 중에 다시 보내면 덮어씁니다. 그래서 작성과 수정이 같은 요청입니다.
 * 이 방의 개발자가 아니면 백엔드가 403 을 주고 그대로 전달합니다.
 */
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
    return NextResponse.json(await writeDevelopmentContract(id, parsed), { status: 201 });
  } catch (error) {
    return toErrorResponse(error);
  }
}
