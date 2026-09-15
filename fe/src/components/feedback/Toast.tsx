"use client";

import * as React from "react";
import { createPortal } from "react-dom";
import { cn } from "@/lib/cn";

/**
 * Toast — 화면 캡처에는 없지만 공통 요소로 정의합니다.
 * 스타일은 Callout 과 같은 팔레트, 먹색 카드 위에 톤 색 점.
 *
 * 사용:
 *   <ToastProvider> 를 layout 에 감싸고
 *   const toast = useToast(); toast({ title: "저장했습니다", tone: "success" });
 */
export type ToastTone = "neutral" | "success" | "warning" | "danger" | "info";

export interface ToastOptions {
  title: React.ReactNode;
  description?: React.ReactNode;
  tone?: ToastTone;
  /** ms, 기본 3500. 0 이면 자동 닫힘 없음 */
  duration?: number;
  action?: { label: string; onClick: () => void };
}

interface ToastItem extends ToastOptions {
  id: number;
}

const ToastContext = React.createContext<((opts: ToastOptions) => void) | null>(null);

export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [items, setItems] = React.useState<ToastItem[]>([]);
  const [mounted, setMounted] = React.useState(false);
  React.useEffect(() => setMounted(true), []);

  const dismiss = React.useCallback((id: number) => setItems((l) => l.filter((t) => t.id !== id)), []);

  const push = React.useCallback(
    (opts: ToastOptions) => {
      const id = Date.now() + Math.random();
      setItems((l) => [...l, { ...opts, id }].slice(-4));
      const d = opts.duration ?? 3500;
      if (d > 0) setTimeout(() => dismiss(id), d);
    },
    [dismiss],
  );

  return (
    <ToastContext.Provider value={push}>
      {children}
      {mounted &&
        createPortal(
          <div className="pointer-events-none fixed inset-x-0 bottom-6 z-[60] flex flex-col items-center gap-2 px-4">
            {items.map((t) => (
              <ToastCard key={t.id} item={t} onDismiss={() => dismiss(t.id)} />
            ))}
          </div>,
          document.body,
        )}
    </ToastContext.Provider>
  );
}

export function useToast() {
  const ctx = React.useContext(ToastContext);
  if (!ctx) throw new Error("useToast 는 <ToastProvider> 안에서만 사용할 수 있습니다.");
  return ctx;
}

const dotTone: Record<ToastTone, string> = {
  neutral: "bg-fg-disabled",
  success: "bg-success",
  warning: "bg-warning",
  danger: "bg-danger",
  info: "bg-info",
};

function ToastCard({ item, onDismiss }: { item: ToastItem; onDismiss: () => void }) {
  return (
    <div
      role="status"
      className={cn(
        "k-toast-in pointer-events-auto flex w-full max-w-[420px] items-start gap-3 rounded-lg bg-ink px-4 py-3 text-sm text-on-ink shadow-lg",
      )}
    >
      <span className={cn("mt-1.5 size-2 shrink-0 rounded-full", dotTone[item.tone ?? "neutral"])} />
      <div className="min-w-0 flex-1">
        <p className="font-semibold">{item.title}</p>
        {item.description && <p className="mt-0.5 text-[13px] text-on-ink/70">{item.description}</p>}
      </div>
      {item.action && (
        <button
          type="button"
          onClick={() => {
            item.action?.onClick();
            onDismiss();
          }}
          className="shrink-0 text-[13px] font-semibold text-primary-border hover:underline"
        >
          {item.action.label}
        </button>
      )}
      <button type="button" onClick={onDismiss} aria-label="닫기" className="-mr-1 shrink-0 rounded p-1 text-on-ink/60 hover:text-on-ink">
        <svg viewBox="0 0 16 16" className="size-3.5" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" aria-hidden>
          <path d="M4 4l8 8M12 4l-8 8" />
        </svg>
      </button>
    </div>
  );
}
