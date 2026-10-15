import { beforeEach, describe, expect, it, vi } from "vitest";
import { POST } from "./route";

const { writeDevelopmentContract } = vi.hoisted(() => ({ writeDevelopmentContract: vi.fn() }));

vi.mock("server-only", () => ({}));
vi.mock("@/lib/api/services", () => ({ writeDevelopmentContract }));

beforeEach(() => {
  writeDevelopmentContract.mockReset();
});

const request = (milestones: unknown[]) =>
  new Request("http://localhost/api/rooms/93/development-contracts", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      startDate: "2026-10-01",
      dueDate: "2026-10-20",
      freeRepairDays: 14,
      milestones,
      deliverableTitles: ["소스 파일 전체"],
      message: null,
    }),
  });

const call = (milestones: unknown[]) =>
  POST(request(milestones), { params: Promise.resolve({ roomId: "93" }) });

describe("POST /api/rooms/[roomId]/development-contracts", () => {
  it("작업마다 붙은 요구사항과 이름·설명을 그대로 보낸다", async () => {
    writeDevelopmentContract.mockResolvedValueOnce({ contractId: 130 });

    const response = await call([
      { issueId: 312, title: " 관리자 화면에 인증 추가 ", detail: "관리자 주소를 막는다", price: 400_000, qaCriteria: ["로그인 화면이 뜬다"] },
      { title: "회원 검색 추가", price: 200_000, workDate: 5, qaCriteria: ["이름으로 찾는다"] },
    ]);

    expect(response.status).toBe(201);
    expect(writeDevelopmentContract).toHaveBeenCalledWith(93, {
      startDate: "2026-10-01",
      dueDate: "2026-10-20",
      freeRepairDays: 14,
      milestones: [
        { issueId: 312, title: "관리자 화면에 인증 추가", detail: "관리자 주소를 막는다", price: 400_000, qaCriteria: ["로그인 화면이 뜬다"] },
        { title: "회원 검색 추가", price: 200_000, workDate: 5, qaCriteria: ["이름으로 찾는다"] },
      ],
      deliverableTitles: ["소스 파일 전체"],
    });
  });

  it("요구사항에 붙은 작업은 이름 없이도 보낸다", async () => {
    writeDevelopmentContract.mockResolvedValueOnce({ contractId: 130 });

    const response = await call([{ issueId: 312, price: 400_000, qaCriteria: ["로그인 화면이 뜬다"] }]);

    expect(response.status).toBe(201);
  });

  it("인계 작업 표시를 그대로 보낸다. 표시가 없으면 일반 작업으로 보낸다", async () => {
    writeDevelopmentContract.mockResolvedValueOnce({ contractId: 130 });

    await call([
      { issueId: 312, price: 400_000, isHandover: false, qaCriteria: ["로그인 화면이 뜬다"] },
      { title: "인계", isHandover: true, price: 0, qaCriteria: ["소스를 받았다"] },
    ]);

    expect(writeDevelopmentContract.mock.calls[0][1].milestones).toEqual([
      { issueId: 312, price: 400_000, qaCriteria: ["로그인 화면이 뜬다"] },
      { title: "인계", isHandover: true, price: 0, qaCriteria: ["소스를 받았다"] },
    ]);
  });

  it("요구사항도 이름도 없는 작업, 빈 설명, 확인 문장 없는 작업은 백엔드를 부르지 않는다", async () => {
    const broken = [
      { issueId: 312, price: 0, isHandover: "yes", qaCriteria: ["소스를 받았다"] },
      { price: 400_000, qaCriteria: ["로그인 화면이 뜬다"] },
      { issueId: 312, detail: " ", price: 400_000, qaCriteria: ["로그인 화면이 뜬다"] },
      { issueId: 312, price: 400_000, qaCriteria: [] },
      { issueId: "312", price: 400_000, qaCriteria: ["로그인 화면이 뜬다"] },
    ];
    for (const milestone of broken) {
      const response = await call([milestone]);
      expect(response.status, JSON.stringify(milestone)).toBe(422);
    }
    expect(writeDevelopmentContract).not.toHaveBeenCalled();
  });
});
