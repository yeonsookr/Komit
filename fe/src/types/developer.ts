/**
 * 개발자 찾기 타입. 목 Swagger `/service/docs` 영역 3 의
 * DeveloperListResponse 와 필드가 같습니다. 명세에 없는 필드를 더하지 않습니다.
 */

/** 공통 코드 항목. 분야(DEV_FIELD)와 기술(TECH_CATEGORY)이 같은 모양입니다. */
export interface CodeItem {
  codeId: number;
  codeName: string;
}

export interface CodeListResponse {
  content: CodeItem[];
}

/** 별점과 그 별점을 만든 리뷰 수. */
export interface Rating {
  score: number | null;
  reviewCount: number;
}

/** 작업 종류별 시작 금액. CONSULT 컨설팅 · DEVELOP 개발. */
export type TaskType = "CONSULT" | "DEVELOP";

export interface DeveloperPrice {
  taskType: TaskType;
  price: number;
}

/** 목록 카드 한 장. */
export interface DeveloperSummary {
  developerId: number;
  name: string;
  profileImageFileId: number | null;
  intro: string;
  /** 잘하는 분야. 한 명에 하나이고 거르는 기준이 됩니다. */
  field: CodeItem | null;
  yearsOfExperience: number;
  isCareerVerified: boolean;
  devRating: Rating;
  explanatoryRating: Rating;
  /** 카드 아래 칩. 분야와 달리 여럿일 수 있습니다. */
  techCategories: CodeItem[];
  prices: DeveloperPrice[];
  contractCount: number;
}

export interface DeveloperListResponse {
  content: DeveloperSummary[];
  /** 1부터 셉니다. 0이 첫 장이 아닙니다. */
  page: number;
  size: number;
  /** 조건에 맞는 사람 수. */
  totalElements: number;
  totalPages: number;
  /** 조건을 걸지 않았을 때의 전체 수. "128명 중 5명"의 앞 숫자입니다. */
  totalDevelopers: number;
}

/** 검색 방식. problem 은 문제 문장으로, name 은 이름으로 찾습니다. */
export type SearchMode = "problem" | "name";

/** 정렬 기준. 값은 목 명세가 정한 문자열 그대로 씁니다. */
export type DeveloperSort =
  | "devRating,desc"
  | "explanatoryRating,desc"
  | "price,asc"
  | "contract,desc";

export interface DeveloperSearchParams {
  searchMode?: SearchMode;
  keyword?: string;
  /** 여러 개면 같은 이름을 거듭 붙여 보냅니다. */
  fieldCodeIds?: number[];
  sort?: DeveloperSort;
  page?: number;
  size?: number;
}

/** 경력 한 줄. `isVerified` 가 참이면 '확인됨', 거짓이면 '확인 중'입니다. */
export interface Career {
  careerId: number;
  company: string;
  role: string;
  startDate: string;
  /** 지금도 이어지는 경력이면 null 입니다. */
  endDate: string | null;
  isVerified: boolean;
  verifiedAt: string | null;
}

/** 포트폴리오 한 칸. 링크가 없으면 비공개 작업물입니다. */
export interface PortfolioItem {
  portfolioItemId: number;
  title: string;
  description: string | null;
  linkUrl: string | null;
  thumbnailFileId: number | null;
}

/** 가격표 한 줄. 컨설팅은 세션, 개발은 작업 단위입니다. */
export interface PriceCatalogItem {
  priceId: number;
  taskType: TaskType;
  taskName: string;
  price: number;
  /** 걸리는 날수. 컨설팅에는 없습니다. */
  periodDays: number | null;
}

/**
 * 개발자 프로필 상세. 목록 카드에 쓰는 값에 소개·경력·작업물·가격표를 더합니다.
 * 로그인하지 않아도 볼 수 있는 공개 프로필입니다.
 */
export interface DeveloperDetail extends DeveloperSummary {
  description: string;
  /** 주요 활동 시간대. "10:00" 처럼 옵니다. */
  activeFrom: string;
  activeTo: string;
  careers: Career[];
  portfolioItems: PortfolioItem[];
  priceCatalog: PriceCatalogItem[];
}

/** 계약을 마친 뒤 받은 리뷰. 의뢰인 이름은 서버가 가려서 내려줍니다. */
export interface Review {
  reviewId: number;
  /** "김O미" 처럼 이미 가려진 값입니다. 화면이 따로 가리지 않습니다. */
  clientName: string;
  contractType: "CONSULTING" | "DEVELOPMENT";
  devScore: number;
  expScore: number;
  /** 점수만 주고 글은 안 남길 수 있습니다. */
  content: string | null;
  /** 개발자가 단 답글. */
  reply: string | null;
  createdAt: string;
}

export interface ReviewListResponse {
  content: Review[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
