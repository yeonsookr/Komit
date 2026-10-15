import { NextResponse } from "next/server";
import { createReport } from "@/lib/api/services";
import { parseNumericParam, toErrorResponse } from "@/lib/api/route";

const INVALID_REQUEST = { code: "INVALID_REQUEST", message: "요청 값을 확인해 주세요." };
const NOT_FOUND = { code: "CONTRACT_NOT_FOUND", message: "계약을 찾을 수 없습니다." };
const MAX_REASON_LENGTH = 255;

export async function POST(
  request: Request,
  { params }: { params: Promise<{ contractId: string }> },
) {
  const { contractId } = await params;
  const id = parseNumericParam(contractId);
  if (id === null) return NextResponse.json(NOT_FOUND, { status: 404 });

  // 증거 파일은 이 핸들러를 지나가지 않습니다. 브라우저가 저장소로 직접 보내고 fileId 만 옵니다.
  let input: { reason?: unknown; fileId?: unknown };
  try {
    input = (await request.json()) as { reason?: unknown; fileId?: unknown };
  } catch {
    return NextResponse.json(INVALID_REQUEST, { status: 422 });
  }

  const { reason, fileId } = input;
  if (typeof reason !== "string" || reason.trim() === "" || reason.length > MAX_REASON_LENGTH) {
    return NextResponse.json(INVALID_REQUEST, { status: 422 });
  }
  // 증거는 없어도 됩니다. 있으면 올리기를 마친 파일이어야 합니다.
  if (fileId != null && (typeof fileId !== "number" || !Number.isInteger(fileId) || fileId <= 0)) {
    return NextResponse.json(INVALID_REQUEST, { status: 422 });
  }

  try {
    const body = { reason: reason.trim(), fileId: (fileId as number | undefined) ?? null };
    return NextResponse.json(await createReport(id, body), { status: 201 });
  } catch (error) {
    return toErrorResponse(error);
  }
}
