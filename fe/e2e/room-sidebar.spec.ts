import { expect, test } from '@playwright/test';

/**
 * 의뢰 사이드바. 목 서버가 닿아야 합니다(BACKEND_API_BASE_URL).
 * 목에는 '수제 비누 가게' 프로젝트의 방 3개(상담 91 · 컨설팅 92 · 개발 93)가 있습니다.
 */
test.describe('의뢰 사이드바', () => {
  test('방을 프로젝트별로 묶어 보여준다', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 900 });
    await page.goto('/rooms/91');

    const sidebar = page.getByRole('navigation', { name: '의뢰 목록' });
    await expect(sidebar.getByText('수제 비누 가게')).toBeVisible();
    await expect(sidebar.getByRole('link')).toHaveCount(3);
    await expect(sidebar.getByText('상담')).toBeVisible();
    await expect(sidebar.getByText('컨설팅')).toBeVisible();
    await expect(sidebar.getByText('개발')).toBeVisible();
  });

  test('지금 보고 있는 방이 활성으로 표시된다', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 900 });
    await page.goto('/rooms/91');

    const sidebar = page.getByRole('navigation', { name: '의뢰 목록' });
    await expect(sidebar.locator('[aria-current="page"]')).toHaveAttribute('href', '/rooms/91');
  });

  test('목록에서 다른 방으로 이동하면 활성 표시가 따라간다', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 900 });
    await page.goto('/rooms/91');

    const sidebar = page.getByRole('navigation', { name: '의뢰 목록' });
    await sidebar.getByRole('link', { name: /개발/ }).click();

    await expect(page).toHaveURL('/rooms/93');
    await expect(sidebar.locator('[aria-current="page"]')).toHaveAttribute('href', '/rooms/93');
  });

  test('맡은 의뢰 탭은 개발자로 들어간 방만 보여준다', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 900 });
    await page.goto('/rooms/91');

    await page.getByRole('tab', { name: '맡은 의뢰' }).click();

    // 목의 내 정보는 developerId 가 null 이라 맡은 의뢰가 없습니다.
    await expect(page.getByText('아직 맡은 의뢰가 없습니다.')).toBeVisible();
  });

  test('좁은 화면에서는 목록이 접혀 대화가 먼저 보인다', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/rooms/91');

    const sidebar = page.getByRole('navigation', { name: '의뢰 목록' });
    await expect(sidebar.getByRole('link')).toHaveCount(0);

    await page.getByRole('button', { name: /의뢰 목록/ }).click();
    await expect(sidebar.getByRole('link')).toHaveCount(3);
  });
});
