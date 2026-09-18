import "server-only";

import { BACKEND } from "../endpoints";
import { backendApi } from "../server";
import { bearerHeaders } from "@/lib/auth/session";
import type {
  Notification,
  NotificationList,
  NotificationListParams,
} from "@/types/notification";

/** 받은 알림을 최신순으로 읽습니다. 안 읽은 것만 서버에서 거를 수 있습니다. */
export async function getNotifications(
  params: NotificationListParams = {},
): Promise<NotificationList> {
  const auth = await bearerHeaders();
  const { data } = await backendApi().get<NotificationList>(BACKEND.NOTIFICATIONS, {
    ...auth,
    params,
  });
  return data;
}

/** 내 알림을 모두 읽음으로 바꾸고 바뀐 목록을 받습니다. */
export async function readAllNotifications(): Promise<NotificationList> {
  const { data } = await backendApi().post<NotificationList>(
    BACKEND.NOTIFICATIONS_READ_ALL,
    undefined,
    await bearerHeaders(),
  );
  return data;
}

/** 알림 하나를 읽음으로 바꿉니다. 이미 읽은 알림에도 같은 응답이 옵니다. */
export async function readNotification(notificationId: number): Promise<Notification> {
  const { data } = await backendApi().patch<Notification>(
    BACKEND.NOTIFICATION_READ(notificationId),
    undefined,
    await bearerHeaders(),
  );
  return data;
}
