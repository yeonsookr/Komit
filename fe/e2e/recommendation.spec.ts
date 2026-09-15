import { expect, test } from '@playwright/test';

/**
 * 개발자 추천 · 상담 요청. 목 서버가 닿아야 합니다(BACKEND_API_BASE_URL).
 * 회차 71 은 이준(항목 501) · 한서(항목 502)를 추천하고, 상담을 요청하면
 * 각각 방 91 · 95 가 열립니다. 404 는 없음 재현용 예약값입니다.
 */
test.describe('개발자 추천', () => {
  test.beforeEach(async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 950 });
  });

  test('추천된 개발자와 모집단 수를 보여준다', async ({ page }) => {
    await page.goto('/recommendations/71');

    await expect(page.getByRole('heading', { name: '누구에게 상담을 요청할까요?' })).toBeVisible();
    await expect(page.getByText('이준 개발자')).toBeVisible();
    await expect(page.getByText('한서 개발자')).toBeVisible();
    // candidateCount 128 중 추천 2명.
    await expect(page.getByText('128명 중 2명')).toBeVisible();
  });

  test('아무도 고르지 않으면 요청 버튼이 잠긴다', async ({ page }) => {
    await page.goto('/recommendations/71');

    await expect(page.getByRole('button', { name: '상담 요청' })).toBeDisabled();
  });

  test('고른 수가 버튼 문구에 반영된다', async ({ page }) => {
    await page.goto('/recommendations/71');

    await page.getByRole('checkbox', { name: /이준 개발자 선택/ }).check();
    await expect(page.getByRole('button', { name: '1명에게 상담 요청' })).toBeEnabled();

    await page.getByRole('checkbox', { name: /한서 개발자 선택/ }).check();
    await expect(page.getByRole('button', { name: '2명에게 상담 요청' })).toBeEnabled();

    // 다시 누르면 빠집니다.
    await page.getByRole('checkbox', { name: /이준 개발자 선택/ }).uncheck();
    await expect(page.getByRole('button', { name: '1명에게 상담 요청' })).toBeEnabled();
  });

  test('상담을 요청하면 열린 방으로 간다', async ({ page }) => {
    await page.goto('/recommendations/71');

    await page.getByRole('checkbox', { name: /이준 개발자 선택/ }).check();
    await page.getByRole('button', { name: '1명에게 상담 요청' }).click();

    // 항목 501 로 열리는 방은 91 입니다.
    await expect(page).toHaveURL('/rooms/91');
    await expect(page.getByRole('heading', { name: '수제 비누 가게' })).toBeVisible();
  });

  test('자세히를 누르면 그 개발자 프로필로 간다', async ({ page }) => {
    await page.goto('/recommendations/71');

    await page.getByRole('link', { name: '자세히 ›' }).first().click();

    await expect(page).toHaveURL('/developers/3');
  });

  test('없는 회차는 찾을 수 없다고 알린다', async ({ page }) => {
    await page.goto('/recommendations/404');

    await expect(page.getByText('추천 결과를 찾을 수 없습니다')).toBeVisible();
  });
});
