import { cookies } from "next/headers";
import { NextResponse } from "next/server";
import { setAdminSessionCookie } from "@/lib/admin/session";
import { toErrorResponse } from "@/lib/api/route";
import { loginAsAdmin } from "@/lib/api/services";
import type { AdminLoginRequest } from "@/types/admin";

const INVALID_REQUEST = { code: "INVALID_REQUEST", message: "아이디와 비밀번호를 입력해 주세요." };

/**
 * 아직 운영자 계정을 발급하는 절차가 없습니다. 화면을 눌러 볼 수 있도록 **개발
 * 빌드에서만** 아이디·비밀번호를 비운 채 눌러도 목 서버의 콘솔 계정으로 들어갑니다.
 *
 * 값은 목 명세 `9 운영 콘솔` 의 로그인 설명에 적힌 재현용 계정이라 비밀이 아닙니다.
 * 브라우저 번들에 넣지 않으려고 화면이 아니라 이 서버 쪽 핸들러에 둡니다.
 *
 * **실제 운영 인증이 붙으면 이 상수와 `withTestAccount` 를 지웁니다.** 지우고 나면
 * 빈 값은 그대로 서버로 가 422 가 됩니다.
 */
const MOCK_CONSOLE_ACCOUNT: AdminLoginRequest = {
  loginId: "soomin",
  password: "komit-console",
};

function parseBody(body: unknown): AdminLoginRequest | null {
  if (typeof body !== "object" || body === null) return null;
  const input = body as Record<string, unknown>;

  const loginId = typeof input.loginId === "string" ? input.loginId.trim() : "";
  const password = typeof input.password === "string" ? input.password : "";
  return { loginId, password };
}

function withTestAccount(request: AdminLoginRequest): AdminLoginRequest {
  if (process.env.NODE_ENV === "production") return request;
  if (request.loginId !== "" || request.password !== "") return request;
  return MOCK_CONSOLE_ACCOUNT;
}

/**
 * POST /api/admin/login — 운영 콘솔 로그인. `9_로그인` 의 「로그인」이 부릅니다.
 *
 * 토큰은 브라우저로 내려보내지 않고 운영자 전용 httpOnly 쿠키에 넣습니다. 화면이
 * 받는 것은 누구로 들어왔는지(`admin`) 뿐입니다.
 *
 * **사용자 로그인과 쿠키도 경로도 섞이지 않습니다.** 카카오 쪽 세션은 이 요청으로
 * 생기지도 지워지지도 않습니다.
 *
 * - 200: 들어왔을 때
 * - 401 / ADMIN_LOGIN_FAILED: 아이디나 비밀번호가 맞지 않을 때
 * - 422 / INVALID_REQUEST: 본문이 계약과 다를 때
 */
export async function POST(request: Request) {
  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return NextResponse.json(INVALID_REQUEST, { status: 422 });
  }

  const parsed = parseBody(body);
  if (parsed === null) return NextResponse.json(INVALID_REQUEST, { status: 422 });

  try {
    const session = await loginAsAdmin(withTestAccount(parsed));
    setAdminSessionCookie(await cookies(), session);
    return NextResponse.json(session.admin);
  } catch (error) {
    return toErrorResponse(error);
  }
}
