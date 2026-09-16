/**
 * 세션이 끝났음을 알리는 아주 작은 이벤트.
 *
 * 브라우저 axios 인스턴스가 401 을 받아 재발급까지 실패하면 여기로 알리고,
 * 인증 스토어 Provider 가 받아 상태를 비로그인으로 돌립니다.
 *
 * axios 계층이 스토어를 직접 import 하면 스토어 → 요청 함수 → axios → 스토어로
 * 순환이 생겨 이렇게 가운데에 둡니다. 브라우저에서만 쓰는 모듈입니다.
 */
type Listener = () => void;

const listeners = new Set<Listener>();

export function onSessionExpired(listener: Listener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function emitSessionExpired(): void {
  for (const listener of listeners) listener();
}
