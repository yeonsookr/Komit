import "server-only";

import { backendApi } from "../server";
import { BACKEND } from "../endpoints";
import type {
  CodeItem,
  CodeListResponse,
  DeveloperDetail,
  DeveloperListResponse,
  DeveloperSearchParams,
  ReviewListResponse,
  MyDeveloperProfile,
  Review,
  WriteDeveloperProfileRequest,
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
  const { data } = await backendApi().get<DeveloperListResponse>(BACKEND.DEVELOPERS, {
    params,
    paramsSerializer: { indexes: null },
  });
  return data;
}

/** 내 개발자 프로필. 아직 만들지 않았으면 NO_DEVELOPER_PROFILE 오류가 그대로 올라갑니다. */
export async function getMyDeveloperProfile(): Promise<MyDeveloperProfile> {
  const { data } = await backendApi().get<MyDeveloperProfile>(BACKEND.MY_DEVELOPER_PROFILE);
  return data;
}

/** 내 개발자 프로필 전체 저장. 처음 저장하면 201, 이후에는 200입니다. */
export async function writeMyDeveloperProfile(
  body: WriteDeveloperProfileRequest,
): Promise<MyDeveloperProfile> {
  const { data } = await backendApi().put<MyDeveloperProfile>(
    BACKEND.MY_DEVELOPER_PROFILE,
    body,
  );
  return data;
}

/** 내가 받은 리뷰의 답글 쓰기·수정. */
export async function writeReviewReply(reviewId: number, reply: string): Promise<Review> {
  const { data } = await backendApi().put<Review>(BACKEND.REVIEW_REPLY(reviewId), { reply });
  return data;
}

/** 공통 코드 목록. 분야 선택은 `DEV_FIELD` 를 씁니다. */
export async function getCodes(groupKey: string): Promise<CodeItem[]> {
  const { data } = await backendApi().get<CodeListResponse>(BACKEND.CODES(groupKey));
  return data.content;
}

/** 개발자 프로필. 없거나 탈퇴했으면 404(DEVELOPER_NOT_FOUND). */
export async function getDeveloper(developerId: number): Promise<DeveloperDetail> {
  const { data } = await backendApi().get<DeveloperDetail>(BACKEND.DEVELOPER(developerId));
  return data;
}

/** 개발자가 받은 리뷰. 최신순이고 `page` 는 1부터 셉니다. */
export async function getDeveloperReviews(
  developerId: number,
  params: { page?: number; size?: number } = {},
): Promise<ReviewListResponse> {
  const { data } = await backendApi().get<ReviewListResponse>(
    BACKEND.DEVELOPER_REVIEWS(developerId),
    { params },
  );
  return data;
}
