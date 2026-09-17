import { describe, expect, it } from "vitest";
import {
  notificationHref,
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
