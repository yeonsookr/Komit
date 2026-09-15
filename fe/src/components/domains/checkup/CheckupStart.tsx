"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/buttons";
import { ChoiceCard, Field, Input, Textarea } from "@/components/common";
import { useToast } from "@/components/feedback";
import { clientApi } from "@/lib/api/client";
import { INTERNAL } from "@/lib/api/endpoints";
import { ApiError } from "@/types/api";
import type { Diagnosis, Survey } from "@/types/diagnosis";
import { SurveyStep } from "./SurveyStep";

/**
 * 설문1의 보기.
 *
 * 진단을 시작하기 전이라 서버에서 문항을 받을 수 없습니다. 설문1~3은 모든
 * 프로젝트에 같은 고정 문항이라 화면이 문구를 갖는다는 목 명세를 따릅니다.
 * 저장은 진단이 열린 뒤 서버가 준 보기 식별자로 합니다(순서가 같습니다).
 */
const STAGE_OPTIONS = [
  "내 컴퓨터에서만 돌아갑니다",
  "배포는 했는데 아직 아무도 안 씁니다",
  "사용자가 조금 있습니다",
  "매출이 발생하고 있습니다",
];

/**
 * CheckupStart — 설문1. 프로젝트 이름·설명과 진행 단계를 받습니다.
 *
 * 「다음」을 누를 때 진단을 시작합니다. 프로젝트 이름을 여기서 받기 때문에
 * 화면에 들어올 때가 아니라 이때 부릅니다(목 명세).
 */
export function CheckupStart() {
  const router = useRouter();
  const showToast = useToast();

  const [name, setName] = React.useState("");
  const [description, setDescription] = React.useState("");
  const [stageIndex, setStageIndex] = React.useState<number | null>(null);
  const [submitting, setSubmitting] = React.useState(false);

  const canSubmit = name.trim().length > 0 && stageIndex !== null;

  const submit = async () => {
    if (!canSubmit || submitting) return;
    setSubmitting(true);

    try {
      // ① 진단을 연다. 이 요청으로 설문지 한 벌이 만들어진다.
      const { data: diagnosis } = await clientApi.post<Diagnosis>(INTERNAL.DIAGNOSES, {
        name: name.trim(),
        description: description.trim(),
      });

      // ② 만들어진 설문에서 첫 문항의 보기 식별자를 받아 고른 답을 저장한다.
      const { data: survey } = await clientApi.get<Survey>(
        INTERNAL.SURVEY(diagnosis.diagnosisId),
      );
      const question = survey.questions[0];
      const optionId = question?.options[stageIndex]?.optionId;

      if (optionId !== undefined) {
        await clientApi.patch(INTERNAL.SURVEY_ANSWERS(diagnosis.diagnosisId), {
          answers: [{ questionId: question.questionId, selectedOptionIds: [optionId] }],
        });
      }

      router.push(`/checkup/${diagnosis.diagnosisId}?step=2`);
    } catch (error) {
      showToast({
        tone: "danger",
        title: error instanceof ApiError ? error.message : "사전점검을 시작하지 못했습니다.",
      });
      setSubmitting(false);
    }
  };

  return (
    <SurveyStep
      step={1}
      title="어떤 걸 만드셨나요?"
      description="3분이면 끝나요."
      footer={
        <Button size="lg" onClick={submit} loading={submitting} disabled={!canSubmit}>
          다음 →
        </Button>
      }
    >
      <Field label="점검을 진행할 프로젝트명을 입력해주세요">
        <Input
          value={name}
          onChange={(event) => setName(event.target.value)}
          placeholder="예) 수제 비누 가게"
          maxLength={50}
        />
      </Field>

      <Field label="프로젝트에 대해 설명해주세요">
        <Textarea
          rows={2}
          value={description}
          onChange={(event) => setDescription(event.target.value)}
          placeholder="예) 수제 비누를 파는 작은 온라인 가게"
          maxLength={200}
        />
      </Field>

      <fieldset>
        <legend className="text-sm font-semibold text-fg">지금 어디까지 왔나요</legend>
        <div className="mt-3 flex flex-col gap-2">
          {STAGE_OPTIONS.map((option, index) => (
            <ChoiceCard
              key={option}
              name="stage"
              label={option}
              checked={stageIndex === index}
              onChange={() => setStageIndex(index)}
            />
          ))}
        </div>
      </fieldset>
    </SurveyStep>
  );
}
