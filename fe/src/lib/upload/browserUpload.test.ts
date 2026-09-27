import { beforeEach, describe, expect, it, vi } from "vitest";
import { uploadFromBrowser } from "./browserUpload";

const { post } = vi.hoisted(() => ({ post: vi.fn() }));

vi.mock("@/lib/api/client", () => ({ clientApi: { post } }));

beforeEach(() => {
  post.mockReset();
  post
    .mockResolvedValueOnce({ data: { uploadId: 9, uploadUrl: "https://storage.example/put", headers: {} } })
    .mockResolvedValueOnce({ data: { fileId: 71 } });
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: true, status: 200 }));
});

describe("uploadFromBrowser", () => {
  it("형식을 모르는 인계 파일은 일반 바이너리로 알린다", async () => {
    await uploadFromBrowser(new File(["x"], "source.custom"), "DELIVERABLE");

    expect(post.mock.calls[0][1]).toMatchObject({ contentType: "application/octet-stream", purpose: "DELIVERABLE" });
  });

  it("다른 용도는 브라우저가 준 형식을 그대로 보낸다", async () => {
    await uploadFromBrowser(new File(["x"], "photo"), "PROFILE_IMAGE");

    expect(post.mock.calls[0][1]).toMatchObject({ contentType: "", purpose: "PROFILE_IMAGE" });
  });

  it("형식을 아는 인계 파일은 그 형식을 보낸다", async () => {
    await uploadFromBrowser(new File(["x"], "source.zip", { type: "application/zip" }), "DELIVERABLE");

    expect(post.mock.calls[0][1]).toMatchObject({ contentType: "application/zip" });
  });
});
