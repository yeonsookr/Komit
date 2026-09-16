import { cookies } from "next/headers";
import { NextResponse } from "next/server";
import { clearSessionCookies } from "@/lib/auth/session";
import { logoutOnServer } from "@/lib/api/services/auth";
import { toErrorResponse } from "@/lib/api/route";
import { ApiError } from "@/types/api";

/**
 * POST /api/auth/logout — 로그아웃. 헤더 아바타 메뉴의 「로그아웃」이 부릅니다.
 *
 * 서버에 알려 리프레시 토큰을 지우게 하고, 쿠키를 지웁니다. 서버가 401 을 주면
 * 이미 끝난 세션이므로 성공과 같이 다룹니다(목 명세: 다시 요청해도 204).
 * 그 밖의 실패는 쿠키를 지우지 않고 그대로 돌려줍니다 — 서버에 남은 세션을 화면만
 * 잊어버리면 사용자는 로그아웃했다고 믿는데 토큰은 살아 있게 됩니다.
 */
export async function POST() {
  const store = await cookies();

  try {
    await logoutOnServer();
  } catch (error) {
    if (!(error instanceof ApiError && error.status === 401)) {
      return toErrorResponse(error);
    }
  }

  clearSessionCookies(store);
  return new NextResponse(null, { status: 204 });
}
