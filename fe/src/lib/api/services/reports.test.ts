import { beforeEach, describe, expect, it, vi } from "vitest";
import { createReport } from "./reports";

const { post } = vi.hoisted(() => ({ post: vi.fn() }));
vi.mock("server-only", () => ({}));
vi.mock("../server", () => ({ backendApi: () => ({ post }) }));

beforeEach(() => {
  post.mockReset();
});

describe("createReport", () => {
  it("multipart 경계가 설정되도록 공통 JSON 헤더를 지운다", async () => {
    const body = new FormData();
    body.set("reason", "계약 진행 내용을 확인해 주세요.");
    const report = {
      reportId: 77,
      contractId: 130,
      reporterUserId: 12,
      targetUserId: 30,
      reason: "계약 진행 내용을 확인해 주세요.",
      fileId: null,
      status: "OPEN" as const,
      createdAt: "2026-09-18T13:00:00+09:00",
      resolvedAt: null,
    };
    post.mockResolvedValueOnce({ data: report });

    await expect(createReport(130, body)).resolves.toEqual(report);
    expect(post).toHaveBeenCalledWith("/api/contracts/130/reports", body, {
      headers: { "Content-Type": undefined },
    });
  });
});
