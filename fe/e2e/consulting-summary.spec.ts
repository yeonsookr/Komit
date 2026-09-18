import { expect, test } from '@playwright/test';
import { signIn } from './support/session';

/**
 * 컨설팅 세션 요약 (영역 4).
 *
 * 요약은 세션에 달려 있지만 화면은 방의 「요약」 탭입니다 — 세션이 끝난 뒤 다시 찾아올
 * 곳은 방이기 때문입니다.
 *
 * 목은 상태를 두지 않아 반영해도 다음 요청에 되돌아옵니다. 반영 직후의 화면까지만
 * 봅니다.
 */
const AS_CLIENT = 92;
const DEVELOPMENT_ROOM = 93;

const FIRST_CHANGE = '검수 기준 갱신 · 회원탈퇴 경로 구현';
const SECOND_CHANGE = '결제 실패 로그 수집';

test.describe('컨설팅 세션 요약', () => {
  test.beforeEach(async ({ page }) => {
    await signIn(page);
  });

  test('요약은 논의한 것 · 결정된 것 · 변경 후보 · 다음에 할 것으로 나뉜다', async ({ page }) => {
    await page.goto(`/rooms/${AS_CLIENT}/summary`);

    await expect(page.getByRole('heading', { name: '컨설팅 요약' })).toBeVisible();
    for (const block of ['논의한 것', '결정된 것', '요청서 변경 후보', '다음에 할 것']) {
      await expect(page.getByRole('heading', { name: new RegExp(block) })).toBeVisible();
    }
  });

  test('고른 후보만 요청서에 반영한다', async ({ page }) => {
    await page.goto(`/rooms/${AS_CLIENT}/summary`);

    // 아무것도 고르지 않으면 반영할 것이 없습니다.
    await expect(page.getByRole('button', { name: '반영할 후보 고르기' })).toBeDisabled();

    await page.getByRole('checkbox', { name: new RegExp(FIRST_CHANGE) }).check();
    await page.getByRole('button', { name: '1건 요청서에 반영' }).click();

    await expect(page.getByText('1건을 요청서에 반영했습니다')).toBeVisible();
  });

  test('반영한 후보는 다시 고를 수 없다', async ({ page }) => {
    await page.goto(`/rooms/${AS_CLIENT}/summary`);

    await page.getByRole('checkbox', { name: new RegExp(FIRST_CHANGE) }).check();
    await page.getByRole('checkbox', { name: new RegExp(SECOND_CHANGE) }).check();
    await page.getByRole('button', { name: '2건 요청서에 반영' }).click();

    await expect(page.getByText('모두 요청서에 반영했습니다')).toBeVisible();
    await expect(page.getByRole('checkbox')).toHaveCount(0);
  });

  test('요약 탭은 컨설팅 방에만 있다', async ({ page }) => {
    await page.goto(`/rooms/${DEVELOPMENT_ROOM}/summary`);

    await expect(page.getByText('방을 찾을 수 없습니다')).toBeVisible();
  });
});
