/**
 * 진단서의 표시 규칙. 화면에서 분기하지 않도록 순수 함수로 빼 둡니다.
 */
import type { Issue, IssueSeverity } from "@/types/diagnosis";

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

/** 위험도 표기. 의뢰인이 읽는 말로 적습니다. */
export const SEVERITY_LABEL: Record<IssueSeverity, string> = {
  HIGH: "위험",
  MEDIUM: "주의",
  LOW: "참고",
};

/** 해결된 요구사항인지. 해결된 것은 고를 수 없고 취소선으로 남깁니다. */
export function isResolved(issue: Issue): boolean {
  return issue.status === "RESOLVED";
}

/** 요청서에 실제로 담긴 요구사항인지. 고르기 전 미해결 이슈는 포함하지 않습니다. */
export function isInRequest(issue: Issue): boolean {
  return issue.status === "SUGGESTED" || issue.status === "CONTRACTED";
}

/**
 * 고를 수 있는 요구사항만 남깁니다.
 * 이미 해결한 것을 요청서에 담으면 안 되기 때문입니다.
 */
export function selectableIssues(issues: Issue[]): Issue[] {
  return issues.filter((issue) => !isResolved(issue));
}

/** 해결 처리를 하면 고른 목록에서도 빼야 합니다. */
export function dropSelection(selected: number[], issueId: number): number[] {
  return selected.filter((id) => id !== issueId);
}

/**
 * 갈래를 보여주는 순서. 급한 것부터 둡니다.
 * 「기타」는 나머지를 받는 자리라 언제나 맨 끝입니다.
 */
export const CATEGORY_ORDER = [71, 73, 75, 72, 76, 77, 78, 74];

/** 갈래가 무슨 뜻인지. 의뢰인이 읽는 말로 한 줄씩 적습니다. */
export const CATEGORY_DESCRIPTION: Record<number, string> = {
  71: "정보가 새거나 아무나 들어올 수 있습니다",
  72: "법에서 갖추라고 한 것이 빠졌습니다",
  73: "돈이 잘못 빠져나갈 수 있습니다",
  74: "앞의 갈래에 들지 않습니다",
  75: "정보가 사라지거나 어긋날 수 있습니다",
  76: "생각보다 요금이 많이 나올 수 있습니다",
  77: "실행 방법·설정이 정리돼 있지 않습니다",
  78: "AI가 만든 예시·임시 코드가 남아 있습니다",
};

/** 갈래 하나와 거기 묶인 결함. */
export interface IssueGroup {
  codeId: number;
  codeName: string;
  issues: Issue[];
}

/**
 * 결함을 갈래로 묶습니다.
 *
 * `CATEGORY_ORDER` 순서로 내놓고, 목록에 없는 번호는 그 뒤에 나온 순서대로
 * 붙입니다. 비어 있는 갈래는 내놓지 않습니다.
 */
export function groupByCategory(issues: Issue[]): IssueGroup[] {
  const groups = new Map<number, IssueGroup>();

  for (const issue of issues) {
    const { codeId, codeName } = issue.category;
    const group = groups.get(codeId) ?? { codeId, codeName, issues: [] };
    group.issues.push(issue);
    groups.set(codeId, group);
  }

  const rank = (codeId: number) => {
    const at = CATEGORY_ORDER.indexOf(codeId);
    return at === -1 ? CATEGORY_ORDER.length : at;
  };
  return [...groups.values()].sort((a, b) => rank(a.codeId) - rank(b.codeId));
}
