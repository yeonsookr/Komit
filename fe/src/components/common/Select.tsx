"use client";

import * as React from "react";
import { cn } from "@/lib/cn";
import { inputBase, inputSizes, type InputSize } from "./Input";

/* ------------------------------------------------------------------ */
/* NativeSelect — 폼 안 셀렉트 (14 시 / 00 분, 은행 선택, 양식 유형) */
/*   회색 채움(surface) 변형이 기본. 계약서 폼처럼 흰 배경일 땐 tone="outline" */
/* ------------------------------------------------------------------ */
export interface NativeSelectProps extends Omit<React.SelectHTMLAttributes<HTMLSelectElement>, "size"> {
  size?: InputSize;
  tone?: "surface" | "outline";
  invalid?: boolean;
  placeholder?: string;
}

export const NativeSelect = React.forwardRef<HTMLSelectElement, NativeSelectProps>(
  ({ size = "md", tone = "surface", invalid, placeholder, className, children, ...props }, ref) => (
    <div className="relative w-full">
      <select
        ref={ref}
        aria-invalid={invalid || undefined}
        defaultValue={placeholder && props.value === undefined && props.defaultValue === undefined ? "" : undefined}
        className={cn(
          inputBase,
          inputSizes[size],
          "appearance-none pr-9",
          tone === "surface" && "border-transparent bg-surface hover:bg-surface-2 hover:border-transparent focus:bg-bg",
          className,
        )}
        {...props}
      >
        {placeholder && (
          <option value="" disabled>
            {placeholder}
          </option>
        )}
        {children}
      </select>
      <Caret className="pointer-events-none absolute top-1/2 right-3 -translate-y-1/2 text-fg-subtle" />
    </div>
  ),
);
NativeSelect.displayName = "NativeSelect";

/* ------------------------------------------------------------------ */
/* Dropdown — 커스텀 메뉴 (펼침_ 화면).                                 */
/*   선택 항목 = 연보라 배경 + 보라 텍스트 + 우측 ✓                       */
/* ------------------------------------------------------------------ */
export interface DropdownOption<T extends string = string> {
  value: T;
  label: React.ReactNode;
  disabled?: boolean;
}

export interface DropdownProps<T extends string = string> {
  options: DropdownOption<T>[];
  value?: T;
  onChange?: (value: T) => void;
  /** 트리거 종류: field(인풋형) | text(정렬 실력 높은 순 ▾ 같은 텍스트형) | pill(분야 선택 ▾) */
  trigger?: "field" | "text" | "pill";
  placeholder?: string;
  /** text 트리거 앞에 붙는 회색 접두 라벨 (예: "정렬") */
  prefix?: React.ReactNode;
  align?: "start" | "end";
  size?: InputSize;
  disabled?: boolean;
  className?: string;
  menuClassName?: string;
}

export function Dropdown<T extends string = string>({
  options,
  value,
  onChange,
  trigger = "field",
  placeholder = "선택",
  prefix,
  align = "start",
  size = "md",
  disabled,
  className,
  menuClassName,
}: DropdownProps<T>) {
  const [open, setOpen] = React.useState(false);
  const rootRef = React.useRef<HTMLDivElement>(null);
  const selected = options.find((o) => o.value === value);

  React.useEffect(() => {
    if (!open) return;
    const onDoc = (e: MouseEvent) => {
      if (!rootRef.current?.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && setOpen(false);
    document.addEventListener("mousedown", onDoc);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onDoc);
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);

  const triggerCls = {
    field: cn(
      inputBase,
      inputSizes[size],
      "flex items-center justify-between gap-2 text-left",
      open && "border-primary bg-primary-soft-hover ring-2 ring-primary/15",
    ),
    text: cn(
      "inline-flex h-8 items-center gap-1 rounded-sm px-1 text-sm font-semibold text-primary hover:bg-primary-soft-hover",
    ),
    pill: cn(
      "inline-flex h-8 items-center gap-1 rounded-full border border-line bg-bg px-3 text-[13px] font-medium text-fg hover:bg-surface-2",
      open && "border-primary bg-primary-soft-hover text-primary",
    ),
  }[trigger];

  return (
    <div ref={rootRef} className={cn("relative", trigger === "field" ? "w-full" : "inline-block", className)}>
      <button
        type="button"
        disabled={disabled}
        aria-haspopup="listbox"
        aria-expanded={open}
        onClick={() => setOpen((v) => !v)}
        className={triggerCls}
      >
        {prefix && <span className="font-normal text-fg-subtle">{prefix}</span>}
        <span className={cn("truncate", !selected && trigger === "field" && "text-fg-disabled")}>
          {selected?.label ?? placeholder}
        </span>
        <Caret className={cn("shrink-0 text-fg-subtle transition-transform", open && "rotate-180", trigger === "text" && "text-primary")} />
      </button>

      {open && (
        <ul
          role="listbox"
          className={cn(
            "absolute z-40 mt-1.5 min-w-[170px] rounded-lg border border-line bg-bg p-1.5 shadow-md",
            trigger === "field" ? "w-full" : "",
            align === "end" ? "right-0" : "left-0",
            menuClassName,
          )}
        >
          {options.map((o) => {
            const active = o.value === value;
            return (
              <li key={o.value} role="option" aria-selected={active}>
                <button
                  type="button"
                  disabled={o.disabled}
                  onClick={() => {
                    onChange?.(o.value);
                    setOpen(false);
                  }}
                  className={cn(
                    "flex h-[38px] w-full items-center justify-between gap-3 rounded-sm px-3 text-left text-sm text-fg",
                    "hover:bg-surface disabled:text-fg-disabled disabled:hover:bg-transparent",
                    active && "bg-primary-soft font-semibold text-primary hover:bg-primary-soft",
                  )}
                >
                  <span className="truncate">{o.label}</span>
                  {active && <Check className="shrink-0" />}
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}

/* ------------------------------------------------------------------ */
export function Caret({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 16 16" className={cn("size-4", className)} fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
      <path d="m4 6 4 4 4-4" />
    </svg>
  );
}
export function Check({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 16 16" className={cn("size-4", className)} fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
      <path d="m3 8.5 3.2 3L13 4.5" />
    </svg>
  );
}
