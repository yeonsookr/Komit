import { expect, test } from '@playwright/test';
import { signIn } from './support/session';

/**
 * 의뢰 사이드바. 목 서버가 닿아야 합니다(BACKEND_API_BASE_URL).
 * 목에는 '수제 비누 가게' 프로젝트의 방 3개(상담 91 · 컨설팅 92 · 개발 93)가 있습니다.
 */
test.describe('의뢰 사이드바', () => {
  test.beforeEach(async ({ page }) => {
    await signIn(page);
  });

  test('방을 프로젝트별로 묶어 보여준다', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 900 });
    await page.goto('/rooms/91');

    // 방 수는 목 데이터에 따라 달라지므로 세지 않고, 묶임과 갈래 표시만 봅니다.
    const sidebar = page.getByRole('navigation', { name: '의뢰 목록' });
    await expect(sidebar.getByText('수제 비누 가게')).toBeVisible();
    await expect(sidebar.getByText('상담').first()).toBeVisible();
    await expect(sidebar.getByText('컨설팅').first()).toBeVisible();
    await expect(sidebar.getByText('개발').first()).toBeVisible();
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
    await sidebar.getByRole('link', { name: /개발/ }).first().click();

    await expect(page).toHaveURL(/\/rooms\/\d+$/);
    await expect(sidebar.locator('[aria-current="page"]')).toHaveCount(1);
  });

  /** 같은 사람이 의뢰인이면서 개발자일 수 있어 두 탭이 서로 다른 방을 보여줍니다. */
  test('맡은 의뢰 탭은 개발자로 들어간 방만 보여준다', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 900 });
    await page.goto('/rooms/91');

    const sidebar = page.getByRole('navigation', { name: '의뢰 목록' });
    await expect(sidebar.getByText('수제 비누 가게')).toBeVisible();

    await page.getByRole('tab', { name: '맡은 의뢰' }).click();

    await expect(sidebar.getByText('수제 비누 가게')).toBeHidden();
    await expect(sidebar.getByText('미미헤어샵 예약')).toBeVisible();
  });

  test('좁은 화면에서는 목록이 접혀 대화가 먼저 보인다', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/rooms/91');

    const sidebar = page.getByRole('navigation', { name: '의뢰 목록' });
    await expect(sidebar.getByRole('link')).toHaveCount(0);

    await page.getByRole('button', { name: /의뢰 목록/ }).click();
    await expect(sidebar.getByRole('link').first()).toBeVisible();
  });
});

/**
 * 헤더 「의뢰」의 도착지. 방을 고르지 않은 채 목록만 보여줍니다.
 */
test.describe('의뢰 목록 진입', () => {
  test.beforeEach(async ({ page }) => {
    await signIn(page);
  });

  test('헤더 의뢰를 누르면 방을 고르지 않은 목록으로 간다', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 900 });
    await page.goto('/rooms/91');

    await page.getByRole('navigation', { name: '주요 메뉴' }).getByRole('link', { name: '의뢰' }).click();

    await expect(page).toHaveURL(/\/rooms$/);
    await expect(page.getByText('의뢰를 골라 주세요')).toBeVisible();
  });

  /** 방 수는 목 데이터에 따라 달라지므로 세지 않고, 고른 방이 없다는 것만 봅니다. */
  test('목록만 보이고 활성으로 표시된 방이 없다', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 900 });
    await page.goto('/rooms');

    const sidebar = page.getByRole('navigation', { name: '의뢰 목록' });
    await expect(sidebar.getByRole('link').first()).toBeVisible();
    await expect(sidebar.locator('[aria-current="page"]')).toHaveCount(0);
  });

  test('방을 고르면 그 방으로 가고 활성 표시가 붙는다', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 900 });
    await page.goto('/rooms');

    const sidebar = page.getByRole('navigation', { name: '의뢰 목록' });
    await sidebar.getByRole('link').first().click();

    await expect(page).toHaveURL(/\/rooms\/\d+$/);
    await expect(sidebar.locator('[aria-current="page"]')).toHaveCount(1);
  });

  /** 대화가 없으니 목록을 접을 이유가 없습니다. 접으면 고를 것이 보이지 않습니다. */
  test('좁은 화면에서는 목록이 펼쳐진 채로 시작한다', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/rooms');

    const sidebar = page.getByRole('navigation', { name: '의뢰 목록' });
    await expect(sidebar.getByRole('link').first()).toBeVisible();
  });

  /** 막는 것은 `src/proxy.ts` 입니다. 로그인 뒤 보던 화면으로 돌아오도록 `next` 를 싣습니다. */
  test('로그인하지 않으면 로그인 화면으로 보낸다', async ({ context, page }) => {
    await context.clearCookies();

    await page.goto('/rooms');
    await expect(page).toHaveURL(/\/login\?next=%2Frooms$/);

    await page.goto('/rooms/91');
    await expect(page).toHaveURL(/\/login\?next=%2Frooms%2F91$/);
  });
});
