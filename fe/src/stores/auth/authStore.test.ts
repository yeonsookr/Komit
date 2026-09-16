import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/types/api";
import type { Me } from "@/types/user";

vi.mock("@/lib/api/services/session", () => ({
  fetchMe: vi.fn(),
  requestLogout: vi.fn(),
}));

import { fetchMe, requestLogout } from "@/lib/api/services/session";
import { createAuthStore } from "./authStore";

const ME: Me = {
  userId: 12,
  name: "김미미",
  profileImageFileId: null,
  hasDevProfile: false,
  developerId: null,
  unreadNotificationCount: 3,
};

describe("authStore", () => {
  beforeEach(() => {
    vi.mocked(fetchMe).mockReset();
    vi.mocked(requestLogout).mockReset();
    vi.spyOn(console, "error").mockImplementation(() => {});
  });

  it("처음에는 아직 모르는 상태다", () => {
    const store = createAuthStore();
    expect(store.getState()).toMatchObject({ status: "loading", user: null });
  });

  it("내 정보를 받으면 로그인 상태가 된다", async () => {
    vi.mocked(fetchMe).mockResolvedValue(ME);
    const store = createAuthStore();

    await store.getState().loadMe();

    expect(store.getState()).toMatchObject({ status: "authenticated", user: ME });
  });

  it("401 이면 비로그인 상태가 된다", async () => {
    vi.mocked(fetchMe).mockRejectedValue(new ApiError(401, "UNAUTHORIZED", "로그인이 필요합니다."));
    const store = createAuthStore();

    await store.getState().loadMe();

    expect(store.getState()).toMatchObject({ status: "anonymous", user: null });
  });

  it("네트워크 오류도 로그인했다고 보지 않는다", async () => {
    vi.mocked(fetchMe).mockRejectedValue(new ApiError(0, "NETWORK_ERROR", "연결 실패"));
    const store = createAuthStore();

    await store.getState().loadMe();

    expect(store.getState().status).toBe("anonymous");
  });

  it("로그아웃하면 서버에 알리고 비로그인으로 돌린다", async () => {
    vi.mocked(requestLogout).mockResolvedValue();
    const store = createAuthStore({ status: "authenticated", user: ME });

    await store.getState().logout();

    expect(requestLogout).toHaveBeenCalledTimes(1);
    expect(store.getState()).toMatchObject({ status: "anonymous", user: null });
  });

  it("로그아웃 요청이 실패해도 화면은 로그아웃한다", async () => {
    vi.mocked(requestLogout).mockRejectedValue(new ApiError(0, "NETWORK_ERROR", "연결 실패"));
    const store = createAuthStore({ status: "authenticated", user: ME });

    await store.getState().logout();

    expect(store.getState().status).toBe("anonymous");
  });

  it("setUser 와 clear 는 상태를 바로 바꾼다", () => {
    const store = createAuthStore();

    store.getState().setUser(ME);
    expect(store.getState()).toMatchObject({ status: "authenticated", user: ME });

    store.getState().clear();
    expect(store.getState()).toMatchObject({ status: "anonymous", user: null });
  });

  it("스토어는 서로 독립이다", () => {
    const a = createAuthStore();
    const b = createAuthStore();

    a.getState().setUser(ME);

    expect(b.getState().status).toBe("loading");
  });
});
