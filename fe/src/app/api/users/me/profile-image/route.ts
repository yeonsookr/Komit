import { NextResponse } from "next/server";
import { updateMyProfileImage } from "@/lib/api/services";
import { toErrorResponse } from "@/lib/api/route";
import { authorized } from "@/lib/auth/session";

const INVALID_REQUEST = {
  code: "INVALID_REQUEST",
  message: "이미지 파일을 선택해 주세요.",
};

/**
 * POST /api/users/me/profile-image — 올린 사진을 내 계정에 붙입니다.
 *
 * **파일은 이 핸들러를 지나가지 않습니다.** 브라우저가 저장소로 직접 보내고 받은 `fileId` 만
 * 여기로 옵니다.
 */
export async function POST(request: Request) {
  try {
    await authorized();
  } catch (error) {
    return toErrorResponse(error);
  }

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
    return NextResponse.json(await updateMyProfileImage({ fileId }));
  } catch (error) {
    return toErrorResponse(error);
  }
}
