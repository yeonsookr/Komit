import { cookies } from "next/headers";
import { type NextRequest, NextResponse } from "next/server";
import { requireServerEnv } from "@/lib/env";
import { buildKakaoAuthorizeUrl, createOauthState, serializeOauthHandoff } from "@/lib/auth/kakao";
import { NEXT_PARAM } from "@/lib/auth/params";
import { safeNext } from "@/lib/auth/safeNext";
import { setOauthHandoffCookie } from "@/lib/auth/session";
import { toErrorResponse } from "@/lib/api/route";

/**
 * GET /api/auth/kakao — 카카오 인가 시작. 로그인 카드의 「카카오로 시작하기」가 옵니다.
 *
 * state 를 만들어 돌아갈 곳(`next`)과 함께 쿠키에 두고 카카오 로그인 화면으로 보냅니다.
 * 콜백이 쿠키의 state 와 카카오가 돌려준 state 를 맞춰 봐 CSRF 를 막습니다.
 *
 * REST API 키와 Redirect URI 는 서버 전용 환경 변수입니다. 없으면 진행하지 않습니다.
 */
export async function GET(request: NextRequest) {
  try {
    const clientId = requireServerEnv("KAKAO_CLIENT_ID");
    const redirectUri = requireServerEnv("KAKAO_REDIRECT_URI");

    const state = createOauthState();
    const next = safeNext(request.nextUrl.searchParams.get(NEXT_PARAM));

    setOauthHandoffCookie(await cookies(), serializeOauthHandoff({ state, next }));

    return NextResponse.redirect(buildKakaoAuthorizeUrl({ clientId, redirectUri, state }));
  } catch (error) {
    return toErrorResponse(error);
  }
}
