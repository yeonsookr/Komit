import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { Container } from "@/components/layouts";
import { RequestFormEditor } from "@/components/domains/checkup";
import { getCodes, getDiagnosis, getIssues } from "@/lib/api/services";
import { isInRequest } from "@/lib/diagnosisReport";
import { ApiError } from "@/types/api";

export const metadata: Metadata = {
  title: "요청서 수정 | Komit",
  description: "요청서의 요구사항과 확인 문장을 고칩니다.",
};

/** 「+ 요구사항 추가」 의 분류 선택지. */
const ISSUE_CATEGORY = "ISSUE_CATEGORY";

/** 경로의 진단 식별자. 숫자가 아니면 없는 진단으로 봅니다. */
function parseDiagnosisId(value: string): number | null {
  if (!/^\d+$/.test(value)) return null;
  const id = Number(value);
  return id > 0 ? id : null;
}

export default async function RequestFormEditPage({
  params,
}: {
  params: Promise<{ diagnosisId: string }>;
}) {
  const { diagnosisId } = await params;
  const id = parseDiagnosisId(diagnosisId);
  if (id === null) notFound();

  try {
    const [diagnosis, issues, categories] = await Promise.all([
      getDiagnosis(id),
      getIssues(id),
      getCodes(ISSUE_CATEGORY),
    ]);

    return (
      <Container className="py-10 sm:py-14">
        {/* 요청서에 고른 요구사항만 수정 대상입니다. */}
        <RequestFormEditor
          diagnosis={diagnosis}
          issues={issues.filter(isInRequest)}
          categories={categories}
        />
      </Container>
    );
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) notFound();
    throw error;
  }
}
