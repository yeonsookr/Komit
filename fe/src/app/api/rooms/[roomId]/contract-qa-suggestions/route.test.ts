import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/types/api";
import { POST } from "./route";

const { suggestContractQaCriteria } = vi.hoisted(() => ({
  suggestContractQaCriteria: vi.fn(),
}));

vi.mock("server-only", () => ({}));
vi.mock("@/lib/api/services", () => ({ suggestContractQaCriteria }));

beforeEach(() => {
  suggestContractQaCriteria.mockReset();
});

const request = (body: unknown) =>
  new Request("http://localhost/api/rooms/93/contract-qa-suggestions", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });

const call = (body: unknown, roomId = "93") =>
  POST(request(body), { params: Promise.resolve({ roomId }) });

describe("POST /api/rooms/[roomId]/contract-qa-suggestions", () => {
  it("현재 편집 중인 기존 작업 문구를 백엔드에 보낸다", async () => {
    suggestContractQaCriteria.mockResolvedValueOnce({
      content: [{ testCase: "첫째" }, { testCase: "둘째" }, { testCase: "셋째" }],
      page: 1,
      size: 3,
      totalElements: 3,
      totalPages: 1,
    });

    const response = await call({
      issueId: 312,
      title: " 관리자 API에 인증 추가 ",
      detail: " 로그인하지 않은 요청을 막는다 ",
    });

    expect(response.status).toBe(200);
    expect(suggestContractQaCriteria).toHaveBeenCalledWith(93, {
      issueId: 312,
      title: "관리자 API에 인증 추가",
      detail: "로그인하지 않은 요청을 막는다",
    });
    expect((await response.json()).content).toHaveLength(3);
  });

  it("아직 저장하지 않은 새 작업은 issueId null로 보낸다", async () => {
    suggestContractQaCriteria.mockResolvedValueOnce({ content: [] });

    const response = await call({ issueId: null, title: "새 작업", detail: null });

    expect(response.status).toBe(200);
    expect(suggestContractQaCriteria).toHaveBeenCalledWith(93, {
      issueId: null,
      title: "새 작업",
      detail: null,
    });
  });

  it("잘못된 방 식별자와 작업 문구면 백엔드를 부르지 않는다", async () => {
    expect((await call({ issueId: null, title: "새 작업", detail: null }, "abc")).status).toBe(404);

    const invalidBodies = [
      { issueId: "312", title: "작업", detail: null },
      { issueId: null, title: " ", detail: null },
      { issueId: null, title: "가".repeat(201), detail: null },
      { issueId: null, title: "작업", detail: " " },
      { issueId: null, title: "작업", detail: "가".repeat(1001) },
    ];
    for (const body of invalidBodies) {
      expect((await call(body)).status, JSON.stringify(body)).toBe(422);
    }
    expect(suggestContractQaCriteria).not.toHaveBeenCalled();
  });

  it("백엔드의 상태·코드·필드 오류를 그대로 전달한다", async () => {
    suggestContractQaCriteria.mockRejectedValueOnce(
      new ApiError(422, "INVALID_REQUEST", "요청 형식이 올바르지 않습니다.", [
        { field: "title", reason: "1자 이상이어야 합니다." },
      ]),
    );

    const response = await call({ issueId: null, title: "작업", detail: null });

    expect(response.status).toBe(422);
    expect(await response.json()).toEqual({
      code: "INVALID_REQUEST",
      message: "요청 형식이 올바르지 않습니다.",
      errors: [{ field: "title", reason: "1자 이상이어야 합니다." }],
    });
  });
});
