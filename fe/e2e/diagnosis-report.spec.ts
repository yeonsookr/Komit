import { expect, test } from '@playwright/test';

/**
 * 진단서. 목 서버가 닿아야 합니다(BACKEND_API_BASE_URL).
 * 진단 31 은 준비도 52점 · 2차 진단이고, 결함 4건 중 1건이 의뢰 내용에서 나왔습니다.
 * 404 는 없는 진단 재현용 예약값입니다.
 */
test.describe('진단서', () => {
  test.beforeEach(async ({ page }) => {
    await page.setViewportSize({ width: 1000, height: 900 });
  });

  test('머리에 회차와 준비도 점수를 보여준다', async ({ page }) => {
    await page.goto('/checkup/31/result');

    await expect(page.getByRole('heading', { name: '발견된 문제점' })).toBeVisible();
    await expect(page.getByText('2차 진단')).toBeVisible();
    await expect(page.getByText('52')).toBeVisible();
    await expect(page.getByText('런칭 준비도')).toBeVisible();
  });

  test('의뢰 내용에서 나온 결함을 따로 보여준다', async ({ page }) => {
    await page.goto('/checkup/31/result');

    await expect(page.getByText('의뢰 내용', { exact: true })).toBeVisible();
    await expect(page.getByText('결제 버튼을 누르면 가끔 아무 반응이 없어요')).toBeVisible();
    // 코드 조각은 API 에 없어 위치만 보여줍니다.
    await expect(page.getByText('app/api/checkout/route.ts:47')).toBeVisible();
    await expect(page.getByText('결제 응답을 기다리지 않고 화면을 넘깁니다.')).toBeVisible();
  });

  test('나머지 결함은 분류 배지와 함께 늘어놓는다', async ({ page }) => {
    await page.goto('/checkup/31/result');

    await expect(page.getByText('의뢰 내용 외 문제점')).toBeVisible();
    await expect(page.getByText('관리자 API에 인증 추가')).toBeVisible();
    await expect(page.getByText('service_role 키 노출 정리')).toBeVisible();
    // 보안 결함이 둘이므로 배지에 2건으로 적힙니다.
    await expect(page.getByText('보안 2건').first()).toBeVisible();
    await expect(page.getByText('위법 1건')).toBeVisible();
  });

  test('의뢰 내용에서 나온 결함은 아래 목록에 겹쳐 나오지 않는다', async ({ page }) => {
    await page.goto('/checkup/31/result');

    // isInput 이 참인 결함은 위 카드에만 있습니다.
    await expect(page.getByText('결제 간헐 실패 수정')).toHaveCount(0);
  });

  test('요청서 만들기로 이어진다', async ({ page }) => {
    await page.goto('/checkup/31/result');

    await expect(page.getByRole('link', { name: /요청서 만들기/ })).toHaveAttribute(
      'href',
      '/checkup/31/requirements',
    );
  });

  test('없는 진단은 찾을 수 없다고 알린다', async ({ page }) => {
    await page.goto('/checkup/404/result');

    await expect(page.getByText('진단서를 찾을 수 없습니다')).toBeVisible();
  });
});
