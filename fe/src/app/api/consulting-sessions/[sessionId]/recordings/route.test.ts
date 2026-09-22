import { beforeEach, describe, expect, it, vi } from "vitest";
import { POST } from "./route";

const { createSessionRecording } = vi.hoisted(() => ({
  createSessionRecording: vi.fn(),
}));

vi.mock("server-only", () => ({}));
vi.mock("@/lib/api/services", () => ({ createSessionRecording }));

beforeEach(() => {
  createSessionRecording.mockReset();
});

function requestWith(body: unknown) {
  return new Request("http://localhost/api/consulting-sessions/55/recordings", {
    method: "POST",
    body: typeof body === "string" ? body : JSON.stringify(body),
  });
}

const params = Promise.resolve({ sessionId: "55" });

describe("POST /api/consulting-sessions/[sessionId]/recordings", () => {
  it("올린 파일을 세션에 붙이고 201 로 답한다", async () => {
    createSessionRecording.mockResolvedValueOnce({
      recordingId: 71,
      speaker: "CLIENT",
      fileId: 612,
      uploadedAt: "2026-08-29T15:12:00+09:00",
    });

    const response = await POST(requestWith({ fileId: 612 }), { params });

    expect(response.status).toBe(201);
    // 화자는 토큰으로 정해집니다. 요청에 담지 않습니다. (R-CON-14)
    expect(createSessionRecording).toHaveBeenCalledWith(55, { fileId: 612 });
  });

  /** 파일 본문은 브라우저가 저장소로 직접 보냅니다. 여기로는 fileId 만 옵니다. */
  it("파일 식별자가 없으면 붙이지 않는다", async () => {
    const response = await POST(requestWith({}), { params });

    expect(response.status).toBe(422);
    expect(createSessionRecording).not.toHaveBeenCalled();
  });

  it("숫자가 아닌 파일 식별자는 붙이지 않는다", async () => {
    const response = await POST(requestWith({ fileId: "612" }), { params });

    expect(response.status).toBe(422);
    expect(createSessionRecording).not.toHaveBeenCalled();
  });

  it("본문이 JSON 이 아니면 붙이지 않는다", async () => {
    const response = await POST(requestWith("not json"), { params });

    expect(response.status).toBe(422);
    expect(createSessionRecording).not.toHaveBeenCalled();
  });
});
