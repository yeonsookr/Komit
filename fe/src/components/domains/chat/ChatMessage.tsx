import * as React from "react";
import { cn } from "@/lib/cn";
import { Avatar, Divider } from "@/components/common";

/**
 * ChatMessage — 상담·컨설팅·개발 방의 말풍선.
 *  - 내 말: 연보라 면, 오른쪽 정렬, 아바타 없음
 *  - 상대 말: 흰 면 + 회색 테두리, 왼쪽 정렬, 아바타 있음
 * 시각은 말풍선 안 아래에 monospace 로 붙습니다.
 */
export interface ChatMessageProps extends React.HTMLAttributes<HTMLDivElement> {
  /** 내가 보낸 말 */
  mine?: boolean;
  /** 보낸 사람 이름. 상대 말의 아바타 이니셜과 스크린리더 라벨로 씁니다. */
  author?: string;
  avatarSrc?: string;
  /** 보낸 시각 (예: "10:14") */
  time?: string;
  /** 아바타 자리를 비워 둘지 (같은 사람이 연달아 말할 때) */
  hideAvatar?: boolean;
}

export function ChatMessage({
  mine,
  author,
  avatarSrc,
  time,
  hideAvatar,
  className,
  children,
  ...props
}: ChatMessageProps) {
  return (
    <div className={cn("flex items-start gap-2", mine && "flex-row-reverse", className)} {...props}>
      {!mine &&
        (hideAvatar ? (
          <span className="size-7 shrink-0" aria-hidden />
        ) : (
          <Avatar size="sm" name={author} src={avatarSrc} className="mt-0.5" />
        ))}

      <div
        className={cn(
          "max-w-[85%] rounded-lg px-4 py-3 sm:max-w-[70%]",
          mine ? "bg-primary-soft" : "border border-line bg-bg",
        )}
      >
        {!mine && author && <p className="sr-only">{author}</p>}
        <p className="text-sm leading-6 break-words whitespace-pre-wrap text-fg">{children}</p>
        {time && <p className="mt-1.5 font-mono text-[11px] text-fg-subtle tabular-nums">{time}</p>}
      </div>
    </div>
  );
}

/**
 * ChatDateDivider — 대화 사이의 날짜 구분 ("8월 22일").
 * 공통 Divider 를 채팅 맥락 이름으로 감싼 것입니다.
 */
export function ChatDateDivider({ date, className }: { date: React.ReactNode; className?: string }) {
  return <Divider label={date} className={cn("py-1", className)} />;
}
