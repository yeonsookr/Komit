import { NextResponse } from "next/server";
import { downloadScanFile } from "@/lib/api/services";
import { parseNumericParam, toErrorResponse } from "@/lib/api/route";

const NOT_FOUND = { code: "DIAGNOSIS_NOT_FOUND", message: "진단을 찾을 수 없습니다." };

/**
 * GET /api/diagnoses/{id}/scan-file — 검사 파일 내려받기.
 *
 * 본문이 파일 내용이고 파일 이름은 Content-Disposition 에 담겨 옵니다.
 * 브라우저가 백엔드를 직접 부르지 않도록 여기서 그대로 흘려보냅니다.
 */
export async function GET(
  _request: Request,
  { params }: { params: Promise<{ diagnosisId: string }> },
) {
  const { diagnosisId } = await params;
  const id = parseNumericParam(diagnosisId);
  if (id === null) return NextResponse.json(NOT_FOUND, { status: 404 });

  try {
    const file = await downloadScanFile(id);

    return new NextResponse(file.body, {
      headers: {
        "Content-Type": file.contentType,
        "Content-Disposition": `attachment; filename="${file.fileName}"`,
      },
    });
  } catch (error) {
    return toErrorResponse(error);
  }
}
