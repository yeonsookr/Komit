import * as React from "react";
import { cn } from "@/lib/cn";

/**
 * Badge — 상태/카테고리/유형 표시.
 * 화면 전체에서 같은 5색 팔레트를 공유합니다.
 *
 *  tone     | 사용 예
 *  ---------|-----------------------------------------------------------
 *  brand    | 진행 중, 개발(방 유형), 결제(카테고리), 활성 nav
 *  neutral  | 예치 대기, 착수 전, 공지, 없음, 기타, 낮음
 *  success  | 완료, 상담(방 유형), ● 확인됨, 4건 중 1건 통과, 지급 완료
 *  warning  | 컨설팅(방 유형), 위법, 보통, ● 확인 중, 9월 5일 자동 통과, 9:42 남음
 *  danger   | 보안, 높음, 진행중(분쟁), 계약 전
 *  info     | 수입 관련 파랑
 *  ink      | 운영 콘솔(로그인), 선택된 토글 칩
 */
export type BadgeTone = "brand" | "neutral" | "success" | "warning" | "danger" | "info" | "ink";

export interface BadgeProps extends React.HTMLAttributes<HTMLSpanElement> {
  tone?: BadgeTone;
  /** pill(둥근) | square(r=6, 카테고리/방 유형 배지) */
  shape?: "pill" | "square";
  size?: "sm" | "md";
  /** 앞에 상태 점 표시 (● 확인됨) */
  dot?: boolean;
  /** 배경 없이 점 + 텍스트만 */
  plain?: boolean;
}

const tones: Record<BadgeTone, { bg: string; text: string; dot: string }> = {
  brand: { bg: "bg-primary-soft", text: "text-primary", dot: "bg-primary" },
  neutral: { bg: "bg-surface", text: "text-fg-muted", dot: "bg-fg-disabled" },
  success: { bg: "bg-success-bg", text: "text-success", dot: "bg-success" },
  warning: { bg: "bg-warning-bg", text: "text-warning", dot: "bg-warning" },
  danger: { bg: "bg-danger-bg", text: "text-danger", dot: "bg-danger" },
  info: { bg: "bg-info-bg", text: "text-info", dot: "bg-info" },
  ink: { bg: "bg-ink", text: "text-on-ink", dot: "bg-on-ink" },
};

export function Badge({
  tone = "neutral",
  shape = "pill",
  size = "md",
  dot,
  plain,
  className,
  children,
  ...props
}: BadgeProps) {
  const t = tones[tone];
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1.5 font-semibold whitespace-nowrap leading-none",
        size === "sm" ? "h-5 px-2 text-[11px]" : "h-6 px-2.5 text-xs",
        shape === "pill" ? "rounded-full" : "rounded-sm",
        plain ? "bg-transparent px-0" : t.bg,
        t.text,
        className,
      )}
      {...props}
    >
      {dot && <span className={cn("size-1.5 shrink-0 rounded-full", t.dot)} />}
      {children}
    </span>
  );
}

/**
 * CountBadge — 알림 벨 위 숫자, 탭 안 카운트 (채팅 6)
 */
export function CountBadge({ count, className, max = 99 }: { count: number; className?: string; max?: number }) {
  if (count <= 0) return null;
  return (
    <span
      className={cn(
        "inline-flex h-[18px] min-w-[18px] items-center justify-center rounded-full bg-primary px-1 font-mono text-[11px] font-bold text-on-primary tabular-nums",
        className,
      )}
    >
      {count > max ? `${max}+` : count}
    </span>
  );
}

/**
 * Tag — 기술 스택 / 스킬 태그 (회색 pill, 삭제 가능)
 */
export interface TagProps extends React.HTMLAttributes<HTMLSpanElement> {
  onRemove?: () => void;
}
export function Tag({ onRemove, className, children, ...props }: TagProps) {
  return (
    <span
      className={cn(
        "inline-flex h-7 items-center gap-1 rounded-md bg-surface px-2.5 text-xs font-medium text-fg-secondary",
        className,
      )}
      {...props}
    >
      {children}
      {onRemove && (
        <button
          type="button"
          onClick={onRemove}
          aria-label="삭제"
          className="-mr-1 ml-0.5 flex size-4 items-center justify-center rounded-full text-fg-subtle hover:bg-line hover:text-fg"
        >
          <svg viewBox="0 0 12 12" className="size-2.5" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" aria-hidden>
            <path d="M3 3l6 6M9 3l-6 6" />
          </svg>
        </button>
      )}
    </span>
  );
}

/**
 * StatusDot — 리스트 행 좌측 상태 점 (진행 상황: 완료 녹 / 대기 황 / 미래 회)
 */
export function StatusDot({ tone = "neutral", className }: { tone?: BadgeTone; className?: string }) {
  return <span className={cn("inline-block size-2 shrink-0 rounded-full", tones[tone].dot, className)} aria-hidden />;
}
