/**
 * 로그인 뒤 돌아갈 곳으로 쓸 수 있는 값인지 봅니다.
 *
 * `/` 로 시작하는 우리 화면의 경로만 통과시킵니다. `//evil.com` 처럼 슬래시가 둘이면
 * 브라우저가 다른 사이트로 읽으므로 막습니다. 백슬래시(`/\evil.com`)도 일부 브라우저가
 * 슬래시로 고쳐 읽어 같은 이유로 막습니다.
 *
 * 로그인 화면(`?next=`)과 인가 시작·콜백 Route Handler 가 같은 검사를 씁니다.
 * 한쪽만 느슨해지면 로그인을 거쳐 낯선 곳으로 튕길 수 있습니다.
 */
export function safeNext(value: string | string[] | null | undefined): string | undefined {
  const next = Array.isArray(value) ? value[0] : value;
  if (next === undefined || next === null) return undefined;
  if (!next.startsWith("/")) return undefined;
  if (next.startsWith("//") || next.startsWith("/\\")) return undefined;
  return next;
}
