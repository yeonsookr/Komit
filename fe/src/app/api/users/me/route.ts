import { NextResponse } from "next/server";
import { getMe } from "@/lib/api/services";
import { toErrorResponse } from "@/lib/api/route";
import { authorized } from "@/lib/auth/session";

/**
 * GET /api/users/me — 헤더와 의뢰 구분에 쓰는 내 정보.
 * 토큰 쿠키가 없으면 서버를 부르지 않고 401 입니다. 헤더의 로그인 상태가 이 응답으로
 * 정해지므로, 목이 빈 요청에 200 을 주더라도 비로그인은 비로그인으로 보여야 합니다.
 */
export async function GET() {
  try {
    await authorized();
    return NextResponse.json(await getMe());
  } catch (error) {
    return toErrorResponse(error);
  }
}
