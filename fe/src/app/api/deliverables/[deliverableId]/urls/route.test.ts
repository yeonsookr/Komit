import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/types/api";
import { POST } from "./route";

const { addDeliverableUrl } = vi.hoisted(() => ({ addDeliverableUrl: vi.fn() }));

vi.mock("server-only", () => ({}));
vi.mock("@/lib/api/services", () => ({ addDeliverableUrl }));

beforeEach(() => {
  addDeliverableUrl.mockReset();
});

const request = (body: string) =>
  new Request("http://localhost/api/deliverables/401/urls", { method: "POST", body });
const params = (deliverableId: string) => ({ params: Promise.resolve({ deliverableId }) });

describe("POST /api/deliverables/[deliverableId]/urls", () => {
  it("적은 URL 을 다듬어 백엔드로 보낸다", async () => {
    addDeliverableUrl.mockResolvedValueOnce({ deliverableId: 401 });

    const response = await POST(request(JSON.stringify({ url: "  https://demo.example.com " })), params("401"));

    expect(response.status).toBe(201);
    expect(addDeliverableUrl).toHaveBeenCalledWith(401, "https://demo.example.com");
  });

  it("주소 형식 오류는 백엔드의 필드 사유를 그대로 전한다", async () => {
    addDeliverableUrl.mockRejectedValueOnce(
      new ApiError(422, "INVALID_REQUEST", "요청 형식이 올바르지 않습니다.", [
        { field: "url", reason: "http 또는 https 주소여야 합니다." },
      ]),
    );

    const response = await POST(request(JSON.stringify({ url: "ftp://demo" })), params("401"));

    expect(response.status).toBe(422);
    expect(await response.json()).toMatchObject({ errors: [{ field: "url", reason: "http 또는 https 주소여야 합니다." }] });
  });

  it("URL 이 없거나 글자가 아니면 백엔드에 묻지 않는다", async () => {
    for (const body of ["{}", JSON.stringify({ url: "   " }), JSON.stringify({ url: 3 }), "not json"]) {
      expect((await POST(request(body), params("401"))).status).toBe(422);
    }
    expect((await POST(request("{}"), params("abc"))).status).toBe(404);
    expect(addDeliverableUrl).not.toHaveBeenCalled();
  });
});
