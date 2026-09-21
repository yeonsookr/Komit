import { NextResponse } from "next/server";
import { createSessionRecording, uploadFile } from "@/lib/api/services";
import { parseNumericParam, toErrorResponse } from "@/lib/api/route";
import { authorized } from "@/lib/auth/session";

const INVALID_REQUEST = {
  code: "INVALID_REQUEST",
  message: "녹음 파일을 찾지 못했습니다.",
};

/**
 * POST /api/consulting-sessions/[sessionId]/recordings — 세션 녹음을 올리고 세션에 붙입니다.
 *
 * 브라우저는 자기 마이크로 담은 소리 하나만 보내고, 올리기 3콜과 세션 연결은 이 핸들러가
 * 합니다. 저장소 주소는 브라우저가 알지 않습니다.
 *
 * 누가 올렸는지는 토큰으로 정해집니다. 요청에 화자를 담지 않습니다. (`R-CON-14`)
 */
export async function POST(
  request: Request,
  { params }: { params: Promise<{ sessionId: string }> },
) {
  const sessionId = parseNumericParam((await params).sessionId);
  if (sessionId === null) {
    return NextResponse.json(INVALID_REQUEST, { status: 422 });
  }

  try {
    await authorized();
  } catch (error) {
    return toErrorResponse(error);
  }

  let recording: FormDataEntryValue | null;
  try {
    recording = (await request.formData()).get("recording");
  } catch {
    return NextResponse.json(INVALID_REQUEST, { status: 422 });
  }

  if (!(recording instanceof File) || recording.size === 0 || !recording.type.startsWith("audio/")) {
    return NextResponse.json(INVALID_REQUEST, { status: 422 });
  }

  try {
    const uploaded = await uploadFile(recording, "SESSION_RECORDING");
    const attached = await createSessionRecording(sessionId, { fileId: uploaded.fileId });
    return NextResponse.json(attached, { status: 201 });
  } catch (error) {
    return toErrorResponse(error);
  }
}
