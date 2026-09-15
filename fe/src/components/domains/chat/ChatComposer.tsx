"use client";

import * as React from "react";
import { cn } from "@/lib/cn";
import { Button } from "@/components/buttons";

/**
 * ChatComposer — 방 하단의 메시지 입력바.
 *  테두리 없는 입력 + 먹색 「보내기」. Enter 로 보내고 Shift+Enter 로 줄을 바꿉니다.
 *  입력값을 밖에서 들고 있어야 하면 value/onChange 를 함께 넘깁니다.
 */
export interface ChatComposerProps {
  onSend?: (text: string) => void;
  value?: string;
  onChange?: (value: string) => void;
  placeholder?: string;
  sendLabel?: string;
  disabled?: boolean;
  /** 보내는 중 (버튼 스피너) */
  sending?: boolean;
  className?: string;
}

export function ChatComposer({
  onSend,
  value,
  onChange,
  placeholder = "메시지를 입력하세요",
  sendLabel = "보내기",
  disabled,
  sending,
  className,
}: ChatComposerProps) {
  const [inner, setInner] = React.useState("");
  const ref = React.useRef<HTMLTextAreaElement>(null);

  const controlled = value !== undefined;
  const text = controlled ? value : inner;

  const setText = (next: string) => {
    if (!controlled) setInner(next);
    onChange?.(next);
  };

  /** 줄 수에 맞춰 높이를 늘립니다 (최대 5줄). */
  const resize = React.useCallback(() => {
    const el = ref.current;
    if (!el) return;
    el.style.height = "auto";
    el.style.height = `${Math.min(el.scrollHeight, 120)}px`;
  }, []);

  React.useEffect(resize, [text, resize]);

  const send = () => {
    const trimmed = text.trim();
    if (!trimmed || disabled || sending) return;
    onSend?.(trimmed);
    setText("");
  };

  return (
    <div className={cn("flex items-end gap-3 px-4 py-3 sm:px-5", className)}>
      <textarea
        ref={ref}
        rows={1}
        value={text}
        disabled={disabled}
        placeholder={placeholder}
        aria-label={placeholder}
        onChange={(e) => setText(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === "Enter" && !e.shiftKey && !e.nativeEvent.isComposing) {
            e.preventDefault();
            send();
          }
        }}
        className="min-w-0 flex-1 resize-none bg-transparent py-2 text-sm leading-6 text-fg outline-none disabled:text-fg-disabled"
      />
      <Button variant="dark" onClick={send} disabled={disabled || !text.trim()} loading={sending} className="shrink-0">
        {sendLabel}
      </Button>
    </div>
  );
}
