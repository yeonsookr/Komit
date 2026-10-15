import { NextResponse } from "next/server";
import {
  attachDeliverable,
  downloadDeliverable,
} from "@/lib/api/services";
import { parseNumericParam, toErrorResponse } from "@/lib/api/route";
import { attachmentDisposition } from "@/lib/api/download";

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
  let accessStage: unknown;
  try {
    ({ fileId, accessStage } = (await request.json()) as { fileId?: unknown; accessStage?: unknown });
  } catch {
    return NextResponse.json(INVALID_REQUEST, { status: 422 });
  }

  if (typeof fileId !== "number" || !Number.isSafeInteger(fileId) || fileId <= 0 ||
      (accessStage !== "TEST" && accessStage !== "PAID")) {
    return NextResponse.json(INVALID_REQUEST, { status: 422 });
  }

  try {
    const deliverable = await attachDeliverable(id, fileId, accessStage);
    return NextResponse.json(deliverable, { status: 201 });
  } catch (error) {
    return toErrorResponse(error);
  }
}

/**
 * GET /api/deliverables/{id}/file — 기존 단일 인계 파일을 내려받습니다.
 *
 * 본문이 파일 자체라 JSON 이 아닙니다. 파일 이름은 백엔드가 준 것을 그대로
 * 넘겨 브라우저가 저장할 때 쓰게 합니다. 지급 전 열람 차단은 백엔드가 강제합니다.
 */
export async function GET(
  _request: Request,
  { params }: { params: Promise<{ deliverableId: string }> },
) {
  const { deliverableId } = await params;
  const id = parseNumericParam(deliverableId);
  if (id === null) return NextResponse.json(NOT_FOUND, { status: 404 });

  try {
    const file = await downloadDeliverable(id);
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
