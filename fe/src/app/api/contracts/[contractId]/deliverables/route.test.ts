import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/types/api";
import { POST } from "./route";

const { addDeliverable } = vi.hoisted(() => ({ addDeliverable: vi.fn() }));

vi.mock("server-only", () => ({}));
vi.mock("@/lib/api/services", () => ({ addDeliverable }));

beforeEach(() => {
  addDeliverable.mockReset();
});

const request = (body: string) =>
  new Request("http://localhost/api/contracts/130/deliverables", { method: "POST", body });
const params = (contractId: string) => ({ params: Promise.resolve({ contractId }) });

describe("POST /api/contracts/[contractId]/deliverables", () => {
  it("적은 산출물 이름을 다듬어 백엔드로 보낸다", async () => {
    addDeliverable.mockResolvedValueOnce({ deliverableId: 410, isContractual: false });

    const response = await POST(request(JSON.stringify({ title: " 운영 설명서 보충 " })), params("130"));

    expect(response.status).toBe(201);
    expect(addDeliverable).toHaveBeenCalledWith(130, "운영 설명서 보충");
  });

  it("최종 인수 시험 중이면 백엔드의 409 사유를 그대로 전한다", async () => {
    addDeliverable.mockRejectedValueOnce(
      new ApiError(409, "HANDOVER_UNDER_REVIEW", "최종 인수 시험 중에는 인계 자료를 바꿀 수 없습니다."),
    );

    const response = await POST(request(JSON.stringify({ title: "추가" })), params("130"));

    expect(response.status).toBe(409);
    expect(await response.json()).toMatchObject({ code: "HANDOVER_UNDER_REVIEW" });
  });

  it("이름이 없으면 백엔드에 묻지 않는다", async () => {
    for (const body of ["{}", JSON.stringify({ title: "" }), JSON.stringify({ title: null }), "not json"]) {
      expect((await POST(request(body), params("130"))).status).toBe(422);
    }
    expect((await POST(request("{}"), params("abc"))).status).toBe(404);
    expect(addDeliverable).not.toHaveBeenCalled();
  });
});
