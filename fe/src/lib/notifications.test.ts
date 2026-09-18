import { describe, expect, it } from "vitest";
import {
  notificationHref,
  notificationTargetHref,
  readNotificationFilter,
  readNotificationPage,
} from "./notifications";

describe("readNotificationFilter", () => {
  it("true일 때만 안 읽음으로 본다", () => {
    expect(readNotificationFilter("true")).toBe("unread");
    expect(readNotificationFilter("false")).toBe("all");
    expect(readNotificationFilter(undefined)).toBe("all");
  });
});

describe("notificationTargetHref", () => {
  it("계약 식별자가 아니라 방 식별자로 계약서 주소를 만든다", () => {
    expect(notificationTargetHref({ targetType: "CONTRACT", roomId: 93 })).toBe(
      "/rooms/93/contract",
    );
  });

  it("현재 열 수 있는 화면이 없는 대상은 주소를 만들지 않는다", () => {
    expect(notificationTargetHref({ targetType: "ANNOUNCEMENT", roomId: null })).toBeNull();
    expect(notificationTargetHref({ targetType: "ISSUE", roomId: null })).toBeNull();
  });
});

describe("readNotificationPage", () => {
  it("1 이상의 정수만 페이지로 쓴다", () => {
    expect(readNotificationPage("3")).toBe(3);
    expect(readNotificationPage("0")).toBe(1);
    expect(readNotificationPage("1.5")).toBe(1);
  });
});

describe("notificationHref", () => {
  it("기본값은 query를 붙이지 않는다", () => {
    expect(notificationHref("all")).toBe("/notifications");
  });

  it("필터와 페이지를 목 query 이름으로 만든다", () => {
    expect(notificationHref("unread", 2)).toBe("/notifications?unreadOnly=true&page=2");
  });
});
