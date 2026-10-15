import { expect, test } from '@playwright/test';
import { signIn } from './support/session';

/**
 * 방 대화 화면. 목 서버가 닿아야 합니다(BACKEND_API_BASE_URL).
 * 방 91 은 목의 무료 상담 방으로, 대화 3건과 요청서 요약이 붙어 있습니다.
 */
test.describe('방 대화', () => {
  test.beforeEach(async ({ page }) => {
    await signIn(page);
  });

  test('상담방에 들어가면 대화와 요청서 요약이 보인다', async ({ page }) => {
    await page.goto('/rooms/91');

    await expect(page.getByRole('heading', { name: '수제 비누 가게' })).toBeVisible();
    await expect(page.getByText('요구사항 3건')).toBeVisible();
    await expect(page.getByText('보관은 30일 후 완전 삭제로 부탁드려요')).toBeVisible();
    await expect(page.getByText('8월 20일')).toBeVisible();
  });

  test('열린 방에서는 메시지를 보낼 수 있다', async ({ page }) => {
    await page.goto('/rooms/91');

    const composer = page.getByPlaceholder('메시지를 입력하세요');
    await composer.fill('그럼 그렇게 진행해 주세요');
    await page.getByRole('button', { name: '보내기' }).click();

    await expect(page.getByText('그럼 그렇게 진행해 주세요')).toBeVisible();
    await expect(composer).toHaveValue('');
  });

  test('없는 방은 찾을 수 없다고 알린다', async ({ page }) => {
    await page.goto('/rooms/404');

    await expect(page.getByText('방을 찾을 수 없습니다')).toBeVisible();
  });
});
