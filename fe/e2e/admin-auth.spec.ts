import { expect, test } from '@playwright/test';
import { signIn, signInAsAdmin } from './support/session';

/**
 * 운영 콘솔의 출입 경계.
 *
 * 여기서 보는 것은 화면 모양이 아니라 **두 세션이 서로를 침범하지 않는다**는 사실입니다.
 * 사용자와 운영자는 계정 체계가 완전히 별개라, 한쪽 쿠키로 다른 쪽이 열리면 그 자체가
 * 권한 누수입니다.
 */
test.describe('운영 콘솔 출입', () => {
  test('쿠키가 없으면 운영 로그인으로 보내고 돌아갈 곳을 싣는다', async ({ page }) => {
    await page.goto('/admin/reports');

    await expect(page).toHaveURL(/\/admin\/login\?next=%2Fadmin%2Freports/);
    await expect(page.getByRole('heading', { name: '운영 콘솔 로그인' })).toBeVisible();
  });

  test('사용자로 로그인해 있어도 운영 콘솔은 열리지 않는다', async ({ page }) => {
    await signIn(page);
    await page.goto('/admin');

    await expect(page).toHaveURL(/\/admin\/login/);
  });

  test('아이디나 비밀번호가 맞지 않으면 들어가지 못한다', async ({ page }) => {
    await page.goto('/admin/login');
    await page.getByLabel('아이디').fill('nobody');
    await page.getByLabel('비밀번호').fill('wrong-password');
    await page.getByRole('button', { name: '로그인' }).click();

    await expect(page.getByText('아이디 또는 비밀번호가 맞지 않습니다.')).toBeVisible();
    await expect(page).toHaveURL(/\/admin\/login/);
  });

  test('로그인하면 홈이 열리고 로그아웃하면 사용자 세션은 남는다', async ({ page }) => {
    await signIn(page);

    await page.goto('/admin/login');
    await page.getByLabel('아이디').fill('soomin');
    await page.getByLabel('비밀번호').fill('komit-console');
    await page.getByRole('button', { name: '로그인' }).click();

    await expect(page).toHaveURL(/\/admin$/);
    await expect(page.getByRole('heading', { name: '운영 콘솔', level: 1 })).toBeVisible();

    // 로그아웃은 상단바에 내놓지 않고 아바타 메뉴 안에 둡니다(`9_클릭_아바타메뉴`).
    await page.getByRole('button', { name: 'soomin 메뉴' }).click();
    await page.getByRole('button', { name: '로그아웃' }).click();
    await expect(page).toHaveURL(/\/admin\/login/);

    const cookies = await page.context().cookies();
    expect(cookies.find((cookie) => cookie.name === 'komit_admin_token')?.value ?? '').toBe('');
    expect(cookies.find((cookie) => cookie.name === 'komit_access_token')?.value).toBeTruthy();
  });

  test('홈에서 업무 세 가지로 갈 수 있다', async ({ page }) => {
    await signInAsAdmin(page);
    await page.goto('/admin');

    await expect(page.getByRole('heading', { name: '신고 처리', level: 3 })).toBeVisible();
    await expect(page.getByRole('heading', { name: '정산 대사', level: 3 })).toBeVisible();
    await expect(page.getByRole('heading', { name: '계약 양식', level: 3 })).toBeVisible();

    await page.getByRole('link', { name: '열기' }).first().click();
    await expect(page).toHaveURL(/\/admin\/reports/);
  });
});
