import { NextResponse } from "next/server";
import { getMe } from "@/lib/api/services";
import { toErrorResponse } from "@/lib/api/route";

/** GET /api/users/me — 헤더와 의뢰 구분에 쓰는 내 정보. */
export async function GET() {
  try {
    return NextResponse.json(await getMe());
  } catch (error) {
    return toErrorResponse(error);
  }
}
