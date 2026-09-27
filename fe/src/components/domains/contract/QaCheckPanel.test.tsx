import * as React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";
import type { DevelopmentAgreement, Milestone } from "@/types/contract";
import { QaCheckPanel } from "./QaCheckPanel";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
}));
vi.mock("@/components/feedback", () => ({
  useToast: () => vi.fn(),
  Callout: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));

function milestone(override: Partial<Milestone>): Milestone {
  return {
    milestoneId: 301,
    issueId: 312,
    title: "1차 · 관리자 API에 인증 추가",
    detail: null,
    location: null,
    workDate: null,
    price: 400_000,
    isHandover: false,
    status: "IN_PROGRESS",
    rejectMessage: null,
    submittedAt: null,
    confirmedAt: null,
    autoPassAt: null,
    qaCriteria: [
      { qaCriterionId: 201, testCase: "로그인 화면이 뜬다", condition: null, status: "UNRESOLVED", issueId: 312 },
    ],
    ...override,
  };
}

function development(milestones: Milestone[]): DevelopmentAgreement {
  return {
    status: "IN_PROGRESS",
    totalAmount: 650_000,
    issueIds: [312, 313],
    startDate: "2026-08-26",
    dueDate: "2026-09-16",
    freeRepairDays: 14,
    milestones,
    deliverables: [],
  };
}

/** 확인 문장 체크 칸 하나의 input 태그. */
function checkboxTags(html: string): string[] {
  return html.match(/<input[^>]*type="checkbox"[^>]*>/g) ?? [];
}

describe("QaCheckPanel", () => {
  it("확인 요청을 받은 작업만 체크하고 제출할 수 있다", () => {
    const html = renderToStaticMarkup(
      <QaCheckPanel
        roomId={93}
        development={development([
          milestone({}),
          milestone({
            milestoneId: 302,
            status: "SUBMITTED",
            submittedAt: "2026-08-29T02:20:00Z",
            qaCriteria: [
              { qaCriterionId: 203, testCase: "탈퇴 뒤 다시 들어갈 수 없다", condition: null, status: "UNRESOLVED", issueId: 313 },
            ],
          }),
        ])}
      />,
    );

    const [notRequested, requested] = checkboxTags(html);
    expect(notRequested).toMatch(/ disabled=""/);
    expect(requested).not.toMatch(/ disabled=""/);
    expect(html).toContain("확인 요청을 받으면 체크할 수 있습니다");
    // 작업 카드의 제출 버튼은 확인 요청을 받은 작업에만 있다. 하단 막대의 전체 제출 버튼 하나를 더해 둘이다.
    expect(html.match(/확인된 기능 제출/g)).toHaveLength(2);
  });
});
