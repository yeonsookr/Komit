import { expect, test } from "@playwright/test";
import { signIn } from "./support/session";

const verification = {
  verificationId: 77301,
  bankName: "신한은행",
  maskedNumber: "···· 8842",
  accountHolder: "김미미",
  expiresAt: "2026-09-17T23:59:00+09:00",
};

const registeredAccount = {
  accountId: 23,
  bankName: "신한은행",
  maskedNumber: "···· 8842",
  accountHolder: "김미미",
};

test.describe("계좌 등록", () => {
  test.beforeEach(async ({ page }) => {
    await signIn(page);
  });

  test("계좌 정보를 입력해 1원을 보낸 뒤 네 자리로 등록한다", async ({ page }) => {
    await page.route(/\/api\/accounts\/verifications$/, async (route) => {
      expect(route.request().method()).toBe("POST");
      expect(route.request().postDataJSON()).toEqual({
        bankCodeId: 52,
        accountNumber: "11012345678842",
        accountHolder: "김미미",
      });
      await route.fulfill({ status: 201, json: verification });
    });

    await page.route(/\/api\/accounts\/verifications\/77301\/confirm$/, async (route) => {
      expect(route.request().method()).toBe("POST");
      expect(route.request().postDataJSON()).toEqual({ authCode: "5678" });
      await route.fulfill({ status: 200, json: registeredAccount });
    });

    await page.goto("/mypage/account");

    await expect(page).toHaveTitle("계좌 등록 | Komit");
    await expect(page.getByRole("heading", { name: "계좌 등록", level: 1 })).toBeVisible();
    await expect(page.getByText("신한은행 ···· 4412 · 예금주 김미미")).toBeVisible();

    await page.getByRole("button", { name: "은행을 선택해 주세요" }).click();
    await page.getByRole("button", { name: "신한은행", exact: true }).click();
    await page.getByLabel("계좌번호").fill("110-123-45678842");
    await expect(page.getByLabel("계좌번호")).toHaveValue("11012345678842");
    await page.getByLabel("예금주").fill("김미미");
    await page.getByRole("button", { name: "1원 보내기" }).click();

    await expect(page.getByRole("heading", { name: "계좌 확인", level: 1 })).toBeVisible();
    await expect(page.getByText("신한은행 ···· 8842 · 예금주 김미미")).toBeVisible();

    for (const [index, digit] of ["5", "6", "7", "8"].entries()) {
      await page.getByLabel(`확인 번호 ${index + 1}번째 자리`).fill(digit);
    }
    await page.getByRole("button", { name: "계좌 등록하기" }).click();

    await expect(page).toHaveURL(/\/mypage$/);
  });

  test("필수 정보와 네 자리가 채워지기 전에는 다음 요청을 막는다", async ({ page }) => {
    await page.route(/\/api\/accounts\/verifications$/, async (route) => {
      await route.fulfill({ status: 201, json: verification });
    });

    await page.goto("/mypage/account");
    await expect(page.getByRole("button", { name: "1원 보내기" })).toBeDisabled();

    await page.getByRole("button", { name: "은행을 선택해 주세요" }).click();
    await page.getByRole("button", { name: "신한은행", exact: true }).click();
    await page.getByLabel("계좌번호").fill("11012345678842");
    await page.getByLabel("예금주").fill("김미미");
    await page.getByRole("button", { name: "1원 보내기" }).click();

    await page.getByLabel("확인 번호 1번째 자리").fill("1");
    await page.getByLabel("확인 번호 2번째 자리").fill("2");
    await page.getByLabel("확인 번호 3번째 자리").fill("3");
    await expect(page.getByRole("button", { name: "계좌 등록하기" })).toBeDisabled();
  });

  test("확인 중에 계좌를 다시 입력할 수 있다", async ({ page }) => {
    await page.route(/\/api\/accounts\/verifications$/, async (route) => {
      await route.fulfill({ status: 201, json: verification });
    });

    await page.goto("/mypage/account");
    await page.getByRole("button", { name: "은행을 선택해 주세요" }).click();
    await page.getByRole("button", { name: "신한은행", exact: true }).click();
    await page.getByLabel("계좌번호").fill("11012345678842");
    await page.getByLabel("예금주").fill("김미미");
    await page.getByRole("button", { name: "1원 보내기" }).click();

    await page.getByRole("button", { name: "계좌 다시 입력" }).click();

    await expect(page.getByRole("heading", { name: "계좌 등록", level: 1 })).toBeVisible();
    await expect(page.getByLabel("계좌번호")).toHaveValue("11012345678842");
    await expect(page.getByLabel("예금주")).toHaveValue("김미미");
  });
});
