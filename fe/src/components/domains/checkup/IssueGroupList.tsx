"use client";

import * as React from "react";
import { Mono } from "@/components/common";
import {
  CATEGORY_DESCRIPTION,
  groupByCategory,
  SEVERITY_LABEL,
} from "@/lib/diagnosisReport";
import { CategoryIcon } from "./CategoryIcon";
import type { Issue, IssueSeverity } from "@/types/diagnosis";

/** 펼쳤을 때 한 번에 보여줄 줄 수. 나머지는 「더 보기」로 잇습니다. */
const VISIBLE = 3;

/** 위험도 블록의 색. 참고는 색을 쓰지 않습니다. */
const SEVERITY_TONE: Record<IssueSeverity, string> = {
  HIGH: "bg-danger-bg text-danger",
  MEDIUM: "bg-warning-bg text-warning",
  LOW: "bg-surface text-fg-muted",
};

export interface IssueGroupListProps {
  issues: Issue[];
}

/**
 * IssueGroupList — 결함을 갈래로 묶어 접어 두는 목록.
 *
 * 결함이 서른 건을 넘어가면 평평한 목록은 읽을 수 없습니다. 갈래만 늘어놓고
 * 누른 갈래만 펼치면 결함이 몇 건이든 첫 화면 높이가 같습니다.
 */
export function IssueGroupList({ issues }: Readonly<IssueGroupListProps>) {
  const groups = groupByCategory(issues);
  /** 펼친 갈래. 와이어프레임처럼 첫 갈래를 먼저 보여주고, 여럿을 함께 펼칠 수 있습니다. */
  const [open, setOpen] = React.useState<number[]>(() =>
    groups[0] ? [groups[0].codeId] : [],
  );
  /** 갈래마다 몇 줄까지 봤는지. 「더 보기」를 누르면 늘어납니다. */
  const [shown, setShown] = React.useState<Record<number, number>>({});

  const toggle = (codeId: number) =>
    setOpen((prev) =>
      prev.includes(codeId) ? prev.filter((id) => id !== codeId) : [...prev, codeId],
    );

  return (
    <ul className="mt-3 flex flex-col gap-3 rounded-xl bg-surface p-4 sm:p-5">
      {groups.map((group) => {
        const expanded = open.includes(group.codeId);
        const limit = shown[group.codeId] ?? VISIBLE;
        const rest = group.issues.length - limit;

        return (
          <li
            key={group.codeId}
            className="overflow-hidden rounded-lg border border-line bg-bg"
          >
            <button
              type="button"
              onClick={() => toggle(group.codeId)}
              aria-expanded={expanded}
              className="flex w-full items-center gap-3 px-4 py-3.5 text-left hover:bg-surface-2 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary"
            >
              <CategoryIcon codeId={group.codeId} />

              <span className="min-w-0 flex-1">
                <span className="block text-[15px] font-bold text-fg">
                  {group.codeName}
                </span>
                <span className="mt-0.5 block text-[12.5px] text-fg-subtle">
                  {CATEGORY_DESCRIPTION[group.codeId] ?? "검사에서 나온 그 밖의 문제입니다"}
                </span>
              </span>

              <span className="shrink-0 rounded-md border border-line px-2.5 py-1 text-xs font-semibold text-fg-secondary">
                <span className="font-mono tabular-nums">{group.issues.length}</span>건
              </span>
              <span aria-hidden className="shrink-0 text-base text-fg-secondary">
                {expanded ? "▴" : "▾"}
              </span>
            </button>

            {expanded && (
              <div className="border-t border-line bg-surface-2">
                <ul className="divide-y divide-line-subtle">
                  {group.issues.slice(0, limit).map((issue) => (
                    <li key={issue.issueId} className="flex items-stretch gap-4 p-2 sm:px-4">
                      {/* 위험도는 줄 높이를 채우는 블록입니다. 제목·설명·경로 세 줄을
                          한 덩어리로 묶고, 세로로 이어져 훑기 좋습니다. */}
                      <span
                        className={`flex w-14 shrink-0 items-center justify-center rounded-lg text-[11.5px] font-bold ${SEVERITY_TONE[issue.severity]}`}
                      >
                        {SEVERITY_LABEL[issue.severity]}
                      </span>

                      <span className="min-w-0 flex-1 py-2">
                        <span className="block text-sm font-bold text-fg">
                          {issue.title}
                        </span>
                        {issue.detail && (
                          <span className="mt-0.5 block text-[13px] text-fg-muted">
                            {issue.detail}
                          </span>
                        )}
                        {issue.location && (
                          <Mono className="mt-0.5 block text-xs text-primary">
                            {issue.location}
                          </Mono>
                        )}
                      </span>
                    </li>
                  ))}
                </ul>

                {rest > 0 && (
                  <button
                    type="button"
                    onClick={() =>
                      setShown((prev) => ({ ...prev, [group.codeId]: group.issues.length }))
                    }
                    className="w-full border-t border-line-subtle px-4 py-2.5 text-center text-[12.5px] font-semibold text-primary hover:underline focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary"
                  >
                    <span className="font-mono tabular-nums">{rest}</span>건 더 보기 ▾
                  </button>
                )}
              </div>
            )}
          </li>
        );
      })}
    </ul>
  );
}
