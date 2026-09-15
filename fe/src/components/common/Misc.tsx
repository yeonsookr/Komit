import * as React from "react";
import { cn } from "@/lib/cn";

/**
 * Divider — hairline. label 을 주면 채팅 날짜 구분자 ("8월 22일") 형태
 */
export function Divider({ label, className }: { label?: React.ReactNode; className?: string }) {
  if (!label) return <hr className={cn("border-0 border-t border-line-subtle", className)} />;
  return (
    <div className={cn("flex items-center gap-3 text-xs text-fg-subtle", className)} role="separator">
      <span className="h-px flex-1 bg-line-subtle" />
      <span className="font-mono tabular-nums">{label}</span>
      <span className="h-px flex-1 bg-line-subtle" />
    </div>
  );
}

/**
 * Mono — 금액·시각·날짜·카운터 등 숫자 텍스트. 이 시스템의 가장 뚜렷한 규칙.
 */
export function Mono({ className, ...props }: React.HTMLAttributes<HTMLSpanElement>) {
  return <span className={cn("font-mono tabular-nums", className)} {...props} />;
}

/**
 * Money — 원화 포맷 (650,000원 / 50,000원 ~)
 */
export interface MoneyProps extends React.HTMLAttributes<HTMLSpanElement> {
  value: number;
  /** "~" 접미 (최소 금액 표기) */
  from?: boolean;
  /** 부호 표시 (+650,000 / -50,000) — 원장 */
  signed?: boolean;
  unit?: string;
  tone?: "default" | "info" | "danger" | "success" | "muted";
}
export function Money({ value, from, signed, unit = "원", tone = "default", className, ...props }: MoneyProps) {
  const abs = Math.abs(value).toLocaleString("ko-KR");
  const sign = signed ? (value < 0 ? "-" : "+") : value < 0 ? "-" : "";
  return (
    <Mono
      className={cn(
        "font-semibold",
        { default: "text-fg", info: "text-info", danger: "text-danger", success: "text-success", muted: "text-fg-subtle" }[tone],
        className,
      )}
      {...props}
    >
      {sign}
      {abs}
      {unit}
      {from && " ~"}
    </Mono>
  );
}

/**
 * Eyebrow — 섹션 위 작은 보라 소제목 (사전 점검 서비스 / STEP 1 / 4)
 */
export function Eyebrow({ mono, className, ...props }: React.HTMLAttributes<HTMLParagraphElement> & { mono?: boolean }) {
  return (
    <p
      className={cn(
        "text-xs font-semibold tracking-wide",
        mono ? "font-mono text-fg-subtle tabular-nums uppercase" : "text-primary",
        className,
      )}
      {...props}
    />
  );
}

/**
 * PageHeader — 브레드크럼 + 제목 + 설명 + 우측 메타
 */
export interface PageHeaderProps extends Omit<React.HTMLAttributes<HTMLDivElement>, "title"> {
  breadcrumb?: React.ReactNode;
  title: React.ReactNode;
  description?: React.ReactNode;
  meta?: React.ReactNode;
  /** 제목 옆 배지 등 */
  badge?: React.ReactNode;
  size?: "md" | "lg";
}
export function PageHeader({ breadcrumb, title, description, meta, badge, size = "lg", className, ...props }: PageHeaderProps) {
  return (
    <div className={cn("flex items-end justify-between gap-6", className)} {...props}>
      <div className="min-w-0">
        {breadcrumb && <div className="mb-2 flex items-center gap-2 text-[13px] text-fg-subtle">{breadcrumb}</div>}
        <div className="flex items-center gap-3">
          <h1 className={cn("font-bold text-fg", size === "lg" ? "text-[28px] leading-tight" : "text-xl")}>{title}</h1>
          {badge}
        </div>
        {description && <p className="mt-1.5 text-sm text-fg-muted">{description}</p>}
      </div>
      {meta && <div className="shrink-0 text-[13px] text-fg-subtle">{meta}</div>}
    </div>
  );
}

/**
 * Breadcrumb — "의뢰 / 상담"
 */
export function Breadcrumb({ items, className }: { items: React.ReactNode[]; className?: string }) {
  return (
    <nav aria-label="breadcrumb" className={cn("flex items-center gap-2 text-[13px] text-fg-subtle", className)}>
      {items.map((it, i) => (
        <React.Fragment key={i}>
          {i > 0 && <span aria-hidden>/</span>}
          <span className={cn(i === items.length - 1 && "text-fg-secondary")}>{it}</span>
        </React.Fragment>
      ))}
    </nav>
  );
}

/**
 * NumberedSteps — 먹색 원형 번호 + 제목 + 설명 (예치금 처리 방식 1·2·3, 등록 절차)
 */
export interface StepItem {
  title: React.ReactNode;
  description?: React.ReactNode;
}
export function NumberedSteps({ items, tone = "ink", className }: { items: StepItem[]; tone?: "ink" | "primary"; className?: string }) {
  return (
    <ol className={cn("flex flex-col gap-4", className)}>
      {items.map((s, i) => (
        <li key={i} className="flex gap-3">
          <span
            className={cn(
              "flex size-6 shrink-0 items-center justify-center rounded-full font-mono text-xs font-bold text-on-ink",
              tone === "ink" ? "bg-ink" : "bg-primary",
            )}
          >
            {i + 1}
          </span>
          <div className="min-w-0 pt-0.5">
            <p className="text-sm font-semibold text-fg">{s.title}</p>
            {s.description && <p className="mt-0.5 text-[13px] text-fg-muted">{s.description}</p>}
          </div>
        </li>
      ))}
    </ol>
  );
}

/**
 * ProgressList — 진행 상황 리스트 (좌 상태 점 + 라벨 + 우 금액/상태)
 *  status: done(녹) | active(황) | pending(회)
 */
export interface ProgressItem {
  label: React.ReactNode;
  sub?: React.ReactNode;
  right?: React.ReactNode;
  status: "done" | "active" | "pending";
}
export function ProgressList({ items, className }: { items: ProgressItem[]; className?: string }) {
  const dot = { done: "bg-success", active: "bg-warning", pending: "bg-line" };
  return (
    <ul className={cn("flex flex-col", className)}>
      {items.map((it, i) => (
        <li
          key={i}
          className={cn(
            "flex items-center gap-3 py-3 text-sm",
            i < items.length - 1 && "border-b border-line-subtle",
            it.status === "pending" && "text-fg-subtle",
          )}
        >
          <span className={cn("size-2 shrink-0 rounded-full", dot[it.status])} />
          <span className="min-w-0 flex-1">
            <span className={cn(it.status !== "pending" && "font-medium text-fg")}>{it.label}</span>
            {it.sub && <span className="ml-2 text-[13px] text-fg-subtle">{it.sub}</span>}
          </span>
          {it.right && <span className="shrink-0 font-mono tabular-nums">{it.right}</span>}
        </li>
      ))}
    </ul>
  );
}

/**
 * StickyBar — 화면 하단 고정 액션 바 (남은 작업 2건 250,000원 / 저장하고 프로필 공개)
 */
export function StickyBar({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={cn(
        "sticky bottom-0 z-30 -mx-4 flex items-center justify-between gap-4 border-t border-line bg-bg/95 px-4 py-3 backdrop-blur",
        className,
      )}
      {...props}
    />
  );
}
