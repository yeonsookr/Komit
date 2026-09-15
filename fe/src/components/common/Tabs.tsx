"use client";

import * as React from "react";
import { cn } from "@/lib/cn";

/**
 * SegmentedTabs — 회색 트랙 + 흰 pill 활성 탭.
 * 화면의 모든 탭은 이 한 가지 형태입니다 (언더라인 탭 없음).
 *  - GNB 역할 전환: 의뢰인 | 개발자
 *  - 방 탭: 채팅 6 / 계약서 / 진행 / 확인 3 / 리뷰(disabled)
 *  - 알림 필터: 전체 / 안 읽음 3
 */
export interface TabItem<T extends string = string> {
  value: T;
  label: React.ReactNode;
  /** 우측 카운트 (보라 숫자) */
  count?: number;
  disabled?: boolean;
}

export interface SegmentedTabsProps<T extends string = string> {
  items: TabItem<T>[];
  value: T;
  onChange: (value: T) => void;
  size?: "sm" | "md";
  /** 트랙을 가로로 채우고 탭을 균등 분할 */
  fullWidth?: boolean;
  className?: string;
  "aria-label"?: string;
}

export function SegmentedTabs<T extends string = string>({
  items,
  value,
  onChange,
  size = "md",
  fullWidth,
  className,
  ...aria
}: SegmentedTabsProps<T>) {
  const listRef = React.useRef<HTMLDivElement>(null);

  const onKeyDown = (e: React.KeyboardEvent) => {
    if (e.key !== "ArrowRight" && e.key !== "ArrowLeft") return;
    e.preventDefault();
    const enabled = items.filter((i) => !i.disabled);
    const idx = enabled.findIndex((i) => i.value === value);
    const next = enabled[(idx + (e.key === "ArrowRight" ? 1 : -1) + enabled.length) % enabled.length];
    if (next) {
      onChange(next.value);
      listRef.current?.querySelector<HTMLButtonElement>(`[data-value="${next.value}"]`)?.focus();
    }
  };

  return (
    <div
      ref={listRef}
      role="tablist"
      onKeyDown={onKeyDown}
      className={cn(
        "inline-flex max-w-full items-center gap-0.5 overflow-x-auto rounded-lg bg-surface p-1",
        fullWidth && "flex w-full",
        className,
      )}
      {...aria}
    >
      {items.map((item) => {
        const active = item.value === value;
        return (
          <button
            key={item.value}
            type="button"
            role="tab"
            data-value={item.value}
            aria-selected={active}
            tabIndex={active ? 0 : -1}
            disabled={item.disabled}
            onClick={() => onChange(item.value)}
            className={cn(
              "inline-flex items-center justify-center gap-1.5 rounded-md font-semibold whitespace-nowrap transition-colors",
              size === "sm" ? "h-7 px-3 text-[13px]" : "h-8 px-3.5 text-sm",
              fullWidth && "flex-1",
              active ? "bg-bg text-fg shadow-sm" : "text-fg-subtle hover:text-fg",
              item.disabled && "cursor-not-allowed text-fg-disabled hover:text-fg-disabled",
            )}
          >
            {item.label}
            {item.count !== undefined && item.count > 0 && (
              <span className={cn("font-mono text-xs tabular-nums", active ? "text-primary" : "text-fg-subtle")}>
                {item.count}
              </span>
            )}
          </button>
        );
      })}
    </div>
  );
}

/**
 * NavPill — GNB 메뉴 항목. 활성 시 연보라 pill + 보라 텍스트
 */
export interface NavPillProps extends React.AnchorHTMLAttributes<HTMLAnchorElement> {
  active?: boolean;
  as?: React.ElementType;
}
export function NavPill({ active, as: Comp = "a", className, ...props }: NavPillProps) {
  return (
    <Comp
      aria-current={active ? "page" : undefined}
      className={cn(
        "inline-flex h-8 shrink-0 items-center rounded-md px-3 text-sm font-medium whitespace-nowrap transition-colors",
        active ? "bg-primary-soft text-primary" : "text-fg-secondary hover:bg-surface hover:text-fg",
        className,
      )}
      {...props}
    />
  );
}
