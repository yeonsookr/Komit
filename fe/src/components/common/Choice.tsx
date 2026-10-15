"use client";

import * as React from "react";
import { cn } from "@/lib/cn";
import { Check } from "./Select";

/* ------------------------------------------------------------------ */
/* Checkbox — 18px, r=4. 체크 시 보라 채움. locked 는 회색 채움(확정된 문장) */
/* ------------------------------------------------------------------ */
export interface CheckboxProps extends Omit<React.InputHTMLAttributes<HTMLInputElement>, "type" | "size"> {
  label?: React.ReactNode;
  description?: React.ReactNode;
  /** 확정·잠김 상태: 체크된 채로 회색 표시 */
  locked?: boolean;
}

export const Checkbox = React.forwardRef<HTMLInputElement, CheckboxProps>(
  ({ label, description, locked, className, disabled, ...props }, ref) => {
    const id = React.useId();
    return (
      <label
        htmlFor={props.id ?? id}
        className={cn(
          "group inline-flex items-start gap-2.5 text-sm text-fg",
          disabled ? "cursor-not-allowed text-fg-disabled" : "cursor-pointer",
          className,
        )}
      >
        <span className="relative mt-0.5 inline-flex size-[18px] shrink-0">
          <input
            ref={ref}
            id={props.id ?? id}
            type="checkbox"
            disabled={disabled}
            className="peer absolute inset-0 size-full cursor-[inherit] appearance-none rounded-xs border border-line bg-bg transition-colors checked:border-primary checked:bg-primary hover:border-fg-disabled checked:hover:border-primary disabled:bg-surface data-[locked=true]:checked:border-fg-disabled data-[locked=true]:checked:bg-fg-disabled"
            data-locked={locked || undefined}
            {...props}
          />
          <Check className="pointer-events-none absolute inset-0 m-auto size-3 text-on-primary opacity-0 peer-checked:opacity-100 [&_path]:stroke-[2.5]" />
        </span>
        {(label || description) && (
          <span className="flex flex-col gap-0.5">
            {label && <span className={cn(locked && "text-fg-subtle line-through")}>{label}</span>}
            {description && <span className="text-[13px] text-fg-subtle">{description}</span>}
          </span>
        )}
      </label>
    );
  },
);
Checkbox.displayName = "Checkbox";

/* ------------------------------------------------------------------ */
/* Radio — 20px 원형 */
/* ------------------------------------------------------------------ */
export interface RadioProps extends Omit<React.InputHTMLAttributes<HTMLInputElement>, "type" | "size"> {
  label?: React.ReactNode;
  description?: React.ReactNode;
}

export const Radio = React.forwardRef<HTMLInputElement, RadioProps>(
  ({ label, description, className, disabled, ...props }, ref) => {
    const id = React.useId();
    return (
      <label
        htmlFor={props.id ?? id}
        className={cn(
          "inline-flex items-start gap-2.5 text-sm text-fg",
          disabled ? "cursor-not-allowed text-fg-disabled" : "cursor-pointer",
          className,
        )}
      >
        <input
          ref={ref}
          id={props.id ?? id}
          type="radio"
          disabled={disabled}
          className="mt-px size-5 shrink-0 cursor-[inherit] appearance-none rounded-full border border-line bg-bg transition-colors checked:border-[6px] checked:border-primary hover:border-fg-disabled checked:hover:border-primary disabled:bg-surface"
          {...props}
        />
        {(label || description) && (
          <span className="flex flex-col gap-0.5">
            {label}
            {description && <span className="text-[13px] text-fg-subtle">{description}</span>}
          </span>
        )}
      </label>
    );
  },
);
Radio.displayName = "Radio";

/* ------------------------------------------------------------------ */
/* ChoiceCard — 설문/진단의 라디오 카드·체크박스 카드, 결제수단 선택      */
/*   선택 시: 연보라 배경 + 보라 테두리 + 보라 라벨                       */
/* ------------------------------------------------------------------ */
export interface ChoiceCardProps extends Omit<React.InputHTMLAttributes<HTMLInputElement>, "size"> {
  type?: "radio" | "checkbox";
  label: React.ReactNode;
  /** 우측 보조 텍스트 (예: "일시불") */
  meta?: React.ReactNode;
  /** 라디오/체크 표시를 숨기고 pill 처럼 (소형 체크박스 pill: 속도가 느림 / 없음) */
  compact?: boolean;
}

