import { describe, expect, it } from "vitest";
import {
  KAKAO_AUTHORIZE_URL,
  buildKakaoAuthorizeUrl,
  createOauthState,
  parseOauthHandoff,
  serializeOauthHandoff,
  siteBaseUrl,
  siteUrl,
} from "./kakao";

describe("siteBaseUrl", () => {
  it("콜백 경로를 걷어내 공개 기준 주소를 만든다", () => {
    expect(siteBaseUrl("https://komit.site/api/auth/kakao/callback").toString()).toBe(
      "https://komit.site/",
    );
  });

  it("하위 경로 배포에서는 접두사를 지킨다", () => {
    expect(siteBaseUrl("https://komit.site/test/api/auth/kakao/callback").toString()).toBe(
      "https://komit.site/test/",
    );
  });
});

describe("siteUrl", () => {
  it("앞에 슬래시가 붙은 경로도 접두사 아래로 붙인다", () => {
    const base = siteBaseUrl("https://komit.site/test/api/auth/kakao/callback");
    expect(siteUrl("/login?error=KAKAO_AUTH_FAILED", base).toString()).toBe(
      "https://komit.site/test/login?error=KAKAO_AUTH_FAILED",
    );
  });

  it("접두사가 없으면 그대로 뿌리에 붙인다", () => {
    const base = siteBaseUrl("https://komit.site/api/auth/kakao/callback");
    expect(siteUrl("/signup/terms", base).toString()).toBe("https://komit.site/signup/terms");
  });
});

describe("buildKakaoAuthorizeUrl", () => {
  it("카카오 인가 주소에 필요한 파라미터를 전부 싣는다", () => {
    const url = new URL(
      buildKakaoAuthorizeUrl({
        clientId: "rest-api-key",
        redirectUri: "https://komit.site/api/auth/kakao/callback",
        state: "abc",
      }),
    );

    expect(`${url.origin}${url.pathname}`).toBe(KAKAO_AUTHORIZE_URL);
    expect(url.searchParams.get("client_id")).toBe("rest-api-key");
    expect(url.searchParams.get("redirect_uri")).toBe("https://komit.site/api/auth/kakao/callback");
    expect(url.searchParams.get("response_type")).toBe("code");
    expect(url.searchParams.get("state")).toBe("abc");
  });
});

describe("createOauthState", () => {
  it("부를 때마다 다른 값을 준다", () => {
    expect(createOauthState()).not.toBe(createOauthState());
  });
});

describe("oauth handoff", () => {
  it("직렬화한 값을 그대로 되읽는다", () => {
    const raw = serializeOauthHandoff({ state: "s1", next: "/checkup" });
    expect(parseOauthHandoff(raw)).toEqual({ state: "s1", next: "/checkup" });
  });

  it("next 가 없어도 된다", () => {
    expect(parseOauthHandoff(serializeOauthHandoff({ state: "s1" }))).toEqual({
      state: "s1",
      next: undefined,
    });
  });

  it("손상되었거나 모양이 다르면 null", () => {
    expect(parseOauthHandoff(undefined)).toBeNull();
    expect(parseOauthHandoff("")).toBeNull();
    expect(parseOauthHandoff("not-json")).toBeNull();
    expect(parseOauthHandoff(JSON.stringify({ next: "/x" }))).toBeNull();
    expect(parseOauthHandoff(JSON.stringify({ state: "" }))).toBeNull();
    expect(parseOauthHandoff(JSON.stringify(["s1"]))).toBeNull();
  });
});
