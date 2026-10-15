import { expect, test } from "@playwright/test";
import { signIn } from "./support/session";

const OLD_IMAGE = "https://cdn.komit.example.com/profiles/old.png";
const NEW_IMAGE = "https://cdn.komit.example.com/profiles/512.png";

const me = {
  userId: 12,
  name: "김미미",
  profileImageFileId: null,
  profileImageUrl: OLD_IMAGE,
  hasDevProfile: false,
  developerId: null,
  unreadNotificationCount: 0,
};

test.describe("내 프로필 사진", () => {
  test.beforeEach(async ({ page }) => {
    await signIn(page);
  });

  test("사진을 바꾸면 마이페이지와 헤더에 함께 반영한다", async ({ page }) => {
    let meCalls = 0;
    await page.route(/\/api\/users\/me$/, async (route) => {
      meCalls += 1;
      await route.fulfill({
        status: 200,
        json: {
          ...me,
          profileImageFileId: meCalls === 1 ? null : 512,
          profileImageUrl: meCalls === 1 ? OLD_IMAGE : NEW_IMAGE,
        },
      });
    });
    await page.route(/\/api\/users\/me\/profile-image$/, async (route) => {
      expect(route.request().method()).toBe("POST");
      expect(route.request().headers()["content-type"]).toContain("multipart/form-data");
      await route.fulfill({
        status: 200,
        json: { name: "김미미", email: "mimi@kakao.com", profileImageUrl: NEW_IMAGE },
      });
    });

    await page.goto("/mypage");
    await expect(page.locator("header img")).toHaveAttribute("src", OLD_IMAGE);

    await page.getByLabel("새 프로필 사진").setInputFiles({
      name: "profile.png",
      mimeType: "image/png",
      buffer: Buffer.from("profile-image"),
    });

    await expect(page.getByText("프로필 사진을 바꿨습니다.")).toBeVisible();
    await expect(page.locator("#profile-image img")).toHaveAttribute("src", NEW_IMAGE);
    await expect(page.locator("header img")).toHaveAttribute("src", NEW_IMAGE);
    expect(meCalls).toBeGreaterThanOrEqual(2);
  });

  test("이미지가 아닌 파일은 보내지 않고 안내한다", async ({ page }) => {
    let uploadCalls = 0;
    await page.route(/\/api\/users\/me$/, async (route) => {
      await route.fulfill({ status: 200, json: me });
    });
    await page.route(/\/api\/users\/me\/profile-image$/, async (route) => {
      uploadCalls += 1;
      await route.abort();
    });

    await page.goto("/mypage");
    await page.getByLabel("새 프로필 사진").setInputFiles({
      name: "profile.txt",
      mimeType: "text/plain",
      buffer: Buffer.from("not-an-image"),
    });

    await expect(page.getByText("이미지 파일을 선택해 주세요.")).toBeVisible();
    expect(uploadCalls).toBe(0);
  });

  test("변경에 실패하면 기존 사진을 유지한다", async ({ page }) => {
    await page.route(/\/api\/users\/me$/, async (route) => {
      await route.fulfill({ status: 200, json: me });
    });
    await page.route(/\/api\/users\/me\/profile-image$/, async (route) => {
      await route.fulfill({
        status: 400,
        json: { code: "FILE_NOT_ATTACHABLE", message: "쓸 수 없는 파일입니다." },
      });
    });

    await page.goto("/mypage");
    const currentImage = page.locator("#profile-image img");
    const previousUrl = await currentImage.getAttribute("src");
    expect(previousUrl).not.toBeNull();

    await page.getByLabel("새 프로필 사진").setInputFiles({
      name: "profile.png",
      mimeType: "image/png",
      buffer: Buffer.from("profile-image"),
    });

    await expect(page.getByText("쓸 수 없는 파일입니다.")).toBeVisible();
    await expect(currentImage).toHaveAttribute("src", previousUrl!);
  });
});
