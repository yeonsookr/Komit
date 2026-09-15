/**
 * 개발자 프로필의 표시 규칙.
 * 화면에서 분기하지 않도록 순수 함수로 빼 둡니다.
 */
import type { PriceCatalogItem, TaskType } from "@/types/developer";

/** "2021.03 – 2024.08" · 지금도 이어지면 "2024.09 – 현재". */
export function formatCareerPeriod(startDate: string, endDate: string | null): string {
  return `${toYearMonth(startDate)} – ${endDate ? toYearMonth(endDate) : "현재"}`;
}

/** "2021-03-01" → "2021.03". 날짜 문자열을 그대로 자르므로 타임존에 흔들리지 않습니다. */
function toYearMonth(date: string): string {
  const [year, month] = date.split("-");
  return `${year}.${month}`;
}

/** "10:00 – 19:00". */
export function formatActiveHours(from: string, to: string): string {
  return `${from} – ${to}`;
}

/** 경력 확인 상태. 목 명세가 정한 두 가지뿐입니다. */
export function careerStatusLabel(isVerified: boolean): string {
  return isVerified ? "확인됨" : "확인 중";
}

/** 가격표를 작업 종류별로 나눕니다. 없는 종류는 빈 배열입니다. */
export function splitPriceCatalog(
  catalog: PriceCatalogItem[],
): Record<TaskType, PriceCatalogItem[]> {
  return {
    CONSULT: catalog.filter((item) => item.taskType === "CONSULT"),
    DEVELOP: catalog.filter((item) => item.taskType === "DEVELOP"),
  };
}

/** "3일". 걸리는 날수가 없으면(컨설팅) 비웁니다. */
export function formatPeriodDays(periodDays: number | null): string | undefined {
  return periodDays === null ? undefined : `${periodDays}일`;
}