export const ChoiceCard = React.forwardRef<HTMLInputElement, ChoiceCardProps>(
  ({ type = "radio", label, meta, compact, className, checked, disabled, ...props }, ref) => {
    return (
      <label
        className={cn(
          "group relative flex items-center gap-3 rounded-md border border-line bg-bg text-sm font-medium text-fg transition-colors",
          "has-[:checked]:border-primary has-[:checked]:bg-primary-soft has-[:checked]:text-primary",
          "has-[:focus-visible]:ring-2 has-[:focus-visible]:ring-primary/25",
          disabled ? "cursor-not-allowed opacity-60" : "cursor-pointer hover:border-fg-disabled has-[:checked]:hover:border-primary",
          compact ? "h-9 px-3.5 text-[13px]" : "min-h-12 px-4 py-3",
          className,
        )}
      >
        <input
          ref={ref}
          type={type}
          checked={checked}
          disabled={disabled}
          className="peer sr-only"
          {...props}
        />
        {!compact && (
          <span
            className={cn(
              "flex size-5 shrink-0 items-center justify-center border border-line bg-bg transition-colors",
              "peer-checked:border-primary peer-checked:bg-primary",
              type === "radio" ? "rounded-full peer-checked:border-[6px] peer-checked:bg-bg" : "rounded-xs",
            )}
          >
            {type === "checkbox" && <Check className="size-3 text-on-primary opacity-0 group-has-[:checked]:opacity-100 [&_path]:stroke-[2.5]" />}
          </span>
        )}
        <span className="flex-1">{label}</span>
        {meta && <span className="text-[13px] font-normal text-fg-subtle group-has-[:checked]:text-primary/70">{meta}</span>}
      </label>
    );
  },
);
ChoiceCard.displayName = "ChoiceCard";

/* ------------------------------------------------------------------ */
/* Switch — 토글 (공개/비공개, 알림 on/off). 36×20 pill                */
/* ------------------------------------------------------------------ */
export interface SwitchProps extends Omit<React.ButtonHTMLAttributes<HTMLButtonElement>, "onChange"> {
  checked: boolean;
  onChange?: (checked: boolean) => void;
  label?: React.ReactNode;
}

export function Switch({ checked, onChange, label, disabled, className, ...props }: SwitchProps) {
  return (
    <label className={cn("inline-flex items-center gap-2 text-sm", disabled ? "cursor-not-allowed opacity-60" : "cursor-pointer", className)}>
      <button
        type="button"
        role="switch"
        aria-checked={checked}
        disabled={disabled}
        onClick={() => onChange?.(!checked)}
        className={cn(
          "relative h-5 w-9 shrink-0 rounded-full transition-colors duration-150",
          checked ? "bg-primary" : "bg-fg-disabled",
        )}
        {...props}
      >
        <span
          className={cn(
            "absolute top-0.5 left-0.5 size-4 rounded-full bg-bg shadow-sm transition-transform duration-150",
            checked && "translate-x-4",
          )}
        />
      </button>
      {label && <span className={checked ? "text-fg" : "text-fg-subtle"}>{label}</span>}
    </label>
  );
}

/* ------------------------------------------------------------------ */
/* RatingScale — 리뷰의 1~5 라디오 스케일 (별 아이콘 없음)               */
/* ------------------------------------------------------------------ */
export interface RatingScaleProps {
  name: string;
  value?: number;
  onChange?: (v: number) => void;
  /** 5개 라벨 (매우 아쉬움 · 아쉬움 · 보통 · 좋음 · 매우 좋음) */
  labels?: [string, string, string, string, string];
  className?: string;
}

export function RatingScale({ name, value, onChange, labels, className }: RatingScaleProps) {
  return (
    <div role="radiogroup" className={cn("grid grid-cols-5 gap-2", className)}>
      {[1, 2, 3, 4, 5].map((n, i) => {
        const active = value === n;
        return (
          <label
            key={n}
            className={cn(
              "flex cursor-pointer flex-col items-center gap-1.5 rounded-md py-3 transition-colors hover:bg-surface-2",
              active && "bg-primary-soft hover:bg-primary-soft",
            )}
          >
            <input type="radio" name={name} value={n} checked={active} onChange={() => onChange?.(n)} className="sr-only" />
            <span
              className={cn(
                "flex size-6 items-center justify-center rounded-full border text-[13px] font-semibold font-mono",
                active ? "border-primary bg-primary text-on-primary" : "border-line text-fg-secondary",
              )}
            >
              {n}
            </span>
            {labels && <span className={cn("whitespace-nowrap text-xs", active ? "font-semibold text-primary" : "text-fg-subtle")}>{labels[i]}</span>}
          </label>
        );
      })}
    </div>
  );
}
