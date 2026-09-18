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

/** 운영자 세션 쿠키. 사용자 쿠키와 이름부터 다릅니다(`src/lib/admin/cookies.ts`). */
const ADMIN_ACCESS_TOKEN_COOKIE = 'komit_admin_token';

/**
 * 운영 콘솔을 열 수 있는 상태로 만듭니다. `signIn` 과 **겹쳐 쓰지 마세요** —
 * 두 세션이 서로를 침범하지 않는지 보는 것이 admin-auth 스펙의 일이라,
 * 그 스펙만 두 쿠키를 함께 넣습니다.
 *
 * 목 서버는 토큰을 검사하지 않아 값은 무엇이든 됩니다. proxy 가 보는 것은
 * 쿠키가 있는지뿐입니다.
 */
export async function signInAsAdmin(page: Page): Promise<void> {
  await page.context().addCookies([
    { name: ADMIN_ACCESS_TOKEN_COOKIE, value: 'admin.e2e-token', url: baseURL },
  ]);
}
