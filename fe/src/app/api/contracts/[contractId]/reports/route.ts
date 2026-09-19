import { NextResponse } from "next/server";
import { createReport, uploadFile } from "@/lib/api/services";
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

  let input: FormData;
  try {
    input = await request.formData();
  } catch {
    return NextResponse.json(INVALID_REQUEST, { status: 422 });
  }

  const reason = input.get("reason");
  if (typeof reason !== "string" || reason.trim() === "" || reason.length > MAX_REASON_LENGTH) {
    return NextResponse.json(INVALID_REQUEST, { status: 422 });
  }

  try {
    const file = input.get("file");
    const uploaded =
      file instanceof File && file.size > 0 ? await uploadFile(file, "REPORT_EVIDENCE") : null;
    const body = { reason: reason.trim(), fileId: uploaded?.fileId ?? null };
    return NextResponse.json(await createReport(id, body), { status: 201 });
  } catch (error) {
    return toErrorResponse(error);
  }
}
