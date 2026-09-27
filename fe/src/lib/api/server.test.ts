import { Readable } from "node:stream";
import { AxiosError, type AxiosAdapter, type InternalAxiosRequestConfig } from "axios";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/types/api";
import { authenticatedBackendApi, authenticatedDownloadApi, backendApi } from "./server";

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

/** 백엔드가 실패 본문을 스트림으로 준 것처럼 거절합니다. */
function streamFailure(status: number, body: Buffer) {
  return vi.fn<AxiosAdapter>(async (config: InternalAxiosRequestConfig) => {
    const response = { data: Readable.from([body]), status, statusText: "", headers: {}, config };
    throw new AxiosError("Request failed", AxiosError.ERR_BAD_REQUEST, config, null, response);
  });
}

describe("authenticatedDownloadApi", () => {
  it("현재 사용자 토큰을 넣고 압축하지 않은 본문을 일반 요청보다 길게 기다린다", async () => {
    authorized.mockResolvedValueOnce({ headers: { Authorization: "Bearer access-token" } });

    await authenticatedDownloadApi().get("/api/deliverables/31/file", { adapter: responseAdapter });

    const config = responseAdapter.mock.calls[0][0];
    expect(config.headers.get("Authorization")).toBe("Bearer access-token");
    expect(config.headers.get("Accept-Encoding")).toBe("identity");
    expect(config.timeout).toBeGreaterThan(5_000);
  });

  it("스트림으로 온 실패 본문을 읽어 백엔드 사유를 그대로 전한다", async () => {
    authorized.mockResolvedValueOnce({ headers: { Authorization: "Bearer access-token" } });
    const body = Buffer.from(
      JSON.stringify({ code: "DELIVERABLE_ACCESS_LOCKED", message: "대금 지급이 완료된 뒤에 내려받을 수 있습니다." }),
    );

    await expect(
      authenticatedDownloadApi().get("/api/deliverable-materials/502/file", {
        adapter: streamFailure(409, body),
        responseType: "stream",
      }),
    ).rejects.toMatchObject({
      status: 409,
      code: "DELIVERABLE_ACCESS_LOCKED",
      message: "대금 지급이 완료된 뒤에 내려받을 수 있습니다.",
    });
  });

  it("실패 본문이 한도를 넘으면 읽다 멈추고 고정 문구로 덮는다", async () => {
    authorized.mockResolvedValueOnce({ headers: { Authorization: "Bearer access-token" } });
    // 한도가 없으면 읽어서 서버 사유로 풀리는 올바른 JSON 이다. 한도 때문에만 고정 문구가 된다.
    const huge = Buffer.from(JSON.stringify({ code: "HUGE", message: "x".repeat(1024 * 1024) }));

    await expect(
      authenticatedDownloadApi().get("/api/deliverable-materials/502/file", {
        adapter: streamFailure(500, huge),
        responseType: "stream",
      }),
    ).rejects.toMatchObject({ status: 500, code: "UNKNOWN_ERROR" });
  });

  it("일반 요청 인스턴스와 따로 만든다", () => {
    expect(authenticatedDownloadApi()).not.toBe(authenticatedBackendApi());
  });
});
