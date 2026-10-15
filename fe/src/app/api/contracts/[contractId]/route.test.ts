import { beforeEach, describe, expect, it, vi } from "vitest";
import { PATCH } from "./route";

const { amendConsultingContract } = vi.hoisted(() => ({ amendConsultingContract: vi.fn() }));

vi.mock("server-only", () => ({}));
vi.mock("@/lib/api/services", () => ({ amendConsultingContract }));

beforeEach(() => {
  amendConsultingContract.mockReset();
});

const json = (body: unknown) =>
  new Request("http://localhost/api/contracts/120", {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });

describe("PATCH /api/contracts/[contractId]", () => {
  it("300자 전달 메시지는 백엔드로 보낸다", async () => {
    amendConsultingContract.mockResolvedValueOnce({ contractId: 120 });

    const response = await PATCH(json({ message: "가".repeat(300) }), {
      params: Promise.resolve({ contractId: "120" }),
    });

    expect(response.status).toBe(200);
    expect(amendConsultingContract).toHaveBeenCalledWith(
      120,
      expect.objectContaining({ message: "가".repeat(300) }),
    );
  });

  it("비운 설명과 메시지를 null 로 보내도 거절하지 않는다", async () => {
    amendConsultingContract.mockResolvedValueOnce({ contractId: 120 });

    const response = await PATCH(json({ price: 70_000, detail: null, message: null }), {
      params: Promise.resolve({ contractId: "120" }),
    });

    expect(response.status).toBe(200);
    expect(amendConsultingContract).toHaveBeenCalledWith(120, { price: 70_000 });
  });

  it("빈 설명은 지우라는 뜻으로 그대로 넘긴다", async () => {
    amendConsultingContract.mockResolvedValueOnce({ contractId: 120 });

    await PATCH(json({ detail: "  " }), { params: Promise.resolve({ contractId: "120" }) });

    expect(amendConsultingContract).toHaveBeenCalledWith(120, { detail: "" });
  });

  it("301자 전달 메시지는 백엔드를 부르지 않는다", async () => {
    const response = await PATCH(json({ message: "가".repeat(301) }), {
      params: Promise.resolve({ contractId: "120" }),
    });

    expect(response.status).toBe(422);
    expect(amendConsultingContract).not.toHaveBeenCalled();
  });
});
