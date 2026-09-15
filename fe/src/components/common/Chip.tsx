"use client";

import * as React from "react";
import { cn } from "@/lib/cn";

/**
 * Chip — 클릭 가능한 선택/필터 요소. Badge(표시 전용)와 구분합니다.
 *
 *  variant   | 선택됨                                  | 비선택                | 사용 예
 *  ----------|-----------------------------------------|-----------------------|-------------------------------
 *  filter    | 연보라 배경 + 보라 테두리 + 보라 텍스트 + ✕ | 회색 배경 + 회색 텍스트 | 필터 칩(인터넷에 올리기 ✕), 입력 예시 칩
 *  toggle    | 먹색 solid + 흰 텍스트                   | 흰 배경 + 회색 테두리   | 프로필 "잘하는 일" 선택
 *  suggest   | —                                       | 회색 배경 (클릭 시 채움) | 랜딩 예시 문장 칩
 */
export interface ChipProps extends Omit<React.ButtonHTMLAttributes<HTMLButtonElement>, "onSelect"> {
  variant?: "filter" | "toggle" | "suggest";
  selected?: boolean;
  /** 지정 시 우측 ✕ 표시 (filter 선택 상태에서 주로 사용) */
  onRemove?: () => void;
  size?: "sm" | "md";
}

export const Chip = React.forwardRef<HTMLButtonElement, ChipProps>(
  ({ variant = "filter", selected, onRemove, size = "md", className, children, ...props }, ref) => {
    const sizeCls = size === "sm" ? "h-7 px-3 text-xs" : "h-8 px-3.5 text-[13px]";
    const look = {
      filter: selected
        ? "border-primary bg-primary-soft text-primary"
        : "border-transparent bg-surface text-fg-muted hover:bg-line-subtle",
      toggle: selected
        ? "border-ink bg-ink text-on-ink"
        : "border-line bg-bg text-fg hover:bg-surface-2",
      suggest: "border-transparent bg-surface text-fg-muted hover:bg-primary-soft hover:text-primary",
    }[variant];

    return (
      <button
        ref={ref}
        type="button"
        aria-pressed={variant !== "suggest" ? !!selected : undefined}
        className={cn(
          "inline-flex items-center gap-1.5 rounded-full border font-medium whitespace-nowrap transition-colors",
          "disabled:cursor-not-allowed disabled:opacity-50",
          sizeCls,
          look,
          className,
        )}
        {...props}
      >
        {children}
        {onRemove && (
          <span
            role="button"
            tabIndex={-1}
            aria-label="제거"
            onClick={(e) => {
              e.stopPropagation();
              onRemove();
            }}
            className="-mr-1 flex size-4 items-center justify-center rounded-full hover:bg-primary/10"
          >
            <svg viewBox="0 0 12 12" className="size-2.5" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" aria-hidden>
              <path d="M3 3l6 6M9 3l-6 6" />
            </svg>
          </span>
        )}
      </button>
    );
  },
);
Chip.displayName = "Chip";

/**
 * ChipGroup — 칩 나열 래퍼 (wrap + gap)
 */
export function ChipGroup({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return <div className={cn("flex flex-wrap gap-2", className)} {...props} />;
}
