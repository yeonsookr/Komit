import { beforeEach, describe, expect, it, vi } from "vitest";
import { POST } from "./route";

const { submitMilestone } = vi.hoisted(() => ({ submitMilestone: vi.fn() }));

vi.mock("server-only", () => ({}));
vi.mock("@/lib/api/services", () => ({ submitMilestone }));

beforeEach(() => {
  submitMilestone.mockReset();
});

const request = (body?: string) =>
  new Request("http://localhost/api/milestones/302/submission", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body,
  });

describe("POST /api/milestones/[milestoneId]/submission", () => {
  it("본문에 무엇이 와도 작업 id 만 백엔드로 보낸다", async () => {
    submitMilestone.mockResolvedValue({ milestoneId: 302, status: "SUBMITTED" });

    for (const body of [undefined, "{}", JSON.stringify({ message: "확인 부탁드립니다" })]) {
      const response = await POST(request(body), { params: Promise.resolve({ milestoneId: "302" }) });
      expect(response.status).toBe(201);
    }

    expect(submitMilestone).toHaveBeenCalledTimes(3);
    for (const call of submitMilestone.mock.calls) {
      expect(call).toEqual([302]);
    }
  });

  it("숫자가 아닌 작업 id 는 백엔드에 묻지 않고 없다고 답한다", async () => {
    const response = await POST(request(), { params: Promise.resolve({ milestoneId: "abc" }) });

    expect(response.status).toBe(404);
    expect(submitMilestone).not.toHaveBeenCalled();
  });
});
