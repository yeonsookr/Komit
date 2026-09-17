import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import type { MySession } from "@/types/schedule";
import { ScheduleView } from "./ScheduleView";

const BASE_SESSION: MySession = {
  sessionId: 55,
  contractId: 120,
  roomId: 92,
  role: "CLIENT",
  counterpartName: "이준",
  scheduledAt: "2026-08-22T15:00:00+09:00",
  durationMinutes: 30,
  price: 50_000,
  endedAt: null,
  followUpUntil: null,
};

describe("ScheduleView", () => {
  it("계약마다 의뢰인과 개발자 역할을 구분한다", () => {
    const html = renderToStaticMarkup(
      <ScheduleView
        schedule={{
          upcoming: [BASE_SESSION],
          past: [
            {
              ...BASE_SESSION,
              sessionId: 57,
              roomId: 101,
              role: "DEVELOPER",
              counterpartName: "박준호",
              endedAt: "2026-08-15T15:31:00+09:00",
              followUpUntil: "2026-08-22T23:59:59+09:00",
            },
          ],
        }}
        now="2026-08-22T14:48:00+09:00"
      />,
    );

    expect(html).toContain("의뢰한 상담");
    expect(html).toContain("맡은 상담");
    expect(html).toContain("이준");
    expect(html).toContain("박준호");
    expect(html).toContain("시작 12분 전");
    expect(html).toContain("50,000원 받음");
    expect(html).toContain('href="/rooms/92"');
    expect(html).toContain('href="/rooms/101"');
  });

  it("어느 역할로도 일정이 없으면 공통 빈 상태를 보여준다", () => {
    const html = renderToStaticMarkup(
      <ScheduleView
        schedule={{ upcoming: [], past: [] }}
        now="2026-08-22T14:48:00+09:00"
      />,
    );

    expect(html).toContain("아직 일정이 없습니다");
  });
});
