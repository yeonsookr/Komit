import { expect, test } from '@playwright/test';
import { signIn } from './support/session';

/**
 * 개발자 프로필. 목 서버가 닿아야 합니다(BACKEND_API_BASE_URL).
 * 개발자 3(이준)은 경력 2건 · 포트폴리오 1건 · 가격표 7줄 · 리뷰 2건을 가집니다.
 * 404 는 없음 재현용 예약값입니다.
 */
test.describe('개발자 프로필', () => {
  test.beforeEach(async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 1000 });
  });

  test('소개와 활동 시간대를 보여준다', async ({ page }) => {
    await page.goto('/developers/3');

    await expect(page.getByRole('heading', { name: '이준', level: 1 })).toBeVisible();
    await expect(page.getByText('DB 연결 5년')).toBeVisible();
    await expect(page.getByText('10:00 – 19:00')).toBeVisible();
    await expect(page.getByText('작은 가게 서비스를 주로 맡습니다.')).toBeVisible();
  });

  test('경력은 확인 상태를 구분해 보여준다', async ({ page }) => {
    await page.goto('/developers/3');

    await expect(page.getByText('카카오스타일')).toBeVisible();
    await expect(page.getByText('2021.03 – 2024.08')).toBeVisible();
    // 지금도 이어지는 경력은 '현재' 로 적습니다.
    await expect(page.getByText('2024.09 – 현재')).toBeVisible();
    await expect(page.getByText('확인됨')).toBeVisible();
    await expect(page.getByText('확인 중')).toBeVisible();
  });

  test('가격표를 컨설팅과 개발로 나눠 보여준다', async ({ page }) => {
    await page.goto('/developers/3');

    await expect(page.getByText('30분 · 한 가지 주제')).toBeVisible();
    await expect(page.getByText('인터넷에 올리기')).toBeVisible();
    // 개발 작업에만 걸리는 날수가 붙습니다.
    await expect(page.getByText('3일')).toBeVisible();
  });

  test('무료 상담 줄은 가격표와 별개로 그려진다', async ({ page }) => {
    await page.goto('/developers/3');

    // 가격표에 담기지 않는 고정 정책입니다(목 명세).
    await expect(page.getByText('무료', { exact: true })).toBeVisible();
  });

  test('리뷰는 가려진 이름과 답글을 보여준다', async ({ page }) => {
    await page.goto('/developers/3');

    await expect(page.getByText('김O미')).toBeVisible();
    await expect(page.getByText('말이 잘 통했습니다.')).toBeVisible();
    await expect(page.getByText('개발자 답글')).toBeVisible();
  });

  test('목록에서 이름을 누르면 프로필로 간다', async ({ page }) => {
    await page.goto('/developers');
    await page.getByRole('link', { name: '이준' }).click();

    await expect(page).toHaveURL('/developers/3');
    await expect(page.getByRole('heading', { name: '이준', level: 1 })).toBeVisible();
  });

  test('무료로 물어보기를 누르면 무료 상담방으로 간다', async ({ page }) => {
    await signIn(page);
    await page.goto('/developers/3');

    await page.getByRole('link', { name: '무료로 물어보기' }).click();

    await expect(page).toHaveURL('/rooms/91');
    await expect(page.getByRole('heading', { name: '수제 비누 가게' })).toBeVisible();
  });

  test('없는 개발자는 찾을 수 없다고 알린다', async ({ page }) => {
    await page.goto('/developers/404');

    await expect(page.getByText('개발자를 찾을 수 없습니다')).toBeVisible();
  });
});
