import { beforeEach, describe, expect, it, vi } from "vitest";
import { POST } from "./route";

const { uploadFile, createSessionRecording, authorized } = vi.hoisted(() => ({
  uploadFile: vi.fn(),
  createSessionRecording: vi.fn(),
  authorized: vi.fn(),
}));

vi.mock("server-only", () => ({}));
vi.mock("@/lib/api/services", () => ({ uploadFile, createSessionRecording }));
vi.mock("@/lib/auth/session", () => ({ authorized }));

beforeEach(() => {
  uploadFile.mockReset();
  createSessionRecording.mockReset();
  authorized.mockReset().mockResolvedValue({});
});

function requestWith(body: BodyInit) {
  return new Request("http://localhost/api/consulting-sessions/55/recordings", {
    method: "POST",
    body,
  });
}

const params = Promise.resolve({ sessionId: "55" });

describe("POST /api/consulting-sessions/[sessionId]/recordings", () => {
  it("올린 파일을 세션에 붙이고 201 로 답한다", async () => {
    const form = new FormData();
    form.append("recording", new File(["sound"], "session-recording.webm", { type: "audio/webm" }));
    uploadFile.mockResolvedValueOnce({ fileId: 612 });
    createSessionRecording.mockResolvedValueOnce({
      recordingId: 71,
      speaker: "CLIENT",
      fileId: 612,
      uploadedAt: "2026-08-29T15:12:00+09:00",
    });

    const response = await POST(requestWith(form), { params });

    expect(response.status).toBe(201);
    expect(uploadFile).toHaveBeenCalledWith(expect.any(File), "SESSION_RECORDING");
    // 화자는 토큰으로 정해집니다. 요청에 담지 않습니다. (R-CON-14)
    expect(createSessionRecording).toHaveBeenCalledWith(55, { fileId: 612 });
  });

  it("소리 파일이 아니면 올리지 않는다", async () => {
    const form = new FormData();
    form.append("recording", new File(["nope"], "note.txt", { type: "text/plain" }));

    const response = await POST(requestWith(form), { params });

    expect(response.status).toBe(422);
    expect(uploadFile).not.toHaveBeenCalled();
  });

  it("로그인하지 않았으면 올리지 않는다", async () => {
    authorized.mockRejectedValueOnce(new Error("no session"));
    const form = new FormData();
    form.append("recording", new File(["sound"], "r.webm", { type: "audio/webm" }));

    const response = await POST(requestWith(form), { params });

    expect(response.status).toBe(500);
    expect(uploadFile).not.toHaveBeenCalled();
  });
});
