/**
 * 개발자 찾기의 검색 조건을 URL query 와 주고받습니다.
 *
 * 조건을 URL 에 두는 이유는 새로고침·뒤로가기·링크 공유에서 같은 결과가 나와야
 * 하기 때문입니다. 화면 상태로만 들고 있으면 그게 깨집니다.
 *
 * 허용된 값만 통과시킵니다. 목 명세에 없는 값이 그대로 서버로 가면 422 가 됩니다.
 */
import type { DeveloperSearchParams, DeveloperSort, SearchMode } from "@/types/developer";

/**
 * 검색어를 주소에 싣는 query 이름.
 *
 * 개발자 찾기로 보내는 쪽(랜딩 검색바 등)이 이 상수를 가져다 씁니다. 양쪽이 글자를
 * 따로 적으면 한쪽만 바뀌어도 타입 검사가 잡지 못하고 검색어가 조용히 버려집니다.
 * 값은 목 명세의 `GET /api/developers` 파라미터 이름과 같습니다.
 */
export const KEYWORD_PARAM = "keyword";

/**
 * 분야 코드를 주소에 싣는 query 이름. 여러 개면 같은 이름을 거듭 붙입니다.
 *
 * 랜딩의 증상 카드처럼 분야를 정해 개발자 찾기를 여는 쪽이 가져다 씁니다.
 * 값은 목 명세의 `GET /api/developers` 파라미터 이름과 같습니다.
 */
export const FIELD_CODE_IDS_PARAM = "fieldCodeIds";

export const SEARCH_MODES: SearchMode[] = ["problem", "name"];

export const SEARCH_MODE_LABEL: Record<SearchMode, string> = {
  problem: "문제",
  name: "개발자명",
};

/** 정렬 선택지. 순서는 화면에 보이는 순서입니다. */
export const SORT_OPTIONS: { value: DeveloperSort; label: string }[] = [
  { value: "devRating,desc", label: "실력 높은 순" },
  { value: "explanatoryRating,desc", label: "설명력 높은 순" },
  { value: "price,asc", label: "비용 낮은 순" },
  { value: "contract,desc", label: "계약 많은 순" },
];

const SORT_VALUES = SORT_OPTIONS.map((option) => option.value);

export const DEFAULT_SEARCH_MODE: SearchMode = "problem";
export const DEFAULT_SORT: DeveloperSort = "devRating,desc";
export const DEFAULT_SIZE = 5;

/** URL query 를 검색 조건으로 읽습니다. 알 수 없는 값은 기본값으로 되돌립니다. */
export function parseSearchParams(
  input: Record<string, string | string[] | undefined>,
): Required<Pick<DeveloperSearchParams, "searchMode" | "sort" | "page" | "size">> &
  Pick<DeveloperSearchParams, "keyword" | "fieldCodeIds"> {
  const searchMode = first(input.searchMode);
  const sort = first(input.sort);
  const keyword = first(input[KEYWORD_PARAM])?.trim();

  return {
    searchMode: isSearchMode(searchMode) ? searchMode : DEFAULT_SEARCH_MODE,
    sort: isSort(sort) ? sort : DEFAULT_SORT,
    ...(keyword ? { keyword } : {}),
    ...(parseFieldCodeIds(input[FIELD_CODE_IDS_PARAM]).length > 0
      ? { fieldCodeIds: parseFieldCodeIds(input[FIELD_CODE_IDS_PARAM]) }
      : {}),
    page: parsePage(first(input.page)),
    size: DEFAULT_SIZE,
  };
}

/**
 * 검색 조건을 URL query 문자열로 만듭니다.
 * 기본값은 적지 않아 주소가 짧게 유지됩니다.
 */
export function toQueryString(params: DeveloperSearchParams): string {
  const query = new URLSearchParams();

  if (params.searchMode && params.searchMode !== DEFAULT_SEARCH_MODE) {
    query.set("searchMode", params.searchMode);
  }
  if (params.keyword?.trim()) query.set(KEYWORD_PARAM, params.keyword.trim());
  for (const codeId of params.fieldCodeIds ?? []) {
    query.append(FIELD_CODE_IDS_PARAM, String(codeId));
  }
  if (params.sort && params.sort !== DEFAULT_SORT) query.set("sort", params.sort);
  if (params.page && params.page > 1) query.set("page", String(params.page));

  return query.toString();
}

function first(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}

function isSearchMode(value: string | undefined): value is SearchMode {
  return value !== undefined && SEARCH_MODES.includes(value as SearchMode);
}

function isSort(value: string | undefined): value is DeveloperSort {
  return value !== undefined && SORT_VALUES.includes(value as DeveloperSort);
}

/** 숫자가 아닌 값은 버립니다. 목은 DEV_FIELD 에 없는 코드를 조용히 무시합니다. */
function parseFieldCodeIds(value: string | string[] | undefined): number[] {
  const values = Array.isArray(value) ? value : value === undefined ? [] : [value];
  return values
    .flatMap((item) => item.split(","))
    .map((item) => Number(item.trim()))
    .filter((item) => Number.isInteger(item) && item > 0);
}

function parsePage(value: string | undefined): number {
  const page = Number(value);
  return Number.isInteger(page) && page >= 1 ? page : 1;
}
