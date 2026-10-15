import { describe, expect, it } from "vitest";
import { ApiError } from "@/types/api";
import { canWriteContractWithoutDraft } from "./contractDraft";

describe("canWriteContractWithoutDraft", () => {
  it.each([502, 503])("AI 초안 실패 %i이면 빈 계약서를 직접 쓸 수 있다", (status) => {
    expect(canWriteContractWithoutDraft(new ApiError(status, "AI_ERROR", "초안 실패"))).toBe(true);
  });

  it.each([0, 401, 403, 404, 500])("AI 초안 외 실패 %i는 직접 작성으로 숨기지 않는다", (status) => {
    expect(canWriteContractWithoutDraft(new ApiError(status, "ERROR", "다른 실패"))).toBe(false);
  });
});
