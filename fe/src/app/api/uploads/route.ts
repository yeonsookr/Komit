import { NextResponse } from "next/server";
import { createUpload } from "@/lib/api/services";
import { toErrorResponse } from "@/lib/api/route";
import type { CreateUploadRequest } from "@/types/upload";

const INVALID_REQUEST = {
  code: "INVALID_REQUEST",
  message: "요청 형식이 올바르지 않습니다.",
};

/**
 * POST /api/uploads — 파일을 보낼 주소를 받습니다.
 *
 * 형식과 크기는 백엔드가 이 단계에서 거릅니다. 받은 주소로는 **브라우저가 저장소에 직접**
 * 파일을 보냅니다. 파일 본문은 이 핸들러를 지나가지 않습니다 — 녹음은 십수 MiB 라 서버가
 * 받았다 다시 보내면 메모리를 그만큼 쓰고 두 번 이동합니다.
 */
export async function POST(request: Request) {
  let body: CreateUploadRequest;
  try {
    body = (await request.json()) as CreateUploadRequest;
  } catch {
    return NextResponse.json(INVALID_REQUEST, { status: 422 });
  }

  try {
    return NextResponse.json(await createUpload(body));
  } catch (error) {
    return toErrorResponse(error);
  }
}
