import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { PAYOUT_PENDING_LABEL } from "@/lib/contract";
import type { DevelopmentAgreement, DevelopmentStatus, Milestone } from "@/types/contract";
import { ProgressPanel } from "./ProgressPanel";

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
      { qaCriterionId: 202, testCase: "권한 없는 요청을 막는다", condition: null, status: "UNRESOLVED", issueId: 312 },
    ],
    ...override,
  };
}

function handover(override: Partial<Milestone>): Milestone {
  return milestone({ milestoneId: 399, issueId: null, title: "인계", price: 0, isHandover: true, ...override });
}

function development(milestones: Milestone[], status: DevelopmentStatus = "IN_PROGRESS"): DevelopmentAgreement {
  return {
    status,
    totalAmount: 650_000,
    issueIds: [312, 313],
    startDate: "2026-08-26",
    dueDate: "2026-09-16",
    freeRepairDays: 14,
    milestones,
    deliverables: [],
  };
}

function render(target: DevelopmentAgreement): string {
  return renderToStaticMarkup(
    <ProgressPanel roomId={93} development={target} deposit={null} counterpartName="박다빈" received={false} />,
  );
}

type Dot = "done" | "active" | "pending";

/** 진행 목록의 한 줄. 글자와 왼쪽 점의 상태만 봅니다. */
interface Row {
  text: string;
  dot: Dot | null;
}

function rows(html: string): Row[] {
  return [...html.matchAll(/<li\b[^>]*>([\s\S]*?)<\/li>/g)].map(([, inner]) => {
    let dot: Dot | null = null;
    if (/\bbg-success\b/.test(inner)) dot = "done";
    else if (/\bbg-warning\b/.test(inner)) dot = "active";
    else if (/\bbg-line\b/.test(inner)) dot = "pending";
    return { text: inner.replace(/<[^>]+>/g, ""), dot };
  });
}

function rowOf(html: string, title: string): Row {
  const found = rows(html).find((row) => row.text.startsWith(`${title} · `));
  if (found === undefined) throw new Error(`${title} 줄이 없습니다`);
  return found;
}

describe("ProgressPanel", () => {
  it("작업 줄에 예정·진행 중·확인 요청 중·돌아옴·통과 다섯 단계를 붙인다", () => {
    const html = render(
      development([
        milestone({ milestoneId: 301, title: "통과한 작업", status: "CONFIRMED", confirmedAt: "2026-08-28T02:00:00Z" }),
        milestone({
          milestoneId: 302,
          title: "요청한 작업",
          status: "SUBMITTED",
          submittedAt: "2026-08-29T02:20:00Z",
          autoPassAt: "2026-09-01T02:20:00Z",
        }),
        milestone({
          milestoneId: 303,
          title: "돌아온 작업",
          qaCriteria: [
            { qaCriterionId: 203, testCase: "탈퇴할 수 있다", condition: null, status: "RESOLVED", issueId: 313 },
            { qaCriterionId: 204, testCase: "탈퇴 뒤 다시 들어갈 수 없다", condition: null, status: "UNRESOLVED", issueId: 313 },
          ],
        }),
        milestone({ milestoneId: 304, title: "하는 중인 작업" }),
        milestone({ milestoneId: 305, title: "시작 전 작업", status: "SCHEDULED" }),
      ]),
    );

    expect(rowOf(html, "통과한 작업")).toMatchObject({ text: expect.stringContaining("통과한 작업 · 통과"), dot: "done" });
    expect(rowOf(html, "요청한 작업")).toMatchObject({ text: expect.stringContaining("요청한 작업 · 확인 요청 중"), dot: "active" });
    expect(rowOf(html, "돌아온 작업")).toMatchObject({ text: expect.stringContaining("돌아온 작업 · 돌아옴"), dot: "active" });
    expect(rowOf(html, "하는 중인 작업")).toMatchObject({ text: expect.stringContaining("하는 중인 작업 · 진행 중"), dot: "active" });
    // 시작 전 작업은 진행 중인 것과 섞이지 않게 흐린 점으로 둔다.
    expect(rowOf(html, "시작 전 작업")).toMatchObject({ text: expect.stringContaining("시작 전 작업 · 예정"), dot: "pending" });
    expect(html).not.toContain("확인 대기");
  });

  it("같은 진행 중 작업이라도 확인 문장이 일부 통과했으면 돌아옴으로 적는다", () => {
    const html = render(
      development([
        milestone({
          milestoneId: 301,
          title: "일부 통과",
          qaCriteria: [
            { qaCriterionId: 201, testCase: "로그인 화면이 뜬다", condition: null, status: "RESOLVED", issueId: 312 },
            { qaCriterionId: 202, testCase: "권한 없는 요청을 막는다", condition: null, status: "UNRESOLVED", issueId: 312 },
          ],
        }),
        milestone({ milestoneId: 302, title: "체크 없음" }),
      ]),
    );

    expect(rowOf(html, "일부 통과").text).toContain("일부 통과 · 돌아옴");
    expect(rowOf(html, "일부 통과").text).not.toContain("진행 중");
    expect(rowOf(html, "체크 없음").text).toContain("체크 없음 · 진행 중");
  });

  it("인계 작업을 통과하고 계약이 끝나기 전에는 마지막 줄을 지급 처리 중으로 둔다", () => {
    const html = render(
      development([
        milestone({ milestoneId: 301, title: "1차", status: "CONFIRMED" }),
        handover({ status: "CONFIRMED", confirmedAt: "2026-09-10T02:00:00Z" }),
      ]),
    );

    const last = rows(html).at(-1);
    expect(last?.text).toContain(PAYOUT_PENDING_LABEL);
    expect(last?.dot).toBe("active");
    expect(html).not.toContain("계약 완료 후 지급");
  });

  it("인계 작업을 통과하기 전에는 마지막 줄이 계약 완료 후 지급으로 남는다", () => {
    const html = render(
      development([
        milestone({ milestoneId: 301, title: "1차", status: "CONFIRMED" }),
        handover({ status: "SUBMITTED", submittedAt: "2026-09-09T02:00:00Z", autoPassAt: "2026-09-12T02:00:00Z" }),
      ]),
    );

    const last = rows(html).at(-1);
    expect(last?.text).toContain("계약 완료 후 지급");
    expect(last?.dot).toBe("pending");
    expect(html).not.toContain(PAYOUT_PENDING_LABEL);
    expect(rowOf(html, "인계").text).toContain("인계 · 확인 요청 중");
  });

  it("머리 문구는 모두 확인한 뒤 지급을 요청하고 지급이 확정돼야 계약이 끝난다고 적는다", () => {
    const html = render(development([milestone({}), handover({ status: "SCHEDULED" })]));

    expect(html).toContain("지급을 요청하고, 지급이 확정되면 계약이 끝납니다.");
    expect(html).not.toContain("한 번에 나갑니다");
  });
});
