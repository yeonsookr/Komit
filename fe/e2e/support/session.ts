import type { Page } from '@playwright/test';

/**
 * 로그인이 필요한 화면을 여는 준비.
 *
 * 인증 연동 전이라 카카오를 거칠 수 없어 샘플 토큰을 쿠키에 직접 넣습니다.
 * 값은 `docs/jwt-samples.md` 의 「사용자 · 유효」(userId 12, 만료 2027-09-14) 입니다.
 *
 * 실제 쿠키는 httpOnly 지만 여기서는 그럴 필요가 없습니다. 이 쿠키를 읽는 것은
 * 브라우저가 아니라 요청을 받는 Next 서버라, 붙어서 나가기만 하면 같습니다.
 */
const SAMPLE_USER_TOKEN =
  'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMiIsImlhdCI6MTc4OTM0NDAwMCwiZXhwIjoxODIwODgwMDAwfQ.RRh0GbxV2FKuHiJUmTjXSk478NE8L4jhYalwJlk4o2E';

const ACCESS_TOKEN_COOKIE = 'komit_access_token';

const baseURL = process.env.E2E_BASE_URL ?? 'http://localhost:3000';

/** 이 페이지의 브라우저 컨텍스트를 로그인한 상태로 만듭니다. `goto` 전에 부릅니다. */
export async function signIn(page: Page): Promise<void> {
  await page.context().addCookies([
    { name: ACCESS_TOKEN_COOKIE, value: SAMPLE_USER_TOKEN, url: baseURL },
  ]);
}
