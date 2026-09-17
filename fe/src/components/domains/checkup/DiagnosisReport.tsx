import { Card, Mono } from "@/components/common";
import { EmptyState } from "@/components/feedback";
import { formatShortDateTime } from "@/lib/format";
import { hasScore, splitByInput, versionLabel } from "@/lib/diagnosisReport";
import { IssueGroupList } from "./IssueGroupList";
import type { Diagnosis, Issue } from "@/types/diagnosis";

export interface DiagnosisReportProps {
  diagnosis: Diagnosis;
  issues: Issue[];
}

/**
 * DiagnosisReport — 검사에서 나온 결함을 보여주는 진단서.
 *
 * 의뢰인이 적은 증상과 관련된 결함을 위에 따로 놓고, 나머지를 아래에 늘어놓습니다.
 */
export function DiagnosisReport({ diagnosis, issues }: Readonly<DiagnosisReportProps>) {
  const { input, others } = splitByInput(issues);

  return (
    <>
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0">
          <h1 className="text-[28px] leading-tight font-bold text-fg">발견된 문제점</h1>
          <p className="mt-1.5 text-[13px] text-fg-subtle">
            {versionLabel(diagnosis.version)}
            {diagnosis.scannedAt && (
              <>
                {" · "}
                <Mono>{formatShortDateTime(diagnosis.scannedAt)}</Mono> 검사
              </>
            )}
          </p>
        </div>

        {hasScore(diagnosis.readinessScore) && (
          <div className="shrink-0 text-right">
            <p className="font-mono text-[32px] leading-none font-bold text-primary tabular-nums">
              {diagnosis.readinessScore}
              <span className="ml-0.5 text-sm font-normal text-fg-subtle">/100</span>
            </p>
            <p className="mt-1 text-[13px] text-fg-subtle">런칭 준비도</p>
          </div>
        )}
      </header>

      {/* 의뢰 내용에서 나온 결함. 사용자가 적은 증상을 그대로 인용합니다. */}
      {input.length > 0 && (
        <Card variant="surface" className="mt-6">
          <p className="text-xs font-semibold text-primary">의뢰 내용</p>
          {diagnosis.userInput && (
            <p className="mt-2 text-base font-bold text-fg">
              &ldquo;{diagnosis.userInput}&rdquo;
            </p>
          )}

          <div className="mt-4 divide-y divide-line-subtle border-t border-line-subtle">
            {input.map((issue) => (
              <div key={issue.issueId} className="py-4 first:pt-4 last:pb-0">
                <p className="text-sm font-bold text-fg">{issue.title}</p>
                {issue.detail && (
                  <p className="mt-1 text-sm text-fg-secondary">{issue.detail}</p>
                )}
              {issue.location && (
                  <Mono className="mt-1 block text-[13px] text-primary">{issue.location}</Mono>
                )}
              </div>
            ))}
          </div>
        </Card>
      )}

      <section className="mt-8">
        <h2 className="text-xs font-semibold text-primary">의뢰 내용 외 문제점</h2>

        {others.length === 0 ? (
          <EmptyState
            className="mt-4"
            title="그 밖의 문제점은 없습니다"
            description="의뢰하신 내용 말고는 발견된 문제가 없습니다."
          />
        ) : (
          <IssueGroupList issues={others} />
        )}
      </section>
    </>
  );
}
