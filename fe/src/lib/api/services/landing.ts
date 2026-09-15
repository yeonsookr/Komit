import "server-only";

import { backendApi } from "../server";
import { BACKEND } from "../endpoints";
import type {
  FeaturedDeveloper,
  FeaturedDeveloperListResponse,
  LandingSummary,
} from "@/types/landing";

/** 랜딩이 보여주는 추천 개발자 수. 명세는 1~10 을 허용하고 화면은 3명을 씁니다. */
export const FEATURED_DEVELOPER_COUNT = 3;

/**
 * 히어로 아래 "이번 주 상담 N건".
 *
 * 이 줄은 랜딩의 핵심이 아니라서 실패해도 페이지를 막지 않습니다(목 명세).
 * 읽지 못하면 null 을 돌려주고 화면은 그 줄만 비웁니다. 로그인 없이 부를 수 있습니다.
 */
export async function getLandingSummary(): Promise<LandingSummary | null> {
  try {
    const { data } = await backendApi().get<LandingSummary>(BACKEND.LANDING_SUMMARY);
    return data;
  } catch (error) {
    console.error("[landing] 지표를 읽지 못했습니다", error);
    return null;
  }
}

/** 랜딩의 추천 개발자. 노출할 사람이 없으면 빈 배열입니다. 로그인 없이 부를 수 있습니다. */
export async function getFeaturedDevelopers(
  size: number = FEATURED_DEVELOPER_COUNT,
): Promise<FeaturedDeveloper[]> {
  const { data } = await backendApi().get<FeaturedDeveloperListResponse>(
    BACKEND.LANDING_FEATURED,
    { params: { size } },
  );
  return data.content;
}
