"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/buttons";
import { Callout, useToast } from "@/components/feedback";
import { clientApi } from "@/lib/api/client";
import { INTERNAL } from "@/lib/api/endpoints";
import {
  isAnswered,
  questionsForStep,
  toQuestionValue,
  TOTAL_STEPS,
  type QuestionValue,
} from "@/lib/survey";
import { ApiError } from "@/types/api";
import type { Survey, SurveyAnswer } from "@/types/diagnosis";
import { ROUTES } from "@/lib/routes";
import { QuestionField } from "./QuestionField";
import { ScanFileStep } from "./ScanFileStep";
import { SurveyStep } from "./SurveyStep";

/** 단계별 머리말. 문항 자체는 서버가 주고, 화면은 제목만 갖습니다. */
const STEP_HEADING: Record<number, { title: string; description?: string }> = {
  2: { title: "어떤 상태인가요?", description: "해당하는 걸 모두 골라주세요." },
  3: {
    title: "검사 파일을 받아 직접 실행해주세요",
    description: "코드는 컴퓨터 밖으로 나가지 않습니다. 만들어진 결과 파일만 올려주시면 됩니다.",
  },
  4: {
    title: "마지막으로 확인할게요",
    description: "올려주신 검사 결과에서 나온 질문이에요.",
  },
};

export interface CheckupSurveyProps {
  diagnosisId: number;
  survey: Survey;
  /** 2~4. 주소의 step 을 그대로 받습니다. */
  step: number;
}

/**
 * CheckupSurvey — 설문 2~4단계.
 *
 * 문항은 서버가 준 것을 그대로 그립니다. 저장하면 설문지 전체가 돌아오므로
 * 그 값으로 상태를 맞춥니다. 같은 단계를 다시 보내도 결과가 같습니다.
 *
 * 설문3은 문항이 없는 검사 파일 단계라 답을 저장하지 않습니다.
 */
