import { beforeEach, describe, expect, it, vi } from "vitest";
import { findDevelopers, getMyDeveloperProfile } from "./developers";

const { authenticatedGet, publicGet } = vi.hoisted(() => ({
  authenticatedGet: vi.fn(),
  publicGet: vi.fn(),
}));

vi.mock("server-only", () => ({}));
vi.mock("../server", () => ({
  authenticatedBackendApi: () => ({ get: authenticatedGet }),
  backendApi: () => ({ get: publicGet }),
}));

beforeEach(() => {
  authenticatedGet.mockReset();
  publicGet.mockReset();
});

describe("developer API client selection", () => {
  it("내 프로필은 인증 Axios 인스턴스로 조회한다", async () => {
    const profile = { intro: "설명하는 개발자" };
    authenticatedGet.mockResolvedValueOnce({ data: profile });

    await expect(getMyDeveloperProfile()).resolves.toEqual(profile);
    expect(authenticatedGet).toHaveBeenCalledWith("/api/users/me/developer-profile");
    expect(publicGet).not.toHaveBeenCalled();
  });

  it("공개 개발자 검색은 공개 Axios 인스턴스를 유지한다", async () => {
    const result = { content: [], page: 1, size: 12, totalElements: 0, totalPages: 0 };
    publicGet.mockResolvedValueOnce({ data: result });

    await expect(findDevelopers()).resolves.toEqual(result);
    expect(publicGet).toHaveBeenCalledWith("/api/developers", {
      params: {},
      paramsSerializer: { indexes: null },
    });
    expect(authenticatedGet).not.toHaveBeenCalled();
  });
});
