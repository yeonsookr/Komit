import "server-only";

import { cookies } from "next/headers";
import { ApiError } from "@/types/api";
import { UNAUTHORIZED_CODE } from "@/types/auth";
/**
 * 쿠키 **이름**은 `cookies.ts` 에 있습니다 — proxy 도 같은 값을 읽어야 하는데 이
 * 모듈은 `server-only` 라 거기서 불러올 수 없습니다. 쓰던 곳이 그대로 쓰도록 여기서
 * 다시 내보냅니다. 쿠키를 굽고 지우는 일은 계속 이 모듈이 합니다.
 */
import { ADMIN_ACCESS_TOKEN_COOKIE } from "./cookies";

export { ADMIN_ACCESS_TOKEN_COOKIE };

/**
 * 운영 콘솔 세션은 httpOnly 쿠키 하나입니다.
 *
 * 사용자 세션과 달리 리프레시 토큰이 없습니다. 목 명세의 운영 로그인은 액세스
 * 토큰과 `expiresIn` 만 주고 재발급 API를 두지 않았습니다. 만료되면 다시 로그인합니다
 * — 그래서 **사용자 쪽 재발급 인터셉터를 이 세션에 붙이면 안 됩니다**(`admin/client.ts`).
 */
type CookieStore = Awaited<ReturnType<typeof cookies>>;

function baseOptions() {
  return {
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax" as const,
    path: "/",
  };
}

/** 운영 로그인 응답의 토큰을 쿠키에 넣습니다. 수명은 서버가 준 `expiresIn` 그대로입니다. */
export function setAdminSessionCookie(
  store: CookieStore,
  tokens: { accessToken: string; expiresIn: number },
): void {
  store.set(ADMIN_ACCESS_TOKEN_COOKIE, tokens.accessToken, {
    ...baseOptions(),
    maxAge: tokens.expiresIn,
  });
}

/**
 * 운영 로그아웃.
 * **사용자 쿠키는 건드리지 않습니다.** 한 브라우저에서 사용자로도 로그인해 있을 수
 * 있고, 운영 콘솔에서 나간다고 그 세션까지 끊을 이유가 없습니다.
 */
export function clearAdminSessionCookie(store: CookieStore): void {
  store.set(ADMIN_ACCESS_TOKEN_COOKIE, "", { ...baseOptions(), maxAge: 0 });
}

/** 현재 요청의 운영자 토큰. 로그인하지 않았으면 null. */
export async function readAdminAccessToken(): Promise<string | null> {
  const store = await cookies();
  return store.get(ADMIN_ACCESS_TOKEN_COOKIE)?.value ?? null;
}

/**
 * 운영 API에 붙이는 axios 옵션. 토큰이 없으면 서버를 부르지 않고 바로 401 을 던집니다.
 *
 * 사용자 쪽 `authorized()` 를 재사용하지 않습니다. 그쪽은 사용자 쿠키를 읽으므로,
 * 사용자로만 로그인한 사람이 운영 API를 부를 수 있게 됩니다. 목 서버는 토큰을
 * 검사하지 않아 그래도 200 이 오고, 그 구멍은 실서버가 붙을 때에야 드러납니다.
 */
export async function adminAuthorized(): Promise<{ headers: { Authorization: string } }> {
  const token = await readAdminAccessToken();
  if (!token) {
    throw new ApiError(401, UNAUTHORIZED_CODE, "운영 콘솔에 로그인해 주세요.");
  }
  return { headers: { Authorization: `Bearer ${token}` } };
}
