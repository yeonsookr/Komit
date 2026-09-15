import { expect, test } from '@playwright/test';

/**
 * 로그인 화면. 서버를 부르지 않는 정적 화면이라 목 서버가 없어도 됩니다.
 * 약관 화면과 카카오 인가 시작 경로는 아직 없어 이동한 주소만 확인합니다.
 */
test.describe('로그인', () => {
  test('카드와 카카오 버튼이 보인다', async ({ page }) => {
    await page.goto('/login');

    await expect(page).toHaveTitle('로그인 | Komit');
    await expect(page.getByRole('heading', { name: '시작하기' })).toBeVisible();
    await expect(page.getByRole('link', { name: '카카오로 시작하기' })).toBeVisible();
    await expect(page.getByText('통신판매중개자')).toBeVisible();
  });

  test('GNB 와 푸터를 두지 않는다', async ({ page }) => {
    await page.goto('/login');

    await expect(page.getByRole('navigation', { name: '주요 메뉴' })).toHaveCount(0);
    await expect(page.getByRole('navigation', { name: '푸터' })).toHaveCount(0);
  });

  test('약관 링크 넷이 각 문서로 간다', async ({ page }) => {
    await page.goto('/login');

    const expected = [
      ['이용약관', '/policies/terms'],
      ['개인정보 처리방침', '/policies/privacy'],
      ['전자금융거래', '/policies/electronic-finance'],
      ['환불과 분쟁 처리', '/policies/refund-dispute'],
    ];

    for (const [label, href] of expected) {
      await expect(page.getByRole('link', { name: label })).toHaveAttribute('href', href);
    }
  });

  test('돌아갈 곳을 넘기면 인가 시작 주소에 실린다', async ({ page }) => {
    await page.goto('/login?next=%2Fcheckup');

    await expect(page.getByRole('link', { name: '카카오로 시작하기' })).toHaveAttribute(
      'href',
      '/api/auth/kakao?next=%2Fcheckup',
    );
  });

  test('우리 화면이 아닌 곳은 돌아갈 곳으로 받지 않는다', async ({ page }) => {
    for (const next of ['https%3A%2F%2Fevil.com', '%2F%2Fevil.com']) {
      await page.goto(`/login?next=${next}`);

      await expect(page.getByRole('link', { name: '카카오로 시작하기' })).toHaveAttribute(
        'href',
        '/api/auth/kakao',
      );
    }
  });
});
