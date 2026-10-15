import { cookies } from "next/headers";
import { NextResponse } from "next/server";
import { withdraw } from "@/lib/api/services";
import { toErrorResponse } from "@/lib/api/route";
import { authorized, clearSessionCookies } from "@/lib/auth/session";

/**
 * POST /api/users/me/withdrawal — 회원 탈퇴. 탈퇴 화면의 「탈퇴하기」가 부릅니다.
 *
 * 보낼 값이 없습니다. 탈퇴 대상은 언제나 토큰의 주인이라 본문으로 받지 않습니다 —
 * 받으면 남의 계정을 가리킬 자리가 생깁니다.
 *
 * 성공하면 쿠키를 지웁니다. 계정이 삭제 표시된 뒤에도 토큰이 남아 있으면 화면은
 * 로그인한 것처럼 보이는데 어떤 요청도 통하지 않습니다. 실패했을 때는 세션이
 * 그대로라 지우지 않습니다. 화면이 버튼을 잠갔더라도 서버가 다시 보므로 정리하지
 * 않은 것이 남아 있으면 409 가 그대로 전달됩니다.
 */
export async function POST() {
  try {
    await authorized();
  } catch (error) {
    return toErrorResponse(error);
  }

  try {
    const result = await withdraw();
    clearSessionCookies(await cookies());
    return NextResponse.json(result);
  } catch (error) {
    return toErrorResponse(error);
  }
}
