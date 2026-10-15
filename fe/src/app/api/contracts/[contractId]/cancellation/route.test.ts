import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/types/api";
import { POST } from "./route";

const { cancelContract } = vi.hoisted(() => ({ cancelContract: vi.fn() }));

vi.mock("server-only", () => ({}));
vi.mock("@/lib/api/services", () => ({ cancelContract }));

beforeEach(() => {
  cancelContract.mockReset();
});

const request = () => new Request("http://localhost/api/contracts/120/cancellation", { method: "POST" });

describe("POST /api/contracts/[contractId]/cancellation", () => {
  it("계약을 취소하고 취소된 계약을 돌려준다 (R-CON-21)", async () => {
    cancelContract.mockResolvedValueOnce({ contractId: 120, status: "CANCELLED" });

    const response = await POST(request(), { params: Promise.resolve({ contractId: "120" }) });

    expect(response.status).toBe(200);
    expect(await response.json()).toMatchObject({ status: "CANCELLED" });
    expect(cancelContract).toHaveBeenCalledWith(120);
  });

  it("환불 확인 중이면 백엔드의 503 과 문구를 그대로 넘긴다", async () => {
    cancelContract.mockRejectedValueOnce(
      new ApiError(503, "REFUND_PENDING", "계약을 취소했습니다. 환불을 확인하는 중이니 잠시 뒤 다시 눌러 주세요."),
    );

    const response = await POST(request(), { params: Promise.resolve({ contractId: "120" }) });

    expect(response.status).toBe(503);
    expect(await response.json()).toEqual({
      code: "REFUND_PENDING",
      message: "계약을 취소했습니다. 환불을 확인하는 중이니 잠시 뒤 다시 눌러 주세요.",
    });
  });

  it("숫자가 아닌 계약 id 는 백엔드를 부르지 않는다", async () => {
    const response = await POST(request(), { params: Promise.resolve({ contractId: "abc" }) });

    expect(response.status).toBe(404);
    expect(cancelContract).not.toHaveBeenCalled();
  });
});
