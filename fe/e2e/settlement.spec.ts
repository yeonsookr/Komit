import { expect, test } from '@playwright/test';

/**
 * 정산. 목 서버가 닿아야 합니다(BACKEND_API_BASE_URL).
 *
 * 로그인이 필요한 화면이라 세션 쿠키를 심고 시작합니다. 목은 토큰을 검사하지 않아
 * 값은 아무거나 됩니다 — 목에 검사가 들어가면 이 픽스처도 함께 고쳐야 합니다.
 *
 * 목의 2026년 8월은 소비 670,000원(환불 30,000원 포함) · 소득 650,000원 ·
 * 예치금 650,000원이고, '수제 비누 가게' 계약에 확인 대기 2건이 남아 있습니다.
 */
const MONTH = '2026-08';

test.describe('정산', () => {
  test.beforeEach(async ({ context, page }) => {
    await context.addCookies([
      { name: 'komit_access_token', value: 'e2e-token', url: 'http://localhost:3000' },
    ]);
    await page.setViewportSize({ width: 1440, height: 1000 });
  });

  test('로그인하지 않으면 로그인 화면으로 보낸다', async ({ context, page }) => {
    await context.clearCookies();
    await page.goto('/settlements');
    await expect(page).toHaveURL(/\/login$/);
  });

  test('왼쪽 목록은 소비와 소득을 함께 보여준다', async ({ page }) => {
    await page.goto(`/settlements?month=${MONTH}`);

    // 의뢰인이면서 개발자일 수 있어 역할로 가르지 않습니다.
    const sidebar = page.getByRole('navigation', { name: '정산 목록' });
    await expect(sidebar.getByRole('link', { name: /소비 내역/ })).toBeVisible();
    await expect(sidebar.getByRole('link', { name: /소득 내역/ })).toBeVisible();
    await expect(sidebar.getByRole('link', { name: /예치금/ })).toBeVisible();
  });

  test('소비 내역은 그 달의 합계와 환불을 보여준다', async ({ page }) => {
    await page.goto(`/settlements?month=${MONTH}`);

    await expect(page.getByRole('heading', { level: 1, name: '소비 내역' })).toBeVisible();
    await expect(page.getByText('2026년 8월 합계')).toBeVisible();
    // 환불은 음수로 들어와 합계에서 이미 빠져 있습니다.
    await expect(page.getByText(/나간 돈 700,000원 − 환불 30,000원/)).toBeVisible();
  });

  test('달을 넘기면 주소의 month 가 바뀐다', async ({ page }) => {
    await page.goto(`/settlements?month=${MONTH}`);

    await page.getByRole('link', { name: '지난달' }).click();
    await expect(page).toHaveURL(/month=2026-07/);
    await expect(page.getByText('2026년 7월 합계')).toBeVisible();

    await page.getByRole('link', { name: '다음 달' }).click();
    await expect(page).toHaveURL(/month=2026-08/);
  });

  test('달을 누르면 연도와 달을 고르는 판이 열린다', async ({ page }) => {
    await page.goto(`/settlements?month=${MONTH}`);

    const panel = page.getByRole('dialog', { name: '연도와 달 고르기' });
    await expect(panel).toBeHidden();

    await page.getByRole('button', { name: '2026년 8월' }).click();
    await expect(panel).toBeVisible();
    await expect(panel.getByRole('link')).toHaveCount(12);
    await expect(panel.locator('[aria-current="page"]')).toHaveText('8월');

    // 연도 넘기기는 아직 고른 것이 아니라 주소를 건드리지 않습니다.
    await panel.getByRole('button', { name: '이전 해' }).click();
    await expect(panel.getByText('2025년')).toBeVisible();
    await expect(page).toHaveURL(new RegExp(`month=${MONTH}`));

    await panel.getByRole('link', { name: '3월' }).click();
    await expect(page).toHaveURL(/month=2025-03/);
    await expect(page.getByText('2025년 3월 합계')).toBeVisible();
    await expect(panel).toBeHidden();
  });

  test('소득 내역은 받을 돈과 정산 계좌를 보여준다', async ({ page }) => {
    await page.goto(`/settlements/income?month=${MONTH}`);

    await expect(page.getByRole('heading', { level: 1, name: '소득 내역' })).toBeVisible();
    await expect(page.getByText('계약이 끝나면 받을 돈')).toBeVisible();
    await expect(page.getByRole('heading', { name: '정산 계좌' })).toBeVisible();
    await expect(page.getByText('국민은행')).toBeVisible();
    await expect(page.getByRole('link', { name: /마이페이지에서 변경/ })).toBeVisible();
  });

  test('예치금은 일괄 지급이라는 것을 알린다', async ({ page }) => {
    await page.goto(`/settlements/deposits?month=${MONTH}`);

    await expect(page.getByRole('heading', { level: 1, name: '예치금' })).toBeVisible();
    // R-PAY-03 — 통과해도 계약이 끝날 때까지 예치금에 남습니다.
    await expect(
      page.getByText('통과시킨 작업의 대금도 계약이 전부 끝날 때까지 예치금에 남습니다.'),
    ).toBeVisible();
    await expect(page.getByRole('heading', { name: '수제 비누 가게' })).toBeVisible();
  });

  test('거래 묶음은 접었다 펼 수 있다', async ({ page }) => {
    await page.goto(`/settlements/deposits?month=${MONTH}`);

    const item = page.getByText('1차 · 관리자 API에 인증 추가');
    await expect(item).toBeVisible();

    await page.getByText('수제 비누 가게 · 이준').click();
    await expect(item).toBeHidden();
  });
});
