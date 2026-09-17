"use client";

import * as React from "react";
import Link from "next/link";
import { Popover } from "@/components/feedback";
import { cn } from "@/lib/cn";
import { formatMonthLabel, shiftMonth } from "@/lib/settlement";

/** 1월부터 12월. 4열 3줄로 놓습니다. */
const MONTHS = Array.from({ length: 12 }, (_, index) => index + 1);

export interface MonthPickerProps {
  /** 지금 보고 있는 달. `YYYY-MM`. */
  month: string;
  /** 그 달로 가는 주소를 만듭니다. 보고 있는 화면에 머무르도록 부모가 정합니다. */
  buildHref: (month: string) => string;
}

/**
 * MonthPicker — 달 고르기. `‹ 2026년 9월 ›` 한 줄이고, 가운데를 누르면 아래에
 * 연도와 달을 고르는 판이 열립니다.
 *
 * 양옆 화살표와 판 안의 달은 모두 `Link` 입니다. 주소를 바꾸는 일이라 버튼이 아니라
 * 링크여야 새 탭으로 열거나 주소를 복사할 수 있고, 뒤로 가기도 자연스럽습니다.
 *
 * 판 안의 연도 넘기기만 버튼입니다. 그건 화면을 옮기는 게 아니라 무엇을 보여줄지
 * 고르는 중간 단계라 주소를 건드리지 않습니다.
 */
export function MonthPicker({ month, buildHref }: Readonly<MonthPickerProps>) {
  const [open, setOpen] = React.useState(false);
  const [selectedYear, viewMonth] = month.split("-").map(Number);

  /** 판 안에서 보고 있는 연도. 열 때마다 고른 달의 연도에서 시작합니다. */
  const [year, setYear] = React.useState(selectedYear);
  React.useEffect(() => {
    if (open) setYear(selectedYear);
  }, [open, selectedYear]);

  return (
    <div className="relative flex items-center justify-between rounded-lg border border-line px-1 py-1">
      <MonthStep href={buildHref(shiftMonth(month, -1))} label="지난달" glyph="‹" />

      <button
        type="button"
        aria-haspopup="dialog"
        aria-expanded={open}
        onClick={() => setOpen((previous) => !previous)}
        className="rounded-md px-2 py-1 font-mono text-[13px] font-semibold text-fg tabular-nums transition-colors hover:bg-surface"
      >
        {formatMonthLabel(month)}
      </button>

      <MonthStep href={buildHref(shiftMonth(month, 1))} label="다음 달" glyph="›" />

      <Popover
        open={open}
        onClose={() => setOpen(false)}
        role="dialog"
        aria-label="연도와 달 고르기"
        className="top-full right-0 left-0 min-w-0 p-3"
      >
        <div className="flex items-center justify-between">
          <YearStep onClick={() => setYear((current) => current - 1)} label="이전 해" glyph="‹" />
          <span className="font-mono text-sm font-semibold text-fg tabular-nums">{year}년</span>
          <YearStep onClick={() => setYear((current) => current + 1)} label="다음 해" glyph="›" />
        </div>

        <div className="mt-3 grid grid-cols-4 gap-1.5">
          {MONTHS.map((value) => {
            const key = `${year}-${String(value).padStart(2, "0")}`;
            const selected = year === selectedYear && value === viewMonth;
            return (
              <Link
                key={key}
                href={buildHref(key)}
                aria-current={selected ? "page" : undefined}
                onClick={() => setOpen(false)}
                className={cn(
                  "rounded-md py-2 text-center font-mono text-[13px] tabular-nums transition-colors",
                  selected
                    ? "bg-primary text-on-primary font-semibold"
                    : "text-fg-secondary hover:bg-surface hover:text-fg",
                )}
              >
                {value}월
              </Link>
            );
          })}
        </div>
      </Popover>
    </div>
  );
}

/** 달을 한 칸 옮기는 화살표. 주소가 바뀌므로 링크입니다. */
function MonthStep({ href, label, glyph }: Readonly<{ href: string; label: string; glyph: string }>) {
  return (
    <Link
      href={href}
      aria-label={label}
      className="inline-flex size-7 shrink-0 items-center justify-center rounded-md text-fg-subtle transition-colors hover:bg-surface hover:text-fg"
    >
      <span aria-hidden>{glyph}</span>
    </Link>
  );
}

/** 판 안에서 연도만 넘깁니다. 아직 고른 것이 아니라 주소를 건드리지 않습니다. */
function YearStep({
  onClick,
  label,
  glyph,
}: Readonly<{ onClick: () => void; label: string; glyph: string }>) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={label}
      className="inline-flex size-7 shrink-0 items-center justify-center rounded-md text-fg-subtle transition-colors hover:bg-surface hover:text-fg"
    >
      <span aria-hidden>{glyph}</span>
    </button>
  );
}
