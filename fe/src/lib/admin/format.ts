/**
 * 운영 콘솔의 날짜·시각 표기.
 *
 * 화면마다 쓰는 모양이 다릅니다. 표는 좁아서 `9/2 16:04` 로 줄이고, 요약과 본문은
 * `2026년 9월 2일` 처럼 풀어 씁니다. 와이어프레임 `9_홈`·`9_신고처리`·`9_정산대사`·
 * `9_계약양식` 에서 관찰한 네 가지 모양을 그대로 함수로 둡니다.
 *
 * **기준 시간대는 한국(KST) 로 못 박습니다.** 서버가 보내는 값에는 `+09:00` 가 붙어
 * 있지만, 화면을 그리는 곳은 Next 서버이기도 하고 브라우저이기도 해서 기계의 시간대에
 * 맡기면 같은 값이 배포 환경에 따라 다른 날짜로 찍힙니다. 운영자는 장부와 대사 결과를
 * 날짜로 대조하므로 하루가 밀리면 안 됩니다.
 *
 * 숫자는 화면에서 monospace + tabular-nums 로 씁니다(`docs/FRONTEND_COMPONENTS.md` 3장).
 */

const TIME_ZONE = "Asia/Seoul";

/** 자정을 `24:00` 으로 적는 구현이 있어 `hourCycle` 로 못 박습니다. */
const HOUR_CYCLE = "h23" as const;

const fullDateParts = new Intl.DateTimeFormat("ko-KR", {
  timeZone: TIME_ZONE,
  year: "numeric",
  month: "numeric",
  day: "numeric",
});

const monthDayTimeParts = new Intl.DateTimeFormat("ko-KR", {
  timeZone: TIME_ZONE,
  month: "numeric",
  day: "numeric",
  hour: "2-digit",
  minute: "2-digit",
  hourCycle: HOUR_CYCLE,
});

type Parts = Partial<Record<Intl.DateTimeFormatPartTypes, string>>;

function read(formatter: Intl.DateTimeFormat, value: string): Parts {
  return Object.fromEntries(
    formatter.formatToParts(new Date(value)).map((part) => [part.type, part.value]),
  );
}

/** `2026년 9월 2일` — 홈의 운영자 줄, 대사 요약 제목. */
export function formatAdminFullDate(value: string): string {
  const { year, month, day } = read(fullDateParts, value);
  return `${year}년 ${month}월 ${day}일`;
}

/** `9월 2일 16:04` — 신고 상세의 접수 시각. */
export function formatAdminMonthDayTime(value: string): string {
  const { month, day, hour, minute } = read(monthDayTimeParts, value);
  return `${month}월 ${day}일 ${hour}:${minute}`;
}

/** `9월 2일` — 대사 이력의 날짜 칸. */
export function formatAdminMonthDay(value: string): string {
  const { month, day } = read(monthDayTimeParts, value);
  return `${month}월 ${day}일`;
}

/** `9/2 16:04` — 신고 목록의 접수 칸. 표가 좁아 줄여 씁니다. */
export function formatAdminSlashDateTime(value: string): string {
  const { month, day, hour, minute } = read(monthDayTimeParts, value);
  return `${month}/${day} ${hour}:${minute}`;
}

/** `9/2` — 신고 목록의 해결일, 양식 목록의 등록일. */
export function formatAdminSlashDate(value: string): string {
  const { month, day } = read(monthDayTimeParts, value);
  return `${month}/${day}`;
}

/** `9월 2일 17:10 기준` — 상단바에 적는 숫자를 센 시점. */
export function formatAdminAsOf(value: string): string {
  return `${formatAdminMonthDayTime(value)} 기준`;
}
