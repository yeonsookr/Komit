/**
 * 진단서의 표시 규칙. 화면에서 분기하지 않도록 순수 함수로 빼 둡니다.
 */
import type { Issue } from "@/types/diagnosis";

/**
 * 의뢰 내용에서 나온 결함과 그 밖의 결함을 가릅니다.
 *
 * `isInput` 이 참인 것을 위에 따로 보여줍니다(목 명세). null 은 관련 여부를
 * 모르는 것이므로 '그 밖'으로 둡니다.
 */
export function splitByInput(issues: Issue[]): { input: Issue[]; others: Issue[] } {
  return {
    input: issues.filter((issue) => issue.isInput === true),
    others: issues.filter((issue) => issue.isInput !== true),
  };
}

/**
 * 분류별 결함 수. 배지의 `보안 4건` 에서 뒤 숫자입니다.
 * 목록 전체를 기준으로 세므로 어느 줄에서 보든 같은 값이 나옵니다.
 */
export function countByCategory(issues: Issue[]): Map<number, number> {
  const counts = new Map<number, number>();
  for (const issue of issues) {
    counts.set(issue.category.codeId, (counts.get(issue.category.codeId) ?? 0) + 1);
  }
  return counts;
}

/** 준비도 점수를 어떻게 보여줄지. 검사 전에는 점수가 없습니다. */
export function hasScore(readinessScore: number | null): readinessScore is number {
  return typeof readinessScore === "number";
}

/** "2차 진단" */
export function versionLabel(version: number): string {
  return `${version}차 진단`;
}
