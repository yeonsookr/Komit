import { cookies } from "next/headers";
import { type NextRequest, NextResponse } from "next/server";
import { requireServerEnv } from "@/lib/env";
import { OAUTH_HANDOFF_COOKIE, clearOauthHandoffCookie, setSessionCookies } from "@/lib/auth/session";
import { parseOauthHandoff } from "@/lib/auth/kakao";
import { loginWithKakao } from "@/lib/api/services/auth";
import { ROUTES } from "@/lib/routes";
import { ApiError } from "@/types/api";

/** 카카오가 아니라 우리 쪽 검사에서 걸렸을 때. 화면에는 로그인 실패 문구로 보입니다. */
const STATE_MISMATCH_CODE = "KAKAO_AUTH_FAILED";

/**
 * GET /api/auth/kakao/callback — 카카오가 인가 코드를 돌려주는 곳.
 *
 * 1. 쿠키의 진행값(state·next)을 꺼내고 지웁니다. 한 번만 쓰는 값입니다.
 * 2. 카카오가 준 state 가 쿠키의 것과 같은지 봅니다. 다르면 로그인 실패입니다.
 * 3. 인가 코드를 코밋 서버에 넘겨 토큰을 받고 httpOnly 쿠키에 넣습니다.
 * 4. 돌아갈 곳으로 보냅니다. 없으면 랜딩입니다.
 *
 * 실패는 전부 로그인 화면으로 돌려보내고 `error` 에 코드만 싣습니다.
 * 문구는 화면이 코드로 정합니다 — 주소에 문장을 실어 보내지 않습니다.
 */
export async function GET(request: NextRequest) {
  const store = await cookies();
  const handoff = parseOauthHandoff(store.get(OAUTH_HANDOFF_COOKIE)?.value);
  clearOauthHandoffCookie(store);

  const params = request.nextUrl.searchParams;
  const code = params.get("code");
  const state = params.get("state");

  const fail = (errorCode: string) =>
    NextResponse.redirect(new URL(ROUTES.loginFailed(errorCode), request.nextUrl.origin));

  // 사용자가 카카오 화면에서 취소했거나 카카오가 오류를 돌려준 경우.
  if (params.get("error") || !code) return fail(STATE_MISMATCH_CODE);
  if (!handoff || !state || state !== handoff.state) return fail(STATE_MISMATCH_CODE);

  try {
    const redirectUri = requireServerEnv("KAKAO_REDIRECT_URI");
    const login = await loginWithKakao({ code, redirectUri });

    setSessionCookies(store, login);

    return NextResponse.redirect(new URL(handoff.next ?? ROUTES.home, request.nextUrl.origin));
  } catch (error) {
    if (error instanceof ApiError && error.status > 0) {
      // 401 KAKAO_AUTH_FAILED · 503 KAKAO_UNAVAILABLE — 화면이 코드로 문구를 고릅니다.
      return fail(error.code);
    }
    console.error("[auth] 카카오 로그인을 처리하지 못했습니다", error);
    return fail(STATE_MISMATCH_CODE);
  }
}
