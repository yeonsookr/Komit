import { cookies } from "next/headers";
import { NextResponse } from "next/server";
import { clearSessionCookies, readRefreshToken, setSessionCookies } from "@/lib/auth/session";
import { refreshTokens } from "@/lib/api/services/auth";
import { toErrorResponse } from "@/lib/api/route";
import { ApiError } from "@/types/api";
import { REFRESH_TOKEN_INVALID_CODE } from "@/types/auth";

/**
 * POST /api/auth/refresh — 액세스 토큰 재발급.
 *
 * 브라우저 axios 가 401 을 받으면 부릅니다. 쿠키의 리프레시 토큰으로 새 토큰을 받아
 * 쿠키를 갈아 끼우고 204 를 돌려줍니다. 본문은 없습니다 — 토큰은 브라우저로 내려가지 않습니다.
 *
 * 리프레시 토큰이 없거나 서버가 401 을 주면 세션이 끝난 것입니다. 쿠키를 지우고
 * 401 REFRESH_TOKEN_INVALID 를 줍니다. 화면은 이걸 받으면 비로그인으로 돌립니다.
 */
export async function POST() {
  const store = await cookies();
  const refreshToken = await readRefreshToken();

  if (!refreshToken) {
    clearSessionCookies(store);
    return toErrorResponse(
      new ApiError(401, REFRESH_TOKEN_INVALID_CODE, "로그인이 만료되었습니다. 다시 로그인해 주세요."),
    );
  }

  try {
    const tokens = await refreshTokens({ refreshToken });
    setSessionCookies(store, tokens);
    return new NextResponse(null, { status: 204 });
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      clearSessionCookies(store);
    }
    return toErrorResponse(error);
  }
}
