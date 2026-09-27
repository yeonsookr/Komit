import { Readable } from "node:stream";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { downloadDeliverable, downloadDeliverableMaterial } from "./milestones";

const { get } = vi.hoisted(() => ({ get: vi.fn() }));

vi.mock("server-only", () => ({}));
vi.mock("../server", () => ({
  authenticatedBackendApi: () => ({}),
  authenticatedDownloadApi: () => ({ get }),
}));

beforeEach(() => {
  get.mockReset();
});

function fileResponse(headers: Record<string, string>, chunks: number[][] = [[1, 2], [3]]) {
  return { data: Readable.from(chunks.map((chunk) => Buffer.from(chunk))), headers };
}

describe("인계 파일 내려받기", () => {
  it("백엔드 본문을 모으지 않고 웹 스트림으로 바꿔 넘긴다", async () => {
    get.mockResolvedValueOnce(
      fileResponse({
        "content-type": "application/zip",
        "content-length": "3",
        "content-disposition": "attachment; filename=\"_.zip\"; filename*=UTF-8''%EC%86%8C%EC%8A%A4.zip",
      }),
    );

    const file = await downloadDeliverableMaterial(501);

    expect(get).toHaveBeenCalledWith("/api/deliverable-materials/501/file", { responseType: "stream" });
    expect(file.body).toBeInstanceOf(ReadableStream);
    expect(file).toMatchObject({ fileName: "소스.zip", contentType: "application/zip", contentLength: "3" });
    expect(new Uint8Array(await new Response(file.body).arrayBuffer())).toEqual(new Uint8Array([1, 2, 3]));
  });

  it("크기가 없거나 숫자가 아니면 null 로 둔다", async () => {
    get.mockResolvedValueOnce(fileResponse({}));
    const missing = await downloadDeliverable(31);

    get.mockResolvedValueOnce(fileResponse({ "content-length": "12, 12" }));
    const malformed = await downloadDeliverable(31);

    expect(get).toHaveBeenCalledWith("/api/deliverables/31/file", { responseType: "stream" });
    expect(missing).toMatchObject({ contentLength: null, contentType: "application/octet-stream" });
    expect(malformed.contentLength).toBeNull();
  });
});
