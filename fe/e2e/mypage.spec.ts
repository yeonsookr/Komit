import { expect, test } from '@playwright/test';
import { signIn } from './support/session';

/**
 * 마이페이지. 목 서버가 닿아야 합니다(BACKEND_API_BASE_URL).
 * 목의 사용자는 김미미(userId 12)이고 프로젝트 3개, 개발자 프로필 없음, 알림 켜짐입니다.
 */
test.describe('마이페이지', () => {
  test.beforeEach(async ({ page }) => {
    await signIn(page);
  });

  test('계정부터 탈퇴까지 칸이 순서대로 있다', async ({ page }) => {
    await page.goto('/mypage');

    await expect(page).toHaveTitle('마이페이지 | Komit');
    await expect(page.getByRole('heading', { name: '마이페이지', level: 1 })).toBeVisible();

    // 개발자 프로필 칸은 프로필이 없어도 만들러 가는 줄 하나로 남습니다.
    await expect(page.getByRole('heading', { level: 2 })).toHaveText([
      '계정',
      '계좌',
      '등록한 프로젝트',
      '개발자 프로필',
      '알림',
      '약관',
      '탈퇴',
    ]);
  });

  /**
   * 목의 사용자는 개발자 프로필이 없습니다(`developer: null`). 감추지 않고
   * 만들러 보내야 개발자로도 활동할 수 있다는 것을 알 수 있습니다.
   */
  test('개발자 프로필이 없으면 프로필 생성으로 보낸다', async ({ page }) => {
    await page.goto('/mypage');

    await expect(page.getByText('아직 만들지 않았습니다')).toBeVisible();
    await expect(page.getByRole('link', { name: '프로필 생성' })).toHaveAttribute(
      'href',
      '/mypage/profile',
    );

    // 의뢰인 일정도 있으므로 개발자 프로필이 없어도 일정으로 갈 수 있습니다.
    await expect(page.getByRole('link', { name: '일정 관리' })).toHaveAttribute(
      'href',
      '/mypage/schedule',
    );
    await expect(page.getByRole('link', { name: '프로필 편집' })).toHaveCount(0);
  });

  test('계정과 계좌 칸이 목의 값을 보여준다', async ({ page }) => {
    await page.goto('/mypage');

    // 계좌 줄에도 예금주로 같은 이름이 있어 정확히 맞춥니다.
    await expect(page.getByText('김미미', { exact: true })).toBeVisible();
    await expect(page.getByText('mimi@kakao.com')).toBeVisible();
    await expect(page.getByText('신한은행 ···· 4412 · 예금주 김미미')).toBeVisible();
  });

  /**
   * 이름 줄은 별도 화면으로 넘기지 않고 그 자리에서 입력칸으로 바뀝니다.
   * 취소가 원래 이름으로 되돌리는지까지 봅니다 — 서버를 부르지 않는 경로입니다.
   */
  test('이름 바꾸기를 누르면 그 자리에서 고칠 수 있다', async ({ page }) => {
    await page.goto('/mypage');

    await page.getByRole('button', { name: '이름 바꾸기' }).click();

    const input = page.getByRole('textbox', { name: '새 이름' });
    await expect(input).toHaveValue('김미미');

    await input.fill('김코밋');
    await page.getByRole('button', { name: '취소' }).click();

    await expect(input).toHaveCount(0);
    await expect(page.getByRole('button', { name: '이름 바꾸기' })).toBeVisible();
  });

  /**
   * 판매자 약관은 개발자에게만 나옵니다. 목의 사용자는 개발자 프로필이 없어
   * 넷만 보여야 합니다 — 다섯이 나오면 조건이 빠진 것입니다.
   */
  test('약관 칸의 문서 넷이 각 본문으로 간다', async ({ page }) => {
    await page.goto('/mypage');

    const expected = [
      '/policies/terms',
      '/policies/privacy',
      '/policies/electronic-finance',
      '/policies/refund-dispute',
    ];

    await expect(page.getByText('개발자 판매자 약관')).toHaveCount(0);

    // 프로젝트 칸의 「진단서 보기」와 섞이지 않게 이름을 정확히 맞춥니다.
    const links = page.getByRole('link', { name: '보기', exact: true });
    await expect(links).toHaveCount(expected.length);

    for (const [index, href] of expected.entries()) {
      await expect(links.nth(index)).toHaveAttribute('href', href);
    }
  });

  test('탈퇴하기를 누르면 탈퇴 화면으로 간다', async ({ page }) => {
    await page.goto('/mypage');

    await page.getByRole('link', { name: '탈퇴하기' }).click();

    await expect(page).toHaveURL(/\/mypage\/withdrawal$/);
    await expect(page.getByRole('heading', { name: '회원 탈퇴', level: 1 })).toBeVisible();
  });
});

