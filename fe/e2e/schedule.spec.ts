import { expect, test } from "@playwright/test";
import { signIn } from "./support/session";

test.describe("내 일정", () => {
  test.beforeEach(async ({ page }) => {
    await signIn(page);
  });

  test("잡힌 세션과 지난 세션을 역할과 상대 이름으로 보여준다", async ({ page }) => {
    await page.goto("/mypage/schedule");

    await expect(page).toHaveTitle("내 일정 | Komit");
    await expect(page.getByRole("heading", { name: "내 일정", level: 1 })).toBeVisible();
    await expect(page.getByRole("heading", { name: "잡힌 세션", level: 2 })).toBeVisible();
    await expect(page.getByRole("heading", { name: "지난 세션", level: 2 })).toBeVisible();
    await expect(page.getByText("의뢰한 상담")).toHaveCount(2);
    await expect(page.getByText("이준 · 30분")).toBeVisible();
    await expect(page.getByText("이준 · 8월 15일 · 30분")).toBeVisible();
    await expect(page.getByText("50,000원 결제", { exact: false })).toBeVisible();
  });

  test("세션과 대화 버튼은 응답의 roomId로 이동한다", async ({ page }) => {
    await page.goto("/mypage/schedule");

    await expect(page.getByRole("link", { name: "세션 방 들어가기" })).toHaveAttribute(
      "href",
      "/rooms/92",
    );
    await expect(page.getByRole("link", { name: "대화 열기" })).toHaveAttribute(
      "href",
      "/rooms/92",
    );

    await page.getByRole("link", { name: "세션 방 들어가기" }).click();
    await expect(page).toHaveURL(/\/rooms\/92$/);
  });

  test("개발자 프로필이 없어도 마이페이지에서 일정으로 갈 수 있다", async ({ page }) => {
    await page.goto("/mypage");

    await page.getByRole("link", { name: "일정 관리" }).click();

    await expect(page).toHaveURL(/\/mypage\/schedule$/);
    await expect(page.getByRole("heading", { name: "내 일정", level: 1 })).toBeVisible();
  });
});
