import type { Metadata } from "next";
import { PageHeader } from "@/components/common";
import { NotificationCenter } from "@/components/domains/notification";
import { Container } from "@/components/layouts";
import { getNotifications } from "@/lib/api/services";
import { requireLogin } from "@/lib/auth/guard";
import {
  NOTIFICATION_PAGE_PARAM,
  UNREAD_ONLY_PARAM,
  readNotificationFilter,
  readNotificationPage,
} from "@/lib/notifications";

export const metadata: Metadata = {
  title: "알림 | Komit",
  description: "방 소식과 확인 요청을 확인합니다.",
  robots: { index: false },
};

export default async function NotificationsPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const query = await searchParams;
  const filter = readNotificationFilter(query[UNREAD_ONLY_PARAM]);
  const page = readNotificationPage(query[NOTIFICATION_PAGE_PARAM]);
  const notifications = await requireLogin(() =>
    getNotifications({ unreadOnly: filter === "unread", page, size: 20 }),
  );

  return (
    <Container className="py-8 sm:py-10">
      <PageHeader title="알림" description="방 소식과 확인 요청이 여기 모입니다." />

      <NotificationCenter
        notifications={notifications}
        filter={filter}
        now={new Date().toISOString()}
      />
    </Container>
  );
}