export function CheckupSurvey({
  diagnosisId,
  survey: initialSurvey,
  step,
}: Readonly<CheckupSurveyProps>) {
  const router = useRouter();
  const showToast = useToast();

  const [survey, setSurvey] = React.useState(initialSurvey);
  const [saving, setSaving] = React.useState(false);
  const [uploadedFileName, setUploadedFileName] = React.useState<string | null>(null);
  /** 「여기에 없어요」 를 누르면 여기로 데려갑니다. */
  const textRef = React.useRef<HTMLTextAreaElement>(null);

  React.useEffect(() => setSurvey(initialSurvey), [initialSurvey]);

  const questions = React.useMemo(
    () => questionsForStep(survey.questions, step),
    [survey.questions, step],
  );

  /** 문항 식별자별 지금 답. */
  const [values, setValues] = React.useState<Record<number, QuestionValue>>({});

  React.useEffect(() => {
    // 서버가 준 `isSelected` 로 시작합니다. 「이전」으로 돌아오면 저장해 둔 답이
    // 그대로 보입니다(설문 조회 명세). 목은 답하기 전에도 보기 몇 개를 고른 채로
    // 내려주는데, 그건 목의 한계라 화면에서 우회하지 않습니다.
    setValues(
      Object.fromEntries(questions.map((q) => [q.questionId, toQuestionValue(q)])),
    );
  }, [questions]);

  const heading = STEP_HEADING[step] ?? { title: "설문" };
  const isScanStep = step === 3;

  /**
   * 설문3은 답할 문항이 없는 대신 **결과 파일을 올려야** 넘어갑니다.
   * 마지막 문항이 그 검사 결과에서 만들어지기 때문입니다.
   * 나머지 단계는 문항에 모두 답해야 합니다.
   */
  const canGoNext = isScanStep
    ? uploadedFileName !== null
    : questions.length > 0 &&
      questions.every((q) => isAnswered(q, values[q.questionId] ?? { selectedOptionIds: [], text: "" }));

  const goTo = (target: number) =>
    target <= 1
      ? router.push(ROUTES.checkup)
      : router.push(ROUTES.checkupSurvey(diagnosisId, target));

  const next = async () => {
    if (saving || !canGoNext) return;

    // 설문3은 저장할 답이 없습니다.
    if (isScanStep) {
      goTo(step + 1);
      return;
    }

    setSaving(true);

    const answers: SurveyAnswer[] = questions.map((question) => {
      const value = values[question.questionId] ?? { selectedOptionIds: [], text: "" };
      return {
        questionId: question.questionId,
        selectedOptionIds: value.selectedOptionIds,
        text: question.answerType === "TEXT" ? value.text.trim() : null,
      };
    });

    try {
      const { data } = await clientApi.patch<Survey>(
        INTERNAL.SURVEY_ANSWERS(diagnosisId),
        { answers },
      );
      setSurvey(data);

      if (step >= TOTAL_STEPS) router.push(ROUTES.checkupResult(diagnosisId));
      else goTo(step + 1);
    } catch (error) {
      showToast({
        tone: "danger",
        title: error instanceof ApiError ? error.message : "답을 저장하지 못했습니다.",
      });
    } finally {
      // 단계 이동은 같은 화면 안에서 일어나 이 컴포넌트가 다시 만들어지지 않습니다.
      // 성공했을 때도 풀어 주지 않으면 다음 단계까지 '저장 중'이 따라와 버튼이 잠깁니다.
      setSaving(false);
    }
  };

  /** 검사 결과를 올리면 설문4 문항이 생기므로 설문지를 다시 읽습니다. */
  const onUploaded = async (fileName: string) => {
    setUploadedFileName(fileName);
    showToast({ tone: "success", title: "성공적으로 업로드 완료했어요" });

    try {
      const { data } = await clientApi.get<Survey>(INTERNAL.SURVEY(diagnosisId));
      setSurvey(data);
    } catch {
      // 다음 단계로 넘어갈 때 다시 읽으므로 여기서는 알리지 않습니다.
    }
  };

  const footer = (
    <>
      <Button variant="outline" size="lg" onClick={() => goTo(step - 1)} disabled={saving}>
        ← 이전
      </Button>
      <Button size="lg" onClick={next} loading={saving} disabled={!canGoNext}>
        {step >= TOTAL_STEPS ? "제출하기" : "다음 →"}
      </Button>
    </>
  );

  // 설문4 문항은 검사 결과를 올려야 생깁니다.
  if (!isScanStep && questions.length === 0) {
    return (
      <SurveyStep step={step} title={heading.title} description={heading.description}>
        <Callout
          tone="info"
          title="검사 결과를 먼저 올려주세요"
          action={
            <Button variant="outline" onClick={() => goTo(3)}>
              3단계로
            </Button>
          }
        >
          마지막 질문은 올려주신 검사 결과에서 만들어집니다.
        </Callout>
      </SurveyStep>
    );
  }

  return (
    <SurveyStep step={step} title={heading.title} description={heading.description} footer={footer}>
      {isScanStep ? (
        <ScanFileStep
          diagnosisId={diagnosisId}
          uploadedFileName={uploadedFileName}
          onUploaded={onUploaded}
        />
      ) : (
        questions.map((question, index) => (
          <QuestionField
            key={question.questionId}
            question={question}
            value={values[question.questionId] ?? { selectedOptionIds: [], text: "" }}
            // 객관식 뒤에 주관식이 이어지는 단계에서만 「여기에 없어요」 를 붙입니다.
            withEscapeHatch={index === 0 && questions.length > 1}
            hideLegend={question.content === heading.title}
            onEscape={() => textRef.current?.focus()}
            onChange={(value) =>
              setValues((prev) => ({ ...prev, [question.questionId]: value }))
            }
            textRef={question.answerType === "TEXT" ? textRef : undefined}
          />
        ))
      )}
    </SurveyStep>
  );
}
