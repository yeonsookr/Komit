import { describe, expect, it, vi } from "vitest";

vi.mock("server-only", () => ({}));
vi.mock("next/headers", () => ({ cookies: vi.fn() }));

import {
  ACCESS_TOKEN_COOKIE,
  REFRESH_TOKEN_COOKIE,
  setSessionCookies,
} from "./session";

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
