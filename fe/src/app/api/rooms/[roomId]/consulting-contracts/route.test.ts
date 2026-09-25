import { beforeEach, describe, expect, it, vi } from "vitest";
import { POST } from "./route";

const { writeConsultingContract } = vi.hoisted(() => ({ writeConsultingContract: vi.fn() }));

vi.mock("server-only", () => ({}));
vi.mock("@/lib/api/services", () => ({ writeConsultingContract }));

beforeEach(() => {
  writeConsultingContract.mockReset();
});

const request = (message: string) =>
  new Request("http://localhost/api/rooms/92/consulting-contracts", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      topic: "회원탈퇴 흐름 확인",
      scheduledAt: "2026-12-29T14:00:00+09:00",
      durationMinutes: 30,
      price: 50_000,
      message,
    }),
  });

describe("POST /api/rooms/[roomId]/consulting-contracts", () => {
  it("300자 전달 메시지는 백엔드로 보낸다", async () => {
    writeConsultingContract.mockResolvedValueOnce({ contractId: 120 });

    const response = await POST(request("가".repeat(300)), {
      params: Promise.resolve({ roomId: "92" }),
    });

    expect(response.status).toBe(201);
    expect(writeConsultingContract).toHaveBeenCalledWith(
      92,
      expect.objectContaining({ message: "가".repeat(300) }),
    );
  });

  it("301자 전달 메시지는 백엔드를 부르지 않는다", async () => {
    const response = await POST(request("가".repeat(301)), {
      params: Promise.resolve({ roomId: "92" }),
    });

    expect(response.status).toBe(422);
    expect(writeConsultingContract).not.toHaveBeenCalled();
  });
});
