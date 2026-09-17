import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { Container } from "@/components/layouts";
import { RequestForm } from "@/components/domains/checkup";
import { getDiagnosis, getIssues } from "@/lib/api/services";
import { ApiError } from "@/types/api";

export const metadata: Metadata = {
  title: "요청서 | Komit",
  description: "맡길 요구사항과 확인 문장을 확인하고 개발자를 찾습니다.",
};

/** 경로의 진단 식별자. 숫자가 아니면 없는 진단으로 봅니다. */
function parseDiagnosisId(value: string): number | null {
  if (!/^\d+$/.test(value)) return null;
  const id = Number(value);
  return id > 0 ? id : null;
}

export default async function RequestFormPage({
  params,
}: {
  params: Promise<{ diagnosisId: string }>;
}) {
  const { diagnosisId } = await params;
  const id = parseDiagnosisId(diagnosisId);
  if (id === null) notFound();

  try {
    // 요청서는 따로 저장되는 문서가 아니라 요구사항들을 모아 부르는 이름입니다.
    const [diagnosis, issues] = await Promise.all([getDiagnosis(id), getIssues(id)]);

    return (
      <Container className="py-10 sm:py-14">
        <RequestForm diagnosis={diagnosis} issues={issues} />
      </Container>
    );
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) notFound();
    throw error;
  }
}
