import { expect, test } from '@playwright/test';
import { signIn } from './support/session';

/**
 * 컨설팅 세션 화면 (영역 4).
 *
 * 세션은 방 안의 탭이 아니라 별도 화면입니다. 채팅 위 배너의 「입장」에서 들어가고,
 * 「세션 종료」로 방에 돌아옵니다.
 *
 * 목의 세션은 55 하나이며 계약 120 · 방 92 에 달려 있습니다.
 */
const ROOM_ID = 92;
const SESSION_ID = 55;

test.describe('컨설팅 세션', () => {
  test.beforeEach(async ({ page }) => {
    await signIn(page);
  });

  test('채팅 위 배너의 「입장」으로 세션에 들어간다', async ({ page }) => {
    await page.goto(`/rooms/${ROOM_ID}`);

    await page.getByRole('link', { name: '입장' }).click();

    await expect(page).toHaveURL(new RegExp(`/sessions/${SESSION_ID}$`));
    await expect(page.getByRole('heading', { name: /컨설팅 · 이준/ })).toBeVisible();
  });

  test('세션 화면에는 방으로 돌아가는 길과 컨트롤 바가 있다', async ({ page }) => {
    await page.goto(`/sessions/${SESSION_ID}`);

    await expect(page.getByRole('link', { name: '← 컨설팅 방' })).toBeVisible();
    await expect(page.getByRole('button', { name: '세션 종료' })).toBeVisible();

    /*
      카메라를 잡기 전에는 끌 것이 없어 잠가 둡니다. 이 환경에는 카메라가 없어
      늘 잠긴 채입니다 — 실제로 켜지는 것은 가짜 미디어를 물린 브라우저 둘로 봅니다.
    */
    await expect(page.getByRole('button', { name: '마이크' })).toBeDisabled();
    await expect(page.getByRole('button', { name: '카메라' })).toBeDisabled();

    // 화면 공유는 아직 붙지 않았습니다. 눌리면 고장으로 읽히므로 잠가 둡니다.
    await expect(page.getByRole('button', { name: '화면 공유' })).toBeDisabled();
  });

  test('세션 대화는 방 대화와 다른 내용이다', async ({ page }) => {
    await page.goto(`/sessions/${SESSION_ID}`);

    // 세션에서 오간 말은 요약의 근거가 되므로 방의 흐름과 섞이지 않습니다.
    await expect(page.getByText('지금 보고 계신 화면 기준으로 말씀드릴게요')).toBeVisible();
    await expect(page.getByText('(컨설팅) 범위가 아직 좀 넓어 보입니다.')).toHaveCount(0);
  });

  test('세션을 마치면 방으로 돌아간다', async ({ page }) => {
    await page.goto(`/sessions/${SESSION_ID}`);

    await page.getByRole('button', { name: '세션 종료' }).click();
    // 되돌릴 수 없어 한 번 확인합니다.
    await expect(page.getByRole('heading', { name: '세션을 마칠까요?' })).toBeVisible();
    await page.getByRole('dialog').getByRole('button', { name: '세션 종료' }).click();

    await expect(page).toHaveURL(new RegExp(`/rooms/${ROOM_ID}$`));
  });
});
