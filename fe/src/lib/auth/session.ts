import "server-only";

import { cookies } from "next/headers";
import { ApiError } from "@/types/api";
import { UNAUTHORIZED_CODE, type TokenResponse } from "@/types/auth";
/**
 * 쿠키 **이름**은 `cookies.ts` 에 있습니다 — proxy 도 같은 값을 읽어야 하는데
 * 이 모듈은 `server-only` 라 거기서 불러올 수 없습니다. 쓰던 곳이 그대로 쓰도록
 * 여기서 다시 내보냅니다. 쿠키를 굽고 지우는 일은 계속 이 모듈이 합니다.
 */
import {
  ACCESS_TOKEN_COOKIE,
  AUTH_COOKIE_PATH,
  OAUTH_HANDOFF_COOKIE,
  REFRESH_TOKEN_COOKIE,
  SESSION_COOKIE_PATH,
} from "./cookies";

/**
 * 로그인 세션은 httpOnly 쿠키 두 개입니다. 브라우저 자바스크립트는 토큰을 읽지
 * 못하고, Next 서버(Route Handler·서버 컴포넌트)만 꺼내 `Authorization` 에 싣습니다
 * (docs/FRONTEND_CONVENTIONS.md 5·6장 — 토큰은 서버 측에서 주입).
 *
 * - 액세스 토큰: 모든 경로에 붙습니다. 수명은 서버가 준 `expiresIn` 그대로입니다.
 * - 리프레시 토큰: `/api/auth` 아래로만 보냅니다. 재발급·로그아웃 말고는 쓸 곳이 없어
 *   화면 요청마다 실려 다닐 이유가 없습니다.
 * - 인가 진행값: 인가 시작 → 콜백 사이에만 살고, 콜백에서 바로 지웁니다.
 */
export { ACCESS_TOKEN_COOKIE, REFRESH_TOKEN_COOKIE, OAUTH_HANDOFF_COOKIE };

/** R-AUTH-02: 코밋 리프레시 토큰과 쿠키는 같은 시점에 만료합니다. */
const REFRESH_TOKEN_MAX_AGE_SECONDS = 14 * 24 * 60 * 60;

/** 카카오 인가 화면을 오가는 데 넉넉한 시간. 넘기면 처음부터 다시 시작합니다. */
const OAUTH_HANDOFF_MAX_AGE_SECONDS = 10 * 60;

type CookieStore = Awaited<ReturnType<typeof cookies>>;

/**
 * 공통 옵션. `secure` 는 배포에서만 켭니다 — 로컬 http 에서 켜면 브라우저가 쿠키를
 * 버려 로그인이 되지 않습니다. `sameSite: lax` 는 카카오에서 돌아오는 최상위
 * 이동(GET)에도 쿠키가 실리게 하려는 값입니다. `strict` 면 콜백에서 진행값을 못 읽습니다.
 */
function baseOptions(path: string) {
  return {
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax" as const,
    path,
  };
}

/**
 * 로그인·재발급 응답의 토큰을 쿠키에 넣습니다.
 *
 * 액세스 쿠키는 서버 응답의 수명을 따르고, 리프레시 쿠키는 R-AUTH-02를 따릅니다.
 * 둘 다 httpOnly라 브라우저 자바스크립트에는 노출되지 않습니다.
 */
export function setSessionCookies(store: CookieStore, tokens: TokenResponse): void {
  store.set(ACCESS_TOKEN_COOKIE, tokens.accessToken, {
    ...baseOptions(SESSION_COOKIE_PATH),
    maxAge: tokens.expiresIn,
  });
  store.set(REFRESH_TOKEN_COOKIE, tokens.refreshToken, {
    ...baseOptions(AUTH_COOKIE_PATH),
    maxAge: REFRESH_TOKEN_MAX_AGE_SECONDS,
  });
}

/** 로그아웃과 재발급 실패에서 부릅니다. 지울 때도 넣을 때와 같은 path 여야 지워집니다. */
export function clearSessionCookies(store: CookieStore): void {
  store.set(ACCESS_TOKEN_COOKIE, "", { ...baseOptions(SESSION_COOKIE_PATH), maxAge: 0 });
  store.set(REFRESH_TOKEN_COOKIE, "", { ...baseOptions(AUTH_COOKIE_PATH), maxAge: 0 });
}

export function setOauthHandoffCookie(store: CookieStore, value: string): void {
  store.set(OAUTH_HANDOFF_COOKIE, value, {
    ...baseOptions(AUTH_COOKIE_PATH),
    maxAge: OAUTH_HANDOFF_MAX_AGE_SECONDS,
  });
}

export function clearOauthHandoffCookie(store: CookieStore): void {
  store.set(OAUTH_HANDOFF_COOKIE, "", { ...baseOptions(AUTH_COOKIE_PATH), maxAge: 0 });
}

/** 현재 요청의 액세스 토큰. 로그인하지 않았으면 null. */
export async function readAccessToken(): Promise<string | null> {
  const store = await cookies();
  return store.get(ACCESS_TOKEN_COOKIE)?.value ?? null;
}

/** 현재 요청의 리프레시 토큰. `/api/auth` 아래 Route Handler 에서만 보입니다. */
export async function readRefreshToken(): Promise<string | null> {
  const store = await cookies();
  return store.get(REFRESH_TOKEN_COOKIE)?.value ?? null;
}

/**
 * 로그인이 필요한 요청에 붙이는 axios 옵션. 토큰이 없으면 서버를 부르지 않고 바로
 * 401 을 던집니다.
 *
 * 브라우저가 부르는 Route Handler(`/api/users/me`, 로그아웃)에서 씁니다. 헤더의
 * 로그인 상태는 이 응답으로 정해지는데, 목 서버가 빈 요청에도 200 을 주면 로그인하지
 * 않은 사람도 로그인한 것처럼 보이게 되어 여기서 먼저 막습니다.
 */
export async function authorized(): Promise<{ headers: { Authorization: string } }> {
  const token = await readAccessToken();
  if (!token) {
    throw new ApiError(401, UNAUTHORIZED_CODE, "로그인이 필요합니다.");
  }
  return { headers: { Authorization: `Bearer ${token}` } };
}
