"use client";

import * as React from "react";
import { createPortal } from "react-dom";
import { cn } from "@/lib/cn";
import { IconButton } from "@/components/buttons";

/**
 * Modal — 오버레이 + 중앙 카드 (서명 모달 기준: r=14, 폭 420~560, 그림자)
 *  구조: ModalHeader(제목 + 서브) / ModalBody / ModalFooter(우측 정렬 버튼: 취소 → 확인)
 */
export interface ModalProps {
  open: boolean;
  onClose: () => void;
  size?: "sm" | "md" | "lg";
  /** 오버레이 클릭으로 닫기 (기본 true) */
  dismissible?: boolean;
  /** 우상단 ✕ 버튼 */
  showClose?: boolean;
  "aria-labelledby"?: string;
  className?: string;
  children: React.ReactNode;
}

const widths = { sm: "max-w-[420px]", md: "max-w-[560px]", lg: "max-w-[720px]" };

export function Modal({ open, onClose, size = "sm", dismissible = true, showClose, className, children, ...aria }: ModalProps) {
  const [mounted, setMounted] = React.useState(false);
  React.useEffect(() => setMounted(true), []);

  React.useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && dismissible && onClose();
    document.addEventListener("keydown", onKey);
    const prev = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.removeEventListener("keydown", onKey);
      document.body.style.overflow = prev;
    };
  }, [open, dismissible, onClose]);

  if (!mounted || !open) return null;

  return createPortal(
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-ink/40 p-4 backdrop-blur-[2px]"
      onMouseDown={(e) => {
        if (dismissible && e.target === e.currentTarget) onClose();
      }}
    >
      <div
        role="dialog"
        aria-modal="true"
        className={cn(
          "relative w-full rounded-2xl border border-line bg-bg p-6 shadow-lg",
          "k-modal-in",
          widths[size],
          className,
        )}
        {...aria}
      >
        {showClose && (
          <IconButton aria-label="닫기" size="sm" onClick={onClose} className="absolute top-3 right-3">
            <svg viewBox="0 0 16 16" className="size-4" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" aria-hidden>
              <path d="M4 4l8 8M12 4l-8 8" />
            </svg>
          </IconButton>
        )}
        {children}
      </div>
    </div>,
    document.body,
  );
}

export function ModalHeader({
  title,
  description,
  id,
  className,
}: {
  title: React.ReactNode;
  description?: React.ReactNode;
  id?: string;
  className?: string;
}) {
  return (
    <div className={cn("mb-5", className)}>
      <h2 id={id} className="text-xl font-bold text-fg">
        {title}
      </h2>
      {description && <p className="mt-1 text-[13px] text-fg-subtle">{description}</p>}
    </div>
  );
}

export function ModalBody({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return <div className={cn("text-sm text-fg-muted", className)} {...props} />;
}

export function ModalFooter({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return <div className={cn("mt-6 flex justify-end gap-2", className)} {...props} />;
}

/**
 * Popover — 확인 팝오버 (해결 처리할까요?) / 아바타 메뉴.
 *   anchor 요소 아래에 뜨는 작은 카드. 포지셔닝은 부모를 relative 로.
 */
export interface PopoverProps extends React.HTMLAttributes<HTMLDivElement> {
  open: boolean;
  onClose: () => void;
  align?: "start" | "end";
}
export function Popover({ open, onClose, align = "start", className, children, ...props }: PopoverProps) {
  const ref = React.useRef<HTMLDivElement>(null);
  React.useEffect(() => {
    if (!open) return;
    const onDoc = (e: MouseEvent) => {
      if (!ref.current?.parentElement?.contains(e.target as Node)) onClose();
    };
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    document.addEventListener("mousedown", onDoc);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onDoc);
      document.removeEventListener("keydown", onKey);
    };
  }, [open, onClose]);
  if (!open) return null;
  return (
    <div
      ref={ref}
      className={cn(
        "absolute z-40 mt-2 min-w-[240px] rounded-xl border border-line bg-bg p-4 shadow-md",
        align === "end" ? "right-0" : "left-0",
        className,
      )}
      {...props}
    >
      {children}
    </div>
  );
}

/**
 * MenuItem — 아바타 팝오버 메뉴 항목 (마이페이지 / 내 프로필 / 로그아웃)
 */
export function MenuItem({
  danger,
  as: Comp = "button",
  className,
  ...props
}: React.ButtonHTMLAttributes<HTMLButtonElement> & {
  danger?: boolean;
  /** 링크로 쓸 때 (as={Link} href="...") */
  as?: React.ElementType;
  href?: string;
}) {
  return (
    <Comp
      type={Comp === "button" ? "button" : undefined}
      className={cn(
        "flex h-10 w-full items-center rounded-md px-3 text-left text-sm text-fg hover:bg-surface",
        danger && "text-danger hover:bg-danger-bg",
        className,
      )}
      {...props}
    />
  );
}
export function MenuDivider() {
  return <div className="my-1.5 h-px bg-line-subtle" role="separator" />;
}
