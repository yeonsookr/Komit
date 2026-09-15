import "server-only";

import { backendApi } from "../server";
import { BACKEND } from "../endpoints";
import type {
  CreateConsultationRequestBody,
  RecommendationBatch,
} from "@/types/recommendation";
import type { RoomSummary } from "@/types/room";

/** 추천 회차 결과. 없거나 남의 추천이면 404(RECOMMENDATION_NOT_FOUND). */
export async function getRecommendationBatch(batchId: number): Promise<RecommendationBatch> {
  const { data } = await backendApi().get<RecommendationBatch>(BACKEND.RECOMMENDATION(batchId));
  return data;
}

/**
 * 상담 요청 보내기.
 *
 * 고른 개발자마다 무료 상담방이 하나씩 열리고, 개발자의 수락을 기다리지 않습니다.
 * **멱등합니다** — 이미 방이 있는 개발자는 그 방을 그대로 돌려주고 새로 만들지 않아,
 * 같은 사람을 두 번 담아 보내거나 두 화면에서 동시에 눌러도 결과가 같습니다.
 *
 * 응답의 방은 보낸 순서가 아니라 `recommendationItemId` 로 맞춥니다.
 */
export async function createConsultationRequests(
  batchId: number,
  body: CreateConsultationRequestBody,
): Promise<RoomSummary[]> {
  const { data } = await backendApi().post<{ content: RoomSummary[] }>(
    BACKEND.CONSULTATION_REQUESTS(batchId),
    body,
  );
  return data.content;
}
