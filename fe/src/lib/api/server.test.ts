import type { AxiosAdapter } from "axios";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/types/api";
import { authenticatedBackendApi, backendApi } from "./server";

const { authorized } = vi.hoisted(() => ({ authorized: vi.fn() }));

vi.mock("server-only", () => ({}));
vi.mock("@/lib/env", () => ({ requireServerEnv: () => "http://backend.test" }));
vi.mock("@/lib/auth/session", () => ({ authorized }));

const responseAdapter = vi.fn<AxiosAdapter>(async (config) => ({
  data: { ok: true },
  status: 200,
  statusText: "OK",
  headers: {},
  config,
}));

beforeEach(() => {
  authorized.mockReset();
  responseAdapter.mockClear();
});

describe("authenticatedBackendApi", () => {
  it("현재 사용자 토큰을 요청 설정에만 주입한다", async () => {
    authorized.mockResolvedValueOnce({
      headers: { Authorization: "Bearer access-token" },
    });

    await authenticatedBackendApi().get("/api/users/me", { adapter: responseAdapter });

    const config = responseAdapter.mock.calls[0][0];
    expect(config.headers.get("Authorization")).toBe("Bearer access-token");
  });

  it("명시적으로 받은 로그인 직후 토큰은 덮어쓰지 않는다", async () => {
    await authenticatedBackendApi().get("/api/settings/terms", {
      adapter: responseAdapter,
      headers: { Authorization: "Bearer handoff-token" },
    });

    expect(authorized).not.toHaveBeenCalled();
    const config = responseAdapter.mock.calls[0][0];
    expect(config.headers.get("Authorization")).toBe("Bearer handoff-token");
  });

  it("세션이 없다는 공통 오류를 다른 오류로 덮지 않는다", async () => {
    authorized.mockRejectedValueOnce(
      new ApiError(401, "UNAUTHORIZED", "로그인이 필요합니다."),
    );

    await expect(
      authenticatedBackendApi().get("/api/users/me", { adapter: responseAdapter }),
    ).rejects.toMatchObject({ status: 401, code: "UNAUTHORIZED" });
  });
});

describe("backendApi", () => {
  it("공개 요청에서는 사용자 쿠키를 읽지 않는다", async () => {
    await backendApi().get("/api/landing/summary", { adapter: responseAdapter });

    expect(authorized).not.toHaveBeenCalled();
  });
});
