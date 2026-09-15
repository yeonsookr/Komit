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

/** "2026-08-20" — 같은 날 메시지를 묶을 때 쓰는 키. 화면에 찍지 않습니다. */
export function toDayKey(isoString: string): string {
  return dayKeyFormat.format(new Date(isoString));
}

/** "8/18 16:40" — 요청서 갱신 시각처럼 좁은 자리에 넣는 표기. */
export function formatShortDateTime(isoString: string): string {
  const date = new Date(isoString);
  const [, month, day] = dayKeyFormat.format(date).split("-");
  return `${Number(month)}/${Number(day)} ${timeFormat.format(date)}`;
}
