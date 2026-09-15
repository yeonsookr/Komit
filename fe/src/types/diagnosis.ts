/**
 * 사전점검(진단) 타입. 목 Swagger `/service/docs` 영역 2 와 필드가 같습니다.
 */

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
