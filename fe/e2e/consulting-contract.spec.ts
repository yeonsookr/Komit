import { expect, test } from '@playwright/test';
import { signIn } from './support/session';

/**
 * 컨설팅 계약서 (영역 4).
 *
 * 목에서 컨설팅 방은 둘입니다 — 92 는 내가 의뢰인, 107 은 내가 개발자입니다.
 * 계약서는 개발자가 쓰므로 두 방에서 보이는 것이 다릅니다.
 */
const AS_CLIENT = 92;
const AS_DEVELOPER = 107;

test.describe('컨설팅 계약서', () => {
  test.beforeEach(async ({ page }) => {
    await signIn(page);
  });

  test('컨설팅 방의 탭은 채팅·계약서·요약 셋이다', async ({ page }) => {
    await page.goto(`/rooms/${AS_CLIENT}`);

    const tabs = page.getByRole('tablist', { name: '방 메뉴' });
    await expect(tabs.getByRole('tab')).toHaveCount(3);
    await expect(tabs.getByRole('tab', { name: '요약' })).toBeVisible();
    // 진행·확인·리뷰는 작업을 여러 번 주고받는 개발 방의 것입니다.
    await expect(tabs.getByRole('tab', { name: '진행' })).toHaveCount(0);
    // 세션은 잡힌 시각에만 들어가는 것이라 탭이 아니라 채팅 위 배너에 있습니다.
    await expect(tabs.getByRole('tab', { name: '세션' })).toHaveCount(0);
  });

  test('의뢰인에게는 세션 구성과 결제 버튼이 보인다', async ({ page }) => {
    await page.goto(`/rooms/${AS_CLIENT}/contract`);

    await expect(page.getByText('회원탈퇴 경로와 결제 간헐 실패를 함께 봅니다')).toBeVisible();
    await expect(page.getByText('8월 29일 (토) 14:00')).toBeVisible();
    // 개발은 「예치」지만 컨설팅은 한 번 만나고 끝이라 「결제」입니다.
    await expect(page.getByRole('button', { name: /서명하고 .*원 결제/ })).toBeVisible();
  });

  test('계약서는 개발자가 쓴다 — 의뢰인은 작성 화면에 들어가지 못한다', async ({ page }) => {
    await page.goto(`/rooms/${AS_CLIENT}/contract/new`);

    await expect(page.getByText('계약서는 개발자가 씁니다')).toBeVisible();
  });

  test('개발자가 세션 구성을 정해 계약서를 보낸다', async ({ page }) => {
    await page.goto(`/rooms/${AS_DEVELOPER}/contract/new`);

    await expect(page.getByRole('heading', { name: '컨설팅 계약서 작성' })).toBeVisible();
    // 무엇을 다룰지와 가격이 비어 있으면 보낼 수 없습니다.
    await expect(page.getByRole('button', { name: '계약서 보내기' })).toBeDisabled();

    await page.getByLabel('다룰 항목').fill('회원탈퇴 경로와 결제 간헐 실패를 함께 봅니다');
    await page.getByLabel('날짜와 시간').fill('2026-08-29');
    await page.getByLabel('시', { exact: true }).selectOption('14');
    await page.getByLabel('세션 시간').selectOption('45');
    await page.getByLabel('가격').fill('70000');

    const send = page.getByRole('button', { name: '계약서 보내기' });
    await expect(send).toBeEnabled();
    await send.click();

    await expect(page.getByText('계약서를 보냈습니다.')).toBeVisible();
    await expect(page).toHaveURL(new RegExp(`/rooms/${AS_DEVELOPER}/contract$`));
  });
});
