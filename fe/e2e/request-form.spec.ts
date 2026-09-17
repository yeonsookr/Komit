import { expect, test } from '@playwright/test';
import { signIn } from './support/session';

/**
 * 요청서. 목 서버가 닿아야 합니다(BACKEND_API_BASE_URL).
 * 진단 31 은 결함 4건 중 1건이 해결(RESOLVED)이라 요구사항 3건 · 제외 1건이 됩니다.
 * 404 는 없는 진단 재현용 예약값입니다.
 */
test.describe('요청서', () => {
  test.beforeEach(async ({ page }) => {
    await signIn(page);
    await page.setViewportSize({ width: 1280, height: 1000 });
  });

  test('요구사항과 확인 문장을 보여준다', async ({ page }) => {
    await page.goto('/checkup/31/request');

    await expect(page.getByRole('heading', { name: '요청서', level: 1 })).toBeVisible();
    await expect(page.getByText('수제 비누 가게 · 요구사항 3건')).toBeVisible();

    // 확인 문장은 qaCriteria[].testCase 를 그대로 씁니다.
    await expect(page.getByText('결제를 50번 연속으로 넣어도 모두 성공한다')).toBeVisible();
    await expect(page.getByText('관리자 주소로 들어가면 로그인 화면이 뜬다')).toBeVisible();
  });

  test('직접 처리한 요구사항은 제외한 것으로 내려간다', async ({ page }) => {
    await page.goto('/checkup/31/request');

    await expect(page.getByRole('heading', { name: '요청서에서 제외한 것' })).toBeVisible();
    await expect(page.getByText('직접 처리')).toBeVisible();
  });

  test('요청서 수정하기는 수정 화면으로 간다', async ({ page }) => {
    await page.goto('/checkup/31/request');

    await expect(page.getByRole('link', { name: '요청서 수정하기' })).toHaveAttribute(
      'href',
      '/checkup/31/request/edit',
    );
  });

  test('개발자 찾기를 누르면 추천 화면으로 간다', async ({ page }) => {
    await page.goto('/checkup/31/request');

    await page.getByRole('button', { name: '개발자 찾기' }).click();

    await expect(page).toHaveURL(/\/recommendations\/\d+/);
    await expect(page.getByRole('heading', { name: '누구에게 상담을 요청할까요?' })).toBeVisible();
  });

  test('요구사항 선택에서 요청서로 이어진다', async ({ page }) => {
    await page.goto('/checkup/31/requirements');

    await page.getByRole('checkbox', { name: /관리자 API에 인증 추가/ }).check();
    await page.getByRole('button', { name: /요청서 만들기/ }).click();

    // 개발자 찾기로 바로 가지 않고 요청서를 먼저 보여줍니다.
    await expect(page).toHaveURL('/checkup/31/request');
    await expect(page.getByRole('heading', { name: '요청서', level: 1 })).toBeVisible();
  });

  test('없는 진단은 찾을 수 없다고 알린다', async ({ page }) => {
    await page.goto('/checkup/404/request');

    await expect(page.getByText('요청서를 찾을 수 없습니다')).toBeVisible();
  });
});
