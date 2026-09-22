import { beforeEach, describe, expect, it, vi } from "vitest";
import { POST } from "./route";

const { createReport } = vi.hoisted(() => ({ createReport: vi.fn() }));

vi.mock("server-only", () => ({}));
vi.mock("@/lib/api/services", () => ({ createReport }));

beforeEach(() => {
  createReport.mockReset();
});

function requestWith(body: unknown) {
  return new Request("http://localhost/api/contracts/120/reports", {
    method: "POST",
    body: typeof body === "string" ? body : JSON.stringify(body),
  });
}

const params = Promise.resolve({ contractId: "120" });

describe("POST /api/contracts/[contractId]/reports", () => {
  /** 증거 파일은 브라우저가 저장소로 직접 보냅니다. 여기로는 fileId 만 옵니다. */
  it("증거를 붙여 신고한다", async () => {
    createReport.mockResolvedValueOnce({ reportId: 5 });

    const response = await POST(requestWith({ reason: "  약속과 다릅니다  ", fileId: 612 }), {
      params,
    });

    expect(response.status).toBe(201);
    expect(createReport).toHaveBeenCalledWith(120, { reason: "약속과 다릅니다", fileId: 612 });
  });

  /** 증거는 없어도 됩니다. */
  it("증거 없이도 신고한다", async () => {
    createReport.mockResolvedValueOnce({ reportId: 5 });

    const response = await POST(requestWith({ reason: "약속과 다릅니다" }), { params });

    expect(response.status).toBe(201);
    expect(createReport).toHaveBeenCalledWith(120, { reason: "약속과 다릅니다", fileId: null });
  });

  it("사유가 비어 있으면 접수하지 않는다", async () => {
    const response = await POST(requestWith({ reason: "   ", fileId: 612 }), { params });

    expect(response.status).toBe(422);
    expect(createReport).not.toHaveBeenCalled();
  });

  it("증거 식별자가 숫자가 아니면 접수하지 않는다", async () => {
    const response = await POST(requestWith({ reason: "사유", fileId: "612" }), { params });

    expect(response.status).toBe(422);
    expect(createReport).not.toHaveBeenCalled();
  });
});
