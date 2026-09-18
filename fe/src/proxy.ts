import { NextResponse, type NextRequest } from "next/server";
import { ADMIN_ACCESS_TOKEN_COOKIE } from "@/lib/admin/cookies";
import { ACCESS_TOKEN_COOKIE } from "@/lib/auth/cookies";
import { NEXT_PARAM } from "@/lib/auth/params";
import { ROUTES } from "@/lib/routes";

/**
 * 로그인이 필요한 화면을 렌더 전에 막습니다.
 *
 * **왜 화면 안이 아니라 여기인가.** 서버 컴포넌트는 데이터를 읽는 것이 곧 렌더라,
 * 화면 안에서 막으면 이미 시작된 렌더를 되돌리는 모양이 됩니다. `loading.tsx` 가
 * 있는 경로는 껍데기가 먼저 나가 200 으로 응답이 확정된 뒤에야 이동이 일어납니다.
 * proxy 는 렌더 전에 돌아 제대로 된 307 을 냅니다.
 *
 * **무엇을 근거로 막는가.** 우리가 쥐고 있는 세션 쿠키가 있는지만 봅니다. 토큰이
 * 쓸 만한지는 판단하지 않습니다 — 그건 코밋 서버가 하고, 만료된 토큰은 서버가 401 을
 * 주면 화면이 로그인으로 보냅니다(`lib/auth/guard.ts`, `lib/admin/guard.ts`). 여기서
 * 막는 것은 **쿠키가 아예 없는 경우**뿐이라, 목 서버가 토큰을 검사하지 않는 것과
 * 무관하게 동작합니다.
 *
 * 로그인한 뒤 보던 화면으로 돌아오도록 `?next=` 를 싣습니다. 값이 쓸 만한지는
 * 로그인 화면과 인가 Route Handler 가 `safeNext` 로 다시 봅니다.
 */
export function proxy(request: NextRequest) {
  const { search } = request.nextUrl;
  const pathname = withoutTrailingSlash(request.nextUrl.pathname);

  if (isAdminPath(pathname)) return guardAdmin(request, pathname, pathname + search);
  if (request.cookies.has(ACCESS_TOKEN_COOKIE)) return NextResponse.next();

  return redirectToLogin(request, ROUTES.login, pathname + search);
}

/**
 * `/admin/login/` 도 로그인 화면입니다.
 *
 * Next 가 끝의 슬래시를 정리해 주기는 하지만 그 정리는 proxy 뒤에 일어납니다.
 * 여기서 글자만 비교하면 `/admin/login/` 이 로그인 화면으로 인정받지 못해, 쿠키 없는
 * 사람을 로그인 화면에서 로그인 화면으로 한 번 더 튕깁니다.
 */
function withoutTrailingSlash(pathname: string): string {
  return pathname.length > 1 && pathname.endsWith("/") ? pathname.slice(0, -1) : pathname;
}

/** `/admin` 과 그 아래 전부. `/administrators` 같은 남의 경로가 걸리지 않게 경계를 봅니다. */
function isAdminPath(pathname: string): boolean {
  return pathname === ROUTES.adminHome || pathname.startsWith(`${ROUTES.adminHome}/`);
}

/**
 * 운영 콘솔은 **운영자 쿠키만** 봅니다.
 *
 * 사용자 쿠키(`komit_access_token`)가 있어도 들여보내지 않습니다. 운영자와 사용자는
 * 계정 체계가 완전히 별개라(목 명세 `9 운영 콘솔`), 한쪽 세션으로 다른 쪽 화면이
 * 열리면 그 자체가 권한 누수입니다.
 *
 * 돌려보내는 곳도 운영 로그인입니다. 사용자 로그인으로 보내면 운영자가 카카오
 * 로그인 화면을 보게 됩니다.
 */
function guardAdmin(request: NextRequest, pathname: string, from: string) {
  if (pathname === ROUTES.adminLogin) return NextResponse.next();
  if (request.cookies.has(ADMIN_ACCESS_TOKEN_COOKIE)) return NextResponse.next();

  return redirectToLogin(request, ROUTES.adminLogin, from);
}

function redirectToLogin(request: NextRequest, loginPath: string, from: string) {
  const login = new URL(loginPath, request.url);
  login.searchParams.set(NEXT_PARAM, from);
  return NextResponse.redirect(login);
}

/**
 * 로그인해야 들어갈 수 있는 화면만 지납니다. 랜딩·개발자 찾기·로그인과 `/api` 는
 * 여기 오지 않습니다 — 랜딩이 proxy 를 지나면 정적 생성이 흔들립니다.
 *
 * `:path*` 는 세그먼트가 없는 경우도 포함합니다. `/rooms` 와 `/rooms/91` 이 모두 걸립니다.
 * `/admin` 도 같은 이유로 `/admin` 자신과 그 아래가 전부 걸리며, 그중 운영 로그인
 * 화면만 `guardAdmin` 이 통과시킵니다 — 여기서 빼면 로그인 화면이 자기 자신으로
 * 무한히 이동하게 됩니다.
 *
 * 사전점검(`/checkup`)은 넣지 않았습니다. 비로그인 헤더에도 「사전점검」이 있어
 * 어디까지 열어 둘지가 기획 판단입니다.
 */
export const config = {
  matcher: [
    "/admin/:path*",
    "/rooms/:path*",
    "/recommendations/:path*",
    "/settlements/:path*",
    "/mypage/:path*",
  ],
};
