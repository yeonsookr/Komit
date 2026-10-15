import axios, { type AxiosRequestConfig } from "axios";
import { describe, expect, it, vi } from "vitest";
import { attachTokenRefresh } from "./refresh";

function unauthorized(config: AxiosRequestConfig) {
  const error = new axios.AxiosError("401", undefined, config as never);
  error.response = { status: 401, data: {}, statusText: "", headers: {}, config: config as never };
  return error;
}

/**
 * 어댑터가 호출 순서대로 응답을 돌려줍니다. 401 은 AxiosError 로, 그 밖은 성공으로.
 * 실제 네트워크 없이 "만료 → 재발급 → 재시도" 순서를 흉내내기 위한 것입니다.
 */
function instanceWith(statuses: number[], options: Partial<Parameters<typeof attachTokenRefresh>[1]> = {}) {
  const instance = axios.create();
  const calls: string[] = [];
  let index = 0;

  instance.defaults.adapter = (config) => {
    calls.push(config.url ?? "");
    const status = statuses[index++] ?? 200;
    if (status === 401) return Promise.reject(unauthorized(config));
    return Promise.resolve({ status, data: { ok: true }, statusText: "", headers: {}, config });
  };

  const refresh = vi.fn(() => Promise.resolve());
  const onExpired = vi.fn();
  attachTokenRefresh(instance, {
    refresh,
    shouldRefresh: (url) => !url.startsWith("/api/auth/"),
    onExpired,
    ...options,
  });

  return { instance, calls, refresh, onExpired };
}

describe("attachTokenRefresh", () => {
  it("401 이면 재발급한 뒤 같은 요청을 한 번 다시 보낸다", async () => {
    const { instance, calls, refresh, onExpired } = instanceWith([401, 200]);

    const response = await instance.get("/api/users/me");

    expect(response.data).toEqual({ ok: true });
    expect(refresh).toHaveBeenCalledTimes(1);
    expect(calls).toEqual(["/api/users/me", "/api/users/me"]);
    expect(onExpired).not.toHaveBeenCalled();
  });

  it("다시 보낸 요청도 401 이면 더 재발급하지 않고 실패시킨다", async () => {
    const { instance, refresh } = instanceWith([401, 401]);

    await expect(instance.get("/api/users/me")).rejects.toMatchObject({
      response: { status: 401 },
    });
    expect(refresh).toHaveBeenCalledTimes(1);
  });

  it("재발급이 실패하면 세션 만료를 알리고 원래 오류를 그대로 던진다", async () => {
    const { instance, onExpired } = instanceWith([401], {
      refresh: () => Promise.reject(new Error("refresh failed")),
    });

    await expect(instance.get("/api/users/me")).rejects.toMatchObject({
      response: { status: 401 },
    });
    expect(onExpired).toHaveBeenCalledTimes(1);
  });

  it("인증 경로의 401 은 재발급하지 않는다", async () => {
    const { instance, refresh } = instanceWith([401]);

    await expect(instance.post("/api/auth/refresh")).rejects.toMatchObject({
      response: { status: 401 },
    });
    expect(refresh).not.toHaveBeenCalled();
  });

  it("401 이 아닌 오류는 손대지 않는다", async () => {
    const instance = axios.create();
    instance.defaults.adapter = () => Promise.reject(new Error("boom"));
    const refresh = vi.fn(() => Promise.resolve());
    attachTokenRefresh(instance, { refresh, shouldRefresh: () => true, onExpired: vi.fn() });

    await expect(instance.get("/x")).rejects.toThrow("boom");
    expect(refresh).not.toHaveBeenCalled();
  });

  it("동시에 여러 요청이 401 을 받아도 재발급은 한 번만 한다", async () => {
    let resolveRefresh: () => void = () => {};
    const refresh = vi.fn(
      () =>
        new Promise<void>((resolve) => {
          resolveRefresh = resolve;
        }),
    );
    const { instance, calls } = instanceWith([401, 401, 200, 200], { refresh });

    const pending = Promise.all([instance.get("/api/rooms"), instance.get("/api/users/me")]);
    await vi.waitFor(() => expect(refresh).toHaveBeenCalledTimes(1));
    resolveRefresh();

    await pending;
    expect(refresh).toHaveBeenCalledTimes(1);
    expect(calls).toHaveLength(4);
  });
});
