import * as React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";
import { PAYOUT_PENDING_LABEL } from "@/lib/contract";
import { formatShortDateTime } from "@/lib/format";
import type { Deliverable, DevelopmentAgreement, Milestone } from "@/types/contract";
import { FinalAcceptanceConfirm, needsFinalAcceptanceConfirm, QaCheckPanel, willPass } from "./QaCheckPanel";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
}));
vi.mock("@/components/feedback", () => ({
  useToast: () => vi.fn(),
  Callout: ({ title, children }: { title?: React.ReactNode; children: React.ReactNode }) => (
    <div>
      {title && <p>{title}</p>}
      {children}
    </div>
  ),
  Modal: ({ open, children }: { open: boolean; children: React.ReactNode }) =>
    open ? <div role="dialog">{children}</div> : null,
  ModalHeader: ({ title }: { title: React.ReactNode }) => <h2>{title}</h2>,
  ModalFooter: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));
// 실제 목록을 그리면서, 처음에 펼쳐 두라고 넘긴 값도 함께 적는다.
vi.mock("./DeliverableFiles", async (importOriginal) => {
  const original = await importOriginal<typeof import("./DeliverableFiles")>();
  return {
    ...original,
    DeliverableFiles: (props: React.ComponentProps<typeof original.DeliverableFiles>) => (
      <div data-default-open={String(props.defaultOpen)}>
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

/** 확인 요청을 받은 인계 작업. 확인 문장 둘 중 하나를 앞서 통과시켰다. */
function handover(override: Partial<Milestone> = {}): Milestone {
  return milestone({
    milestoneId: 309,
    issueId: null,
    title: "인계",
    price: 0,
    isHandover: true,
    status: "SUBMITTED",
    submittedAt: "2026-09-10T01:00:00Z",
    autoPassAt: "2026-09-13T01:00:00Z",
    qaCriteria: [
      { qaCriterionId: 901, testCase: "시험 URL에서 로그인할 수 있다", condition: null, status: "RESOLVED", issueId: null },
      { qaCriterionId: 902, testCase: "소스를 받아 빌드할 수 있다", condition: null, status: "UNRESOLVED", issueId: null },
    ],
    ...override,
  });
}

function development(milestones: Milestone[], override: Partial<DevelopmentAgreement> = {}): DevelopmentAgreement {
  return {
    status: "IN_PROGRESS",
    totalAmount: 650_000,
    issueIds: [312, 313],
    startDate: "2026-08-26",
    dueDate: "2026-09-16",
    freeRepairDays: 14,
    milestones,
    deliverables: [],
    ...override,
  };
}

const deliverables: Deliverable[] = [
  {
    deliverableId: 41,
    title: "소스 코드",
    fileId: null,
    deliveredAt: null,
    isContractual: true,
    materials: [
      { deliverableMaterialId: 71, fileId: null, url: "https://staging.example.com", accessStage: "TEST", deliveredAt: "2026-09-09T01:00:00Z" },
      { deliverableMaterialId: 72, fileId: 5001, url: null, accessStage: "PAID", deliveredAt: "2026-09-09T01:10:00Z" },
    ],
  },
];

/** 확인 문장 체크 칸 하나의 input 태그. */
function checkboxTags(html: string): string[] {
  return html.match(/<input[^>]*type="checkbox"[^>]*>/g) ?? [];
}

/** 인계 자료 목록을 여닫는 버튼 태그. */
function filesToggleTag(html: string): string {
  return html.match(/<button[^>]*aria-expanded="[^"]*"[^>]*>/)?.[0] ?? "";
}

function render(agreement: DevelopmentAgreement): string {
  return renderToStaticMarkup(<QaCheckPanel roomId={93} development={agreement} />);
}

describe("QaCheckPanel", () => {
  it("확인 요청을 받은 작업만 체크하고 제출할 수 있다", () => {
    const html = render(
      development([
        milestone({}),
        milestone({
          milestoneId: 302,
          status: "SUBMITTED",
          submittedAt: "2026-08-29T02:20:00Z",
          qaCriteria: [
            { qaCriterionId: 203, testCase: "탈퇴 뒤 다시 들어갈 수 없다", condition: null, status: "UNRESOLVED", issueId: 313 },
          ],
        }),
      ]),
    );

    const [notRequested, requested] = checkboxTags(html);
    expect(notRequested).toMatch(/ disabled=""/);
    expect(requested).not.toMatch(/ disabled=""/);
    expect(html).toContain("확인 요청을 받으면 체크할 수 있습니다");
    // 작업 카드의 제출 버튼은 확인 요청을 받은 작업에만 있다. 하단 막대의 전체 제출 버튼 하나를 더해 둘이다.
    expect(html.match(/확인된 기능 제출/g)).toHaveLength(2);
  });

  it("인계 작업이 이번 체크로 통과될 때만 확인창을 연다", () => {
    const regular = milestone({ status: "SUBMITTED", submittedAt: "2026-08-29T02:20:00Z" });

    // 일반 작업 통과에는 확인창이 없다.
    expect(willPass(regular, { 201: true })).toBe(true);
    expect(needsFinalAcceptanceConfirm([regular], { 201: true })).toBe(false);
    // 인계 작업의 남은 문장을 채우면 최종 인수 시험 통과라 묻는다. 전체 제출에 섞여 있어도 같다.
    expect(needsFinalAcceptanceConfirm([handover()], { 902: true })).toBe(true);
    expect(needsFinalAcceptanceConfirm([regular, handover()], { 201: true, 902: true })).toBe(true);
    // 일부만 보내면 돌려보내는 것이라 묻지 않는다.
    const twoLeft = handover({
      qaCriteria: [
        { qaCriterionId: 901, testCase: "시험 URL에서 로그인할 수 있다", condition: null, status: "UNRESOLVED", issueId: null },
        { qaCriterionId: 902, testCase: "소스를 받아 빌드할 수 있다", condition: null, status: "UNRESOLVED", issueId: null },
      ],
    });
    expect(needsFinalAcceptanceConfirm([twoLeft], { 902: true })).toBe(false);
    // 새로 체크한 것이 없거나 확인 요청 중이 아니면 묻지 않는다.
    expect(needsFinalAcceptanceConfirm([handover()], {})).toBe(false);
    expect(needsFinalAcceptanceConfirm([handover({ status: "IN_PROGRESS" })], { 902: true })).toBe(false);
    // 확인 문장이 없거나 이미 모두 통과한 작업은 새 체크가 없어 통과로 보지 않는다.
    expect(willPass(handover({ qaCriteria: [] }), {})).toBe(false);
    const allPassed = handover({
      qaCriteria: [
        { qaCriterionId: 902, testCase: "소스를 받아 빌드할 수 있다", condition: null, status: "RESOLVED", issueId: null },
      ],
    });
    expect(willPass(allPassed, { 902: true })).toBe(false);
  });

  it("확인창은 되돌릴 수 없음, 지급 확정 뒤 해금, 자동 통과를 알린다", () => {
    const html = renderToStaticMarkup(<FinalAcceptanceConfirm />);

    expect(html).toContain("되돌릴 수 없습니다");
    expect(html).toContain("인계 자료도 바뀌지 않습니다");
    expect(html).toContain("계약 총액 지급을 한 번 요청합니다");
    expect(html).toContain("지급액과 수수료가 확정된");
    expect(html).toContain("해금 파일을 받을 수 있습니다");
    expect(html).toContain("기한이 지나면 자동으로 통과됩니다");
    // 화면 문구에는 규칙 번호와 값을 적지 않는다.
    expect(html).not.toMatch(/R-[A-Z]+-\d+|\d+일/);
  });

  it("확인창은 제출을 누르기 전에는 열려 있지 않다", () => {
    const html = render(development([handover()], { deliverables }));

    expect(html).not.toContain('role="dialog"');
    expect(html).not.toContain("최종 인수 시험을 통과시킬까요?");
  });

  it("인계 작업이 확인 요청 중이면 자료 목록을 펼치고 그 자료로 시험하라고 안내한다", () => {
    const html = render(development([handover()], { deliverables }));

    expect(html).toContain('data-default-open="true"');
    expect(filesToggleTag(html)).toContain('aria-expanded="true"');
    expect(html).toContain("https://staging.example.com");
    expect(html).toContain("위 인계 자료로 아래 확인 문장을");
  });

  it("인계 작업이 확인 요청 중이 아니면 자료 목록을 접어 두고 시험 안내를 보이지 않는다", () => {
    const html = render(
      development([handover({ status: "IN_PROGRESS", submittedAt: null, autoPassAt: null, qaCriteria: [] })], {
        deliverables,
      }),
    );

    // 넘기지 않으면 목록의 기본(산출물이 있으면 접힘)을 따른다.
    expect(html).toContain('data-default-open="undefined"');
    expect(filesToggleTag(html)).toContain('aria-expanded="false"');
    expect(html).not.toContain("위 인계 자료로 아래 확인 문장을");
  });

  it("일부만 통과시켜 돌아온 작업에는 자동 통과가 멈췄다고 알린다", () => {
    const returned = milestone({
      qaCriteria: [
        { qaCriterionId: 201, testCase: "로그인 화면이 뜬다", condition: null, status: "RESOLVED", issueId: 312 },
        { qaCriterionId: 202, testCase: "틀린 비밀번호를 막는다", condition: null, status: "UNRESOLVED", issueId: 312 },
      ],
    });

    expect(render(development([returned]))).toContain("다시 요청할 때까지 자동 통과가 멈춥니다");
    // 아직 요청하지 않은 작업은 돌아온 것이 아니다.
    expect(render(development([milestone({})]))).not.toContain("자동 통과가 멈춥니다");
  });

  it("확인 요청 중인 작업마다 서버가 준 자동 통과 시각을 보인다", () => {
    const html = render(
      development([
        milestone({
          status: "SUBMITTED",
          submittedAt: "2026-08-29T02:20:00Z",
          autoPassAt: "2026-09-01T02:20:00Z",
        }),
        milestone({
          milestoneId: 302,
          status: "SUBMITTED",
          submittedAt: "2026-08-30T05:00:00Z",
          autoPassAt: "2026-09-02T09:30:00Z",
          qaCriteria: [
            { qaCriterionId: 203, testCase: "탈퇴 뒤 다시 들어갈 수 없다", condition: null, status: "UNRESOLVED", issueId: 313 },
          ],
        }),
      ]),
    );

    expect(html).toContain(`${formatShortDateTime("2026-09-01T02:20:00Z")} 자동 통과`);
    expect(html).toContain(`${formatShortDateTime("2026-09-02T09:30:00Z")} 자동 통과`);
  });

  it("최종 인수 시험을 통과하고 지급이 확정되기 전이면 지급 처리 중을 보인다", () => {
    const passed = handover({
      status: "CONFIRMED",
      confirmedAt: "2026-09-12T01:00:00Z",
      autoPassAt: null,
      qaCriteria: [
        { qaCriterionId: 901, testCase: "시험 URL에서 로그인할 수 있다", condition: null, status: "RESOLVED", issueId: null },
      ],
    });

    expect(render(development([passed], { deliverables }))).toContain(PAYOUT_PENDING_LABEL);
    expect(render(development([passed], { deliverables, status: "COMPLETED" }))).not.toContain(PAYOUT_PENDING_LABEL);
  });
});
