import { describe, expect, it } from "vitest";
import {
  formatAdminAsOf,
  formatAdminFullDate,
  formatAdminMonthDay,
  formatAdminMonthDayTime,
  formatAdminSlashDate,
  formatAdminSlashDateTime,
} from "./format";

describe("운영 콘솔 날짜 표기", () => {
  const asOf = "2026-09-02T17:10:00+09:00";

  it("와이어프레임의 네 가지 모양을 그대로 만든다", () => {
    expect(formatAdminFullDate(asOf)).toBe("2026년 9월 2일");
    expect(formatAdminMonthDayTime("2026-09-02T16:04:00+09:00")).toBe("9월 2일 16:04");
    expect(formatAdminMonthDay(asOf)).toBe("9월 2일");
    expect(formatAdminSlashDateTime("2026-09-02T16:04:00+09:00")).toBe("9/2 16:04");
    expect(formatAdminSlashDate("2026-08-31T09:12:00+09:00")).toBe("8/31");
    expect(formatAdminAsOf(asOf)).toBe("9월 2일 17:10 기준");
  });

  it("보는 기계의 시간대와 상관없이 한국 날짜로 적는다", () => {
    // 같은 순간을 UTC 로 적어 보내도 한국에서는 9월 3일 오전 1시다.
    expect(formatAdminFullDate("2026-09-02T16:00:00Z")).toBe("2026년 9월 3일");
    expect(formatAdminSlashDateTime("2026-09-02T16:00:00Z")).toBe("9/3 01:00");
  });

  it("자정을 24시로 적지 않는다", () => {
    expect(formatAdminSlashDateTime("2026-09-02T00:00:00+09:00")).toBe("9/2 00:00");
  });
});
