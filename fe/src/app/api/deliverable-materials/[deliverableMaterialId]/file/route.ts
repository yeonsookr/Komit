import { NextResponse } from "next/server";
import { downloadDeliverableMaterial } from "@/lib/api/services";
import { parseNumericParam, toErrorResponse } from "@/lib/api/route";

const NOT_FOUND = { code: "DELIVERABLE_NOT_FOUND", message: "인계물을 찾을 수 없습니다." };

/** 자료별 권한 검사를 통과한 인계 파일만 전달합니다. */
export async function GET(
  _request: Request,
  { params }: { params: Promise<{ deliverableMaterialId: string }> },
) {
  const { deliverableMaterialId } = await params;
  const id = parseNumericParam(deliverableMaterialId);
  if (id === null) return NextResponse.json(NOT_FOUND, { status: 404 });

  try {
    const { body, fileName, contentType } = await downloadDeliverableMaterial(id);
    return new NextResponse(body, {
      headers: {
        "Content-Type": contentType,
        "Content-Disposition": `attachment; filename="${encodeURIComponent(fileName)}"`,
      },
    });
  } catch (error) {
    return toErrorResponse(error);
  }
}
