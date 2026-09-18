import { cookies } from "next/headers";
import { NextResponse } from "next/server";
import { clearAdminSessionCookie } from "@/lib/admin/session";

/**
 * POST /api/admin/logout — 운영 콘솔에서 나가기.
 *
 * **서버를 부르지 않습니다.** 운영 세션에는 리프레시 토큰이 없어 서버에 지울 것이
 * 남지 않습니다(목 명세 영역 9에 로그아웃 API가 없는 이유입니다). 액세스 토큰은
 * `expiresIn` 이 지나면 스스로 만료됩니다.
 *
 * **사용자 쿠키는 건드리지 않습니다.** 한 브라우저에서 사용자로도 로그인해 있을 수
 * 있고, 운영 콘솔에서 나간다고 그 세션까지 끊을 이유가 없습니다.
 */
export async function POST() {
  clearAdminSessionCookie(await cookies());
  return new NextResponse(null, { status: 204 });
}
