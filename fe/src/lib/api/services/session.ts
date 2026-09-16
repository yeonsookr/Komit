import { clientApi } from "../client";
import { INTERNAL } from "../endpoints";
import type { Me } from "@/types/user";

/**
 * 브라우저가 부르는 세션 요청. Next 서버(`/api`)만 상대하고 토큰은 만지지 않습니다.
 *
 * `services/index.ts` 에 넣지 않습니다. 그 묶음은 server-only 모듈이라 클라이언트
 * 컴포넌트가 import 하면 빌드가 실패합니다. 이 파일은 경로로 직접 import 합니다.
 */

/** 내 정보. 로그인하지 않았으면 401 ApiError. */
export async function fetchMe(): Promise<Me> {
  const { data } = await clientApi.get<Me>(INTERNAL.ME);
  return data;
}

/** 토큰 재발급. 새 쿠키는 응답이 갈아 끼우므로 돌려줄 값이 없습니다. */
export async function requestTokenRefresh(): Promise<void> {
  await clientApi.post(INTERNAL.AUTH_REFRESH);
}

/** 로그아웃. 쿠키가 지워집니다. */
export async function requestLogout(): Promise<void> {
  await clientApi.post(INTERNAL.AUTH_LOGOUT);
}
