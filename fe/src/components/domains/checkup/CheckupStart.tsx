"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/buttons";
import { Field, Input, Textarea } from "@/components/common";
import { useToast } from "@/components/feedback";
import { clientApi } from "@/lib/api/client";
import { INTERNAL } from "@/lib/api/endpoints";
import { ApiError } from "@/types/api";
import type { Diagnosis, UnfinishedSurvey } from "@/types/diagnosis";
import { ROUTES } from "@/lib/routes";
import { SurveyStep } from "./SurveyStep";
import { UnfinishedSurveyList } from "./UnfinishedSurveyList";

export interface CheckupStartProps {
  /** 끝내지 않은 설문. 없으면 빈 배열입니다. */
  unfinished?: UnfinishedSurvey[];
}

/**
 * CheckupStart — 설문1. 프로젝트 이름과 설명만 받습니다.
 *
 * **문항은 없습니다.** 진단을 열기 전이라 서버에 문항이 없고, 설문 문항은
 * 설문2부터 받습니다. 프로젝트를 먼저 만들어야 설문지가 생기기 때문입니다.
 *
 * 「다음」을 누를 때 진단을 시작합니다. 프로젝트 이름을 여기서 받기 때문에
 * 화면에 들어올 때가 아니라 이때 부릅니다(목 명세).
 */
export function CheckupStart({ unfinished = [] }: Readonly<CheckupStartProps>) {
  const router = useRouter();
  const showToast = useToast();

  const [name, setName] = React.useState("");
  const [description, setDescription] = React.useState("");
  const [submitting, setSubmitting] = React.useState(false);

  const canSubmit = name.trim().length > 0;

  const submit = async () => {
    if (!canSubmit || submitting) return;
    setSubmitting(true);

    try {
      // 진단을 연다. 이 요청으로 설문지 한 벌이 만들어지고, 문항은 설문2부터 답한다.
      const { data: diagnosis } = await clientApi.post<Diagnosis>(INTERNAL.DIAGNOSES, {
        name: name.trim(),
        description: description.trim(),
      });

      router.push(ROUTES.checkupSurvey(diagnosis.diagnosisId, 2));
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
          rows={4}
          value={description}
          onChange={(event) => setDescription(event.target.value)}
          placeholder="예) 수제 비누를 파는 작은 온라인 가게"
          maxLength={200}
        />
      </Field>

      <UnfinishedSurveyList surveys={unfinished} />
    </SurveyStep>
  );
}
