/**
 * 사전점검(진단) 타입. 목 Swagger `/service/docs` 영역 2 와 필드가 같습니다.
 */

import type { CodeItem } from "./developer";

/** 답 형식. SINGLE 단수 객관식 · MULTI 복수 객관식 · TEXT 주관식. */
export type AnswerType = "SINGLE" | "MULTI" | "TEXT";

export interface SurveyOption {
  optionId: number;
  content: string;
  /** 이미 고른 보기. 뒤로 갔다 와도 상태가 그대로 보입니다. */
  isSelected: boolean;
}

export interface SurveyQuestion {
  questionId: number;
  content: string;
  answerType: AnswerType;
  /**
   * 제시된 보기. **주관식이면 답이 들어 있는 행 하나**이고, 답 전이면 빈 배열입니다.
   * 주관식 답을 담는 별도 필드는 없습니다.
   */
  options: SurveyOption[];
}

export interface Survey {
  surveyId: number;
  diagnosisVersion: number;
  /** 문항은 순서대로 옵니다. */
  questions: SurveyQuestion[];
}

/** 한 문항에 대한 답. */
export interface SurveyAnswer {
  questionId: number;
  /** 단수는 하나, 복수는 여럿, 주관식은 빈 배열. */
  selectedOptionIds: number[];
  /** 주관식 답. 객관식이면 null. */
  text?: string | null;
}

export interface SaveSurveyAnswersRequest {
  answers: SurveyAnswer[];
}

/** 진단을 시작합니다. 새 프로젝트는 이름이 있어야 합니다. */
export interface StartDiagnosisRequest {
  /** 주면 그 프로젝트를 다시 진단하고 회차가 1 오릅니다. */
  projectId?: number;
  name?: string;
  description?: string;
}

export interface DiagnosisProject {
  projectId: number;
  name: string;
  description: string | null;
  domain: string | null;
  techCategories: { techCodeId: number; codeName: string }[];
}

export interface Diagnosis {
  diagnosisId: number;
  version: number;
  project: DiagnosisProject;
  userInput: string | null;
  /** 검사 전에는 null 입니다. */
  readinessScore: number | null;
  scannedAt: string | null;
  unresolvedIssueCount: number;
  totalIssueCount: number;
  resolvedSinceLastVersionCount: number;
}

/** 검사 진행. DONE 이면 진단서를 볼 수 있습니다. */
export type InspectionStatus = "PENDING" | "IN_PROGRESS" | "DONE";

export interface Inspection {
  inspectionId: number;
  diagnosisVersion: number;
  status: InspectionStatus;
}

/** 결함 처리 상태. */
export type IssueStatus =
  | "UNRESOLVED"
  | "RESOLVED"
  | "SUGGESTED"
  | "CONTRACTED"
  | "DISABLED";

/** 결함 위험도. */
export type IssueSeverity = "LOW" | "MEDIUM" | "HIGH";

/** 확인 문장. 계약에서 통과 판정의 기준이 됩니다. */
export interface QaCriterion {
  qaCriterionId: number;
  testCase: string | null;
  condition: string | null;
  status: "UNRESOLVED" | "RESOLVED";
  issueId: number;
}

/** 검사에서 나온 결함 하나. 진단서와 요구사항 선택에서 함께 씁니다. */
export interface Issue {
  issueId: number;
  title: string;
  detail: string | null;
  /** 문제가 있는 위치. 코드에서 찾지 못했으면 null. */
  location: string | null;
  /** 사용자가 적은 증상과 관련된 결함인지. 진단서는 참인 것을 위에 따로 보여줍니다. */
  isInput: boolean | null;
  category: CodeItem;
  status: IssueStatus;
  severity: IssueSeverity;
  /** 문제가 사라진 것을 확인한 진단 회차. 아직 남아 있으면 null. */
  fixedVersion: number | null;
  originIssueId: number | null;
  qaCriteria: QaCriterion[];
}

export interface IssueListResponse {
  content: Issue[];
}

/** 요구사항 직접 추가. */
export interface CreateIssueRequest {
  title: string;
  detail?: string | null;
  categoryCodeId: number;
  severity: IssueSeverity;
}

/** 해결 처리 · 되돌리기. */
export interface UpdateIssueStatusRequest {
  status: "UNRESOLVED" | "RESOLVED";
}
