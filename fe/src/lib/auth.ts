/**
 * 로그인 앞뒤로 오가는 값 처리.
 *
 * 인증 자체(토큰 발급·검증)는 백엔드 연동 단계에서 더합니다. 지금은 로그인을 마친 뒤
 * 어디로 돌아갈지에 필요한 것만 둡니다.
 */

/**
 * 로그인한 뒤 돌아갈 곳을 주소에 싣는 query 이름.
 *
 * 로그인이 필요한 화면이 사용자를 로그인으로 보낼 때 이 이름으로 목적지를 넘기고,
 * 로그인 화면이 같은 이름으로 읽습니다. 양쪽이 글자를 따로 적으면 한쪽만 바뀌어도
 * 타입 검사가 잡지 못하고 로그인 뒤 엉뚱한 곳으로 갑니다. (컨벤션 8.2)
 */
export const NEXT_PARAM = "next";

/**
 * 돌아갈 곳으로 쓸 수 있는 값인지 봅니다. 쓸 수 없으면 undefined.
 *
 * 열린 리다이렉트를 막기 위한 검사입니다. 이 값을 그대로 믿으면
 * `/login?next=//evil.com` 같은 링크로 우리 도메인을 거쳐 낯선 사이트로 보낼 수 있고,
 * 그 사이트가 로그인 화면을 흉내 내면 계정을 잃습니다.
 *
 * 통과하는 것은 `/checkup` 처럼 우리 화면의 경로뿐입니다.
 * - `https://evil.com` — 슬래시로 시작하지 않는 외부 주소라 막습니다.
 * - `//evil.com` — 슬래시로 시작하지만 브라우저가 외부 주소로 읽으므로 막습니다.
 *
 * 실제 이동은 백엔드 연동 단계에서 일어나므로 그때 한 번 더 검사합니다.
 * 한 군데만 믿지 않는 것이 이런 방어의 기본입니다.
 */
export function safeNext(value: string | string[] | undefined): string | undefined {
  const next = Array.isArray(value) ? value[0] : value;
  if (next === undefined) return undefined;
  return next.startsWith("/") && !next.startsWith("//") ? next : undefined;
}
