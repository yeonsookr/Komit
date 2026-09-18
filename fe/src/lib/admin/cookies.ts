/**
 * 운영 콘솔 세션 쿠키 이름.
 *
 * `session.ts` 와 따로 두는 이유는 **proxy 가 읽어야 하기 때문**입니다.
 * `session.ts` 는 `server-only` 이고 `next/headers` 를 쓰는데 proxy 에서는 둘 다
 * 쓸 수 없습니다. 이름만 여기 두고 양쪽이 같은 값을 봅니다 — 두 곳에 따로 적으면
 * 한쪽만 바뀌어도 타입 검사와 빌드는 통과하고 로그인만 조용히 깨집니다.
 *
 * **사용자 쿠키(`komit_access_token`)와 이름부터 다릅니다.** 운영자와 사용자는
 * 계정 체계가 완전히 별개라(목 명세 `9 운영 콘솔` — "운영 콘솔은 토큰이 따로다")
 * 한쪽 세션이 다른 쪽을 열거나 닫으면 안 됩니다.
 *
 * 운영자 이름 같은 표시용 값은 쿠키에 두지 않습니다. 화면이 `GET /api/admin/summary`
 * 의 `admin` 으로 받습니다 — 표시값을 쿠키에 복사해 두면 서버와 어긋날 자리가 생깁니다.
 */

/** 운영자 액세스 토큰. `/admin` 화면과 `/api/admin` 핸들러가 함께 읽어 path 는 `/` 입니다. */
export const ADMIN_ACCESS_TOKEN_COOKIE = "komit_admin_token";
