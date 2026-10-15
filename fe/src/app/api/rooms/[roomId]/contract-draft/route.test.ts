import { beforeEach, describe, expect, it, vi } from "vitest";
import { POST } from "./route";

const { generateContractDraft } = vi.hoisted(() => ({
  generateContractDraft: vi.fn(),
}));

vi.mock("server-only", () => ({}));
vi.mock("@/lib/api/services", () => ({ generateContractDraft }));

beforeEach(() => {
  generateContractDraft.mockReset();
});

describe("POST /api/rooms/[roomId]/contract-draft", () => {
  it("방 식별자로 계약 초안을 생성한다", async () => {
    generateContractDraft.mockResolvedValueOnce({ contractId: 120 });

    const response = await POST(new Request("http://localhost/api/rooms/92/contract-draft"), {
      params: Promise.resolve({ roomId: "92" }),
    });

    expect(response.status).toBe(200);
    expect(generateContractDraft).toHaveBeenCalledWith(92);
    await expect(response.json()).resolves.toEqual({ contractId: 120 });
  });

  it("잘못된 방 식별자는 백엔드를 부르지 않는다", async () => {
    const response = await POST(new Request("http://localhost/api/rooms/x/contract-draft"), {
      params: Promise.resolve({ roomId: "x" }),
    });

    expect(response.status).toBe(404);
    expect(generateContractDraft).not.toHaveBeenCalled();
  });
});
