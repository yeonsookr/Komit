import { expect, test } from "@playwright/test";
import { signIn } from "./support/session";

test.describe("문제 신고", () => {
  test.beforeEach(async ({ page }) => {
    await signIn(page);
  });

  test("신고 내용과 선택한 파일 한 개를 전송하고 진행 화면으로 돌아간다", async ({
    page,
  }) => {
    await page.route(/\/api\/contracts\/130\/reports$/, async (route) => {
      expect(route.request().method()).toBe("POST");
      const body = await route.request().postDataBuffer();
      const text = body?.toString("utf8") ?? "";
      expect(text).toContain('name="reason"');
      expect(text).toContain("작업 진행 상황을 확인해 주세요.");
      expect(text).toContain('name="file"; filename="evidence.txt"');
      await route.fulfill({
        status: 201,
        json: {
          reportId: 77,
          contractId: 130,
          reporterUserId: 12,
          targetUserId: 30,
          reason: "작업 진행 상황을 확인해 주세요.",
          fileId: 81,
          status: "OPEN",
          createdAt: "2026-09-18T13:00:00+09:00",
          resolvedAt: null,
        },
      });
    });

    await page.goto("/rooms/93/report");

    await expect(page.getByText("계약 진행 중 문제가 생겼나요?")).toBeVisible();
    await expect(page.getByRole("button", { name: "신고하기" })).toBeVisible();
    await page.getByLabel("신고 내용을 적어주세요").fill("  작업 진행 상황을 확인해 주세요.  ");
    await page.locator('input[type="file"]').setInputFiles({
      name: "evidence.txt",
      mimeType: "text/plain",
      buffer: Buffer.from("evidence"),
    });
    await expect(page.getByText("evidence.txt")).toBeVisible();
    await page.getByRole("button", { name: "신고하기" }).click();

    await expect(page.getByText("신고가 접수되었습니다.")).toBeVisible();
    await expect(page).toHaveURL("/rooms/93/progress");
  });

  test("공백 내용은 요청하지 않고 입력 안내를 표시한다", async ({ page }) => {
    let requests = 0;
    await page.route(/\/api\/contracts\/130\/reports$/, async (route) => {
      requests += 1;
      await route.abort();
    });

    await page.goto("/rooms/93/report");
    await page.getByLabel("신고 내용을 적어주세요").fill("   ");
    await page.getByRole("button", { name: "신고하기" }).click();

    await expect(page.getByText("신고 내용을 입력해 주세요.")).toBeVisible();
    expect(requests).toBe(0);
  });

  test("255자까지만 입력한다", async ({ page }) => {
    await page.goto("/rooms/93/report");
    await page.getByLabel("신고 내용을 적어주세요").fill("가".repeat(256));

    await expect(page.getByLabel("신고 내용을 적어주세요")).toHaveValue("가".repeat(255));
    await expect(page.getByText("255 / 255")).toBeVisible();
  });

  test("활성 신고가 있으면 서버 안내와 작성 내용을 유지한다", async ({ page }) => {
    await page.route(/\/api\/contracts\/130\/reports$/, async (route) => {
      await route.fulfill({
        status: 409,
        json: {
          code: "REPORT_ALREADY_OPEN",
          message: "이미 접수되어 확인 중인 신고가 있습니다.",
        },
      });
    });

    await page.goto("/rooms/93/report");
    const reason = page.getByLabel("신고 내용을 적어주세요");
    await reason.fill("기존 신고가 있는지 확인합니다.");
    await page.getByRole("button", { name: "신고하기" }).click();

    await expect(page.getByText("이미 접수되어 확인 중인 신고가 있습니다.")).toBeVisible();
    await expect(reason).toHaveValue("기존 신고가 있는지 확인합니다.");
    await expect(page).toHaveURL("/rooms/93/report");
  });
});
