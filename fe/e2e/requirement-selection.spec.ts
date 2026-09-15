import { expect, test } from '@playwright/test';

/**
 * 요구사항 선택. 목 서버가 닿아야 합니다(BACKEND_API_BASE_URL).
 * 진단 31 은 결함 4건(그중 하나는 이미 해결됨)을 가지고, 요청서를 만들면
 * 추천 회차가 열립니다. 404 는 없는 진단 재현용 예약값입니다.
 */
test.describe('요구사항 선택', () => {
  test.beforeEach(async ({ page }) => {
    await page.setViewportSize({ width: 1200, height: 1000 });
  });

  test('머리에 준비도와 진단 수치를 보여준다', async ({ page }) => {
    await page.goto('/checkup/31/requirements');

    await expect(page.getByText('준비도')).toBeVisible();
    await expect(page.getByText('수제 비누를 파는 작은 온라인 가게')).toBeVisible();
    await expect(page.getByText('11건')).toBeVisible();
    await expect(page.getByText('3건 해결')).toBeVisible();
  });

  test('고른 수가 아래 바와 머리에 함께 반영된다', async ({ page }) => {
    await page.goto('/checkup/31/requirements');

    await expect(page.getByRole('button', { name: /요청서 만들기/ })).toBeDisabled();

    await page.getByRole('checkbox', { name: /관리자 API에 인증 추가/ }).check();
    await expect(page.getByText('1건 선택됨').first()).toBeVisible();

    await page.getByRole('checkbox', { name: /개인정보처리방침/ }).check();
    await expect(page.getByText('2건 선택됨').first()).toBeVisible();
    await expect(page.getByRole('button', { name: /요청서 만들기/ })).toBeEnabled();
  });

  test('이미 해결된 요구사항은 고를 수 없다', async ({ page }) => {
    await page.goto('/checkup/31/requirements');

    // 목의 결함 314 는 RESOLVED 라 취소선과 「되돌리기」 로 나옵니다.
    await expect(page.getByRole('checkbox', { name: /service_role/ })).toBeDisabled();
    await expect(page.getByRole('button', { name: '되돌리기' })).toBeVisible();
  });

  test('직접 해결 처리하면 고른 목록에서도 빠진다', async ({ page }) => {
    await page.goto('/checkup/31/requirements');

    await expect(page.getByRole('button', { name: '되돌리기' })).toHaveCount(1);

    await page.getByRole('checkbox', { name: /관리자 API에 인증 추가/ }).check();
    await expect(page.getByText('1건 선택됨').first()).toBeVisible();

    await page.getByRole('button', { name: '직접 해결했어요' }).nth(1).click();

    // 해결된 줄이 하나 늘고, 고른 목록에서도 빠집니다.
    // (목은 경로의 id 만 echo 하고 본문은 고정 예시를 주므로 제목은 확인하지 않습니다)
    await expect(page.getByRole('button', { name: '되돌리기' })).toHaveCount(2);
    await expect(page.getByText('0건 선택됨').first()).toBeVisible();
  });

  test('요구사항을 직접 추가할 수 있다', async ({ page }) => {
    await page.goto('/checkup/31/requirements');

    await page.getByRole('button', { name: '+ 요구사항 추가' }).click();
    await page.getByRole('textbox').first().fill('주문 취소 화면 추가');
    await page.getByRole('button', { name: '추가하기' }).click();

    await expect(page.getByText('요구사항을 추가했습니다.')).toBeVisible();
  });

  test('요청서를 만들면 추천 화면으로 간다', async ({ page }) => {
    await page.goto('/checkup/31/requirements');

    await page.getByRole('checkbox', { name: /관리자 API에 인증 추가/ }).check();
    await page.getByRole('button', { name: /요청서 만들기/ }).click();

    await expect(page).toHaveURL(/\/recommendations\/\d+/);
    await expect(page.getByRole('heading', { name: '누구에게 상담을 요청할까요?' })).toBeVisible();
  });

  test('진단서에서 이어진다', async ({ page }) => {
    await page.goto('/checkup/31/result');
    await page.getByRole('link', { name: /요청서 만들기/ }).click();

    await expect(page).toHaveURL('/checkup/31/requirements');
  });

  test('없는 진단은 찾을 수 없다고 알린다', async ({ page }) => {
    await page.goto('/checkup/404/requirements');

    await expect(page.getByText('요구사항을 찾을 수 없습니다')).toBeVisible();
  });
});
