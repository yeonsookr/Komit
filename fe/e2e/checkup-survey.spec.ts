import { expect, test } from '@playwright/test';
import { signIn } from './support/session';

/**
 * 사전점검 설문. 목 서버가 닿아야 합니다(BACKEND_API_BASE_URL).
 * 진단 31 은 문항 4개(단수 · 복수 · 주관식 · 단수)를 가지고,
 * 404 는 없는 진단 재현용 예약값입니다.
 */
test.describe('사전점검 설문', () => {
  test.beforeEach(async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 900 });
  });

  test('설문1은 이름과 진행 단계를 받는다', async ({ page }) => {
    await page.goto('/checkup');

    await expect(page.getByText('STEP 1 / 4')).toBeVisible();
    await expect(page.getByRole('heading', { name: '어떤 걸 만드셨나요?' })).toBeVisible();
    await expect(page.getByRole('heading', { name: '진행 중인 설문' })).toHaveCount(0);
    // 이름과 단계를 모두 채우기 전에는 넘어갈 수 없습니다.
    await expect(page.getByRole('button', { name: '다음 →' })).toBeDisabled();
  });

  test('로그인한 사용자에게만 진행 중인 설문을 보여준다', async ({ page }) => {
    await signIn(page);
    await page.goto('/checkup');

    await expect(page.getByRole('heading', { name: '진행 중인 설문' })).toBeVisible();
    await expect(page.getByRole('link', { name: '이어서 하기 ›' }).first()).toBeVisible();
  });

  test('이름과 단계를 채우면 진단이 열리고 설문2로 간다', async ({ page }) => {
    await page.goto('/checkup');

    await page.getByPlaceholder('예) 수제 비누 가게').fill('수제 비누 가게');
    await page.getByText('배포는 했는데 아직 아무도 안 씁니다').click();
    await page.getByRole('button', { name: '다음 →' }).click();

    // 목은 진단 31 을 돌려줍니다.
    await expect(page).toHaveURL(/\/checkup\/31\?step=2/);
    await expect(page.getByText('STEP 2 / 4')).toBeVisible();
  });

  test('복수 문항은 여러 개를 고를 수 있다', async ({ page }) => {
    await page.goto('/checkup/31?step=2');

    // 처음 들어오면 아무것도 골라져 있지 않습니다.
    await expect(page.getByRole('checkbox', { name: '보안이 걱정돼요' })).not.toBeChecked();
    await expect(page.getByRole('button', { name: '다음 →' })).toBeDisabled();

    // 입력은 sr-only 이고 사용자는 라벨을 누릅니다.
    await page.getByText('보안이 걱정돼요').click();
    await page.getByText('결제를 받아야 해요').click();

    await expect(page.getByRole('checkbox', { name: '보안이 걱정돼요' })).toBeChecked();
    await expect(page.getByRole('checkbox', { name: '결제를 받아야 해요' })).toBeChecked();
  });

  test('「여기에 없어요」 를 누르면 주관식으로 데려간다', async ({ page }) => {
    await page.goto('/checkup/31?step=2');

    await page.getByText('여기에 없어요 — 직접 적을게요').click();

    // 서버로 보내지 않는 화면 전용 장치라 체크되지 않고 입력만 잡습니다.
    await expect(page.getByRole('textbox')).toBeFocused();
  });

  test('설문2는 복수 문항과 주관식을 함께 받는다', async ({ page }) => {
    await page.goto('/checkup/31?step=2');

    // 보기 여섯 개와 화면 전용 「여기에 없어요」 한 줄.
    await expect(page.getByRole('checkbox')).toHaveCount(7);
    // 주관식은 빈 칸에서 시작합니다.
    await expect(page.getByRole('textbox')).toHaveValue('');
  });

  test('설문3은 검사 파일 단계다', async ({ page }) => {
    await page.goto('/checkup/31?step=3');

    await expect(page.getByRole('heading', { name: '검사 파일을 받아 직접 실행해주세요' })).toBeVisible();
    await expect(page.getByText('/plugin install komit-scan@komit')).toBeVisible();
    await expect(page.getByRole('button', { name: '내려받기' })).toBeVisible();
    await expect(page.getByRole('button', { name: '복사' })).toBeVisible();
    await expect(page.getByRole('button', { name: '파일 선택' })).toBeVisible();
    // 마지막 문항이 검사 결과에서 만들어지므로 올리기 전에는 넘어갈 수 없습니다.
    await expect(page.getByRole('button', { name: '다음 →' })).toBeDisabled();
  });

  test('앞 단계를 거쳐 들어와도 업로드하면 다음이 열린다', async ({ page }) => {
    // 단계 이동은 컴포넌트를 다시 만들지 않으므로, 앞 단계의 '저장 중' 이 풀리지
    // 않으면 버튼이 잠긴 채로 남습니다.
    await page.goto('/checkup/31?step=2');
    await page.getByText('보안이 걱정돼요').click();
    await page.getByRole('textbox').fill('결제가 안 돼요');
    await page.getByRole('button', { name: '다음 →' }).click();
    await expect(page.getByText('STEP 3 / 4')).toBeVisible();

    await page.setInputFiles('input[type=file]', {
      name: 'komit-scan-7K2M9.md',
      mimeType: 'text/markdown',
      buffer: Buffer.from('# result'),
    });

    await expect(page.getByRole('button', { name: '다음 →' })).toBeEnabled();

    await page.getByRole('button', { name: '다음 →' }).click();
    await expect(page.getByText('STEP 4 / 4')).toBeVisible();
  });

  test('결과 파일을 올리면 파일 이름이 찍힌다', async ({ page }) => {
    await page.goto('/checkup/31?step=3');

    await page.setInputFiles('input[type=file]', {
      name: 'komit-scan-7K2M9.md',
      mimeType: 'text/markdown',
      buffer: Buffer.from('# result'),
    });

    await expect(page.getByText('komit-scan-7K2M9.md')).toBeVisible();
    await expect(page.getByText('성공적으로 업로드 완료했어요')).toBeVisible();
    // 올리고 나면 다음으로 넘어갈 수 있습니다.
    await expect(page.getByRole('button', { name: '다음 →' })).toBeEnabled();

    await page.getByRole('button', { name: '다음 →' }).click();
    await expect(page).toHaveURL(/step=4/);
  });

  test('마지막 단계의 버튼은 제출하기다', async ({ page }) => {
    await page.goto('/checkup/31?step=4');

    await expect(page.getByText('STEP 4 / 4')).toBeVisible();
    await expect(page.getByRole('button', { name: '제출하기' })).toBeVisible();
  });

  test('이전 버튼으로 앞 단계로 돌아간다', async ({ page }) => {
    await page.goto('/checkup/31?step=3');

    await page.getByRole('button', { name: '← 이전' }).click();
    await expect(page).toHaveURL(/step=2/);
  });

  test('주소의 단계가 범위를 벗어나면 2단계로 되돌린다', async ({ page }) => {
    await page.goto('/checkup/31?step=9');

    await expect(page.getByText('STEP 2 / 4')).toBeVisible();
  });

  test('없는 진단은 찾을 수 없다고 알린다', async ({ page }) => {
    await page.goto('/checkup/404');

    await expect(page.getByText('사전점검을 찾을 수 없습니다')).toBeVisible();
  });
});
