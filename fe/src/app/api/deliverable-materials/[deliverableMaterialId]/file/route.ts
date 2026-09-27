import { NextResponse } from "next/server";
import { downloadDeliverableMaterial } from "@/lib/api/services";
import { parseNumericParam, toErrorResponse } from "@/lib/api/route";
import { attachmentDisposition } from "@/lib/api/download";

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
    const file = await downloadDeliverableMaterial(id);
    // 본문은 스트림 그대로 넘깁니다. 수십 MB 파일도 메모리에 모으지 않습니다.
    const headers = new Headers({
      "Content-Type": file.contentType,
      "Content-Disposition": attachmentDisposition(file.fileName),
    });
    if (file.contentLength !== null) headers.set("Content-Length", file.contentLength);
    return new NextResponse(file.body, { headers });
  } catch (error) {
    return toErrorResponse(error);
  }
}
