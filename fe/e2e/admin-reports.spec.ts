import { expect, test } from '@playwright/test';
import { signInAsAdmin } from './support/session';

/** 목이 실패 재현용으로 잡아 둔 값입니다(목 명세 영역 9). */
const MISSING_REPORT_ID = 404;

test.describe('신고 처리', () => {
  test.beforeEach(async ({ page }) => {
    await signInAsAdmin(page);
  });

  test('상태 필터와 고른 신고가 주소에 남는다', async ({ page }) => {
    await page.goto('/admin/reports');

    // 고르지 않아도 첫 줄이 펼쳐져 상세가 비어 있지 않습니다.
    await expect(page.getByRole('heading', { name: '신고 상세' })).toBeVisible();

    await page.getByRole('link', { name: /종결됨/ }).click();
    await expect(page).toHaveURL(/\/admin\/reports\?status=RESOLVED/);
    await expect(page.getByRole('cell', { name: '접수됨' })).toHaveCount(0);

    await page.getByRole('link', { name: /전체/ }).click();
    await expect(page).toHaveURL(/\/admin\/reports$/);
  });

  /**
   * 운영자는 날짜가 아니라 눈에 띄는 칸(계약 이름)을 누릅니다. 링크가 한 칸에만
   * 있으면 아무 일도 일어나지 않는 것처럼 보입니다.
   */
  test('날짜 칸이 아닌 곳을 눌러도 그 신고 상세가 열린다', async ({ page }) => {
    await page.goto('/admin/reports');

    // 처음에는 첫 줄(수제 비누 가게)의 상세가 펼쳐져 있습니다.
    await expect(page.getByText('화면 캡처.png')).toBeVisible();

    await page.getByRole('row', { name: /미미헤어샵 예약/ }).click();

    await expect(page).toHaveURL(/reportId=70/);
    await expect(page.getByText('대화 내용.pdf')).toBeVisible();
    await expect(page.getByText('화면 캡처.png')).toHaveCount(0);
  });

  test('없는 신고를 가리키면 목록은 남고 상세만 비운다', async ({ page }) => {
    await page.goto(`/admin/reports?reportId=${MISSING_REPORT_ID}`);

    await expect(page.getByText('고른 신고를 찾지 못했습니다')).toBeVisible();
    await expect(page.getByRole('heading', { name: '들어온 신고' })).toBeVisible();
  });

  test('종결은 확인을 받고, 되돌릴 수 없다고 알린다', async ({ page }) => {
    await page.goto('/admin/reports');

    await page.getByRole('button', { name: '종결 처리' }).click();
    await expect(page.getByRole('heading', { name: '이 신고를 종결할까요?' })).toBeVisible();
    await expect(page.getByText('되돌릴 수 없습니다')).toBeVisible();

    await page.getByRole('dialog').getByRole('button', { name: '종결 처리' }).click();
    await expect(page.getByText('신고를 종결했습니다.')).toBeVisible();
  });

  /**
   * 목은 상태를 저장하지 않아 「이미 종결된 신고」를 실제로 만들 수 없습니다. 이미
   * 종결된 건을 다시 종결했을 때 화면이 어떻게 구는지는 응답을 가로채서 봅니다
   * (컨벤션 14.2 — 외부 서버 상태에 의존하지 않도록 라우팅으로 가로챈다).
   */
  test('이미 종결된 신고는 서버가 막고 그 사실을 알린다', async ({ page }) => {
    await page.route('**/api/admin/reports/*/resolve', (route) =>
      route.fulfill({
        status: 409,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 'REPORT_ALREADY_RESOLVED',
          message: '이미 종결된 신고입니다.',
        }),
      }),
    );
    await page.goto('/admin/reports');

    await page.getByRole('button', { name: '종결 처리' }).click();
    await page.getByRole('dialog').getByRole('button', { name: '종결 처리' }).click();

    await expect(page.getByText('이미 종결된 신고입니다.')).toBeVisible();
  });
});
