/**
 * 화면에 찍는 날짜·시각 포맷터.
 *
 * 타임존을 Asia/Seoul 로 고정합니다. 서버 렌더와 브라우저 렌더가 서로 다른
 * 타임존에서 돌면 같은 값이 다른 문자열이 되어 hydration 이 어긋납니다.
 *
 * 컴포넌트 안에서 toLocaleString 을 직접 부르지 말고 이 함수들을 씁니다.
 */

const TIME_ZONE = "Asia/Seoul";

const timeFormat = new Intl.DateTimeFormat("ko-KR", {
  timeZone: TIME_ZONE,
  hour: "2-digit",
  minute: "2-digit",
  hour12: false,
});

const dateLabelFormat = new Intl.DateTimeFormat("ko-KR", {
  timeZone: TIME_ZONE,
  month: "long",
  day: "numeric",
});

const fullDateFormat = new Intl.DateTimeFormat("ko-KR", {
  timeZone: TIME_ZONE,
  year: "numeric",
  month: "long",
  day: "numeric",
});

const dayKeyFormat = new Intl.DateTimeFormat("en-CA", {
  timeZone: TIME_ZONE,
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
});

/** "10:14" — 말풍선 아래 시각. */
export function formatTime(isoString: string): string {
  return timeFormat.format(new Date(isoString));
}

/** "8월 20일" — 대화 사이 날짜 구분. */
export function formatDateLabel(isoString: string): string {
  return dateLabelFormat.format(new Date(isoString));
}

/** "2026년 3월 1일" — 약관 개정일처럼 연도까지 밝혀야 하는 자리. */
export function formatFullDate(isoString: string): string {
  return fullDateFormat.format(new Date(isoString));
}

/** "2026-08-20" — 같은 날 메시지를 묶을 때 쓰는 키. 화면에 찍지 않습니다. */
export function toDayKey(isoString: string): string {
  return dayKeyFormat.format(new Date(isoString));
}

/** "8/17" — 마이페이지의 진단 날짜처럼 시각까지는 필요 없는 자리. */
export function formatShortDate(isoString: string): string {
  const [, month, day] = dayKeyFormat.format(new Date(isoString)).split("-");
  return `${Number(month)}/${Number(day)}`;
}

/** "8/18 16:40" — 요청서 갱신 시각처럼 좁은 자리에 넣는 표기. */
export function formatShortDateTime(isoString: string): string {
  const date = new Date(isoString);
  const [, month, day] = dayKeyFormat.format(date).split("-");
  return `${Number(month)}/${Number(day)} ${timeFormat.format(date)}`;
}

/**
 * "지금 · 12분 전 · 3시간 전 · 어제 · 8/20" — 목록의 마지막 활동 시각.
 *
 * `now` 를 받는 이유는 두 가지입니다. 테스트에서 시점을 고정할 수 있고,
 * 서버에서 한 번 계산해 문자열로 내려보내 hydration 이 어긋나지 않게 합니다.
 */
export function formatRelativeTime(isoString: string, now: Date = new Date()): string {
  const target = new Date(isoString);
  const diffMs = now.getTime() - target.getTime();
  const diffMinutes = Math.floor(diffMs / 60_000);

  // 미래 시각(서버와 시계가 조금 어긋난 경우)은 방금으로 봅니다.
  if (diffMinutes < 1) return "지금";
  if (diffMinutes < 60) return `${diffMinutes}분 전`;

  const dayDiff = dayDistance(target, now);
  if (dayDiff === 0) return `${Math.floor(diffMinutes / 60)}시간 전`;
  if (dayDiff === 1) return "어제";

  const [, month, day] = dayKeyFormat.format(target).split("-");
  return `${Number(month)}/${Number(day)}`;
}

/** 두 시점이 한국 기준으로 며칠 떨어져 있는지. */
function dayDistance(from: Date, to: Date): number {
  const fromDay = Date.parse(`${dayKeyFormat.format(from)}T00:00:00Z`);
  const toDay = Date.parse(`${dayKeyFormat.format(to)}T00:00:00Z`);
  return Math.round((toDay - fromDay) / 86_400_000);
}
