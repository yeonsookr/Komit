"use client";

import { useRouter } from "next/navigation";
import { SegmentedTabs } from "@/components/common";
import { notificationHref, type NotificationFilter } from "@/lib/notifications";

export interface NotificationTabsProps {
  filter: NotificationFilter;
  unreadCount: number;
}

/** 필터는 주소에 남겨 새로고침해도 같은 목록을 봅니다. */
export function NotificationTabs({ filter, unreadCount }: Readonly<NotificationTabsProps>) {
  const router = useRouter();

  return (
    <SegmentedTabs
      aria-label="알림 필터"
      value={filter}
      items={[
        { value: "all", label: "전체" },
        { value: "unread", label: "안 읽음", count: unreadCount },
      ]}
      onChange={(next) => router.push(notificationHref(next))}
    />
  );
}
