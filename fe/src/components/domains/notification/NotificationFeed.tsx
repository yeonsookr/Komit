import Link from "next/link";
import { Badge, Card, StatusDot } from "@/components/common";
import { EmptyState } from "@/components/feedback";
import { buttonClass } from "@/components/buttons";
import { formatRelativeTime } from "@/lib/format";
import { notificationHref, type NotificationFilter } from "@/lib/notifications";
import type { NotificationList } from "@/types/notification";

export interface NotificationFeedProps {
  notifications: NotificationList;
  filter: NotificationFilter;
  now: string;
}

/** 공지는 본문을 펼쳐 보이고, 나머지는 읽음 상태가 드러나는 목록으로 나눕니다. */
export function NotificationFeed({
  notifications,
  filter,
  now,
}: Readonly<NotificationFeedProps>) {
  const announcements = notifications.content.filter((item) => item.type === "공지");
  const items = notifications.content.filter((item) => item.type !== "공지");
  const currentTime = new Date(now);

  if (notifications.content.length === 0) {
    return (
      <EmptyState
        className="mt-6"
        title={filter === "unread" ? "안 읽은 알림이 없습니다" : "받은 알림이 없습니다"}
        description={
          filter === "unread"
            ? "새로운 소식은 전체 알림에서 다시 볼 수 있습니다."
            : "방 소식과 확인 요청이 생기면 여기에 모입니다."
        }
      />
    );
  }

  return (
    <>
      {announcements.length > 0 && (
        <section className="mt-10" aria-labelledby="announcement-heading">
          <h2 id="announcement-heading" className="text-lg font-bold text-fg">
            공지
          </h2>
          <div className="mt-3 space-y-3">
            {announcements.map((item) => (
              <Card key={item.notificationId} variant="soft" as="article">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <Badge tone="neutral" shape="square">
                    공지
                  </Badge>
                  <time className="font-mono text-xs text-fg-subtle tabular-nums" dateTime={item.createdAt}>
                    {formatRelativeTime(item.createdAt, currentTime)}
                  </time>
                </div>
                <h3 className="mt-3 text-[15px] font-bold text-fg">{item.title}</h3>
                <p className="mt-2 text-sm leading-6 text-fg-muted">{item.content}</p>
              </Card>
            ))}
          </div>
        </section>
      )}

      {items.length > 0 && (
        <section className="mt-10" aria-labelledby="notification-heading">
          <h2 id="notification-heading" className="text-lg font-bold text-fg">
            알림
          </h2>
          <Card variant="outline" padding="none" className="mt-3 px-5">
            <ul>
              {items.map((item) => (
                <li
                  key={item.notificationId}
                  className="flex gap-3 py-5 [&:not(:last-child)]:border-b [&:not(:last-child)]:border-line-subtle"
                >
                  <StatusDot tone={item.isRead ? "neutral" : "brand"} className="mt-1.5" />
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-start justify-between gap-x-4 gap-y-1">
                      <h3 className="text-sm font-semibold text-fg">{item.title}</h3>
                      <time className="font-mono text-xs text-fg-subtle tabular-nums" dateTime={item.createdAt}>
                        {formatRelativeTime(item.createdAt, currentTime)}
                      </time>
                    </div>
                    <p className="mt-1 text-[13px] leading-5 text-fg-muted">{item.content}</p>
                  </div>
                </li>
              ))}
            </ul>
          </Card>
        </section>
      )}

      {notifications.totalPages > 1 && (
        <nav aria-label="알림 페이지" className="mt-6 flex justify-center gap-2">
          {notifications.page > 1 && (
            <Link
              href={notificationHref(filter, notifications.page - 1)}
              className={buttonClass({ variant: "outline", size: "sm" })}
            >
              이전
            </Link>
          )}
          {notifications.page < notifications.totalPages && (
            <Link
              href={notificationHref(filter, notifications.page + 1)}
              className={buttonClass({ variant: "outline", size: "sm" })}
            >
              다음
            </Link>
          )}
        </nav>
      )}
    </>
  );
}
