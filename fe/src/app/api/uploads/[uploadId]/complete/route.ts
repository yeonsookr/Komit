import { NextResponse } from "next/server";
import { completeUpload } from "@/lib/api/services";
import { parseNumericParam, toErrorResponse } from "@/lib/api/route";

const NOT_FOUND = { code: "FILE_NOT_FOUND", message: "파일을 찾을 수 없습니다." };

/**
 * POST /api/uploads/{uploadId}/complete — 올리기를 마칩니다.
 *
 * 저장소에 객체가 실제로 있는지와 시작 때 알린 크기·형식이 같은지 백엔드가 확인합니다.
 * 여기까지 마쳐야 `fileId` 를 어딘가에 붙일 수 있습니다.
 */
export async function POST(
  _request: Request,
  { params }: { params: Promise<{ uploadId: string }> },
) {
  const { uploadId } = await params;
  const id = parseNumericParam(uploadId);
  if (id === null) return NextResponse.json(NOT_FOUND, { status: 404 });

  try {
    return NextResponse.json(await completeUpload(id));
  } catch (error) {
    return toErrorResponse(error);
  }
}
