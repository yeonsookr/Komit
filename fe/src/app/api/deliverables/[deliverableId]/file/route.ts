import { NextResponse } from "next/server";
import {
  attachDeliverable,
  downloadDeliverable,
} from "@/lib/api/services";
import { parseNumericParam, toErrorResponse } from "@/lib/api/route";

const INVALID_REQUEST = { code: "INVALID_REQUEST", message: "파일을 첨부해 주세요." };
const NOT_FOUND = { code: "DELIVERABLE_NOT_FOUND", message: "인계물을 찾을 수 없습니다." };

/**
 * POST /api/deliverables/{id}/file — 개발자가 인계물을 올립니다.
 *
 * 파일을 먼저 저장소에 올리고(3콜) 받은 `fileId` 로 인계물에 붙입니다.
 * 올리기 주소는 브라우저가 알 필요가 없어 이 핸들러가 넷을 모두 처리합니다.
 */
export async function POST(
  request: Request,
  { params }: { params: Promise<{ deliverableId: string }> },
) {
  const { deliverableId } = await params;
  const id = parseNumericParam(deliverableId);
  if (id === null) return NextResponse.json(NOT_FOUND, { status: 404 });

  // 파일은 이 핸들러를 지나가지 않습니다. 브라우저가 저장소로 직접 보내고 fileId 만 옵니다.
  let fileId: unknown;
  try {
    ({ fileId } = (await request.json()) as { fileId?: unknown });
  } catch {
    return NextResponse.json(INVALID_REQUEST, { status: 422 });
  }

  if (typeof fileId !== "number" || !Number.isInteger(fileId) || fileId <= 0) {
    return NextResponse.json(INVALID_REQUEST, { status: 422 });
  }

  try {
    const deliverable = await attachDeliverable(id, fileId);
    return NextResponse.json(deliverable, { status: 201 });
  } catch (error) {
    return toErrorResponse(error);
  }
}

/**
 * GET /api/deliverables/{id}/file — 의뢰인이 인계물을 내려받습니다.
 *
 * 본문이 파일 자체라 JSON 이 아닙니다. 파일 이름은 백엔드가 준 것을 그대로
 * 넘겨 브라우저가 저장할 때 쓰게 합니다.
 */
export async function GET(
  _request: Request,
  { params }: { params: Promise<{ deliverableId: string }> },
) {
  const { deliverableId } = await params;
  const id = parseNumericParam(deliverableId);
  if (id === null) return NextResponse.json(NOT_FOUND, { status: 404 });

  try {
    const { body, fileName, contentType } = await downloadDeliverable(id);

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
