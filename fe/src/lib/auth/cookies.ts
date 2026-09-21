/**
 * 세션 쿠키 이름.
 *
 * `session.ts` 와 따로 두는 이유는 **proxy 가 읽어야 하기 때문**입니다.
 * `session.ts` 는 `server-only` 이고 `next/headers` 를 쓰는데, proxy 에서는
 * 둘 다 쓸 수 없습니다. 이름만 여기 두고 양쪽이 같은 값을 봅니다 — 두 곳에 따로
 * 적으면 한쪽만 바뀌어도 타입 검사와 빌드가 통과하고 로그인만 조용히 깨집니다.
 *
 * 쿠키를 굽고 지우는 일은 여전히 `session.ts` 가 합니다. 여기에는 이름만 둡니다.
 */

export interface AuthCookieScope {
  accessToken: string;
  refreshToken: string;
  oauthHandoff: string;
  sessionPath: string;
  authPath: string;
}

/**
 * 같은 호스트의 루트 FE와 `/test` FE가 세션을 공유하지 않도록 이름과 Path를 나눕니다.
 *
 * `BASE_PATH`는 Next.js 설정과 함께 이미지 빌드 시 확정되고 Docker runner에도 그대로
 * 남습니다. 루트 이미지는 기존 쿠키 이름을 유지하고, 하위 경로 이미지만 접두사를 붙입니다.
 */
export function authCookieScope(basePath = process.env.BASE_PATH?.trim() ?? ""): AuthCookieScope {
  if (basePath && (!basePath.startsWith("/") || basePath.endsWith("/"))) {
    throw new Error(`BASE_PATH 는 "/"로 시작하고 "/"로 끝나지 않아야 합니다: ${basePath}`);
  }

  const namespace = basePath
    ? `komit_${basePath.slice(1).replace(/[^a-zA-Z0-9]+/g, "_")}`
    : "komit";

  return {
    accessToken: `${namespace}_access_token`,
    refreshToken: `${namespace}_refresh_token`,
    oauthHandoff: `${namespace}_oauth`,
    sessionPath: basePath || "/",
    authPath: `${basePath}/api/auth`,
  };
}

const COOKIE_SCOPE = authCookieScope();

/** 액세스 토큰. 해당 FE의 경로에만 붙습니다. */
export const ACCESS_TOKEN_COOKIE = COOKIE_SCOPE.accessToken;

/** 리프레시 토큰. 해당 FE의 `/api/auth` 아래로만 보냅니다. */
export const REFRESH_TOKEN_COOKIE = COOKIE_SCOPE.refreshToken;

/** 카카오 인가 시작 → 콜백 사이에만 사는 진행값. */
export const OAUTH_HANDOFF_COOKIE = COOKIE_SCOPE.oauthHandoff;

export const SESSION_COOKIE_PATH = COOKIE_SCOPE.sessionPath;
export const AUTH_COOKIE_PATH = COOKIE_SCOPE.authPath;
