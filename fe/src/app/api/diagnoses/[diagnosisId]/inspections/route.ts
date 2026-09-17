import { NextResponse } from "next/server";
import { uploadInspection } from "@/lib/api/services";
import { parseNumericParam, toErrorResponse } from "@/lib/api/route";

const INVALID_REQUEST = { code: "INVALID_REQUEST", message: "파일을 첨부해 주세요." };
const NOT_FOUND = { code: "DIAGNOSIS_NOT_FOUND", message: "진단을 찾을 수 없습니다." };

/** POST /api/diagnoses/{id}/inspections — 검사 결과 파일 올리기. */
export async function POST(
  request: Request,
  { params }: { params: Promise<{ diagnosisId: string }> },
) {
  const { diagnosisId } = await params;
  const id = parseNumericParam(diagnosisId);
  if (id === null) return NextResponse.json(NOT_FOUND, { status: 404 });

  let file: FormDataEntryValue | null;
  try {
    file = (await request.formData()).get("file");
  } catch {
    return NextResponse.json(INVALID_REQUEST, { status: 422 });
  }

  if (!(file instanceof File) || file.size === 0) {
    return NextResponse.json(INVALID_REQUEST, { status: 422 });
  }

  try {
    return NextResponse.json(await uploadInspection(id, file), { status: 201 });
  } catch (error) {
    return toErrorResponse(error);
  }
}
