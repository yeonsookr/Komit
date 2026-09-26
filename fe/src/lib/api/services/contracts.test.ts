import { beforeEach, describe, expect, it, vi } from "vitest";
import { suggestContractQaCriteria } from "./contracts";

const { post } = vi.hoisted(() => ({ post: vi.fn() }));

vi.mock("server-only", () => ({}));
vi.mock("../server", () => ({ authenticatedBackendApi: () => ({ post }) }));

beforeEach(() => {
  post.mockReset();
});

describe("suggestContractQaCriteria", () => {
  it("현재 작업 문구를 계약 QA API에 보내고 AI 응답 한도까지 기다린다", async () => {
    const body = { issueId: null, title: "새 작업", detail: null };
    const suggestions = {
      content: [{ testCase: "첫째" }, { testCase: "둘째" }, { testCase: "셋째" }],
      page: 1,
      size: 3,
      totalElements: 3,
      totalPages: 1,
    };
    post.mockResolvedValueOnce({ data: suggestions });

    await expect(suggestContractQaCriteria(93, body)).resolves.toEqual(suggestions);
    expect(post).toHaveBeenCalledWith(
      "/api/rooms/93/contract-qa-suggestions",
      body,
      { timeout: 30_000 },
    );
  });
});
