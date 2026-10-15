/**
 * 세션에서 보낼 화면을 고릅니다.
 *
 * **브라우저는 사용자가 누른 그 순간에만 고르는 창을 띄웁니다.** 그래서 방의 입장 버튼에서
 * 고르는 창을 열고, 세션 화면이 통화를 열 때 그 결과를 넘겨받습니다. 고르기를 기다리지
 * 않고 넘어가므로 통화 연결과 녹음 시작이 밀리지 않습니다. 새로고침하면 넘길 것이 없어
 * 세션 화면의 「화면 공유」로 다시 고릅니다.
 */

/** 전체 화면 탭이 먼저 열립니다. 탭이나 창 하나를 고를 수도 있습니다. */
const SCREEN: DisplayMediaStreamOptions = {
  video: { displaySurface: "monitor" },
  // 소리는 받지 않습니다. 녹음은 참가자마다 자기 마이크만 담습니다. (R-CON-14)
  audio: false,
};

/** 고른 화면. 고르는 창에서 취소하면 `null` 입니다 — 알릴 일이 아닙니다. */
export async function requestScreen(): Promise<MediaStream | null> {
  let display: MediaStream;
  try {
    display = await navigator.mediaDevices.getDisplayMedia(SCREEN);
  } catch {
    return null;
  }
  if (display.getVideoTracks().length === 0) {
    stopStream(display);
    return null;
  }
  return display;
}

export function stopStream(stream: MediaStream | null | undefined): void {
  stream?.getTracks().forEach((track) => track.stop());
}

let held: { sessionId: number; screen: Promise<MediaStream | null> } | null = null;

/** 입장하며 연 고르기를 세션 화면으로 넘길 때까지 맡아 둡니다. */
export function holdScreen(sessionId: number, screen: Promise<MediaStream | null>): void {
  dropHeldScreen();
  held = { sessionId, screen };
}

/** 맡겨 둔 고르기를 꺼냅니다. 다른 세션 것이면 끊고 `null` 입니다. */
export function takeHeldScreen(sessionId: number): Promise<MediaStream | null> | null {
  if (!held) return null;
  const { sessionId: heldFor, screen } = held;
  held = null;
  if (heldFor !== sessionId) {
    void screen.then(stopStream);
    return null;
  }
  return screen;
}

/** 입장하지 못했을 때처럼 넘길 곳이 없어진 고르기를 끊습니다. */
export function dropHeldScreen(): void {
  if (held) void held.screen.then(stopStream);
  held = null;
}
