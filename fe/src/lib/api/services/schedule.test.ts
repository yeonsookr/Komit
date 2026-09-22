import { beforeEach, describe, expect, it, vi } from "vitest";
import { getMySchedule } from "./schedule";

const { get } = vi.hoisted(() => ({ get: vi.fn() }));
vi.mock("server-only", () => ({}));
vi.mock("../server", () => ({ authenticatedBackendApi: () => ({ get }) }));

beforeEach(() => get.mockReset());

describe("getMySchedule", () => {
  it("의뢰인과 개발자 일정을 함께 돌려준다", async () => {
    const schedule = {
      upcoming: [
        {
          sessionId: 56,
          contractId: 120,
          roomId: 92,
          role: "CLIENT",
          counterpartName: "이준",
        },
      ],
      past: [],
    };
    get.mockResolvedValueOnce({ data: schedule });

    await expect(getMySchedule()).resolves.toEqual(schedule);
    expect(get).toHaveBeenCalledWith("/api/users/me/sessions");
  });
});
