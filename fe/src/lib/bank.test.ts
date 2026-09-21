import { describe, expect, it } from "vitest";
import { DEMO_BANK_CODE, findBank } from "./bank";
import type { CodeItem } from "@/types/developer";

const BANKS: CodeItem[] = [
  { codeId: 51, codeName: "국민은행" },
  { codeId: 58, codeName: "싸피은행" },
];

describe("findBank", () => {
  it("에스크로 은행코드를 서버 목록의 코드 항목으로 옮긴다", () => {
    expect(findBank(BANKS, DEMO_BANK_CODE)).toEqual({ codeId: 58, codeName: "싸피은행" });
  });

  it("서버 목록에 그 은행이 없으면 null이다", () => {
    expect(findBank([{ codeId: 51, codeName: "국민은행" }], DEMO_BANK_CODE)).toBeNull();
  });

  it("매핑에 없는 은행코드면 null이다", () => {
    expect(findBank(BANKS, "020")).toBeNull();
  });
});
