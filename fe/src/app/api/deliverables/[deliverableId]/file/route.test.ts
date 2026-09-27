import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/types/api";
import { GET, POST } from "./route";

const { attachDeliverable, downloadDeliverable } = vi.hoisted(() => ({
  attachDeliverable: vi.fn(),
  downloadDeliverable: vi.fn(),
}));

vi.mock("server-only", () => ({}));
vi.mock("@/lib/api/services", () => ({ attachDeliverable, downloadDeliverable }));

beforeEach(() => {
  attachDeliverable.mockReset();
  downloadDeliverable.mockReset();
});

function requestWith(body: unknown) {
  return new Request("http://localhost/api/deliverables/31/file", {
    method: "POST",
    body: typeof body === "string" ? body : JSON.stringify(body),
  });
}

const params = Promise.resolve({ deliverableId: "31" });

describe("POST /api/deliverables/[deliverableId]/file", () => {
  /** 산출물은 브라우저가 저장소로 직접 보냅니다. 여기로는 파일 ID와 열람 단계만 옵니다. */
  it("올린 파일을 인계물에 붙인다", async () => {
    attachDeliverable.mockResolvedValueOnce({ deliverableId: 31 });

    const response = await POST(requestWith({ fileId: 612, accessStage: "PAID" }), { params });

    expect(response.status).toBe(201);
    expect(attachDeliverable).toHaveBeenCalledWith(31, 612, "PAID");
  });

  it("파일 식별자가 없으면 붙이지 않는다", async () => {
    const response = await POST(requestWith({}), { params });

    expect(response.status).toBe(422);
    expect(attachDeliverable).not.toHaveBeenCalled();
  });

  it("안전하게 나타낼 수 없는 파일 식별자는 붙이지 않는다", async () => {
    const response = await POST(
      requestWith(`{"fileId": ${Number.MAX_SAFE_INTEGER + 2}, "accessStage": "PAID"}`),
      { params },
    );

    expect(response.status).toBe(422);
    expect(attachDeliverable).not.toHaveBeenCalled();
  });

  it("열람 단계가 없거나 허용값이 아니면 붙이지 않는다", async () => {
    for (const body of [{ fileId: 612 }, { fileId: 612, accessStage: "READY" }]) {
      const response = await POST(requestWith(body), { params });
      expect(response.status).toBe(422);
    }
    expect(attachDeliverable).not.toHaveBeenCalled();
  });
});

describe("GET /api/deliverables/[deliverableId]/file", () => {
  const getRequest = () => new Request("http://localhost/api/deliverables/31/file");

  it("기존 단일 파일을 스트림으로 받아 한글 이름과 크기를 싣는다", async () => {
    downloadDeliverable.mockResolvedValueOnce({
      body: new Blob([new Uint8Array([7, 8])]).stream(),
      fileName: "소스.zip",
      contentType: "application/zip",
      contentLength: "2",
    });

    const response = await GET(getRequest(), { params });

    expect(response.status).toBe(200);
    expect(response.headers.get("Content-Type")).toBe("application/zip");
    expect(response.headers.get("Content-Length")).toBe("2");
    expect(response.headers.get("Content-Disposition")).toContain("filename*=UTF-8''%EC%86%8C%EC%8A%A4.zip");
    expect(new Uint8Array(await response.arrayBuffer())).toEqual(new Uint8Array([7, 8]));
    expect(downloadDeliverable).toHaveBeenCalledWith(31);
  });

  it("지급 전이면 백엔드의 409 사유를 같은 모양으로 전한다", async () => {
    downloadDeliverable.mockRejectedValueOnce(
      new ApiError(409, "DELIVERABLE_ACCESS_LOCKED", "대금 지급이 완료된 뒤에 내려받을 수 있습니다."),
    );

    const response = await GET(getRequest(), { params });

    expect(response.status).toBe(409);
    expect(await response.json()).toMatchObject({
      code: "DELIVERABLE_ACCESS_LOCKED",
      message: "대금 지급이 완료된 뒤에 내려받을 수 있습니다.",
    });
  });
});
