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

  const body = new FormData();
  body.set("reason", reason.trim());
  const file = input.get("file");
  if (file instanceof File && file.size > 0) body.set("file", file, file.name);

  try {
    return NextResponse.json(await createReport(id, body), { status: 201 });
  } catch (error) {
    return toErrorResponse(error);
  }
}
