import { expect, test } from '@playwright/test';
import { signInAsAdmin } from './support/session';

test.describe('정산 대사', () => {
  test.beforeEach(async ({ page }) => {
    await signInAsAdmin(page);
  });

  test('조회 전용이다 — 다시 돌리거나 고치는 버튼이 없다', async ({ page }) => {
    await page.goto('/admin/reconciliation');

    await expect(page.getByRole('heading', { name: '정산 대사', level: 1 })).toBeVisible();
    await expect(page.getByText('매일 09:00 자동 실행')).toBeVisible();

    // 대사는 Escrow 소유입니다. 콘솔에 실행·수정 버튼이 생기면 두 장부가 갈라집니다.
    await expect(page.getByRole('button', { name: /실행|다시|수정|맞추기/ })).toHaveCount(0);
  });

  test('장부 합계와 차이, 지난 이력의 차이 발생을 구분해 보여 준다', async ({ page }) => {
    await page.goto('/admin/reconciliation');

    await expect(page.getByText('Komit 장부 합계')).toBeVisible();
    await expect(page.getByText('결제대행사 실제 잔고')).toBeVisible();

    const history = page.getByRole('table').last();
    await expect(history.getByText('차이 발생')).toBeVisible();
    await expect(history.getByText('일치').first()).toBeVisible();
  });
});
