import { describe, expect, it } from "vitest";
import { safeNext } from "./safeNext";

describe("safeNext", () => {
  it("우리 화면의 경로는 그대로 돌려준다", () => {
    expect(safeNext("/checkup")).toBe("/checkup");
    expect(safeNext("/rooms/12?tab=chat")).toBe("/rooms/12?tab=chat");
  });

  it("배열이면 첫 값만 본다", () => {
    expect(safeNext(["/checkup", "/other"])).toBe("/checkup");
  });

  it("없으면 undefined", () => {
    expect(safeNext(undefined)).toBeUndefined();
    expect(safeNext(null)).toBeUndefined();
    expect(safeNext([])).toBeUndefined();
  });

  it("다른 사이트로 읽힐 수 있는 값은 막는다", () => {
    expect(safeNext("https://evil.com")).toBeUndefined();
    expect(safeNext("//evil.com")).toBeUndefined();
    expect(safeNext("/\\evil.com")).toBeUndefined();
    expect(safeNext("evil.com/login")).toBeUndefined();
  });
});
