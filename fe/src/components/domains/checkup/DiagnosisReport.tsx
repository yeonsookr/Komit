import { Badge, Card, Mono } from "@/components/common";
import { EmptyState } from "@/components/feedback";
import { formatShortDateTime } from "@/lib/format";
import { countByCategory, hasScore, splitByInput, versionLabel } from "@/lib/diagnosisReport";
import type { BadgeTone } from "@/components/common";
import type { Diagnosis, Issue } from "@/types/diagnosis";

/** 분류별 배지 색. 목에 없는 분류는 회색으로 둡니다. */
const CATEGORY_TONE: Record<string, BadgeTone> = {
  보안: "danger",
  위법: "warning",
  결제: "warning",
};

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
  const counts = countByCategory(issues);

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
      {input.length > 0 && diagnosis.userInput && (
        <Card variant="surface" className="mt-6">
          <p className="text-xs font-semibold text-primary">의뢰 내용</p>
          <p className="mt-2 text-base font-bold text-fg">&ldquo;{diagnosis.userInput}&rdquo;</p>

          {input.map((issue) => (
            <div key={issue.issueId} className="mt-4">
              {issue.location && (
                <Mono className="block text-[13px] text-primary">{issue.location}</Mono>
              )}
              {issue.detail && (
                <p className="mt-3 text-sm text-fg-secondary">{issue.detail}</p>
              )}
            </div>
          ))}
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
          <ul className="mt-2">
            {others.map((issue) => (
              <li
                key={issue.issueId}
                className="flex items-start gap-3 border-b border-line-subtle py-5"
              >
                <CategoryIcon name={issue.category.codeName} />

                <div className="min-w-0 flex-1">
                  <p className="text-sm font-bold text-fg">{issue.title}</p>
                  {issue.detail && (
                    <p className="mt-1 text-[13px] text-fg-muted">{issue.detail}</p>
                  )}
                  {issue.location && (
                    <Mono className="mt-1 block text-xs text-fg-subtle">{issue.location}</Mono>
                  )}
                </div>

                <Badge
                  tone={CATEGORY_TONE[issue.category.codeName] ?? "neutral"}
                  className="mt-0.5 shrink-0"
                >
                  {issue.category.codeName} {counts.get(issue.category.codeId) ?? 1}건
                </Badge>
              </li>
            ))}
          </ul>
        )}
      </section>
    </>
  );
}

/** 분류를 한눈에 알아보게 하는 작은 그림. 뜻은 옆 배지가 전합니다. */
function CategoryIcon({ name }: Readonly<{ name: string }>) {
  return (
    <span
      aria-hidden
      className="mt-0.5 flex size-6 shrink-0 items-center justify-center text-fg-subtle"
    >
      {name === "보안" && (
        <svg viewBox="0 0 24 24" className="size-5" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
          <path d="M12 3 4 6v6c0 4.4 3.4 8.3 8 9 4.6-.7 8-4.6 8-9V6l-8-3z" />
          <path d="m9.5 11.5 2 2 3.5-3.5" />
        </svg>
      )}
      {name === "위법" && (
        <svg viewBox="0 0 24 24" className="size-5" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
          <path d="M12 3v18M7 21h10M5 8h14M7 8l-3 6h6L7 8zM17 8l-3 6h6l-3-6z" />
        </svg>
      )}
      {name === "결제" && (
        <svg viewBox="0 0 24 24" className="size-5" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
          <rect x="2.5" y="5.5" width="19" height="13" rx="2" />
          <path d="M2.5 10h19" />
        </svg>
      )}
    </span>
  );
}
