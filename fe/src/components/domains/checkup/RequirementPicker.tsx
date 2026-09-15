"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/buttons";
import { Badge, Checkbox, Tag } from "@/components/common";
import { useToast } from "@/components/feedback";
import { cn } from "@/lib/cn";
import { clientApi } from "@/lib/api/client";
import { INTERNAL } from "@/lib/api/endpoints";
import { ROUTES } from "@/lib/routes";
import { dropSelection, isResolved, SEVERITY_LABEL } from "@/lib/diagnosisReport";
import { ApiError } from "@/types/api";
import type { BadgeTone } from "@/components/common";
import type { CodeItem } from "@/types/developer";
import type { Diagnosis, Issue, IssueSeverity } from "@/types/diagnosis";
import type { RecommendationBatch } from "@/types/recommendation";
import { AddRequirementForm } from "./AddRequirementForm";

/** 분류별 배지 색. 목에 없는 분류는 회색으로 둡니다. */
const CATEGORY_TONE: Record<string, BadgeTone> = {
  보안: "danger",
  위법: "warning",
  결제: "brand",
};

/** 위험도 배지 색. */
const SEVERITY_TONE: Record<IssueSeverity, BadgeTone> = {
  HIGH: "danger",
  MEDIUM: "warning",
  LOW: "neutral",
};

export interface RequirementPickerProps {
  diagnosis: Diagnosis;
  issues: Issue[];
  /** 「+ 요구사항 추가」 의 분류 선택지. */
  categories: CodeItem[];
}

/**
 * RequirementPicker — 맡길 요구사항을 고릅니다.
 *
 * 이미 고친 것은 해결 처리해 목록에서 흐리게 남기고, 고른 것만 요청서가 됩니다.
 */
export function RequirementPicker({
  diagnosis,
  issues: initialIssues,
  categories,
}: Readonly<RequirementPickerProps>) {
  const router = useRouter();
  const showToast = useToast();

  const [issues, setIssues] = React.useState(initialIssues);
  const [selected, setSelected] = React.useState<number[]>([]);
  const [busyIssueId, setBusyIssueId] = React.useState<number | null>(null);
  const [submitting, setSubmitting] = React.useState(false);

  React.useEffect(() => setIssues(initialIssues), [initialIssues]);

  const toggle = (issueId: number) =>
    setSelected((prev) =>
      prev.includes(issueId) ? prev.filter((id) => id !== issueId) : [...prev, issueId],
    );

  /** 직접 해결했어요 / 되돌리기. */
  const changeStatus = async (issue: Issue, status: "RESOLVED" | "UNRESOLVED") => {
    setBusyIssueId(issue.issueId);

    try {
      const { data } = await clientApi.patch<Issue>(INTERNAL.ISSUE(issue.issueId), { status });

      setIssues((prev) => prev.map((row) => (row.issueId === data.issueId ? data : row)));
      // 해결 처리하면 고른 목록에서도 빠져야 합니다.
      if (status === "RESOLVED") setSelected((prev) => dropSelection(prev, issue.issueId));
    } catch (error) {
      showToast({
        tone: "danger",
        title: error instanceof ApiError ? error.message : "상태를 바꾸지 못했습니다.",
      });
    } finally {
      setBusyIssueId(null);
    }
  };

  /** 고른 요구사항으로 추천 회차를 만들고 그 화면으로 갑니다. */
  const submit = async () => {
    if (selected.length === 0 || submitting) return;
    setSubmitting(true);

    try {
      const { data } = await clientApi.post<RecommendationBatch>(
        INTERNAL.RECOMMENDATIONS(diagnosis.project.projectId),
        { diagnosisId: diagnosis.diagnosisId, issueIds: selected },
      );
      router.push(ROUTES.recommendation(data.batchId));
    } catch (error) {
      showToast({
        tone: "danger",
        title: error instanceof ApiError ? error.message : "요청서를 만들지 못했습니다.",
      });
      setSubmitting(false);
    }
  };

  const unresolvedCount = issues.filter((issue) => !isResolved(issue)).length;

  return (
    <>
      <div className="mt-8 flex flex-wrap items-baseline justify-between gap-3">
        <h2 className="text-base font-bold text-fg">요구사항</h2>
        <p className="text-[13px] text-fg-subtle">
          미해결 <span className="font-mono tabular-nums">{unresolvedCount}</span>건
          {" · "}
          <span className="font-mono tabular-nums">{selected.length}</span>건 선택됨
        </p>
      </div>

      <ul className="mt-2">
        {issues.map((issue) => {
          const resolved = isResolved(issue);

          return (
            <li
              key={issue.issueId}
              className="flex items-center gap-3 border-b border-line-subtle py-4"
            >
              <Checkbox
                checked={selected.includes(issue.issueId)}
                disabled={resolved}
                onChange={() => toggle(issue.issueId)}
                aria-label={`${issue.title} 선택`}
                className="shrink-0"
              />

              <Badge
                tone={CATEGORY_TONE[issue.category.codeName] ?? "neutral"}
                shape="square"
                size="sm"
                className="w-14 shrink-0 justify-center"
              >
                {issue.category.codeName}
              </Badge>

              <p
                className={cn(
                  "min-w-0 flex-1 truncate text-sm",
                  resolved ? "text-fg-disabled line-through" : "text-fg",
                )}
              >
                {issue.title}
              </p>

              <Badge tone={SEVERITY_TONE[issue.severity]} size="sm" className="shrink-0">
                {SEVERITY_LABEL[issue.severity]}
              </Badge>

              <Button
                variant="outline"
                size="sm"
                className="shrink-0"
                loading={busyIssueId === issue.issueId}
                onClick={() => changeStatus(issue, resolved ? "UNRESOLVED" : "RESOLVED")}
              >
                {resolved ? "되돌리기" : "직접 해결했어요"}
              </Button>
            </li>
          );
        })}
      </ul>

      <AddRequirementForm
        diagnosisId={diagnosis.diagnosisId}
        categories={categories}
        onAdded={(issue) => setIssues((prev) => [...prev, issue])}
      />

      <div className="mt-8 flex flex-wrap items-center justify-between gap-4 rounded-xl bg-surface px-5 py-4">
        <div className="min-w-0">
          <p className="text-sm font-bold text-fg">
            <span className="font-mono tabular-nums">{selected.length}</span>건 선택됨
          </p>
          <p className="mt-0.5 text-[13px] text-fg-muted">고른 요구사항으로 요청서를 만듭니다.</p>
        </div>

        <Button
          size="lg"
          onClick={submit}
          loading={submitting}
          disabled={selected.length === 0}
          rightIcon={<SendIcon />}
        >
          요청서 만들기
        </Button>
      </div>
    </>
  );
}

function SendIcon() {
  return (
    <svg viewBox="0 0 24 24" className="size-4" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
      <path d="M22 2 11 13M22 2l-7 20-4-9-9-4 20-7z" />
    </svg>
  );
}
