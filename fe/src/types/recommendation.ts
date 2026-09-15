/**
 * 개발자 추천 타입. 목 Swagger `/service/docs` 영역 3 의
 * RecommendationBatchResponse 와 필드가 같습니다.
 */
import type { DeveloperSummary } from "./developer";

/** 추천된 개발자 한 명. 순위는 서버가 정합니다. */
export interface RecommendationItem {
  recommendationItemId: number;
  rank: number;
  developer: DeveloperSummary;
}

/**
 * 추천 회차. 같은 프로젝트에서 다시 찾으면 `sequenceNo` 가 1 오릅니다.
 * 지난 회차를 늘어놓는 화면도 API 도 없습니다.
 */
export interface RecommendationBatch {
  batchId: number;
  sequenceNo: number;
  projectId: number;
  projectName: string;
  /** 이 추천의 근거가 된 진단 판. */
  diagnosisVersion: number;
  /** 추천을 고르기 전 후보 수. 화면의 "128명 중"에 해당합니다. */
  candidateCount: number;
  /** 맞는 개발자가 없던 회차면 빈 배열입니다. */
  items: RecommendationItem[];
}

/** 상담 요청. 고른 추천 항목을 보냅니다. */
export interface CreateConsultationRequestBody {
  /** 이 회차의 추천 항목만 보낼 수 있습니다. 비우면 422. */
  recommendationItemIds: number[];
}

/** 고른 요구사항으로 추천 회차를 만듭니다. 진단 없이 부를 수 없습니다. */
export interface CreateRecommendationRequest {
  diagnosisId: number;
  /** 요청서에 담을 요구사항. 한 건 이상 골라야 합니다. */
  issueIds: number[];
}
