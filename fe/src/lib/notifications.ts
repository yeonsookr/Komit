import { ROUTES } from "./routes";

/** 알림 화면이 주소로 주고받는 query 이름입니다. */
export const UNREAD_ONLY_PARAM = "unreadOnly";
export const NOTIFICATION_PAGE_PARAM = "page";

export type NotificationFilter = "all" | "unread";

/** 목 API의 페이지는 1부터 시작합니다. 잘못된 값은 첫 장으로 돌립니다. */
export function readNotificationPage(value: string | string[] | undefined): number {
  const raw = Array.isArray(value) ? value[0] : value;
  if (!raw || !/^\d+$/.test(raw)) return 1;

  const page = Number(raw);
  return page >= 1 ? page : 1;
}

/** `unreadOnly=true`만 안 읽음 탭으로 인정합니다. */
export function readNotificationFilter(
  value: string | string[] | undefined,
): NotificationFilter {
  const raw = Array.isArray(value) ? value[0] : value;
  return raw === "true" ? "unread" : "all";
}

/** 필터나 페이지를 바꿔도 query 이름을 화면마다 다시 쓰지 않습니다. */
export function notificationHref(filter: NotificationFilter, page = 1): string {
  const query = new URLSearchParams();
  if (filter === "unread") query.set(UNREAD_ONLY_PARAM, "true");
  if (page > 1) query.set(NOTIFICATION_PAGE_PARAM, String(page));
  const suffix = query.toString();
  return suffix ? `${ROUTES.notifications}?${suffix}` : ROUTES.notifications;
}
