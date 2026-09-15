import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Container } from "@/components/layouts";
import { DiagnosisReport } from "@/components/domains/checkup";
import { getDiagnosis, getIssues } from "@/lib/api/services";
import { ROUTES } from "@/lib/routes";
import { ApiError } from "@/types/api";

export const metadata: Metadata = {
  title: "발견된 문제점 | Komit",
  description: "사전점검에서 나온 문제를 한눈에 보고 요청서로 이어갑니다.",
};

/** 경로의 진단 식별자. 숫자가 아니면 없는 진단으로 봅니다. */
function parseDiagnosisId(value: string): number | null {
  if (!/^\d+$/.test(value)) return null;
  const id = Number(value);
  return id > 0 ? id : null;
}

export default async function DiagnosisResultPage({
  params,
}: {
  params: Promise<{ diagnosisId: string }>;
}) {
  const { diagnosisId } = await params;
  const id = parseDiagnosisId(diagnosisId);
  if (id === null) notFound();

  try {
    // 머리와 결함 목록을 함께 읽습니다.
    const [diagnosis, issues] = await Promise.all([getDiagnosis(id), getIssues(id)]);

    return (
      <Container className="py-10 sm:py-14">
        <DiagnosisReport diagnosis={diagnosis} issues={issues} />

        <div className="mt-10">
          <Link
            href={ROUTES.checkupRequirements(id)}
            className="inline-flex h-12 items-center gap-2 rounded-md bg-primary px-6 text-sm font-semibold text-on-primary hover:bg-primary-hover focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          >
            요청서 만들기 →
          </Link>
        </div>
      </Container>
    );
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) notFound();
    throw error;
  }
}
