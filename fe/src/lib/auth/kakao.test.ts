import { describe, expect, it } from "vitest";
import {
  KAKAO_AUTHORIZE_URL,
  buildKakaoAuthorizeUrl,
  createOauthState,
  parseOauthHandoff,
  serializeOauthHandoff,
} from "./kakao";

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
