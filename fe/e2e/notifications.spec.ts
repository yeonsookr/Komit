import { expect, test } from "@playwright/test";
import { signIn } from "./support/session";

const me = {
  userId: 12,
  name: "김미미",
  profileImageFileId: null,
  profileImageUrl: null,
  hasDevProfile: false,
  developerId: null,
  unreadNotificationCount: 3,
};

const contractNotification = {
  notificationId: 951,
  type: "승인알림",
  title: "이준 개발자가 3차를 끝냈습니다",
  content: "수제 비누 가게 · 회원탈퇴 경로 구현 · 확인 문장 세 줄",
  targetType: "CONTRACT",
  targetId: 130,
  roomId: 93,
  isRead: true,
  createdAt: "2026-08-30T14:30:00+09:00",
};

test.describe("알림", () => {
  test.beforeEach(async ({ page }) => {
    await signIn(page);
  });

  test("공지와 일반 알림을 나누고 안 읽은 것만 거른다", async ({ page }) => {
    await page.goto("/notifications");

    await expect(page).toHaveTitle("알림 | Komit");
    await expect(page.getByRole("heading", { name: "공지", level: 2 })).toBeVisible();
    await expect(page.getByRole("heading", { name: "알림", level: 2 })).toBeVisible();
    await expect(page.getByText("9월 1일부터 정산 지급 주기가 바뀝니다")).toBeVisible();
    await expect(page.getByText("컨설팅 세션이 30분 뒤에 시작합니다")).toBeVisible();

    await page.getByRole("tab", { name: /안 읽음/ }).click();

    await expect(page).toHaveURL(/\/notifications\?unreadOnly=true$/);
    await expect(page.getByText("이준 개발자가 3차를 끝냈습니다")).toBeVisible();
    await expect(page.getByText("컨설팅 세션이 30분 뒤에 시작합니다")).toHaveCount(0);
  });

  test("모두 읽음으로 바꾸면 목록과 헤더 숫자를 함께 갱신한다", async ({ page }) => {
    let meCalls = 0;
    await page.route(/\/api\/users\/me$/, async (route) => {
      meCalls += 1;
      await route.fulfill({
        status: 200,
        json: { ...me, unreadNotificationCount: meCalls === 1 ? 3 : 0 },
      });
    });
    await page.route(/\/api\/notifications\/read-all$/, async (route) => {
      expect(route.request().method()).toBe("POST");
      await route.fulfill({
        status: 200,
        json: {
          content: [{ ...contractNotification, isRead: true }],
          unreadCount: 0,
          page: 1,
          size: 20,
          totalElements: 1,
          totalPages: 1,
        },
      });
    });

    await page.goto("/notifications");
    await expect(page.getByRole("link", { name: "알림 3건 안 읽음" })).toBeVisible();

    await page.getByRole("button", { name: "모두 읽음으로" }).click();

    await expect(page.getByRole("link", { name: "알림", exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: "모두 읽음으로" })).toBeDisabled();
    expect(meCalls).toBeGreaterThanOrEqual(2);
  });

  test("확인하기는 알림을 읽고 계약이 달린 방으로 이동한다", async ({ page }) => {
    await page.route(/\/api\/users\/me$/, async (route) => {
      await route.fulfill({ status: 200, json: { ...me, unreadNotificationCount: 2 } });
    });
    await page.route(/\/api\/notifications\/951\/read$/, async (route) => {
      expect(route.request().method()).toBe("PATCH");
      expect(route.request().postData()).toBeNull();
      await route.fulfill({ status: 200, json: contractNotification });
    });

    await page.goto("/notifications");
    await page.getByRole("button", { name: "확인하기" }).first().click();

    // 계약 식별자 130이 아니라 응답의 방 식별자 93으로 화면 주소를 만듭니다.
    await expect(page).toHaveURL(/\/rooms\/93\/contract$/);
  });
});
