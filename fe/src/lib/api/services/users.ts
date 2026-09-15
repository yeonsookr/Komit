import "server-only";

import { backendApi } from "../server";
import { BACKEND } from "../endpoints";
import type { Me } from "@/types/user";

/** 내 정보. 헤더와 '맡긴 의뢰 / 맡은 의뢰' 구분에 씁니다. */
export async function getMe(): Promise<Me> {
  const { data } = await backendApi().get<Me>(BACKEND.ME);
  return data;
}
