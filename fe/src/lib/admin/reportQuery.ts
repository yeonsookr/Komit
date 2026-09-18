import { ROUTES } from "@/lib/routes";
import type { AdminReportStatus } from "@/types/admin";
import { REPORT_ID_PARAM, REPORT_PAGE_PARAM, REPORT_STATUS_PARAM } from "./params";

/**
 * 신고 화면이 주소에 담는 것.
 *
 * 필터·페이지·고른 신고를 화면 상태가 아니라 주소에 둡니다. 운영자가 어느 건을 보던
 * 중에 새로고침하거나 그 주소를 동료에게 붙여 넣어도 같은 화면이 열려야 하기 때문입니다.
 *
 * 값을 만드는 곳과 읽는 곳이 하나여야 어긋나지 않으므로 파서와 주소 조립을 같이 둡니다.
 */
export interface ReportQuery {
  /** 비우면 전부. 목 명세의 `status` 와 같은 이름·같은 값입니다. */
  status?: AdminReportStatus;
  /** 1부터 시작합니다. 서버도 1부터 셉니다. */
  page: number;
  /** 상세를 펼쳐 둘 신고. 고르지 않았으면 목록의 첫 줄이 열립니다. */
  reportId?: number;
}

export const FIRST_PAGE = 1;

type RawParams = Record<string, string | string[] | undefined>;

function first(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}

/** 1 이상의 정수만 받습니다. 주소는 누구나 고쳐 넣을 수 있어 모양부터 봅니다. */
function positiveInteger(value: string | undefined): number | undefined {
  if (value === undefined || !/^\d+$/.test(value)) return undefined;
  const parsed = Number(value);
  return parsed > 0 ? parsed : undefined;
}

function reportStatus(value: string | undefined): AdminReportStatus | undefined {
  return value === "OPEN" || value === "RESOLVED" ? value : undefined;
}

/**
 * 주소의 query 를 화면이 쓸 값으로 바꿉니다.
 *
 * **모르는 값은 버리고 기본으로 돌아갑니다.** 손으로 고친 `?status=WHATEVER` 를 그대로
 * 서버에 넘기면 422 가 되어 운영자는 화면이 깨진 것으로 보게 됩니다.
 */
export function parseReportQuery(params: RawParams): ReportQuery {
  return {
    status: reportStatus(first(params[REPORT_STATUS_PARAM])),
    page: positiveInteger(first(params[REPORT_PAGE_PARAM])) ?? FIRST_PAGE,
    reportId: positiveInteger(first(params[REPORT_ID_PARAM])),
  };
}

/**
 * 신고 화면 주소. 기본값은 적지 않습니다 — 「전체 · 1쪽」이 `?page=1` 로만 남아
 * 주소가 짧고, 필터를 바꿨는지 한눈에 보입니다.
 */
export function reportsHref(query: Partial<ReportQuery>): string {
  const search = new URLSearchParams();
  if (query.status) search.set(REPORT_STATUS_PARAM, query.status);
  if (query.page !== undefined && query.page !== FIRST_PAGE) {
    search.set(REPORT_PAGE_PARAM, String(query.page));
  }
  if (query.reportId !== undefined) search.set(REPORT_ID_PARAM, String(query.reportId));

  const rendered = search.toString();
  return rendered === "" ? ROUTES.adminReports : `${ROUTES.adminReports}?${rendered}`;
}
