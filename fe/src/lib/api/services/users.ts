import "server-only";

import { backendApi } from "../server";
import { BACKEND } from "../endpoints";
import { bearerHeaders } from "@/lib/auth/session";
import type { Me } from "@/types/user";

/**
 * 내 정보. 헤더와 '맡긴 의뢰 / 맡은 의뢰' 구분에 씁니다.
 * 현재 요청의 액세스 토큰이 있으면 실어 보냅니다. 로그인하지 않았으면 서버가 401 을 줍니다.
 */
export async function getMe(): Promise<Me> {
  const { data } = await backendApi().get<Me>(BACKEND.ME, await bearerHeaders());
  return data;
}
