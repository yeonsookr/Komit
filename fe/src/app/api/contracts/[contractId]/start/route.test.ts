import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/types/api";
import { POST } from "./route";

const { startContract } = vi.hoisted(() => ({ startContract: vi.fn() }));

vi.mock("server-only", () => ({}));
vi.mock("@/lib/api/services", () => ({ startContract }));

beforeEach(() => {
  startContract.mockReset();
});

const request = () => new Request("http://localhost/api/contracts/130/start", { method: "POST" });
const params = (contractId: string) => ({ params: Promise.resolve({ contractId }) });

describe("POST /api/contracts/[contractId]/start", () => {
  it("착수한 계약을 백엔드·목과 같은 200 으로 돌려준다", async () => {
    startContract.mockResolvedValueOnce({ contractId: 130 });

    const response = await POST(request(), params("130"));

    expect(response.status).toBe(200);
    expect(startContract).toHaveBeenCalledWith(130);
  });

  it("예치 전이면 백엔드의 409 사유를 그대로 전한다", async () => {
    startContract.mockRejectedValueOnce(new ApiError(409, "DEPOSIT_REQUIRED", "예치가 끝나야 착수할 수 있습니다."));

    const response = await POST(request(), params("130"));

    expect(response.status).toBe(409);
    expect(await response.json()).toMatchObject({ code: "DEPOSIT_REQUIRED" });
  });

  it("숫자가 아닌 계약 식별자는 백엔드에 묻지 않는다", async () => {
    expect((await POST(request(), params("abc"))).status).toBe(404);
    expect(startContract).not.toHaveBeenCalled();
  });
});
