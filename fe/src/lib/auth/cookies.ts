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

/** 액세스 토큰. 모든 경로에 붙습니다. */
export const ACCESS_TOKEN_COOKIE = "komit_access_token";

/** 리프레시 토큰. `/api/auth` 아래로만 보냅니다. */
export const REFRESH_TOKEN_COOKIE = "komit_refresh_token";

/** 카카오 인가 시작 → 콜백 사이에만 사는 진행값. */
export const OAUTH_HANDOFF_COOKIE = "komit_oauth";
