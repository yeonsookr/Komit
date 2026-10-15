import * as React from "react";
import { cn } from "@/lib/cn";

/**
 * Callout — 인라인 안내/경고 배너. 테두리 없음, r=10.
 *  tone
 *   - info    : 연보라 (계약 진행 중 문제가 생겼나요?, 상담 전환 안내)
 *   - neutral : 회색   (어떤 알림을 받을지는 마이페이지 › 알림에서…, 등록 절차)
 *   - warning : 연황   (통과시키면 그 작업은 완료로 확정되고…, 등록한 계좌가 없습니다)
 *   - danger  : 연분홍 (지금은 탈퇴할 수 없습니다)
 *   - success : 연녹
 */
export type CalloutTone = "info" | "neutral" | "warning" | "danger" | "success";

export interface CalloutProps extends Omit<React.HTMLAttributes<HTMLDivElement>, "title"> {
  tone?: CalloutTone;
  title?: React.ReactNode;
  /** 우측 액션 슬롯 (버튼/링크) */
  action?: React.ReactNode;
  icon?: React.ReactNode;
  size?: "sm" | "md";
}

const tones: Record<CalloutTone, { bg: string; title: string; body: string }> = {
  info: { bg: "bg-primary-soft", title: "text-primary", body: "text-fg-muted" },
  neutral: { bg: "bg-surface", title: "text-fg", body: "text-fg-muted" },
  warning: { bg: "bg-warning-bg", title: "text-warning", body: "text-fg-muted" },
  danger: { bg: "bg-danger-bg", title: "text-danger", body: "text-fg-muted" },
  success: { bg: "bg-success-bg", title: "text-success", body: "text-fg-muted" },
};

export function Callout({ tone = "info", title, action, icon, size = "md", className, children, ...props }: CalloutProps) {
  const t = tones[tone];
  return (
    <div
      role={tone === "danger" || tone === "warning" ? "alert" : "note"}
      className={cn(
        "flex items-start gap-3 rounded-lg",
        size === "sm" ? "px-3.5 py-2.5 text-[13px]" : "px-4 py-3.5 text-sm",
        t.bg,
        className,
      )}
      {...props}
    >
      {icon && <span className={cn("mt-0.5 shrink-0 [&>svg]:size-4", t.title)}>{icon}</span>}
      <div className="min-w-0 flex-1">
        {title && <p className={cn("font-semibold", t.title)}>{title}</p>}
        {children && <div className={cn(title && "mt-0.5", t.body)}>{children}</div>}
      </div>
      {action && <div className="shrink-0 self-center">{action}</div>}
    </div>
  );
}

/**
 * HelpRow — "확인이 어려우면 · 무료로 물어보기" 같은 좌 라벨 + 본문 + 우 버튼 행
 */
export interface HelpRowProps extends React.HTMLAttributes<HTMLDivElement> {
  label: React.ReactNode;
  action?: React.ReactNode;
  boxed?: boolean;
}
export function HelpRow({ label, action, boxed, className, children, ...props }: HelpRowProps) {
  return (
    <div
      className={cn(
        "flex items-center gap-4 text-sm",
        boxed ? "rounded-lg bg-surface px-4 py-3" : "py-2",
        className,
      )}
      {...props}
    >
      <span className="w-24 shrink-0 text-[13px] text-fg-subtle">{label}</span>
      <span className="min-w-0 flex-1 text-fg-muted">{children}</span>
      {action && <span className="shrink-0">{action}</span>}
    </div>
  );
}

/**
 * EmptyState — 빈 상태. 화면에서는 일러스트 없이 텍스트 + 액션으로만 표현
 */
export interface EmptyStateProps extends Omit<React.HTMLAttributes<HTMLDivElement>, "title"> {
  title: React.ReactNode;
  description?: React.ReactNode;
  action?: React.ReactNode;
}
export function EmptyState({ title, description, action, className, ...props }: EmptyStateProps) {
  return (
    <div className={cn("flex flex-col items-center gap-2 rounded-xl bg-surface px-6 py-10 text-center", className)} {...props}>
      <p className="text-sm font-semibold text-fg">{title}</p>
      {description && <p className="max-w-sm text-[13px] text-fg-muted">{description}</p>}
      {action && <div className="mt-2">{action}</div>}
    </div>
  );
}
