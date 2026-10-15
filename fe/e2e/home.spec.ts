import { expect, test } from '@playwright/test';

/**
 * 랜딩. 목 서버(:8080)가 떠 있어야 합니다.
 * 목의 추천 개발자는 이준 · 한서 · 박선 3명이고, 이번 주 상담은 412건입니다.
 * 로그인·약관 화면은 아직 없어 이동한 주소만 확인합니다.
 */
test.describe('랜딩', () => {
  test.beforeEach(async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 1000 });
    await page.goto('/');
  });

  test('히어로 · 추천 개발자 · 진행 순서가 보인다', async ({ page }) => {
    await expect(page).toHaveTitle('Komit');
    // 「당신의」 뒤의 낱말은 2.2초마다 바뀝니다. 무엇이 들어 있든 문장은 같습니다.
    await expect(
      page.getByRole('heading', { name: /^당신의 .+ 아이디어, Komit과 함께 완성해보세요\.$/ }),
    ).toBeVisible();
    await expect(page.getByText('이번 주 상담')).toContainText('412');

    const featured = page.getByRole('region', { name: '추천 개발자' });
    for (const name of ['이준', '한서', '박선']) {
      await expect(featured.getByText(name, { exact: true })).toBeVisible();
    }
    await expect(featured.getByRole('link', { name: '상담 시작하기' })).toHaveCount(3);

    await expect(page.getByRole('region', { name: '진행 순서' }).getByRole('listitem')).toHaveCount(4);
  });

  test('예시 칩을 누르면 입력칸이 채워지고, 적은 문장이 개발자 찾기의 검색어가 된다', async ({ page }) => {
    await page.getByRole('button', { name: '예약 버튼이 반응이 없어요' }).click();
    await expect(page.getByLabel('겪고 있는 문제')).toHaveValue('예약 버튼이 반응이 없어요');

    await page.getByRole('button', { name: '문제 진단하기' }).click();

    await expect(page).toHaveURL(/\/developers\?keyword=/);
    expect(new URL(page.url()).searchParams.get('keyword')).toBe('예약 버튼이 반응이 없어요');
    // 개발자 찾기가 그 문장을 검색어로 이어받습니다.
    await expect(page.getByLabel('검색어')).toHaveValue('예약 버튼이 반응이 없어요');
  });

  test('아무것도 적지 않으면 조건 없는 개발자 찾기로 간다', async ({ page }) => {
    await page.getByRole('button', { name: '문제 진단하기' }).click();

    // 빈 검색어가 주소에 남지 않아야 합니다.
    await expect(page).toHaveURL(/\/developers$/);
  });

  test('증상 카드는 그 분야의 개발자 찾기로 간다', async ({ page }) => {
    await page.getByRole('link', { name: '저장이 안 돼요' }).click();
    await expect(page).toHaveURL(/\/developers\?fieldCodeIds=42$/);
  });

  test('뭐가 문제인지 몰라요는 분야를 좁히지 않고 개발자 찾기로 간다', async ({ page }) => {
    await page.getByRole('link', { name: '뭐가 문제인지 몰라요' }).click();
    await expect(page).toHaveURL(/\/developers$/);
  });

  test('상담 시작하기는 그 개발자의 프로필로 간다', async ({ page }) => {
    await page
      .getByRole('region', { name: '추천 개발자' })
      .getByRole('link', { name: '상담 시작하기' })
      .first()
      .click();
    await expect(page).toHaveURL(/\/developers\/3$/);
  });

  /** GNB 에서 빠진 뒤로 이 링크는 푸터에만 있습니다. */
  test('이용 방법은 진행 순서 섹션의 앵커다', async ({ page }) => {
    await page.getByRole('navigation', { name: '푸터' }).getByRole('link', { name: '이용 방법' }).click();
    await expect(page).toHaveURL(/\/#process$/);
    await expect(page.locator('#process')).toBeInViewport();
  });

  /** 랜딩은 비로그인으로 그려집니다. 정산은 로그인한 뒤에만 나옵니다. */
  test('비로그인 GNB 에는 정산이 없다', async ({ page }) => {
    const nav = page.getByRole('navigation', { name: '주요 메뉴' });
    await expect(nav.getByRole('link')).toHaveText(['의뢰', '개발자 찾기', '사전점검']);
  });
});
