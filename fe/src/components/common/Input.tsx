"use client";

import * as React from "react";
import { cn } from "@/lib/cn";

/* ------------------------------------------------------------------ */
/* Field — 라벨/헬퍼/에러 래퍼. 가로형(계약서·계좌 폼)과 세로형(설문) 모두 지원 */
/* ------------------------------------------------------------------ */
export interface FieldProps {
  label?: React.ReactNode;
  /** 라벨 배치: vertical(위) | horizontal(좌측 고정폭 100px) */
  layout?: "vertical" | "horizontal";
  helper?: React.ReactNode;
  error?: React.ReactNode;
  required?: boolean;
  htmlFor?: string;
  className?: string;
  children: React.ReactNode;
}

export function Field({
  label,
  layout = "vertical",
  helper,
  error,
  required,
  htmlFor,
  className,
  children,
}: FieldProps) {
  const labelEl = label && (
    <label
      htmlFor={htmlFor}
      className={cn(
        "text-[13px] font-semibold text-fg-secondary",
        layout === "horizontal" && "font-medium text-fg-subtle sm:w-[100px] sm:shrink-0 sm:pt-2.5",
      )}
    >
      {label}
      {required && <span className="ml-0.5 text-danger">*</span>}
    </label>
  );
  const message = (error || helper) && (
    <p className={cn("mt-1.5 text-[13px]", error ? "text-danger" : "text-fg-subtle")}>
      {error ?? helper}
    </p>
  );

  if (layout === "horizontal") {
    return (
      <div className={cn("flex flex-col gap-1.5 sm:flex-row sm:items-start sm:gap-4", className)}>
        {labelEl}
        <div className="min-w-0 flex-1">
          {children}
          {message}
        </div>
      </div>
    );
  }
  return (
    <div className={cn("flex flex-col gap-1.5", className)}>
      {labelEl}
      {children}
      {message}
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* 공통 인풋 스타일 */
/* ------------------------------------------------------------------ */
export const inputBase =
  "w-full rounded-md border border-line bg-bg text-sm text-fg placeholder:text-fg-disabled " +
  "transition-colors duration-150 outline-none " +
  "hover:border-fg-disabled focus:border-primary focus:ring-2 focus:ring-primary/15 " +
  "disabled:bg-surface disabled:text-fg-disabled disabled:cursor-not-allowed " +
  "aria-[invalid=true]:border-danger aria-[invalid=true]:focus:ring-danger/15";

export type InputSize = "sm" | "md" | "lg";
export const inputSizes: Record<InputSize, string> = {
  sm: "h-9 px-3 text-[13px]",
  md: "h-10 px-3.5",
  lg: "h-12 px-4 text-[15px]",
};

/* ------------------------------------------------------------------ */
/* Input */
/* ------------------------------------------------------------------ */
export interface InputProps extends Omit<React.InputHTMLAttributes<HTMLInputElement>, "size"> {
  size?: InputSize;
  invalid?: boolean;
  /** 좌측 아이콘 (검색 돋보기 등) */
  leftIcon?: React.ReactNode;
  /** 우측 접미사 (원, 일, 시 등 단위) */
  suffix?: React.ReactNode;
  /** 숫자·금액 입력: monospace + 우측 정렬 */
  mono?: boolean;
  /** 밑줄형 (프로필 요금 설정) */
  underline?: boolean;
  /** leftIcon/suffix 사용 시 감싸는 래퍼의 클래스 (폭 지정은 여기에: "w-28") */
  containerClassName?: string;
}

export const Input = React.forwardRef<HTMLInputElement, InputProps>(
  ({ size = "md", invalid, leftIcon, suffix, mono, underline, className, containerClassName, ...props }, ref) => {
    const hasAddon = leftIcon || suffix;
    const inputEl = (
      <input
        ref={ref}
        aria-invalid={invalid || undefined}
        className={cn(
          inputBase,
          inputSizes[size],
          mono && "font-mono tabular-nums text-right",
          underline &&
            "rounded-none border-x-0 border-t-0 border-b bg-transparent px-0 focus:ring-0 hover:border-line",
          leftIcon && "pl-10",
          suffix && "pr-10",
          className,
        )}
        {...props}
      />
    );
    if (!hasAddon) return inputEl;
    return (
      <div className={cn("relative w-full", containerClassName)}>
        {leftIcon && (
          <span className="pointer-events-none absolute inset-y-0 left-3.5 flex items-center text-fg-subtle [&>svg]:size-4">
            {leftIcon}
          </span>
        )}
        {inputEl}
        {suffix && (
          <span className="pointer-events-none absolute inset-y-0 right-3.5 flex items-center text-[13px] text-fg-subtle">
            {suffix}
          </span>
        )}
      </div>
    );
  },
);
Input.displayName = "Input";

/* ------------------------------------------------------------------ */
/* Textarea — 우하단 글자수 카운터 (0 / 255) 지원 */
/* ------------------------------------------------------------------ */
export interface TextareaProps extends React.TextareaHTMLAttributes<HTMLTextAreaElement> {
  invalid?: boolean;
  /** maxLength 와 함께 쓰면 `n / max` 카운터 표시 */
  showCount?: boolean;
  /** 연회색 배경 변형 (문제 신고 폼) */
  tone?: "default" | "surface";
}

export const Textarea = React.forwardRef<HTMLTextAreaElement, TextareaProps>(
  ({ invalid, showCount, tone = "default", maxLength, className, value, defaultValue, onChange, ...props }, ref) => {
    const [len, setLen] = React.useState(String(value ?? defaultValue ?? "").length);
    React.useEffect(() => {
      if (value !== undefined) setLen(String(value).length);
    }, [value]);
    return (
      <div className="relative w-full">
        <textarea
          ref={ref}
          aria-invalid={invalid || undefined}
          maxLength={maxLength}
          value={value}
          defaultValue={defaultValue}
          onChange={(e) => {
            setLen(e.target.value.length);
            onChange?.(e);
          }}
          className={cn(
            inputBase,
            "min-h-[100px] resize-y px-3.5 py-2.5 leading-relaxed",
            tone === "surface" && "border-transparent bg-surface-2 focus:bg-bg",
            showCount && "pb-7",
            className,
          )}
          {...props}
        />
        {showCount && (
          <span className="pointer-events-none absolute right-3 bottom-2.5 font-mono text-xs tabular-nums text-fg-subtle">
            {len}
            {maxLength ? ` / ${maxLength}` : ""}
          </span>
        )}
      </div>
    );
  },
);
Textarea.displayName = "Textarea";

/* ------------------------------------------------------------------ */
/* SearchInput — 랜딩/개발자찾기 검색바 (돋보기 + 큰 높이) */
/* ------------------------------------------------------------------ */
export const SearchInput = React.forwardRef<HTMLInputElement, Omit<InputProps, "leftIcon">>(
  ({ size = "lg", className, ...props }, ref) => (
    <Input
      ref={ref}
      type="search"
      size={size}
      leftIcon={<SearchIcon />}
      className={cn("rounded-lg", className)}
      {...props}
    />
  ),
);
SearchInput.displayName = "SearchInput";

function SearchIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden>
      <circle cx="11" cy="11" r="7" />
      <path d="m20 20-3.5-3.5" />
    </svg>
  );
}
