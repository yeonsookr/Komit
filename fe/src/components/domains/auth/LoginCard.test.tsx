import { renderToStaticMarkup } from "react-dom/server";
import { afterEach, describe, expect, it } from "vitest";
import { LoginCard, kakaoStartHref } from "./LoginCard";

describe("kakaoStartHref", () => {
  const originalBasePath = process.env.NEXT_PUBLIC_BASE_PATH;

  afterEach(() => {
    if (originalBasePath === undefined) delete process.env.NEXT_PUBLIC_BASE_PATH;
    else process.env.NEXT_PUBLIC_BASE_PATH = originalBasePath;
  });

  it("기본 FE 주소는 기존과 같다", () => {
    delete process.env.NEXT_PUBLIC_BASE_PATH;
    expect(kakaoStartHref()).toBe("/api/auth/kakao");
    expect(kakaoStartHref("/rooms")).toBe("/api/auth/kakao?next=%2Frooms");
    expect(renderToStaticMarkup(<LoginCard />)).toContain('href="/api/auth/kakao"');
  });

  it("basePath가 있는 이미지에서만 접두사를 붙인다", () => {
    process.env.NEXT_PUBLIC_BASE_PATH = "/test";
    expect(kakaoStartHref()).toBe("/test/api/auth/kakao");
    expect(kakaoStartHref("/rooms")).toBe("/test/api/auth/kakao?next=%2Frooms");
    expect(renderToStaticMarkup(<LoginCard />)).toContain('href="/test/api/auth/kakao"');
  });
});
