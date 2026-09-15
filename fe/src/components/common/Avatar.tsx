import * as React from "react";
import { cn } from "@/lib/cn";

/**
 * Avatar — 원형, 연회색 배경 + 한 글자 이니셜.
 *  GNB 32 / 채팅 28~32 / 리스트 44 / 프로필 72
 */
export interface AvatarProps extends React.HTMLAttributes<HTMLSpanElement> {
  /** 표시할 이름. 이미지가 없으면 첫 글자를 이니셜로 사용 */
  name?: string;
  src?: string;
  size?: "xs" | "sm" | "md" | "lg" | "xl";
  /** 우하단 온라인/라이브 점 */
  status?: "online" | "live";
}

const sizes = {
  xs: "size-6 text-[11px]",
  sm: "size-7 text-xs",
  md: "size-8 text-[13px]",
  lg: "size-11 text-base",
  xl: "size-[72px] text-2xl",
};

export function Avatar({ name, src, size = "md", status, className, ...props }: AvatarProps) {
  const initial = name?.trim().charAt(0) ?? "";
  return (
    <span
      className={cn(
        "relative inline-flex shrink-0 items-center justify-center overflow-hidden rounded-full bg-surface font-semibold text-fg-muted select-none",
        sizes[size],
        className,
      )}
      aria-label={name}
      role={name ? "img" : undefined}
      {...props}
    >
      {src ? (
        // eslint-disable-next-line @next/next/no-img-element
        <img src={src} alt={name ?? ""} className="size-full object-cover" />
      ) : (
        initial
      )}
      {status && (
        <span
          className={cn(
            "absolute right-0 bottom-0 size-2.5 rounded-full ring-2 ring-bg",
            status === "online" ? "bg-success" : "bg-danger",
          )}
        />
      )}
    </span>
  );
}

/**
 * RatingDots — 5개 도트 평점 (●●●●○) + 숫자.
 *  실력 = 진회색 도트, 설명력 = 보라 도트
 */
export interface RatingDotsProps extends React.HTMLAttributes<HTMLDivElement> {
  label?: React.ReactNode;
  /** 0 ~ 5 */
  value: number;
  /** 우측 숫자 (예: 4.4). 미지정 시 value 를 소수 1자리로 */
  score?: string | number;
  /** 건수 (13건) */
  count?: number;
  tone?: "ink" | "primary";
}
export function RatingDots({ label, value, score, count, tone = "ink", className, ...props }: RatingDotsProps) {
  const filled = Math.round(Math.min(5, Math.max(0, value)));
  const color = tone === "primary" ? "text-primary" : "text-fg";
  return (
    <div className={cn("flex items-center gap-2 text-[13px]", className)} {...props}>
      {label && <span className={cn("w-10 shrink-0", tone === "primary" ? "text-primary" : "text-fg-subtle")}>{label}</span>}
      <span className="flex items-center gap-[3px]" aria-hidden>
        {Array.from({ length: 5 }).map((_, i) => (
          <span
            key={i}
            className={cn(
              "size-1.5 rounded-full",
              i < filled ? (tone === "primary" ? "bg-primary" : "bg-fg-secondary") : "bg-line",
            )}
          />
        ))}
      </span>
      <span className={cn("ml-auto font-mono font-semibold tabular-nums", color)}>
        {score ?? value.toFixed(1)}
      </span>
      {count !== undefined && <span className="text-xs text-fg-subtle">{count}건</span>}
    </div>
  );
}
