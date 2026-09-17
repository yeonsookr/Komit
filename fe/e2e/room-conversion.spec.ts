import { expect, test } from '@playwright/test';
import { signIn } from './support/session';

/**
 * 상담방 갈래 전환. 목 서버가 닿아야 합니다(BACKEND_API_BASE_URL).
 * 방 91 은 열린 무료 상담방이고, 전환하면 컨설팅은 92 · 개발은 93 이 열립니다.
 * 409 는 이미 갈래가 정해진 방의 재현용 예약값입니다.
 */
test.describe('상담방 갈래 전환', () => {
  test.beforeEach(async ({ page }) => {
    await signIn(page);
    await page.setViewportSize({ width: 1440, height: 950 });
  });

  test('열린 상담방에는 두 갈래 버튼이 있다', async ({ page }) => {
    await page.goto('/rooms/91');

    await expect(page.getByRole('button', { name: '컨설팅 계약 진행' })).toBeVisible();
    await expect(page.getByRole('button', { name: '개발 계약 진행' })).toBeVisible();
  });

  test('컨설팅·개발 방에는 갈래 버튼이 없다', async ({ page }) => {
    // 이미 갈래가 정해져 열린 방이므로 다시 고를 일이 없습니다.
    await page.goto('/rooms/92');
    await expect(page.getByRole('button', { name: '컨설팅 계약 진행' })).toHaveCount(0);

    await page.goto('/rooms/93');
    await expect(page.getByRole('button', { name: '개발 계약 진행' })).toHaveCount(0);
  });

  test('진행 전에 한 번 확인한다', async ({ page }) => {
    await page.goto('/rooms/91');
    // 클릭 전 hydration 을 기다립니다. 그러지 않으면 핸들러가 붙기 전에 눌립니다.
    await page.getByRole('button', { name: '개발 계약 진행' }).click();

    await expect(page.getByText('개발 계약으로 진행할까요?')).toBeVisible();

    // 취소하면 아무것도 바뀌지 않습니다.
    await page.getByRole('button', { name: '취소' }).click();
    await expect(page.getByText('개발 계약으로 진행할까요?')).toHaveCount(0);
    await expect(page.getByRole('button', { name: '개발 계약 진행' })).toBeVisible();
  });

  test('갈래를 정하면 안내와 이동 버튼으로 바뀐다', async ({ page }) => {
    await page.goto('/rooms/91');
    // 클릭 전 hydration 을 기다립니다. 그러지 않으면 핸들러가 붙기 전에 눌립니다.
    await page.getByRole('button', { name: '컨설팅 계약 진행' }).click();
    await page.getByRole('button', { name: '진행하기' }).click();

    // 상담이 닫히므로 입력바가 사라지고 안내가 대신 놓입니다.
    await expect(page.getByRole('button', { name: '컨설팅 방으로' })).toBeVisible();
    await expect(page.getByPlaceholder('메시지를 입력하세요')).toHaveCount(0);
    // 갈래가 정해졌으므로 고르는 버튼도 사라집니다.
    await expect(page.getByRole('button', { name: '컨설팅 계약 진행' })).toHaveCount(0);
  });

  test('이동 버튼을 누르면 새로 열린 방으로 간다', async ({ page }) => {
    await page.goto('/rooms/91');
    // 클릭 전 hydration 을 기다립니다. 그러지 않으면 핸들러가 붙기 전에 눌립니다.
    await page.getByRole('button', { name: '개발 계약 진행' }).click();
    await page.getByRole('button', { name: '진행하기' }).click();
    await page.getByRole('button', { name: '개발 방으로' }).click();

    // 개발 갈래로 열리는 방은 93 입니다.
    await expect(page).toHaveURL('/rooms/93');
  });
});
