import { expect, test } from '@playwright/test';
import { signInAsAdmin } from './support/session';

test.describe('계약 양식', () => {
  test.beforeEach(async ({ page }) => {
    await signInAsAdmin(page);
  });

  test('보관된 옛 판도 목록에 남고 고르면 본문이 열린다', async ({ page }) => {
    await page.goto('/admin/contract-templates');

    await expect(page.getByRole('cell', { name: '사용 중' }).first()).toBeVisible();
    await expect(page.getByRole('cell', { name: '보관' }).first()).toBeVisible();

    await page.getByRole('link', { name: '개발 외주 표준계약서 v2' }).click();
    await expect(page).toHaveURL(/templateId=9/);
    await expect(page.getByRole('heading', { name: '양식 본문' })).toBeVisible();
  });

  /** 줄을 골랐는데 아래 본문이 그대로면 판을 나눈 뜻이 없습니다. */
  test('다른 판을 고르면 제목과 본문이 함께 바뀐다', async ({ page }) => {
    await page.goto('/admin/contract-templates');
    await expect(page.locator('pre')).toContainText('개발 외주 계약서');

    // 제목 링크가 아니라 줄 아무 데나 눌러도 열립니다.
    await page.getByRole('row', { name: /컨설팅 표준계약서 v2/ }).click();

    await expect(page).toHaveURL(/templateId=12/);
    await expect(page.locator('pre')).toContainText('컨설팅 계약서');
    await expect(page.locator('pre')).toContainText('{{session_datetime}}');
  });

  test('미리보기가 채울 수 없는 자리표시자를 알려 준다', async ({ page }) => {
    await page.goto('/admin/contract-templates/new');

    await page
      .getByLabel('양식 본문')
      .fill('<p>{{client_name}} 그리고 {{unknown_value}}</p>');
    await page.getByRole('button', { name: '미리보기' }).click();

    const dialog = page.getByRole('dialog');
    await expect(dialog.getByText('채울 수 없는 자리표시자가 있습니다')).toBeVisible();
    await expect(dialog.getByText('{{unknown_value}}')).toBeVisible();
  });

  test('제목과 본문이 비면 저장하지 않는다', async ({ page }) => {
    await page.goto('/admin/contract-templates/new');

    await page.getByRole('button', { name: '등록' }).click();

    await expect(page.getByText('양식 제목을 입력해 주세요.')).toBeVisible();
    await expect(page.getByText('양식 본문을 입력해 주세요.')).toBeVisible();
    await expect(page).toHaveURL(/\/admin\/contract-templates\/new/);
  });

  test('등록하면 새 판으로 저장되고 그 판이 열린다', async ({ page }) => {
    await page.goto('/admin/contract-templates/new');

    await page.getByLabel('제목').fill('개발 외주 표준계약서 v4');
    await page.getByLabel('양식 본문').fill('<h2>개발 외주 계약서</h2>');
    await page.getByRole('button', { name: '등록' }).click();

    await expect(page.getByText('양식을 등록했습니다.')).toBeVisible();
    await expect(page).toHaveURL(/\/admin\/contract-templates\?templateId=\d+/);
  });

  test('개정은 유형을 바꿀 수 없고 원본을 덮어쓰지 않는다', async ({ page }) => {
    await page.goto('/admin/contract-templates/14/edit');

    await expect(page.getByRole('heading', { name: '양식 개정', level: 1 })).toBeVisible();
    // 유형은 고정이라 고르는 요소 자체가 없습니다.
    await expect(page.getByRole('button', { name: '양식 유형 선택' })).toHaveCount(0);
    await expect(page.getByText('저장하면 새 판이 만들어지고 지금 판은 보관으로 내려갑니다.')).toBeVisible();
    // 본문은 고칠 판에서 시작합니다.
    await expect(page.getByLabel('양식 본문')).toContainText('개발 외주 계약서');
  });
});
