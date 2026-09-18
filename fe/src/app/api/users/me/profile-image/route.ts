import { NextResponse } from "next/server";
import { updateMyProfileImage, uploadFile } from "@/lib/api/services";
import { toErrorResponse } from "@/lib/api/route";
import { authorized } from "@/lib/auth/session";

const INVALID_REQUEST = {
  code: "INVALID_REQUEST",
  message: "이미지 파일을 선택해 주세요.",
};

/**
 * POST /api/users/me/profile-image — 프로필 사진을 올리고 내 계정에 붙입니다.
 *
 * 브라우저는 사진 한 장만 보내고, 올리기 3콜과 계정 연결은 이 핸들러가 처리합니다.
 */
export async function POST(request: Request) {
  try {
    await authorized();
  } catch (error) {
    return toErrorResponse(error);
  }

  let file: FormDataEntryValue | null;
  try {
    file = (await request.formData()).get("image");
  } catch {
    return NextResponse.json(INVALID_REQUEST, { status: 422 });
  }

  if (!(file instanceof File) || file.size === 0 || !file.type.startsWith("image/")) {
    return NextResponse.json(INVALID_REQUEST, { status: 422 });
  }

  try {
    const uploaded = await uploadFile(file, "PROFILE_IMAGE");
    const account = await updateMyProfileImage({ fileId: uploaded.fileId });
    return NextResponse.json(account);
  } catch (error) {
    return toErrorResponse(error);
  }
}
