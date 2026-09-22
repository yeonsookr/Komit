import { beforeEach, describe, expect, it, vi } from "vitest";
import { POST } from "./route";

const { signContract } = vi.hoisted(() => ({ signContract: vi.fn() }));

vi.mock("server-only", () => ({}));
vi.mock("@/lib/api/services", () => ({ signContract }));

beforeEach(() => {
  signContract.mockReset();
});

function requestWith(body: unknown) {
  return new Request("http://localhost/api/contracts/120/signatures", {
    method: "POST",
    body: typeof body === "string" ? body : JSON.stringify(body),
  });
}

const params = Promise.resolve({ contractId: "120" });

describe("POST /api/contracts/[contractId]/signatures", () => {
  /** 서명 그림은 브라우저가 저장소로 직접 보냅니다. 여기로는 fileId 만 옵니다. */
  it("올린 서명으로 계약에 서명한다", async () => {
    signContract.mockResolvedValueOnce({ contractId: 120 });

    const response = await POST(requestWith({ fileId: 612 }), { params });

    expect(response.status).toBe(201);
    expect(signContract).toHaveBeenCalledWith(120, { signatureImageFileId: 612 });
  });

  it("파일 식별자가 없으면 서명하지 않는다", async () => {
    const response = await POST(requestWith({}), { params });

    expect(response.status).toBe(422);
    expect(signContract).not.toHaveBeenCalled();
  });
});
