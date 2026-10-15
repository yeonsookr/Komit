import { describe, expect, it, vi } from "vitest";

vi.mock("server-only", () => ({}));
vi.mock("next/headers", () => ({ cookies: vi.fn() }));

import {
  ACCESS_TOKEN_COOKIE,
  REFRESH_TOKEN_COOKIE,
  setSessionCookies,
} from "./session";
import { authCookieScope } from "./cookies";

describe("setSessionCookies", () => {
  it("액세스 토큰 수명과 R-AUTH-02 리프레시 토큰 수명을 httpOnly 쿠키에 적용한다", () => {
    const set = vi.fn();
    const store = { set } as unknown as Parameters<typeof setSessionCookies>[0];

    setSessionCookies(store, {
      accessToken: "access-token",
      refreshToken: "refresh-token",
      expiresIn: 900,
    });

    expect(set).toHaveBeenNthCalledWith(
      1,
      ACCESS_TOKEN_COOKIE,
      "access-token",
      expect.objectContaining({ httpOnly: true, path: "/", maxAge: 900 }),
    );
    expect(set).toHaveBeenNthCalledWith(
      2,
      REFRESH_TOKEN_COOKIE,
      "refresh-token",
      expect.objectContaining({ httpOnly: true, path: "/api/auth", maxAge: 14 * 24 * 60 * 60 }),
    );
  });
});

describe("authCookieScope", () => {
  it("루트 FE는 기존 쿠키 이름과 경로를 유지한다", () => {
    expect(authCookieScope("")).toEqual({
      accessToken: "komit_access_token",
      refreshToken: "komit_refresh_token",
      oauthHandoff: "komit_oauth",
      sessionPath: "/",
      authPath: "/api/auth",
    });
  });

  it("/test FE는 루트 FE와 겹치지 않는 이름과 경로를 쓴다", () => {
    expect(authCookieScope("/test")).toEqual({
      accessToken: "komit_test_access_token",
      refreshToken: "komit_test_refresh_token",
      oauthHandoff: "komit_test_oauth",
      sessionPath: "/test",
      authPath: "/test/api/auth",
    });
  });
});
