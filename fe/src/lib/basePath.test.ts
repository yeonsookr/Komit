import { describe, expect, it } from "vitest";
import { appPath, clientBasePath } from "./basePath";

describe("basePath", () => {
  it("기본 FE 경로는 바꾸지 않는다", () => {
    expect(clientBasePath("")).toBe("");
    expect(appPath("/api/users/me", "")).toBe("/api/users/me");
  });

  it("테스트 FE의 모든 브라우저 경로에 전역 접두사를 붙인다", () => {
    expect(clientBasePath("/test")).toBe("/test");
    expect(appPath("/api/users/me", "/test")).toBe("/test/api/users/me");
  });

  it("잘못된 접두사와 상대 경로를 거부한다", () => {
    expect(() => clientBasePath("test")).toThrow();
    expect(() => clientBasePath("/test/")).toThrow();
    expect(() => appPath("api/users/me", "/test")).toThrow();
  });
});
