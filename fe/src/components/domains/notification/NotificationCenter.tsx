"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/buttons";
import { useToast } from "@/components/feedback";
import { clientApi } from "@/lib/api/client";
import { INTERNAL } from "@/lib/api/endpoints";
import {
  notificationTargetHref,
  type NotificationFilter,
} from "@/lib/notifications";
import { useAuthStore } from "@/stores/auth";
import { ApiError } from "@/types/api";
import type { Notification, NotificationList } from "@/types/notification";
import { NotificationFeed } from "./NotificationFeed";
import { NotificationTabs } from "./NotificationTabs";

export interface NotificationCenterProps {
  notifications: NotificationList;
  filter: NotificationFilter;
  now: string;
}

/** 목록의 읽음 상태와 헤더 숫자를 한 요청 뒤 함께 맞춥니다. */
export function NotificationCenter({
  notifications,
  filter,
  now,
}: Readonly<NotificationCenterProps>) {
  const router = useRouter();
  const showToast = useToast();
  const loadMe = useAuthStore((state) => state.loadMe);
  const [list, setList] = React.useState(notifications);
  const [readingAll, setReadingAll] = React.useState(false);
  const [confirmingId, setConfirmingId] = React.useState<number | null>(null);

  React.useEffect(() => setList(notifications), [notifications]);

  const refreshHeader = React.useCallback(async () => {
    await loadMe();
  }, [loadMe]);

  const readAll = async () => {
    if (readingAll || list.unreadCount === 0) return;
    setReadingAll(true);

    try {
      const { data } = await clientApi.post<NotificationList>(INTERNAL.NOTIFICATIONS_READ_ALL);
      setList(
        filter === "unread"
          ? { ...data, content: [], totalElements: 0, totalPages: 0 }
          : data,
      );
      await refreshHeader();
      router.refresh();
    } catch (error) {
      const message = error instanceof ApiError ? error.message : "알림을 읽음으로 바꾸지 못했습니다.";
      showToast({ tone: "danger", title: message });
    } finally {
      setReadingAll(false);
    }
  };

  const confirm = async (notification: Notification) => {
    const href = notificationTargetHref(notification);
    if (!href || confirmingId !== null) return;
    setConfirmingId(notification.notificationId);

    try {
      await clientApi.patch(INTERNAL.NOTIFICATION_READ(notification.notificationId));
      await refreshHeader();
      router.push(href);
    } catch (error) {
      const message = error instanceof ApiError ? error.message : "알림을 확인하지 못했습니다.";
      showToast({ tone: "danger", title: message });
      setConfirmingId(null);
    }
  };

  return (
    <>
      <div className="mt-8 flex flex-wrap items-center justify-between gap-3">
        <NotificationTabs filter={filter} unreadCount={list.unreadCount} />
        <Button
          variant="ghost"
          size="sm"
          disabled={list.unreadCount === 0}
          loading={readingAll}
          onClick={readAll}
        >
          모두 읽음으로
        </Button>
      </div>

      <NotificationFeed
        notifications={list}
        filter={filter}
        now={now}
        confirmingId={confirmingId}
        onConfirm={confirm}
      />
    </>
  );
}
