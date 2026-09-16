import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { CheckupSurvey } from "@/components/domains/checkup";
import { getSurvey } from "@/lib/api/services";
import { parseStep, STEP_PARAM } from "@/lib/survey";
import { ApiError } from "@/types/api";

export const metadata: Metadata = {
  title: "사전점검 설문 | Komit",
  description: "몇 가지만 답하면 무엇이 막혀 있는지 정리해 드립니다.",
};

/** 경로의 진단 식별자. 숫자가 아니면 없는 진단으로 봅니다. */
function parseDiagnosisId(value: string): number | null {
  if (!/^\d+$/.test(value)) return null;
  const id = Number(value);
  return id > 0 ? id : null;
}

export default async function CheckupSurveyPage({
  params,
  searchParams,
}: {
  params: Promise<{ diagnosisId: string }>;
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const { diagnosisId } = await params;
  const id = parseDiagnosisId(diagnosisId);
  if (id === null) notFound();

  const step = parseStep((await searchParams)[STEP_PARAM]);

  try {
    const survey = await getSurvey(id);
    return <CheckupSurvey diagnosisId={id} survey={survey} step={step} />;
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) notFound();
    throw error;
  }
}
