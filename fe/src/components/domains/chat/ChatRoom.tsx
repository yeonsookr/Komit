import * as React from "react";
import { cn } from "@/lib/cn";

/**
 * ChatRoom — 방의 대화 영역 카드.
 *   [banner]   세션 안내처럼 대화 위에 붙는 줄 (선택)
 *   [children] 말풍선 목록
 *   [footer]   입력바(ChatComposer) 또는 마무리 안내(ChatNotice)
 *
 * 높이를 정해 스크롤시키려면 bodyClassName 으로 넘깁니다. (예: "max-h-[480px]")
 */
export interface ChatRoomProps extends React.HTMLAttributes<HTMLDivElement> {
  banner?: React.ReactNode;
  footer?: React.ReactNode;
  bodyClassName?: string;
}

export function ChatRoom({ banner, footer, bodyClassName, className, children, ...props }: ChatRoomProps) {
  return (
    <section
      className={cn("flex min-w-0 flex-col overflow-hidden rounded-xl border border-line bg-bg", className)}
      {...props}
    >
      {banner && <div className="border-b border-line-subtle">{banner}</div>}

      <div className={cn("flex flex-1 flex-col gap-4 overflow-y-auto px-4 py-5 sm:px-5", bodyClassName)}>
        {children}
      </div>

      {footer && <div className="border-t border-line-subtle">{footer}</div>}
    </section>
  );
}

/**
 * ChatNotice — 방이 마무리됐을 때 입력바 대신 놓이는 안내 행.
 *   "개발 계약으로 진행해 이 상담은 마무리됐습니다." + 「개발 방으로」
 */
export interface ChatNoticeProps extends React.HTMLAttributes<HTMLDivElement> {
  action?: React.ReactNode;
}

export function ChatNotice({ action, className, children, ...props }: ChatNoticeProps) {
  return (
    <div
      className={cn(
        "flex flex-col gap-3 px-4 py-3.5 sm:flex-row sm:items-center sm:justify-between sm:px-5",
        className,
      )}
      {...props}
    >
      <p className="text-[13px] leading-5 text-fg-muted">{children}</p>
      {action && <div className="shrink-0">{action}</div>}
    </div>
  );
}

/**
 * ChatSessionBanner — 대화 위에 붙는 컨설팅 세션 안내.
 *   "컨설팅 세션 · 8월 29일 (토) 14:00 · 45분 / 시작 10분 전부터 들어갈 수 있습니다" + 「입장」
 */
export interface ChatSessionBannerProps extends Omit<React.HTMLAttributes<HTMLDivElement>, "title"> {
  title: React.ReactNode;
  description?: React.ReactNode;
  action?: React.ReactNode;
}

export function ChatSessionBanner({
  title,
  description,
  action,
  className,
  ...props
}: ChatSessionBannerProps) {
  return (
    <div
      className={cn(
        "flex flex-col gap-3 px-4 py-3.5 sm:flex-row sm:items-center sm:justify-between sm:px-5",
        className,
      )}
      {...props}
    >
      <div className="min-w-0">
        <p className="text-sm font-semibold text-fg">{title}</p>
        {description && <p className="mt-0.5 text-[13px] text-fg-subtle">{description}</p>}
      </div>
      {action && <div className="shrink-0">{action}</div>}
    </div>
  );
}
