import * as React from "react";
import { cn } from "@/lib/cn";

/**
 * Card — 기본 컨테이너.
 *  variant
 *   - outline : 흰 배경 + 1px 테두리, r=12, 그림자 없음 (기본 카드, 계약서 조항)
 *   - surface : 회색 면(#f3f2f8), 테두리 없음 (요약 카드, 예치금 내역, 히어로 금액 카드)
 *   - soft    : 연보라 배경 + 보라 테두리 (강조: "뭐가 문제인지 몰라요", 오늘 세션 카드)
 *   - elevated: 흰 배경 + 그림자 (팝오버/모달 카드)
 */
export interface CardProps extends React.HTMLAttributes<HTMLDivElement> {
  variant?: "outline" | "surface" | "soft" | "elevated";
  padding?: "none" | "sm" | "md" | "lg";
  /** 클릭 가능한 카드(증상 카드 등) — hover 스타일 추가 */
  interactive?: boolean;
  /** 선택 상태 (interactive 와 함께) */
  selected?: boolean;
  as?: "div" | "section" | "article" | "li" | "button";
}

export const Card = React.forwardRef<HTMLDivElement, CardProps>(
  ({ variant = "outline", padding = "md", interactive, selected, as = "div", className, ...props }, ref) => {
    const Comp = as as React.ElementType;
    return (
      <Comp
        ref={ref}
        className={cn(
          "rounded-xl text-left",
          {
            outline: "border border-line bg-bg",
            surface: "bg-surface",
            soft: "border border-primary-border bg-primary-soft",
            elevated: "border border-line bg-bg shadow-md",
          }[variant],
          { none: "", sm: "p-4", md: "p-5", lg: "p-6" }[padding],
          interactive &&
            "cursor-pointer transition-colors hover:border-fg-disabled focus-visible:outline-2 focus-visible:outline-primary",
          selected && "border-primary bg-primary-soft text-primary hover:border-primary",
          className,
        )}
        {...props}
      />
    );
  },
);
Card.displayName = "Card";

/**
 * CardHeader — 좌 제목 + 우 메타/액션 ("요구사항  3건 · 합계 650,000원")
 */
export interface CardHeaderProps extends Omit<React.HTMLAttributes<HTMLDivElement>, "title"> {
  title: React.ReactNode;
  description?: React.ReactNode;
  /** 우측 슬롯: 메타 텍스트 또는 버튼 */
  action?: React.ReactNode;
  /** 제목 크기 */
  size?: "sm" | "md" | "lg";
}
export function CardHeader({ title, description, action, size = "md", className, ...props }: CardHeaderProps) {
  return (
    <div className={cn("flex items-start justify-between gap-4", className)} {...props}>
      <div className="min-w-0">
        <h3
          className={cn(
            "font-bold text-fg",
            { sm: "text-[15px]", md: "text-base", lg: "text-lg" }[size],
          )}
        >
          {title}
        </h3>
        {description && <p className="mt-0.5 text-[13px] text-fg-subtle">{description}</p>}
      </div>
      {action && <div className="shrink-0 text-[13px] text-fg-subtle">{action}</div>}
    </div>
  );
}

/**
 * CardSection — 카드 내부 구분 영역 (상단 hairline)
 */
export function CardSection({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return <div className={cn("border-t border-line-subtle pt-4 [&:not(:first-child)]:mt-4", className)} {...props} />;
}

/**
 * KeyValueRow — 계약서·설정 페이지의 라벨-값 행
 *   좌 라벨(회색 13px, 고정폭) + 우 값 + (선택) 우측 액션
 */
export interface KeyValueRowProps extends React.HTMLAttributes<HTMLDivElement> {
  label: React.ReactNode;
  value?: React.ReactNode;
  action?: React.ReactNode;
  /** 값에 monospace 적용 (금액, 날짜) */
  mono?: boolean;
  labelWidth?: number;
}
export function KeyValueRow({ label, value, action, mono, labelWidth = 100, className, children, ...props }: KeyValueRowProps) {
  return (
    <div
      className={cn(
        "flex items-start gap-4 py-3.5 text-sm [&:not(:last-child)]:border-b [&:not(:last-child)]:border-line-subtle",
        className,
      )}
      {...props}
    >
      <span className="shrink-0 text-[13px] text-fg-subtle" style={{ width: labelWidth }}>
        {label}
      </span>
      <span className={cn("min-w-0 flex-1 text-fg", mono && "font-mono tabular-nums")}>{value ?? children}</span>
      {action && <span className="-my-2 flex shrink-0 items-center">{action}</span>}
    </div>
  );
}

/**
 * StatCard — 큰 숫자 카드 (계약이 끝나면 받을 돈 650,000원)
 */
export interface StatCardProps extends React.HTMLAttributes<HTMLDivElement> {
  label: React.ReactNode;
  value: React.ReactNode;
  unit?: React.ReactNode;
  /** 값 색상 */
  tone?: "default" | "info" | "danger" | "primary";
  badge?: React.ReactNode;
  description?: React.ReactNode;
  size?: "md" | "lg";
  variant?: CardProps["variant"];
}
export function StatCard({
  label,
  value,
  unit,
  tone = "default",
  badge,
  description,
  size = "lg",
  variant = "surface",
  className,
  ...props
}: StatCardProps) {
  return (
    <Card variant={variant} padding={size === "lg" ? "lg" : "md"} className={className} {...props}>
      <div className="flex items-start justify-between gap-3">
        <p className="text-[13px] font-semibold text-fg-secondary">{label}</p>
        {badge}
      </div>
      <p
        className={cn(
          "mt-2 font-mono font-bold tracking-wide tabular-nums",
          size === "lg" ? "text-[44px] leading-none" : "text-xl leading-tight",
          { default: "text-fg", info: "text-info", danger: "text-danger", primary: "text-primary" }[tone],
        )}
      >
        {value}
        {unit && <span className={cn("ml-1 font-sans font-semibold text-fg-muted", size === "lg" ? "text-base" : "text-xs")}>{unit}</span>}
      </p>
      {description && (
        <p className={cn("text-[13px] text-fg-muted", size === "lg" ? "mt-5 border-t border-line pt-4" : "mt-2")}>{description}</p>
      )}
    </Card>
  );
}
