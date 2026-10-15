"use client";

import * as React from "react";
import { cn } from "@/lib/cn";
import { buttonClass, type ButtonSize, type ButtonStyleOptions } from "./buttonStyles";

/**
 * Button — 클릭으로 동작하는 요소. 모양(variant·size)은 buttonStyles.ts 가 정합니다.
 * 이동만 하는 요소는 <Link className={buttonClass(...)}> 를 씁니다.
 */
export interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement>,
    Omit<ButtonStyleOptions, "className"> {
  /** 로딩 중이면 스피너 표시 + 클릭 비활성 */
  loading?: boolean;
  leftIcon?: React.ReactNode;
  rightIcon?: React.ReactNode;
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
