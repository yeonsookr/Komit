import { expect, test } from '@playwright/test';

/**
 * 개발자 찾기. 목 서버(:8080)가 떠 있어야 합니다.
 * 목에는 한서(버그 잡기) · 이준(DB 연결) · 정하(인터넷에 올리기) 3명이 있습니다.
 */
test.describe('개발자 찾기', () => {
  test.beforeEach(async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 1000 });
  });

  test('목록과 전체 인원 수를 보여준다', async ({ page }) => {
    await page.goto('/developers');

    await expect(page.getByRole('heading', { name: '개발자 찾기' })).toBeVisible();
    await expect(page.getByRole('link', { name: '이준' })).toBeVisible();
    // totalDevelopers 128 중 조건에 맞는 3명.
    await expect(page.getByText('128')).toBeVisible();
  });

  test('분야를 고르면 주소에 담기고 목록이 좁혀진다', async ({ page }) => {
    await page.goto('/developers');

    await page.getByRole('button', { name: /분야 선택/ }).click();
    await page.getByRole('checkbox', { name: 'DB 연결' }).check();
    await page.getByRole('button', { name: '적용하기' }).click();

    await expect(page).toHaveURL(/fieldCodeIds=42/);
    await expect(page.getByRole('link', { name: '이준' })).toBeVisible();
    // 버그 잡기인 한서는 빠집니다.
    await expect(page.getByRole('link', { name: '한서' })).toHaveCount(0);
  });

  test('고른 분야는 칩으로 남고 ✕ 로 뺄 수 있다', async ({ page }) => {
    await page.goto('/developers?fieldCodeIds=42');

    await expect(page.getByRole('button', { name: /DB 연결/ })).toBeVisible();
    // ✕ 는 칩 버튼 안의 별도 버튼입니다.
    await page.getByRole('button', { name: /DB 연결/ }).getByRole('button', { name: '제거' }).click();

    await expect(page).not.toHaveURL(/fieldCodeIds/);
    await expect(page.getByRole('link', { name: '한서' })).toBeVisible();
  });

  test('이름으로 검색한다', async ({ page }) => {
    await page.goto('/developers');

    await page.getByRole('button', { name: '문제' }).click();
    await page.getByRole('option', { name: '개발자명' }).click();
    await page.getByLabel('검색어').fill('이준');
    await page.getByRole('button', { name: '검색하기' }).click();

    await expect(page).toHaveURL(/searchMode=name/);
    await expect(page.getByRole('link', { name: '이준' })).toBeVisible();
    await expect(page.getByRole('link', { name: '정하' })).toHaveCount(0);
  });

  test('정렬을 바꾸면 주소에 담긴다', async ({ page }) => {
    await page.goto('/developers');

    await page.getByRole('button', { name: /실력 높은 순/ }).click();
    await page.getByRole('option', { name: '비용 낮은 순' }).click();

    await expect(page).toHaveURL(/sort=price%2Casc/);
  });

  test('주소로 들어와도 같은 조건이 그려진다', async ({ page }) => {
    await page.goto('/developers?searchMode=name&keyword=%EC%9D%B4%EC%A4%80&sort=price%2Casc');

    await expect(page.getByLabel('검색어')).toHaveValue('이준');
    await expect(page.getByRole('button', { name: /비용 낮은 순/ })).toBeVisible();
  });
});
