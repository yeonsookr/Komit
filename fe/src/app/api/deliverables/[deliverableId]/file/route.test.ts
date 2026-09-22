import { beforeEach, describe, expect, it, vi } from "vitest";
import { POST } from "./route";

const { attachDeliverable, downloadDeliverable } = vi.hoisted(() => ({
  attachDeliverable: vi.fn(),
  downloadDeliverable: vi.fn(),
}));

vi.mock("server-only", () => ({}));
vi.mock("@/lib/api/services", () => ({ attachDeliverable, downloadDeliverable }));

beforeEach(() => {
  attachDeliverable.mockReset();
});

function requestWith(body: unknown) {
  return new Request("http://localhost/api/deliverables/31/file", {
    method: "POST",
    body: typeof body === "string" ? body : JSON.stringify(body),
  });
}

const params = Promise.resolve({ deliverableId: "31" });

describe("POST /api/deliverables/[deliverableId]/file", () => {
  /** 산출물은 브라우저가 저장소로 직접 보냅니다. 여기로는 fileId 만 옵니다. */
  it("올린 파일을 인계물에 붙인다", async () => {
    attachDeliverable.mockResolvedValueOnce({ deliverableId: 31 });

    const response = await POST(requestWith({ fileId: 612 }), { params });

    expect(response.status).toBe(201);
    expect(attachDeliverable).toHaveBeenCalledWith(31, 612);
  });

  it("파일 식별자가 없으면 붙이지 않는다", async () => {
    const response = await POST(requestWith({}), { params });

    expect(response.status).toBe(422);
    expect(attachDeliverable).not.toHaveBeenCalled();
  });
});
