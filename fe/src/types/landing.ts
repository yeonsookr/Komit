/**
 * 랜딩 타입. 목 Swagger `/service/docs` 영역 1 의
 * LandingSummaryResponse · FeaturedDeveloperListResponse 와 필드가 같습니다.
 * 명세에 없는 필드를 더하지 않습니다.
 */
import type { CodeItem, DeveloperPrice } from "./developer";

/** 히어로 아래 "이번 주 상담 N건" 한 줄. */
export interface LandingSummary {
  /** 이번 주에 열린 문의 방 수. 0 이어도 오류가 아닙니다. */
  weeklyInquiryRoomCount: number;
  /** 집계 기준이 되는 주의 시작 시각. */
  weekStartAt: string;
}

/**
 * 랜딩에 노출하는 추천 개발자 한 명.
 * 로그인 전에도 보이는 공개 정보라 목록 카드(DeveloperSummary)보다 필드가 적고,
 * 평점은 리뷰 수 없이 점수만 옵니다.
 */
export interface FeaturedDeveloper {
  developerId: number;
  name: string;
  /** 올린 사진이 없으면 null. 그때 화면은 이름 첫 글자를 씁니다. */
  profileImageFileId: number | null;
  intro: string;
  /** 등록하지 않았으면 null. */
  yearsOfExperience: number | null;
  /** 주로 담당하는 분야(DEV_FIELD). 등록하지 않았으면 null. */
  field: CodeItem | null;
  /** 받은 리뷰가 없으면 null. */
  devRating: number | null;
  explanatoryRating: number | null;
  /** 작업 유형별 최저가. 무료 상담은 가격표에 없어 여기 담기지 않습니다. */
  prices: DeveloperPrice[];
}

export interface FeaturedDeveloperListResponse {
  /** 노출할 개발자가 없으면 빈 배열. 페이지 정보는 없습니다. */
  content: FeaturedDeveloper[];
}
