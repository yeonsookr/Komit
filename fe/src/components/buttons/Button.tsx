"use client";

import * as React from "react";
import { cn } from "@/lib/cn";

/**
 * Button
 * 화면에서 관찰된 5가지 변형:
 *  - primary : 보라 solid  (문제 진단하기, 상담 시작하기, 다음 →, 입장, 리뷰 남기기)
 *  - dark    : 먹색 solid  (보내기, 저장하기, 서명하고 계약하기, 예치하기, 신고하기)
 *  - outline : 흰 배경 + 회색 테두리 (취소, 수정 제안하기, 사진 바꾸기, 파일 고르기)
 *  - ghost   : 텍스트만, 보라 (+ 항목 추가, 보기 ›, 모두 읽음으로)
 *  - danger  : 연분홍 배경 + 빨간 테두리/텍스트 (문제 신고, 세션 종료, 탈퇴하기)
 */
export type ButtonVariant = "primary" | "dark" | "outline" | "ghost" | "danger";
export type ButtonSize = "sm" | "md" | "lg" | "xl";

export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  /** 가로 전체 채움 (스티키 CTA, 카드 하단 버튼) */
  fullWidth?: boolean;
  /** 로딩 중이면 스피너 표시 + 클릭 비활성 */
  loading?: boolean;
  leftIcon?: React.ReactNode;
  rightIcon?: React.ReactNode;
}

const base =
  "inline-flex items-center justify-center gap-1.5 whitespace-nowrap font-semibold " +
  "rounded-md transition-colors duration-150 select-none " +
  "disabled:cursor-not-allowed disabled:bg-surface disabled:text-fg-disabled disabled:border-transparent " +
  "focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary";

const variants: Record<ButtonVariant, string> = {
  primary: "bg-primary text-on-primary hover:bg-primary-hover active:bg-primary-active",
  dark: "bg-ink text-on-ink hover:bg-ink-hover",
  outline:
    "bg-bg text-fg border border-line hover:bg-surface-2 active:bg-surface " +
    "disabled:bg-bg disabled:border-line",
  ghost:
    "bg-transparent text-primary hover:bg-primary-soft-hover active:bg-primary-soft px-2 " +
    "disabled:bg-transparent",
  danger:
    "bg-danger-bg-strong text-danger border border-danger-border hover:bg-danger-bg " +
    "disabled:bg-bg",
};

const sizes: Record<ButtonSize, string> = {
  sm: "h-8 px-3 text-[13px]",
  md: "h-10 px-4 text-sm",
  lg: "h-12 px-5 text-[15px]",
  xl: "h-14 px-6 text-base",
};

/**
 * 버튼 모양 클래스만 뽑아 씁니다 — 링크(`<Link>`, `<a>`)를 버튼처럼 보이게 할 때.
 * `<Link><Button/></Link>` 처럼 인터랙티브 요소를 겹치지 않기 위한 출구입니다.
 *
 *   <Link href={ROUTES.developer(id)} className={buttonClass({ fullWidth: true })}>상담 시작하기</Link>
 */
export function buttonClass({
  variant = "primary",
  size = "md",
  fullWidth,
  className,
}: Pick<ButtonProps, "variant" | "size" | "fullWidth" | "className"> = {}): string {
  return cn(base, variants[variant], sizes[size], fullWidth && "w-full", className);
}

export const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  (
    {
      variant = "primary",
      size = "md",
      fullWidth,
      loading,
      leftIcon,
      rightIcon,
      className,
      children,
      disabled,
      type = "button",
      ...props
    },
    ref,
  ) => {
    return (
      <button
        ref={ref}
        type={type}
        disabled={disabled || loading}
        aria-busy={loading || undefined}
        className={buttonClass({ variant, size, fullWidth, className })}
        {...props}
      >
        {loading ? <Spinner /> : leftIcon}
        {children}
        {!loading && rightIcon}
      </button>
    );
  },
);
Button.displayName = "Button";

function Spinner() {
  return (
    <svg
      className="size-4 animate-spin"
      viewBox="0 0 24 24"
      fill="none"
      aria-hidden
    >
      <circle cx="12" cy="12" r="9" stroke="currentColor" strokeOpacity="0.25" strokeWidth="3" />
      <path d="M21 12a9 9 0 0 0-9-9" stroke="currentColor" strokeWidth="3" strokeLinecap="round" />
    </svg>
  );
}

/**
 * IconButton — 아이콘만 있는 정사각 버튼 (행 삭제 ✕, 월 이동 ‹ ›, 닫기)
 */
export interface IconButtonProps extends Omit<ButtonProps, "leftIcon" | "rightIcon" | "fullWidth"> {
  /** 접근성 라벨 (필수) */
  "aria-label": string;
}

export const IconButton = React.forwardRef<HTMLButtonElement, IconButtonProps>(
  ({ variant = "ghost", size = "md", className, children, ...props }, ref) => {
    const sq: Record<ButtonSize, string> = {
      sm: "size-8",
      md: "size-10",
      lg: "size-12",
      xl: "size-14",
    };
    return (
      <Button
        ref={ref}
        variant={variant}
        size={size}
        className={cn("px-0", sq[size], variant === "ghost" && "text-fg-subtle hover:text-fg", className)}
        {...props}
      >
        {children}
      </Button>
    );
  },
);
IconButton.displayName = "IconButton";
