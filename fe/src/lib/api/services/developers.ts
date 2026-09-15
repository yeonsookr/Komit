import "server-only";

import { backendApi } from "../server";
import { BACKEND } from "../endpoints";
import type {
  CodeItem,
  CodeListResponse,
  DeveloperListResponse,
  DeveloperSearchParams,
} from "@/types/developer";

/**
 * 개발자 찾기.
 *
 * `fieldCodeIds` 는 같은 이름을 거듭 붙여 보냅니다(`?fieldCodeIds=41&fieldCodeIds=42`).
 * axios 기본 직렬화는 `fieldCodeIds[]` 로 대괄호를 붙이므로 따로 지정합니다.
 *
 * 로그인하지 않아도 부를 수 있는 API 입니다 — 랜딩에서 바로 들어오는 경로가 있습니다.
 */
export async function findDevelopers(
  params: DeveloperSearchParams = {},
): Promise<DeveloperListResponse> {
  const { data } = await backendApi.get<DeveloperListResponse>(BACKEND.DEVELOPERS, {
    params,
    paramsSerializer: { indexes: null },
  });
  return data;
}

/** 공통 코드 목록. 분야 선택은 `DEV_FIELD` 를 씁니다. */
export async function getCodes(groupKey: string): Promise<CodeItem[]> {
  const { data } = await backendApi.get<CodeListResponse>(BACKEND.CODES(groupKey));
  return data.content;
}
