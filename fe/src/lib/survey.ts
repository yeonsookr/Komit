/**
 * 설문 답을 다루는 규칙. 화면에서 분기하지 않도록 순수 함수로 빼 둡니다.
 */
import type { SurveyQuestion } from "@/types/diagnosis";

/** 한 문항의 답. 객관식은 보기 식별자, 주관식은 글입니다. */
export interface QuestionValue {
  selectedOptionIds: number[];
  text: string;
}

/** 사전점검 설문은 네 단계입니다. */
export const TOTAL_STEPS = 4;

/**
 * 단계별로 답할 문항의 자리(0부터).
 *
 * 설문1은 진단을 열기 전이라 첫 문항 하나, 설문2는 두 문항을 함께 받습니다.
 * **설문3은 검사 파일 단계라 문항이 없습니다** — 목 명세의 답 저장 화면도
 * 설문1·2·4뿐입니다. 설문4는 검사 결과에서 나온 마지막 문항입니다.
 */
export const STEP_QUESTION_INDEXES: Record<number, number[]> = {
  1: [0],
  2: [1, 2],
  3: [],
  4: [3],
};

/** 그 단계에서 답할 문항들. 아직 없는 문항은 빠집니다. */
export function questionsForStep(questions: SurveyQuestion[], step: number): SurveyQuestion[] {
  return (STEP_QUESTION_INDEXES[step] ?? [])
    .map((index) => questions[index])
    .filter((question): question is SurveyQuestion => question !== undefined);
}

/**
 * 서버가 준 문항에서 지금 답을 읽어냅니다. 뒤로 갔다 와도 고른 상태가 살아납니다.
 *
 * **주관식 답은 고른 보기의 `content` 로 옵니다.** 목 명세대로 주관식이면
 * `options` 가 답이 들어 있는 행 하나이고, 답 전이면 빈 배열입니다.
 */
export function toQuestionValue(question: SurveyQuestion): QuestionValue {
  if (question.answerType === "TEXT") {
    return {
      selectedOptionIds: [],
      text: question.options.find((option) => option.isSelected)?.content ?? "",
    };
  }

  return {
    selectedOptionIds: question.options
      .filter((option) => option.isSelected)
      .map((option) => option.optionId),
    text: "",
  };
}

/** 답을 했는지. 「다음」을 열지 말지 정합니다. */
export function isAnswered(question: SurveyQuestion, value: QuestionValue): boolean {
  return question.answerType === "TEXT"
    ? value.text.trim().length > 0
    : value.selectedOptionIds.length > 0;
}

/**
 * 보기를 고릅니다.
 * 단수 문항은 늘 하나만 남깁니다 — 둘 이상 보내면 서버가 422 를 줍니다.
 */
export function toggleOption(
  question: SurveyQuestion,
  value: QuestionValue,
  optionId: number,
): QuestionValue {
  if (question.answerType !== "MULTI") {
    return { selectedOptionIds: [optionId], text: "" };
  }

  const selected = value.selectedOptionIds.includes(optionId)
    ? value.selectedOptionIds.filter((id) => id !== optionId)
    : [...value.selectedOptionIds, optionId];

  return { selectedOptionIds: selected, text: "" };
}

/** 주소의 단계. 2~4만 허용하고 벗어나면 2로 되돌립니다. */
export function parseStep(value: string | string[] | undefined): number {
  const raw = Array.isArray(value) ? value[0] : value;
  const step = Number(raw);
  return Number.isInteger(step) && step >= 2 && step <= TOTAL_STEPS ? step : 2;
}
