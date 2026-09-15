"use client";

import * as React from "react";
import { ChoiceCard, Textarea } from "@/components/common";
import { cn } from "@/lib/cn";
import { toggleOption, type QuestionValue } from "@/lib/survey";
import type { SurveyQuestion } from "@/types/diagnosis";

export interface QuestionFieldProps {
  question: SurveyQuestion;
  value: QuestionValue;
  onChange: (value: QuestionValue) => void;
  /** 주관식으로 넘어가게 하는 「여기에 없어요」 를 붙일지. */
  withEscapeHatch?: boolean;
  /** 「여기에 없어요」 를 골랐을 때 옮겨 갈 곳. */
  onEscape?: () => void;
  /** 주관식 입력을 밖에서 집을 수 있게 합니다. */
  textRef?: React.Ref<HTMLTextAreaElement>;
  /** 문항 문구가 단계 제목과 같을 때 눈에 보이는 중복을 없앱니다. */
  hideLegend?: boolean;
}

/**
 * QuestionField — 답 형식에 맞는 입력을 그립니다.
 *   SINGLE 하나만 · MULTI 여럿 · TEXT 주관식
 *
 * 무엇을 고를 수 있는지는 `lib/survey` 가 정하고, 여기서는 그리기만 합니다.
 */
export function QuestionField({
  question,
  value,
  onChange,
  withEscapeHatch,
  onEscape,
  textRef,
  hideLegend,
}: Readonly<QuestionFieldProps>) {
  if (question.answerType === "TEXT") {
    return (
      <fieldset>
        <legend className="text-sm font-semibold text-fg">{question.content}</legend>
        <Textarea
          ref={textRef}
          className="mt-3"
          rows={4}
          maxLength={500}
          value={value.text}
          onChange={(event) => onChange({ selectedOptionIds: [], text: event.target.value })}
          placeholder="예) 결제 버튼을 누르면 가끔 아무 반응이 없어요. 배포하고 나서부터 그래요."
        />
      </fieldset>
    );
  }

  const isMulti = question.answerType === "MULTI";
  // 보기가 많으면 두 칸으로 늘어놓습니다. 단수 문항은 한 칸을 씁니다.
  const twoColumns = isMulti && question.options.length > 4;

  return (
    <fieldset>
      <legend className={cn("text-sm font-semibold text-fg", hideLegend && "sr-only")}>
        {question.content}
      </legend>

      <div
        className={cn(
          "mt-3 grid gap-2",
          twoColumns ? "grid-cols-1 sm:grid-cols-2" : "grid-cols-1",
        )}
      >
        {question.options.map((option) => (
          <ChoiceCard
            key={option.optionId}
            type={isMulti ? "checkbox" : "radio"}
            name={`question-${question.questionId}`}
            label={option.content}
            checked={value.selectedOptionIds.includes(option.optionId)}
            onChange={() => onChange(toggleOption(question, value, option.optionId))}
          />
        ))}

        {/*
          「여기에 없어요」 는 목 명세에 없는 화면 전용 장치입니다.
          서버로 보내지 않고, 고르면 아래 주관식으로 데려갑니다.
        */}
        {withEscapeHatch && (
          <ChoiceCard
            type="checkbox"
            label="여기에 없어요 — 직접 적을게요"
            className={twoColumns ? "sm:col-span-2" : undefined}
            checked={false}
            onChange={() => onEscape?.()}
          />
        )}
      </div>
    </fieldset>
  );
}
