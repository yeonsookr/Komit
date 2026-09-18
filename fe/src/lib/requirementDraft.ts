/**
 * 요청서 수정 화면이 들고 있는 편집 상태와 그 규칙.
 *
 * 저장은 **버튼 하나가 요청 하나**입니다(목 명세). 줄마다 API 를 부르지 않고
 * 화면이 전체 상태를 들고 있다가 통째로 보냅니다. 그래서 지우고 더하는 규칙을
 * 여기 모아 둡니다.
 */
import type { Issue, IssueSeverity, WriteRequirement } from "@/types/diagnosis";

/** 편집 중인 확인 문장 한 줄. */
export interface DraftCriterion {
  /** 기존 줄이면 그 식별자, 새로 적는 줄이면 null. */
  qaCriterionId: number | null;
  testCase: string;
  /** 화면에서 줄을 구분하는 키. 새 줄은 식별자가 없어 따로 둡니다. */
  key: string;
}

/** 편집 중인 요구사항 한 줄. */
export interface DraftRequirement {
  issueId: number | null;
  title: string;
  detail: string | null;
  /** 읽기 전용으로 보여 주는 위치. 새로 적는 줄에는 없습니다. */
  location: string | null;
  categoryCodeId: number;
  severity: IssueSeverity;
  criteria: DraftCriterion[];
  key: string;
}

let keySeed = 0;

/** 화면에서만 쓰는 줄 키. 서버로 보내지 않습니다. */
function nextKey(prefix: string): string {
  keySeed += 1;
  return `${prefix}-new-${keySeed}`;
}

/** 서버가 준 요구사항을 편집 상태로 옮깁니다. */
export function toDraft(issues: Issue[]): DraftRequirement[] {
  return issues.map((issue) => ({
    issueId: issue.issueId,
    title: issue.title,
    detail: issue.detail,
    location: issue.location,
    categoryCodeId: issue.category.codeId,
    severity: issue.severity,
    criteria: issue.qaCriteria.map((qa) => ({
      qaCriterionId: qa.qaCriterionId,
      testCase: qa.testCase ?? "",
      key: `qa-${qa.qaCriterionId}`,
    })),
    key: `issue-${issue.issueId}`,
  }));
}

/** 빈 요구사항 한 줄. 「+ 요구사항 추가」가 만듭니다. */
export function newRequirement(
  title: string,
  categoryCodeId: number,
  severity: IssueSeverity,
): DraftRequirement {
  return {
    issueId: null,
    title,
    detail: null,
    location: null,
    categoryCodeId,
    severity,
    criteria: [],
    key: nextKey("issue"),
  };
}

/** 빈 확인 문장 한 줄. 「+ 확인 문장 추가」가 만듭니다. */
export function newCriterion(): DraftCriterion {
  return { qaCriterionId: null, testCase: "", key: nextKey("qa") };
}

/**
 * 저장할 모양으로 옮깁니다.
 *
 * 비어 있는 확인 문장은 보내지 않습니다 — 적다 만 줄이 그대로 저장되면
 * 통과 기준이 빈 문장이 되어 버립니다.
 */
export function toRequest(draft: DraftRequirement[]): WriteRequirement[] {
  return draft.map((requirement) => ({
    issueId: requirement.issueId,
    title: requirement.title,
    detail: requirement.detail,
    categoryCodeId: requirement.categoryCodeId,
    severity: requirement.severity,
    qaCriteria: requirement.criteria
      .filter((criterion) => criterion.testCase.trim().length > 0)
      .map((criterion) => ({
        qaCriterionId: criterion.qaCriterionId,
        testCase: criterion.testCase.trim(),
      })),
  }));
}

/** 저장할 수 있는 상태인지. 제목이 빈 요구사항은 서버가 422 로 막습니다. */
export function canSave(draft: DraftRequirement[]): boolean {
  return draft.length > 0 && draft.every((row) => row.title.trim().length > 0);
}
