import { beforeEach, describe, expect, it, vi } from "vitest";
import { createReport } from "./reports";

const { post } = vi.hoisted(() => ({ post: vi.fn() }));
vi.mock("server-only", () => ({}));
vi.mock("../server", () => ({ authenticatedBackendApi: () => ({ post }) }));

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

beforeEach(() => {
  post.mockReset();
});

describe("createReport", () => {
  it("신고 내용과 파일 식별자를 JSON 으로 보낸다", async () => {
    post.mockResolvedValueOnce({ data: report });

    const body = { reason: "계약 진행 내용을 확인해 주세요.", fileId: null };
    await expect(createReport(130, body)).resolves.toEqual(report);

    // 공통 JSON 헤더를 그대로 쓰므로 세 번째 인자가 없습니다.
    expect(post).toHaveBeenCalledWith("/api/contracts/130/reports", body);
  });

  it("증거를 올렸으면 그 파일 식별자를 담아 보낸다", async () => {
    post.mockResolvedValueOnce({ data: { ...report, fileId: 81 } });

    const body = { reason: "증거를 첨부합니다.", fileId: 81 };
    await createReport(130, body);

    expect(post).toHaveBeenCalledWith("/api/contracts/130/reports", body);
  });
});
