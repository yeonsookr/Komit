/**
 * 개발 계약서 편집 화면의 상태 규칙. 화면에서 분기하지 않도록 순수 함수로 빼 둡니다.
 */
import { isDevelopmentDraft, isHandover } from "@/lib/contract";
import type { Contract, Milestone, WriteMilestoneRequest } from "@/types/contract";
import type { Issue, IssueStatus } from "@/types/diagnosis";

/** 편집 중인 작업 한 줄. 화면이 들고 있는 값이라 작업 식별자가 없어도 됩니다. */
export interface EditorMilestone {
  key: string;
  /** 붙은 요구사항. 새로 합의한 작업이면 null 이고, 보내면 서버가 요구사항을 만듭니다. */
  issueId: number | null;
  title: string;
  detail: string;
  price: string;
  workDate: string;
  qaCriteria: string[];
}

/** 인계 작업. 가격도 기간도 없고 넘겨받았는지 확인하는 문장만 있습니다. */
export interface EditorHandover {
  /** 이미 보낸 계약서의 인계 작업이 붙은 요구사항. 다시 보낼 때 그대로 보내 새로 만들지 않게 합니다. */
  issueId: number | null;
  lines: string[];
}

/** 인계 작업의 이름. 가격이 0 원인 작업 하나로 계약서에 들어갑니다 (R-CON-04). */
export const HANDOVER_TITLE = "인계 · 소스 · 계정 · 설명서";

/**
 * 요청서에 담긴 요구사항의 상태. 서버가 AI 초안에 요청서를 넣는 기준과 같습니다.
 * 고르지 않은 것(`UNRESOLVED`)과 직접 해결한 것(`RESOLVED`)은 계약서에 들어가지 않습니다.
 */
const REQUEST_STATUSES: readonly IssueStatus[] = ["SUGGESTED", "CONTRACTED"];

let counter = 0;
const nextKey = () => `draft-${(counter += 1)}`;

export const emptyMilestone = (): EditorMilestone => ({
  key: nextKey(),
  issueId: null,
  title: "",
  detail: "",
  price: "",
  workDate: "",
  qaCriteria: [""],
});

/** 문장이 없으면 입력 칸 하나를 둡니다. 비어 있는 채로는 보낼 수 없습니다. */
const toLines = (sentences: string[]) => (sentences.length === 0 ? [""] : sentences);

/**
 * AI 초안은 금액이 모두 0 이라 금액 칸을 비워 둡니다. 0 을 그대로 보이면 채운 값으로
 * 읽히고, 그대로 보내면 인계 작업으로 읽힙니다.
 */
function fromMilestone(milestone: Milestone, draft: boolean): EditorMilestone {
  return {
    key: nextKey(),
    issueId: milestone.issueId,
    title: milestone.title,
    detail: milestone.detail ?? "",
    price: draft ? "" : String(milestone.price),
    workDate: milestone.workDate === null ? "" : String(milestone.workDate),
    qaCriteria: toLines(milestone.qaCriteria.map((qa) => qa.testCase ?? "")),
  };
}

function fromIssue(issue: Issue): EditorMilestone {
  return {
    key: nextKey(),
    issueId: issue.issueId,
    title: issue.title,
    detail: issue.detail ?? "",
    price: "",
    workDate: "",
    qaCriteria: toLines(issue.qaCriteria.map((qa) => qa.testCase ?? "")),
  };
}

/**
 * 계약서를 편집 상태로 풉니다.
 *
 * - 개발자가 보낸 계약서: 인계 작업(금액 0)을 따로 떼어 마지막 블록으로 둡니다.
 * - AI 초안: 작업이 모두 금액 0 이라 인계 작업이 없습니다. 모두 일반 작업으로 풉니다.
 * - 계약서 없음: AI 초안을 받지 못한 때입니다. 요청서에 담긴 요구사항마다 작업을 하나씩
 *   만들고 요청서의 확인 문장을 넣습니다. 서버가 초안을 만드는 규칙과 같고, 상담에서 더
 *   합의한 것만 빠집니다.
 */
