import { expect, test } from '@playwright/test';

/**
 * 요청서 수정. 목 서버가 닿아야 합니다(BACKEND_API_BASE_URL).
 * 진단 31 의 미해결 요구사항 3건이 수정 대상이고, 저장은 버튼 하나가 요청 하나입니다.
 * 404 는 없는 진단 재현용 예약값입니다.
 */
test.describe('요청서 수정', () => {
  test.beforeEach(async ({ page }) => {
    await page.setViewportSize({ width: 1260, height: 1100 });
  });

  test('요구사항과 확인 문장을 입력칸으로 보여준다', async ({ page }) => {
    await page.goto('/checkup/31/request/edit');

    await expect(page.getByRole('heading', { name: '요청서 수정' })).toBeVisible();
    await expect(page.getByText('수제 비누 가게 · 요청서의 요구사항을 수정합니다')).toBeVisible();

    // 확인 문장은 고칠 수 있어야 하므로 입력칸입니다.
    await expect(
      page.getByRole('textbox', { name: '확인 문장' }).first(),
    ).toHaveValue('결제를 50번 연속으로 넣어도 모두 성공한다');
  });

  test('직접 해결한 요구사항은 수정 대상이 아니다', async ({ page }) => {
    await page.goto('/checkup/31/request/edit');

    // 요청서에 없는 것이라 여기에도 없습니다.
    await expect(page.getByText('service_role 키 노출 정리')).toHaveCount(0);
    await expect(page.getByText('3건')).toBeVisible();
  });

  test('확인 문장을 더하고 뺄 수 있다', async ({ page }) => {
    await page.goto('/checkup/31/request/edit');

    const criteria = page.getByRole('textbox', { name: '확인 문장' });
    const before = await criteria.count();

    await page.getByRole('button', { name: '+ 확인 문장 추가' }).first().click();
    await expect(criteria).toHaveCount(before + 1);

    await page.getByRole('button', { name: '확인 문장 삭제' }).first().click();
    await expect(criteria).toHaveCount(before);
  });

  test('요구사항을 지우면 목록에서 빠진다', async ({ page }) => {
    await page.goto('/checkup/31/request/edit');

    await page.getByRole('button', { name: '요구사항 삭제' }).first().click();

    await expect(page.getByText('결제 간헐 실패 수정')).toHaveCount(0);
    await expect(page.getByText('2건')).toBeVisible();
  });

  test('요구사항을 모두 빼면 저장할 수 없다', async ({ page }) => {
    await page.goto('/checkup/31/request/edit');

    for (let i = 0; i < 3; i += 1) {
      await page.getByRole('button', { name: '요구사항 삭제' }).first().click();
    }

    await expect(page.getByRole('button', { name: '저장하기' })).toBeDisabled();
  });

  test('요구사항을 직접 더할 수 있다', async ({ page }) => {
    await page.goto('/checkup/31/request/edit');

    await page.getByRole('button', { name: '+ 요구사항 추가' }).click();
    await page.getByRole('textbox', { name: '무엇을 맡기고 싶으신가요?' }).fill('주문 취소 화면 추가');
    await page.getByRole('button', { name: '추가하기' }).click();

    await expect(page.getByText('주문 취소 화면 추가')).toBeVisible();
    await expect(page.getByText('4건')).toBeVisible();
  });

  test('저장하면 요청서 화면으로 돌아간다', async ({ page }) => {
    await page.goto('/checkup/31/request/edit');

    await page.getByRole('button', { name: '저장하기' }).click();

    await expect(page.getByText('요청서를 저장했습니다.')).toBeVisible();
    await expect(page).toHaveURL('/checkup/31/request');
  });

  test('요청서에서 이어진다', async ({ page }) => {
    await page.goto('/checkup/31/request');

    await page.getByRole('link', { name: '요청서 수정하기' }).click();

    await expect(page).toHaveURL('/checkup/31/request/edit');
  });

  test('없는 진단은 찾을 수 없다고 알린다', async ({ page }) => {
    await page.goto('/checkup/404/request/edit');

    await expect(page.getByText('요청서를 찾을 수 없습니다')).toBeVisible();
  });
});
