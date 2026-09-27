import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/types/api";
import { GET } from "./route";

const { downloadDeliverableMaterial } = vi.hoisted(() => ({ downloadDeliverableMaterial: vi.fn() }));

vi.mock("server-only", () => ({}));
vi.mock("@/lib/api/services", () => ({ downloadDeliverableMaterial }));

beforeEach(() => {
  downloadDeliverableMaterial.mockReset();
});

const request = () => new Request("http://localhost/api/deliverable-materials/501/file");
const params = (deliverableMaterialId: string) => ({ params: Promise.resolve({ deliverableMaterialId }) });

describe("GET /api/deliverable-materials/[deliverableMaterialId]/file", () => {
  it("파일을 그대로 넘기고 한글 이름을 브라우저가 읽을 수 있게 싣는다", async () => {
    downloadDeliverableMaterial.mockResolvedValueOnce({
      body: new Uint8Array([1, 2, 3]).buffer,
      fileName: "인계 소스.zip",
      contentType: "application/octet-stream",
    });

    const response = await GET(request(), params("501"));

    expect(response.status).toBe(200);
    expect(response.headers.get("Content-Disposition")).toContain(
      "filename*=UTF-8''%EC%9D%B8%EA%B3%84%20%EC%86%8C%EC%8A%A4.zip",
    );
    expect(new Uint8Array(await response.arrayBuffer())).toEqual(new Uint8Array([1, 2, 3]));
    expect(downloadDeliverableMaterial).toHaveBeenCalledWith(501);
  });

  it("지급 전 PAID 파일이면 백엔드의 409 사유를 그대로 전한다", async () => {
    downloadDeliverableMaterial.mockRejectedValueOnce(
      new ApiError(409, "DELIVERABLE_ACCESS_LOCKED", "대금 지급이 완료된 뒤에 내려받을 수 있습니다."),
    );

    const response = await GET(request(), params("502"));

    expect(response.status).toBe(409);
    expect(await response.json()).toMatchObject({
      code: "DELIVERABLE_ACCESS_LOCKED",
      message: "대금 지급이 완료된 뒤에 내려받을 수 있습니다.",
    });
  });

  it("숫자가 아닌 식별자는 백엔드에 묻지 않고 없다고 답한다", async () => {
    const response = await GET(request(), params("abc"));

    expect(response.status).toBe(404);
    expect(downloadDeliverableMaterial).not.toHaveBeenCalled();
  });
});
