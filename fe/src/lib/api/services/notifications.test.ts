import { beforeEach, describe, expect, it, vi } from "vitest";
import { getNotifications, readAllNotifications, readNotification } from "./notifications";

const { get, patch, post } = vi.hoisted(() => ({
  get: vi.fn(),
  patch: vi.fn(),
  post: vi.fn(),
}));
vi.mock("server-only", () => ({}));
vi.mock("../server", () => ({ backendApi: () => ({ get, patch, post }) }));
vi.mock("@/lib/auth/session", () => ({
  bearerHeaders: async () => ({ headers: { Authorization: "Bearer t" } }),
}));

const AUTH = { headers: { Authorization: "Bearer t" } };

beforeEach(() => {
  get.mockReset();
  patch.mockReset();
  post.mockReset();
});

describe("getNotifications", () => {
  it("필터와 페이지를 토큰과 함께 보낸다", async () => {
    const list = { content: [], unreadCount: 0, page: 2, size: 10, totalElements: 0, totalPages: 0 };
    get.mockResolvedValueOnce({ data: list });

    await expect(getNotifications({ unreadOnly: true, page: 2, size: 10 })).resolves.toEqual(list);
    expect(get).toHaveBeenCalledWith("/api/notifications", {
      ...AUTH,
      params: { unreadOnly: true, page: 2, size: 10 },
    });
  });
});

describe("readAllNotifications", () => {
  it("본문 없이 모두 읽음 요청을 보낸다", async () => {
    const list = { content: [], unreadCount: 0 };
    post.mockResolvedValueOnce({ data: list });

    await expect(readAllNotifications()).resolves.toEqual(list);
    expect(post).toHaveBeenCalledWith("/api/notifications/read-all", undefined, AUTH);
  });
});

describe("readNotification", () => {
  it("알림 식별자로 읽음 요청을 보낸다", async () => {
    const notification = { notificationId: 951, isRead: true };
    patch.mockResolvedValueOnce({ data: notification });

    await expect(readNotification(951)).resolves.toEqual(notification);
    expect(patch).toHaveBeenCalledWith("/api/notifications/951/read", undefined, AUTH);
  });
});
