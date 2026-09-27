import * as React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";
import type { Deliverable, DevelopmentAgreement, Milestone } from "@/types/contract";
import { QaSubmitPanel } from "./QaSubmitPanel";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
}));
vi.mock("@/components/feedback", () => ({
  useToast: () => vi.fn(),
  Callout: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));
// 실제 목록을 그리면서, 접힌 목록 안이라 보이지 않는 넘긴 값도 함께 적는다.
vi.mock("./DeliverableFiles", async (importOriginal) => {
  const original = await importOriginal<typeof import("./DeliverableFiles")>();
  return {
    ...original,
    DeliverableFiles: (props: React.ComponentProps<typeof original.DeliverableFiles>) => (
      <div data-paid={String(props.paid)} data-registration={props.registration}>
        <original.DeliverableFiles {...props} />
      </div>
    ),
  };
});

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
    status: "CONFIRMED",
    rejectMessage: null,
    submittedAt: null,
    confirmedAt: "2026-09-01T02:00:00Z",
    autoPassAt: null,
    qaCriteria: [
      { qaCriterionId: 201, testCase: "로그인 화면이 뜬다", condition: null, status: "RESOLVED", issueId: 312 },
    ],
    ...override,
  };
}

function handover(override: Partial<Milestone>): Milestone {
  return milestone({
    milestoneId: 302,
    issueId: null,
    title: "인계",
    price: 0,
    isHandover: true,
    status: "IN_PROGRESS",
    confirmedAt: null,
    qaCriteria: [
      { qaCriterionId: 202, testCase: "소스를 받아 실행된다", condition: null, status: "UNRESOLVED", issueId: null },
      { qaCriterionId: 203, testCase: "운영 설명서가 있다", condition: null, status: "UNRESOLVED", issueId: null },
    ],
    ...override,
  });
}

const deliverable: Deliverable = {
  deliverableId: 401,
  title: "완성된 웹사이트",
  fileId: null,
  deliveredAt: null,
  isContractual: true,
  materials: [],
};

function render(
  milestones: Milestone[],
  deliverables: Deliverable[] = [deliverable],
  status: DevelopmentAgreement["status"] = "IN_PROGRESS",
): string {
  const development: DevelopmentAgreement = {
    status,
    totalAmount: 400_000,
    issueIds: [312],
    startDate: "2026-08-26",
    dueDate: "2026-09-16",
    freeRepairDays: 14,
    milestones,
    deliverables,
  };
  return renderToStaticMarkup(
    <QaSubmitPanel roomId={93} contractId={130} development={development} counterpartName="김의뢰" />,
  );
}

/** 산출물 추가 칸. 속성 순서와 상관없이 찾습니다. */
function titleInput(html: string): string {
  return html.match(/<input[^>]*aria-label="산출물 항목 이름"[^>]*>/)?.[0] ?? "";
}

const NOT_OPEN = "일반 작업을 모두 통과한 뒤에 인계 자료를 등록할 수 있습니다.";
const UNDER_REVIEW = "최종 인수 시험 중이라 인계 자료를 바꿀 수 없습니다.";
const PASSED = "최종 인수 시험을 통과해 인계 자료를 더 바꿀 수 없습니다.";

describe("QaSubmitPanel", () => {
  it("일반 작업이 남았으면 인계 자료 등록이 아직 열리지 않았다고 알린다", () => {
    const html = render([milestone({ status: "SUBMITTED", confirmedAt: null }), handover({ status: "SCHEDULED" })]);

    expect(html).toContain(NOT_OPEN);
  });

  it("일반 작업을 모두 통과하고 인계 작업을 요청하기 전이면 닫힌 까닭을 보이지 않는다", () => {
    const html = render([milestone({}), handover({})]);

    for (const reason of [NOT_OPEN, UNDER_REVIEW, PASSED]) expect(html).not.toContain(reason);
  });

  it("인계 작업의 확인을 요청했으면 시험 중이라 잠근다", () => {
    const html = render([
      milestone({}),
      handover({ status: "SUBMITTED", submittedAt: "2026-09-10T02:00:00Z", autoPassAt: "2026-09-13T02:00:00Z" }),
    ]);

    expect(html).toContain(UNDER_REVIEW);
  });

  it("인계 작업을 통과했으면 더 바꿀 수 없다고 알린다", () => {
    const html = render([milestone({}), handover({ status: "CONFIRMED", confirmedAt: "2026-09-12T02:00:00Z" })]);

    expect(html).toContain(PASSED);
  });

  it("일부 통과로 인계 작업이 돌아오면 다시 등록할 수 있다", () => {
    const returned = handover({
      status: "IN_PROGRESS",
      submittedAt: null,
      qaCriteria: [
        { qaCriterionId: 202, testCase: "소스를 받아 실행된다", condition: null, status: "RESOLVED", issueId: null },
        { qaCriterionId: 203, testCase: "운영 설명서가 있다", condition: null, status: "UNRESOLVED", issueId: null },
      ],
    });
    const html = render([milestone({}), returned], []);

    expect(html).not.toContain(UNDER_REVIEW);
    // 산출물이 없으면 목록을 펼쳐 둔다. 산출물 추가 칸이 열려 있다.
    expect(titleInput(html)).not.toBe("");
    expect(titleInput(html)).not.toContain(' disabled=""');
  });

  it("닫혀 있으면 산출물 추가도 잠근다", () => {
    const html = render([milestone({}), handover({ status: "SUBMITTED", submittedAt: "2026-09-10T02:00:00Z" })], []);

    expect(titleInput(html)).toContain(' disabled=""');
  });

  it("지급이 끝났는지와 등록 상태를 인계 자료 목록에 넘긴다", () => {
    const passed = handover({ status: "CONFIRMED", confirmedAt: "2026-09-12T02:00:00Z" });

    expect(render([milestone({}), passed], [deliverable], "COMPLETED")).toContain('data-paid="true"');
    expect(render([milestone({}), passed])).toContain('data-paid="false"');
    expect(render([milestone({}), passed])).toContain('data-registration="FINAL_ACCEPTANCE_PASSED"');
  });
});
