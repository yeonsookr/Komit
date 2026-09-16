/**
 * 인증(영역 1) 계약. 목 Swagger `/service/docs` 의 1 랜딩 · 로그인 태그와 같습니다.
 *
 * 토큰 자체는 브라우저 자바스크립트로 내려오지 않습니다. Route Handler 가 받아
 * httpOnly 쿠키에 넣고, 화면은 `Me`(내 정보)만 받습니다.
 */

/** POST /api/auth/kakao 요청. */
export interface KakaoLoginRequest {
  /** 카카오 로그인 화면에서 받은 인가 코드. 한 번만 쓸 수 있습니다. */
  code: string;
  /** 인가 코드를 받을 때 쓴 주소. 그때와 같은 값이어야 합니다. */
  redirectUri: string;
}

/** 토큰 발급 응답. 재발급도 같은 모양입니다. */
export interface TokenResponse {
  /** `Authorization: Bearer <accessToken>` 으로 실어 보내는 JWT. */
  accessToken: string;
  /** 액세스 토큰을 다시 받을 때 쓰는 UUID. 재발급하면 이전 값은 못 씁니다. */
  refreshToken: string;
  /** 액세스 토큰이 유효한 시간. 초 단위. */
  expiresIn: number;
}

/** 로그인 직후 화면을 그리는 최소 사용자 정보. `Me` 의 부분집합입니다. */
export interface LoginUser {
  userId: number;
  name: string;
  profileImageFileId: number | null;
  hasDevProfile: boolean;
}

/** POST /api/auth/kakao 응답. */
export interface KakaoLoginResponse extends TokenResponse {
  /** 이번 로그인으로 새로 가입했는지. */
  isNewUser: boolean;
  user: LoginUser;
}

/** POST /api/auth/token/refresh 요청. */
export interface RefreshTokenRequest {
  refreshToken: string;
}

/** 액세스 토큰이 없거나 만료되었을 때 서버가 주는 코드. */
export const UNAUTHORIZED_CODE = "UNAUTHORIZED";
/** 리프레시 토큰이 만료되었거나 이미 쓴 값일 때. 이때는 로그인 화면으로 보냅니다. */
export const REFRESH_TOKEN_INVALID_CODE = "REFRESH_TOKEN_INVALID";
