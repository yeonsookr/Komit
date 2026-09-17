"use client";

import * as React from "react";
import Link from "next/link";
import { Card } from "@/components/common";
import { formatShortDate } from "@/lib/format";
import { ROUTES } from "@/lib/routes";
import type { UnfinishedSurvey } from "@/types/diagnosis";

/** 접기 전에 보여줄 개수. 대부분 직전 것을 이어 하므로 하나면 충분합니다. */
const VISIBLE = 1;

export interface UnfinishedSurveyListProps {
  surveys: UnfinishedSurvey[];
}

/**
 * UnfinishedSurveyList — 설문1 아래 「진행 중인 설문」.
 *
 * 설문을 끝내지 않고 나간 진단을 다시 잡는 자리입니다. 가장 최근 것만 펼치고
 * 나머지는 접습니다 — 건수가 늘어도 「다음」 버튼이 아래로 밀리지 않습니다.
 */
export function UnfinishedSurveyList({ surveys }: Readonly<UnfinishedSurveyListProps>) {
  const [expanded, setExpanded] = React.useState(false);

  if (surveys.length === 0) return null;

  const shown = expanded ? surveys : surveys.slice(0, VISIBLE);
  const hidden = surveys.length - VISIBLE;

  return (
    <section className="mt-10">
      <h2 className="text-[13px] font-semibold text-fg-secondary">진행 중인 설문</h2>

      <Card className="mt-2" padding="none">
        <ul className="divide-y divide-line-subtle">
          {shown.map((survey) => (
            <li
              key={survey.diagnosisId}
              className="flex items-center justify-between gap-4 px-4 py-3.5 sm:px-5"
            >
              <span className="min-w-0">
                <span className="block truncate text-sm font-semibold text-fg">
                  {survey.projectName}
                </span>
                <span className="mt-0.5 block text-[13px] text-fg-subtle">
                  {formatShortDate(survey.updatedAt)} 저장됨
                </span>
              </span>

              <Link
                href={ROUTES.checkupSurvey(survey.diagnosisId, 2)}
                className="shrink-0 rounded-sm text-[13px] font-semibold text-primary hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
              >
                이어서 하기 ›
              </Link>
            </li>
          ))}

          {!expanded && hidden > 0 && (
            <li className="px-4 py-2 text-center sm:px-5">
              <button
                type="button"
                onClick={() => setExpanded(true)}
                className="rounded-sm text-[13px] font-semibold text-primary hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
              >
                <span className="font-mono tabular-nums">{hidden}</span>건 더 보기 ▾
              </button>
            </li>
          )}
        </ul>
      </Card>
    </section>
  );
}
