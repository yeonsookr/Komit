import "server-only";

import { backendApi } from "../server";
import { BACKEND } from "../endpoints";
import { authorized } from "@/lib/auth/session";
import type {
  KakaoLoginRequest,
  KakaoLoginResponse,
  RefreshTokenRequest,
  TokenResponse,
} from "@/types/auth";

/**
 * 카카오 인가 코드로 로그인. 처음 들어온 사용자는 필수 약관 동의 화면으로 이어집니다.
 * 401(KAKAO_AUTH_FAILED)·503(KAKAO_UNAVAILABLE) 은 그대로 ApiError 로 올라옵니다.
 */
export async function loginWithKakao(body: KakaoLoginRequest): Promise<KakaoLoginResponse> {
  const { data } = await backendApi().post<KakaoLoginResponse>(BACKEND.AUTH_KAKAO, body);
  return data;
}

/**
 * 액세스 토큰 재발급. 리프레시 토큰도 함께 새 값으로 바뀝니다.
 * 401(REFRESH_TOKEN_INVALID) 이면 세션이 끝난 것이라 로그인 화면으로 보냅니다.
 */
export async function refreshTokens(body: RefreshTokenRequest): Promise<TokenResponse> {
  const { data } = await backendApi().post<TokenResponse>(BACKEND.AUTH_TOKEN_REFRESH, body);
  return data;
}

/**
 * 로그아웃. 서버가 리프레시 토큰을 지웁니다. 204 이고 본문이 없습니다.
 * 이미 로그아웃된 상태로 다시 불러도 204 라 화면은 성공으로 다루면 됩니다.
 */
export async function logoutOnServer(): Promise<void> {
  await backendApi().post(BACKEND.AUTH_LOGOUT, undefined, await authorized());
}
