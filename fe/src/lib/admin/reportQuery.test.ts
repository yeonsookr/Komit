import { describe, expect, it } from "vitest";
import { parseReportQuery, reportsHref } from "./reportQuery";

describe("신고 화면 주소", () => {
  it("빈 주소는 전체 · 1쪽이다", () => {
    expect(parseReportQuery({})).toEqual({ status: undefined, page: 1, reportId: undefined });
  });

  it("주소에 적힌 필터·페이지·선택을 읽는다", () => {
    expect(parseReportQuery({ status: "RESOLVED", page: "3", reportId: "77" })).toEqual({
      status: "RESOLVED",
      page: 3,
      reportId: 77,
    });
  });

  it("손으로 고친 값은 버리고 기본으로 돌아간다", () => {
    expect(parseReportQuery({ status: "WHATEVER", page: "0", reportId: "-1" })).toEqual({
      status: undefined,
      page: 1,
      reportId: undefined,
    });
    expect(parseReportQuery({ page: "2.5", reportId: "abc" })).toEqual({
      status: undefined,
      page: 1,
      reportId: undefined,
    });
  });

  it("같은 이름이 여러 번 오면 첫 값을 쓴다", () => {
    expect(parseReportQuery({ status: ["OPEN", "RESOLVED"] }).status).toBe("OPEN");
  });

  it("기본값은 주소에 적지 않는다", () => {
    expect(reportsHref({ page: 1 })).toBe("/admin/reports");
    expect(reportsHref({ status: "OPEN", page: 1 })).toBe("/admin/reports?status=OPEN");
    expect(reportsHref({ status: "OPEN", page: 2, reportId: 77 })).toBe(
      "/admin/reports?status=OPEN&page=2&reportId=77",
    );
  });

  it("읽은 값을 도로 주소로 만들면 같은 값이 나온다", () => {
    const query = parseReportQuery({ status: "RESOLVED", page: "4", reportId: "70" });
    expect(parseReportQuery(Object.fromEntries(new URL(reportsHref(query), "http://x").searchParams)))
      .toEqual(query);
  });
});