/**
 * 회원 탈퇴. 목은 언제나 막힌 상태(진행 중인 의뢰 1건 + 예치금 650,000원)를 줍니다.
 */
test.describe('회원 탈퇴', () => {
  test.beforeEach(async ({ page }) => {
    await signIn(page);
    await page.goto('/mypage/withdrawal');
  });

  test('막고 있는 것을 한 문장과 목록으로 보여준다', async ({ page }) => {
    await expect(page.getByText('지금은 탈퇴할 수 없습니다')).toBeVisible();
    await expect(
      page.getByText('진행 중인 의뢰 1건과 예치금 650,000원이 남아 있습니다.'),
    ).toBeVisible();

    await expect(page.getByText('수제 비누 가게 · 진행 중인 의뢰')).toBeVisible();
    await expect(page.getByText('예치금 650,000원', { exact: true })).toBeVisible();
  });

  /**
   * 응답은 계약 식별자와 방 식별자를 함께 주는데 화면 주소는 방 기준입니다.
   * 계약 번호(130)로 주소를 만들면 없는 화면으로 가므로 방 번호(93)인지 봅니다.
   *
   * 두 줄이 같은 계약을 가리켜 목적지도 같습니다 — 예치금을 정리하려면 결국
   * 그 계약을 끝내야 하기 때문입니다.
   */
  test('이동을 누르면 그 계약의 계약서 화면으로 간다', async ({ page }) => {
    const moves = page.getByRole('link', { name: '이동' });
    await expect(moves).toHaveCount(2);

    for (const index of [0, 1]) {
      await expect(moves.nth(index)).toHaveAttribute('href', '/rooms/93/contract');
    }

    await moves.first().click();

    await expect(page).toHaveURL(/\/rooms\/93\/contract$/);
    await expect(page).toHaveTitle(/계약서/);
  });

  /**
   * 막는 것이 없는 갈래도 줄을 남깁니다. 무엇을 확인했는지 알 수 있어야 하기 때문에
   * 「없음」도 정보입니다 — 줄을 지우는 변경이 들어오면 여기서 걸립니다.
   */
  test('막지 않는 갈래는 없음으로 남는다', async ({ page }) => {
    await expect(page.getByText('못 받은 소득')).toBeVisible();
    await expect(page.getByText('없음')).toBeVisible();
  });

  test('정리할 것이 남아 있으면 탈퇴 버튼이 잠긴다', async ({ page }) => {
    await expect(page.getByText('남은 의뢰와 예치금을 정리하면 이 버튼이 열립니다.')).toBeVisible();
    await expect(page.getByRole('button', { name: '탈퇴하기' })).toBeDisabled();
  });

  test('탈퇴하면 무엇이 남는지 알려준다', async ({ page }) => {
    await expect(page.getByRole('heading', { name: '탈퇴하면 이렇게 됩니다' })).toBeVisible();

    // R-ACC-07 리뷰 유지 · R-ACC-06 30일 보관
    await expect(page.getByText('작성자만 가려집니다')).toBeVisible();
    await expect(page.getByText('30일 보관 후 완전히 삭제합니다')).toBeVisible();
  });
});
