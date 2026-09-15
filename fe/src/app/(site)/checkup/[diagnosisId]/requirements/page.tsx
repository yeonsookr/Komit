import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { Container } from "@/components/layouts";
import { Tag } from "@/components/common";
import { RequirementPicker } from "@/components/domains/checkup";
import { getCodes, getDiagnosis, getIssues } from "@/lib/api/services";
import { hasScore, versionLabel } from "@/lib/diagnosisReport";
import { formatShortDateTime } from "@/lib/format";
import { ApiError } from "@/types/api";

export const metadata: Metadata = {
  title: "요구사항 선택 | Komit",
  description: "발견된 문제 중 맡길 것을 골라 요청서를 만듭니다.",
};

/** 「+ 요구사항 추가」 의 분류 선택지. */
const ISSUE_CATEGORY = "ISSUE_CATEGORY";

/** 경로의 진단 식별자. 숫자가 아니면 없는 진단으로 봅니다. */
function parseDiagnosisId(value: string): number | null {
  if (!/^\d+$/.test(value)) return null;
  const id = Number(value);
  return id > 0 ? id : null;
}

export default async function RequirementsPage({
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
      <Container width="wide" className="py-10 sm:py-14">
        <header className="flex flex-wrap items-start gap-x-6 gap-y-3">
          {hasScore(diagnosis.readinessScore) && (
            <div className="shrink-0 text-center">
              <p className="font-mono text-[32px] leading-none font-bold text-primary tabular-nums">
                {diagnosis.readinessScore}
              </p>
              <p className="mt-1 text-[13px] text-fg-subtle">준비도</p>
            </div>
          )}

          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-2">
              <h1 className="text-[26px] leading-tight font-bold text-fg">
                {diagnosis.project.description ?? diagnosis.project.name}
              </h1>
              <ul className="flex flex-wrap gap-1.5">
                {diagnosis.project.techCategories.map((tech) => (
                  <li key={tech.techCodeId}>
                    <Tag>{tech.codeName}</Tag>
                  </li>
                ))}
              </ul>
            </div>

            {/* 수치는 모두 진단 응답에서 옵니다. 화면이 다시 세지 않습니다. */}
            <dl className="mt-3 flex flex-wrap gap-x-8 gap-y-2 text-[13px]">
              <div>
                <dt className="text-fg-subtle">진단</dt>
                <dd className="mt-0.5 font-semibold text-fg">
                  {versionLabel(diagnosis.version).replace("차 진단", "차")}
                  {diagnosis.scannedAt && (
                    <>
                      {" · "}
                      <span className="font-mono tabular-nums">
                        {formatShortDateTime(diagnosis.scannedAt).split(" ")[0]}
                      </span>
                    </>
                  )}
                </dd>
              </div>
              <div>
                <dt className="text-fg-subtle">결함</dt>
                <dd className="mt-0.5 font-mono font-semibold text-fg tabular-nums">
                  {diagnosis.totalIssueCount}건
                </dd>
              </div>
              <div>
                <dt className="text-fg-subtle">지난 진단 대비</dt>
                <dd className="mt-0.5 font-mono font-semibold text-fg tabular-nums">
                  {diagnosis.resolvedSinceLastVersionCount}건 해결
                </dd>
              </div>
            </dl>
          </div>
        </header>

        <RequirementPicker diagnosis={diagnosis} issues={issues} categories={categories} />
      </Container>
    );
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) notFound();
    throw error;
  }
}
