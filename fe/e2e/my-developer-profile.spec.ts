import { expect, test } from "@playwright/test";
import { signIn } from "./support/session";

test.describe("개발자 내 프로필", () => {
  test.beforeEach(async ({ page }) => {
    await signIn(page);
    await page.goto("/mypage/profile");
  });

  test("조회한 프로필을 첨부 식별자까지 유지해 저장한다", async ({ page }) => {
    let requestBodyText: string | null = null;
    await page.route("**/api/users/me/developer-profile", async (route) => {
      requestBodyText = route.request().postData();
      await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({}) });
    });

    await page.getByPlaceholder("어떤 개발자인지 짧게 소개해 주세요").fill("쉬운 말로 설명하는 개발자입니다.");
    await page.getByRole("button", { name: "저장하고 프로필 공개" }).click();

    await expect.poll(() => requestBodyText).not.toBeNull();
    const requestBody = JSON.parse(requestBodyText!) as Record<string, unknown>;
    expect(requestBody.intro).toBe("쉬운 말로 설명하는 개발자입니다.");
    expect(requestBody.careers).toEqual(expect.arrayContaining([
      expect.objectContaining({ careerId: 43, evidenceFileId: 514 }),
    ]));
    expect(requestBody.portfolioItems).toEqual(expect.arrayContaining([
      expect.objectContaining({ portfolioItemId: 7, thumbnailFileId: 512 }),
    ]));
  });

  test("사진 변경은 수정 내용 폐기 확인 뒤 이동한다", async ({ page }) => {
    await page.getByPlaceholder("어떤 개발자인지 짧게 소개해 주세요").fill("수정 중인 소개");

    page.once("dialog", async (dialog) => dialog.dismiss());
    await page.getByRole("button", { name: "사진 바꾸기" }).click();
    await expect(page).toHaveURL(/\/mypage\/profile$/);

    page.once("dialog", async (dialog) => dialog.accept());
    await page.getByRole("button", { name: "사진 바꾸기" }).click();
    await expect(page).toHaveURL(/\/mypage#profile-image$/);
  });

  test("리뷰 답글은 프로필 전체 저장과 별도로 보낸다", async ({ page }) => {
    let replyBody: Record<string, unknown> | null = null;
    await page.route("**/api/reviews/84/reply", async (route) => {
      replyBody = route.request().postDataJSON() as Record<string, unknown>;
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          reviewId: 84,
          clientName: "최O진",
          contractType: "CONSULTING",
          devScore: 4,
          expScore: 5,
          content: "일정이 하루 밀렸지만 미리 알려주셔서 괜찮았습니다.",
          reply: "기다려 주셔서 감사합니다.",
          createdAt: "2026-08-29T14:20:00+09:00",
        }),
      });
    });

    await page.getByPlaceholder("답글을 입력해 주세요").fill("기다려 주셔서 감사합니다.");
    await page.getByRole("button", { name: "답글 저장" }).click();

    await expect.poll(() => replyBody).toEqual({ reply: "기다려 주셔서 감사합니다." });
  });
});
