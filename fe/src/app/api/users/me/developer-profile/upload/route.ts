import { NextResponse } from "next/server";
import { uploadFile } from "@/lib/api/services";
import { toErrorResponse } from "@/lib/api/route";
import { authorized } from "@/lib/auth/session";
import type { UploadPurpose } from "@/types/upload";

const PURPOSES: UploadPurpose[] = ["CAREER_EVIDENCE", "PORTFOLIO_IMAGE"];
const INVALID_REQUEST = { code: "INVALID_REQUEST", message: "올릴 파일을 확인해 주세요." };

export async function POST(request: Request) {
  try {
    await authorized();
    const form = await request.formData();
    const file = form.get("file");
    const purpose = form.get("purpose");
    if (!(file instanceof File) || file.size === 0 || typeof purpose !== "string" || !PURPOSES.includes(purpose as UploadPurpose)) {
      return NextResponse.json(INVALID_REQUEST, { status: 422 });
    }
    return NextResponse.json(await uploadFile(file, purpose as UploadPurpose));
  } catch (error) {
    return toErrorResponse(error);
  }
}
