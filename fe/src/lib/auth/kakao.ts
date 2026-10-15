/**
 * 카카오 인가 요청 주소.
 *
 * 인가 코드를 받아 오는 앞단(front channel)은 프론트가 맡고, 코드로 토큰을 받는
 * 뒷단은 코밋 서버가 맡습니다(목 명세 POST /api/auth/kakao 의 `code`·`redirectUri`).
 * 이 파일은 순수 함수만 두어 서버·테스트 어디서나 부를 수 있게 합니다.
 */

export const KAKAO_AUTHORIZE_URL = "https://kauth.kakao.com/oauth/authorize";

export interface KakaoAuthorizeParams {
  /** 카카오 개발자 콘솔의 REST API 키. 서버 전용 환경 변수에서 읽습니다. */
  clientId: string;
  /** 카카오 콘솔에 등록한 값과 글자까지 같아야 합니다. */
  redirectUri: string;
  /** CSRF 방지용 임의 문자열. 콜백에서 쿠키의 값과 맞춰 봅니다. */
  state: string;
}

export function buildKakaoAuthorizeUrl({ clientId, redirectUri, state }: KakaoAuthorizeParams): string {
  const url = new URL(KAKAO_AUTHORIZE_URL);
  url.searchParams.set("client_id", clientId);
  url.searchParams.set("redirect_uri", redirectUri);
  url.searchParams.set("response_type", "code");
  url.searchParams.set("state", state);
  return url.toString();
}

/** 콜백 Route Handler 의 경로. 공개 기준 주소를 되짚는 데 씁니다. */
export const KAKAO_CALLBACK_PATH = "/api/auth/kakao/callback";

/**
 * 콜백 주소에서 이 배포의 공개 기준 주소를 되짚습니다.
 *
 * 하위 경로로 서비스하는 배포(`basePath`)에서는 origin 만 쓰면 접두사가 날아가
 * 로그인 뒤 다른 배포로 떨어집니다. 예를 들어 콜백이
 * `https://komit.site/test/api/auth/kakao/callback` 이면 기준 주소는
 * `https://komit.site/test/` 여야 합니다.
 */
export function siteBaseUrl(redirectUri: string): URL {
  const callback = new URL(redirectUri);
  const prefix = callback.pathname.endsWith(KAKAO_CALLBACK_PATH)
    ? callback.pathname.slice(0, -KAKAO_CALLBACK_PATH.length)
    : "";
  return new URL(`${prefix}/`, callback.origin);
}

/** 기준 주소 아래의 절대 주소. `/login` 처럼 앞에 `/` 가 붙어 있어도 접두사를 지킵니다. */
export function siteUrl(path: string, base: URL): URL {
  return new URL(path.replace(/^\/+/, ""), base);
}

/** 인가 요청마다 새로 만드는 state. 예측할 수 없어야 하므로 crypto 를 씁니다. */
export function createOauthState(): string {
  return crypto.randomUUID();
}

/**
 * 인가 시작 → 콜백 사이에 쿠키로 들고 다니는 값.
 * state 는 CSRF 검사에, next 는 로그인 뒤 돌아갈 곳에 씁니다.
 */
export interface OauthHandoff {
  state: string;
  next?: string;
}

export function serializeOauthHandoff(handoff: OauthHandoff): string {
  return JSON.stringify(handoff);
}

/** 쿠키 값이 손상되었거나 모양이 다르면 null. 그때는 로그인 실패로 다룹니다. */
export function parseOauthHandoff(raw: string | undefined): OauthHandoff | null {
  if (!raw) return null;
  try {
    const parsed: unknown = JSON.parse(raw);
    if (typeof parsed !== "object" || parsed === null) return null;
    const candidate = parsed as Record<string, unknown>;
    if (typeof candidate.state !== "string" || candidate.state.length === 0) return null;
    const next = typeof candidate.next === "string" ? candidate.next : undefined;
    return { state: candidate.state, next };
  } catch {
    return null;
  }
}