export function toEditorState(
  contract: Contract | null,
  requested: Issue[] = [],
): { milestones: EditorMilestone[]; handover: EditorHandover } {
  const development = contract?.development ?? null;
  let milestones: EditorMilestone[];
  let handover: EditorHandover = { issueId: null, lines: [""] };

  if (development === null) {
    milestones = requested
      .filter((issue) => REQUEST_STATUSES.includes(issue.status))
      .map(fromIssue);
  } else if (isDevelopmentDraft(contract)) {
    milestones = development.milestones.map((milestone) => fromMilestone(milestone, true));
  } else {
    milestones = development.milestones
      .filter((milestone) => !isHandover(milestone))
      .map((milestone) => fromMilestone(milestone, false));
    const handoverMilestone = development.milestones.find(isHandover);
    if (handoverMilestone !== undefined) {
      handover = {
        issueId: handoverMilestone.issueId,
        lines: toLines(handoverMilestone.qaCriteria.map((qa) => qa.testCase ?? "")),
      };
    }
  }

  return {
    milestones: milestones.length === 0 ? [emptyMilestone()] : milestones,
    handover,
  };
}

/** 적은 줄만. 앞뒤 공백을 떼고 빈 줄을 뺍니다. */
export function filledLines(lines: string[]): string[] {
  return lines.map((line) => line.trim()).filter((line) => line !== "");
}

/** 후보 중복 판정용 문장. 앞뒤와 연속 공백 차이는 같은 문장으로 봅니다. */
export function normalizeQaText(line: string): string {
  return line.trim().replaceAll(/\s+/g, " ");
}

/**
 * 추천 후보를 작업의 확인 문장에 더합니다.
 * 비어 있는 입력 칸 하나뿐이면 그 칸을 채우고, 이미 같은 문장이 있으면 다시 넣지 않습니다.
 */
export function applyQaSuggestion(lines: string[], candidate: string): string[] {
  const next = candidate.trim();
  const normalized = normalizeQaText(next);
  if (normalized === "" || lines.some((line) => normalizeQaText(line) === normalized)) {
    return lines;
  }
  return lines.length === 1 && lines[0].trim() === "" ? [next] : [...lines, next];
}

/** 금액이 정해지지 않은 일반 작업인지. 0 원은 인계 작업에만 씁니다. */
export function isUnpriced(milestone: EditorMilestone): boolean {
  return !(Number(milestone.price) >= 1);
}

/**
 * 작업을 보낼 수 있는지. 작업마다 이름과 확인 문장, 1 원 이상의 금액이 있어야 합니다.
 * 금액 0 인 일반 작업을 받으면 계약서가 그 작업을 인계 작업으로 읽습니다.
 */
export function canSendMilestones(
  milestones: EditorMilestone[],
  handover: EditorHandover,
): boolean {
  return (
    milestones.length > 0 &&
    filledLines(handover.lines).length > 0 &&
    milestones.every(
      (milestone) =>
        milestone.title.trim() !== "" &&
        filledLines(milestone.qaCriteria).length > 0 &&
        !isUnpriced(milestone),
    )
  );
}

/**
 * 보낼 작업 목록. 인계는 늘 마지막 작업이고 가격이 0 이라 합계에 들어가지 않습니다.
 *
 * 요구사항에 붙은 작업은 `issueId` 를 그대로 보냅니다. 빼고 보내면 서버가 새 작업으로 읽고
 * 요구사항을 새로 만듭니다.
 */
export function toWriteMilestones(
  milestones: EditorMilestone[],
  handover: EditorHandover,
): WriteMilestoneRequest[] {
  const written: WriteMilestoneRequest[] = milestones.map((milestone) => ({
    ...(milestone.issueId === null ? {} : { issueId: milestone.issueId }),
    title: milestone.title.trim(),
    ...(milestone.detail.trim() === "" ? {} : { detail: milestone.detail.trim() }),
    price: Number(milestone.price),
    ...(milestone.workDate.trim() === "" ? {} : { workDate: Number(milestone.workDate) }),
    qaCriteria: filledLines(milestone.qaCriteria),
  }));

  written.push({
    ...(handover.issueId === null ? {} : { issueId: handover.issueId }),
    title: HANDOVER_TITLE,
    price: 0,
    qaCriteria: filledLines(handover.lines),
  });
  return written;
}
