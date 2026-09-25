import { beforeEach, describe, expect, it, vi } from "vitest";
import { PATCH } from "./route";

const { amendConsultingContract } = vi.hoisted(() => ({ amendConsultingContract: vi.fn() }));

vi.mock("server-only", () => ({}));
vi.mock("@/lib/api/services", () => ({ amendConsultingContract }));

beforeEach(() => {
  amendConsultingContract.mockReset();
});

const request = (message: string) =>
  new Request("http://localhost/api/contracts/120", {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ message }),
  });

describe("PATCH /api/contracts/[contractId]", () => {
  it("300자 전달 메시지는 백엔드로 보낸다", async () => {
    amendConsultingContract.mockResolvedValueOnce({ contractId: 120 });

    const response = await PATCH(request("가".repeat(300)), {
      params: Promise.resolve({ contractId: "120" }),
    });

    expect(response.status).toBe(200);
    expect(amendConsultingContract).toHaveBeenCalledWith(
      120,
      expect.objectContaining({ message: "가".repeat(300) }),
    );
  });

  it("301자 전달 메시지는 백엔드를 부르지 않는다", async () => {
    const response = await PATCH(request("가".repeat(301)), {
      params: Promise.resolve({ contractId: "120" }),
    });

    expect(response.status).toBe(422);
    expect(amendConsultingContract).not.toHaveBeenCalled();
  });
});
