import { beforeEach, describe, expect, it, vi } from "vitest";
import { POST } from "./route";

const { updateMyProfileImage, authorized } = vi.hoisted(() => ({
  updateMyProfileImage: vi.fn(),
  authorized: vi.fn(),
}));

vi.mock("server-only", () => ({}));
vi.mock("@/lib/api/services", () => ({ updateMyProfileImage }));
vi.mock("@/lib/auth/session", () => ({ authorized }));

beforeEach(() => {
  updateMyProfileImage.mockReset();
  authorized.mockReset().mockResolvedValue({});
});

function requestWith(body: unknown) {
  return new Request("http://localhost/api/users/me/profile-image", {
    method: "POST",
    body: typeof body === "string" ? body : JSON.stringify(body),
  });
}

describe("POST /api/users/me/profile-image", () => {
  /** 파일 본문은 브라우저가 저장소로 직접 보냅니다. 여기로는 fileId 만 옵니다. */
  it("올린 사진을 계정에 붙인다", async () => {
    updateMyProfileImage.mockResolvedValueOnce({ profileImageUrl: "http://localhost/i.png" });

    const response = await POST(requestWith({ fileId: 612 }));

    expect(response.status).toBe(200);
    expect(updateMyProfileImage).toHaveBeenCalledWith({ fileId: 612 });
  });

  it("파일 식별자가 없으면 붙이지 않는다", async () => {
    const response = await POST(requestWith({}));

    expect(response.status).toBe(422);
    expect(updateMyProfileImage).not.toHaveBeenCalled();
  });

  it("로그인하지 않았으면 붙이지 않는다", async () => {
    authorized.mockRejectedValueOnce(new Error("no session"));

    const response = await POST(requestWith({ fileId: 612 }));

    expect(response.status).toBe(500);
    expect(updateMyProfileImage).not.toHaveBeenCalled();
  });
});
