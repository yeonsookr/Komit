import * as React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";
import { PAYOUT_PENDING_LABEL } from "@/lib/contract";
import type { Deliverable, DeliverableMaterial, DevelopmentAgreement, Milestone } from "@/types/contract";
import { QaSubmitPanel } from "./QaSubmitPanel";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
}));
vi.mock("@/components/feedback", () => ({
  useToast: () => vi.fn(),
  Callout: ({ title, children }: { title?: React.ReactNode; children: React.ReactNode }) => (
    <div>
      {title}
      {children}
    </div>
  ),
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

function material(override: Partial<DeliverableMaterial>): DeliverableMaterial {
  return {
    deliverableMaterialId: 501,
    fileId: null,
    url: "https://staging.example.com",
    accessStage: "TEST",
    deliveredAt: "2026-09-09T02:00:00Z",
    ...override,
  };
}

const testUrl = material({});
const paidFile = material({ deliverableMaterialId: 502, fileId: 601, url: null, accessStage: "PAID" });

/** 인수 시험 URL 과 해금 파일을 모두 갖춘 산출물. */
const ready: Deliverable = { ...deliverable, materials: [testUrl, paidFile] };

/** 글자가 이름과 같은 버튼. 여는 태그부터 내용까지 돌려줍니다. */
function button(html: string, label: string): string | undefined {
  return html
    .split("<button")
    .slice(1)
    .map((part) => `<button${part.slice(0, part.indexOf("</button>"))}`)
    .find((tag) => tag.replace(/<[^>]+>/g, "").trim() === label);
}

function isDisabled(tag: string | undefined): boolean {
  return /^<button[^>]*\sdisabled=""/.test(tag ?? "");
}

/** 요청 조건 목록의 줄. 태그를 걷어 낸 글자만 돌려줍니다. */
function checklistRows(html: string): string[] {
  const list = /<ul[^>]*aria-labelledby="handover-checklist-title"[^>]*>([\s\S]*?)<\/ul>/.exec(html)?.[1];
  if (list === undefined) return [];
  return [...list.matchAll(/<li[^>]*>([\s\S]*?)<\/li>/g)].map((row) => row[1].replace(/<[^>]+>/g, ""));
}

/** 두 문장 가운데 하나만 통과돼 돌아온 작업의 확인 문장. */
const halfChecked = (issueId: number | null): Milestone["qaCriteria"] => [
  { qaCriterionId: 211, testCase: "목록이 뜬다", condition: null, status: "RESOLVED", issueId },
  { qaCriterionId: 212, testCase: "검색이 된다", condition: null, status: "UNRESOLVED", issueId },
];

/** 요청하기 전 진행 중인 일반 작업. 통과한 문장이 없습니다. */
const inProgress = milestone({
  status: "IN_PROGRESS",
  confirmedAt: null,
  qaCriteria: [{ qaCriterionId: 201, testCase: "로그인 화면이 뜬다", condition: null, status: "UNRESOLVED", issueId: 312 }],
});

const HANDOVER_REQUEST = "인계하고 확인 요청 보내기";
const RESEND = "확인 요청 다시 보내기";
const RESEND_NOTE = "다시 보내면 자동 통과 기한을 새로 셉니다.";
const RETURNED_NOTE = "김의뢰님이 확인 문장 일부만 통과시켜 작업이";
const HANDOVER_RETURNED_NOTE = "인계 자료도 다시 등록할 수 있습니다.";

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

  describe("최종 인수 시험 요청 조건", () => {
    const pending = milestone({ status: "SUBMITTED", confirmedAt: null, submittedAt: "2026-09-08T02:00:00Z" });

    it.each([
      [true, true, true],
      [false, true, true],
      [true, false, true],
      [true, true, false],
      [false, false, true],
      [false, true, false],
      [true, false, false],
      [false, false, false],
    ])("일반 작업 통과 %s · 시험 자료 %s · 해금 파일 %s 에 따라 요청 버튼을 잠근다", (regularDone, test, paid) => {
      const materials = [...(test ? [testUrl] : []), ...(paid ? [paidFile] : [])];
      const html = render(
        [regularDone ? milestone({}) : pending, handover({})],
        [{ ...deliverable, materials }],
      );

      expect(button(html, HANDOVER_REQUEST)).toBeDefined();
      expect(isDisabled(button(html, HANDOVER_REQUEST))).toBe(!(regularDone && test && paid));
      expect(checklistRows(html).map((row) => row.endsWith(" · 갖춤"))).toEqual([regularDone, test, paid]);
      expect(html.includes("조건을 모두 갖추면 확인을 요청할 수 있습니다.")).toBe(!(regularDone && test && paid));
    });

    it("한 산출물이 아니어도 계약 전체에서 자료를 세어 연다", () => {
      const html = render(
        [milestone({}), handover({})],
        [
          { ...deliverable, materials: [testUrl] },
          { ...deliverable, deliverableId: 402, title: "운영 설명서", materials: [paidFile] },
        ],
      );

      expect(isDisabled(button(html, HANDOVER_REQUEST))).toBe(false);
    });

    it("기존 단일 파일만 있으면 조건을 채우지 않는다", () => {
      const html = render([milestone({}), handover({})], [{ ...deliverable, fileId: 700, materials: [testUrl] }]);

      expect(isDisabled(button(html, HANDOVER_REQUEST))).toBe(true);
      expect(checklistRows(html)[2]).toContain("아직 안 됨");
    });

    it("일반 작업의 요청 버튼은 인계 자료와 상관없이 열려 있다", () => {
      const html = render([inProgress, handover({ status: "SCHEDULED" })], []);

      expect(isDisabled(button(html, "확인 요청 보내기"))).toBe(false);
    });

    it("인계 작업을 통과하면 요청 조건을 보이지 않는다", () => {
      const passed = handover({ status: "CONFIRMED", confirmedAt: "2026-09-12T02:00:00Z" });

      expect(checklistRows(render([milestone({}), passed], [ready]))).toEqual([]);
      expect(render([milestone({}), passed], [ready])).not.toContain("최종 인수 시험 요청 조건");
    });
  });

  describe("확인 요청 중", () => {
    it("일반 작업은 다시 보내기와 기한을 새로 센다는 안내를 보인다", () => {
      const html = render([
        milestone({
          status: "SUBMITTED",
          confirmedAt: null,
          submittedAt: "2026-09-08T02:00:00Z",
          autoPassAt: "2026-09-11T02:00:00Z",
        }),
        handover({ status: "SCHEDULED" }),
      ]);

      expect(button(html, RESEND)).toBeDefined();
      expect(isDisabled(button(html, RESEND))).toBe(false);
      expect(html).toContain(RESEND_NOTE);
      expect(html).toContain("확인 요청 중");
    });

    it("인계 작업도 다시 보내기로 바뀌고 요청 조건을 갖췄으면 열려 있다", () => {
      const html = render(
        [
          milestone({}),
          handover({ status: "SUBMITTED", submittedAt: "2026-09-10T02:00:00Z", autoPassAt: "2026-09-13T02:00:00Z" }),
        ],
        [ready],
      );

      expect(button(html, HANDOVER_REQUEST)).toBeUndefined();
      expect(isDisabled(button(html, RESEND))).toBe(false);
      expect(html).toContain(RESEND_NOTE);
    });

    it("작업마다 서버가 준 자동 통과 시각을 보인다", () => {
      const html = render([
        milestone({
          status: "SUBMITTED",
          confirmedAt: null,
          submittedAt: "2026-09-08T02:00:00Z",
          autoPassAt: "2026-09-11T02:00:00Z",
        }),
        milestone({
          milestoneId: 303,
          status: "SUBMITTED",
          confirmedAt: null,
          submittedAt: "2026-09-10T02:00:00Z",
          autoPassAt: "2026-09-14T02:00:00Z",
        }),
        handover({ status: "SCHEDULED" }),
      ]);

      // 카드마다 시각까지 적는다. 의뢰인 카드와 같은 형식이다.
      expect(html).toContain("9/11 11:00 자동 통과");
      expect(html).toContain("9/14 11:00 자동 통과");
      // 하단 줄은 가장 이른 날만 모아 보인다.
      expect(html).toContain("9월 11일 자동 통과");
      expect(html).not.toContain("9월 14일 자동 통과");
    });

    it("요청하지 않은 작업에는 다시 보내기 안내를 보이지 않는다", () => {
      const html = render([inProgress, handover({ status: "SCHEDULED" })]);

      expect(html).not.toContain(RESEND_NOTE);
      expect(button(html, RESEND)).toBeUndefined();
    });
  });

  describe("돌아온 작업", () => {
    it("일반 작업이면 고쳐 다시 요청하면 기한을 새로 센다고 알린다", () => {
      const returned = milestone({ status: "IN_PROGRESS", confirmedAt: null, qaCriteria: halfChecked(312) });
      const html = render([returned, handover({ status: "SCHEDULED" })]);

      expect(html).toContain("돌아옴");
      expect(html).toContain(RETURNED_NOTE);
      expect(html).toContain("자동 통과 기한을 새로 셉니다.");
      expect(html).not.toContain(HANDOVER_RETURNED_NOTE);
      expect(html).not.toContain(RESEND_NOTE);
    });

    it("인계 작업이면 인계 자료를 다시 등록할 수 있다고도 알린다", () => {
      const html = render([milestone({}), handover({ status: "IN_PROGRESS", qaCriteria: halfChecked(null) })], [ready]);

      expect(html).toContain(RETURNED_NOTE);
      expect(html).toContain(HANDOVER_RETURNED_NOTE);
      expect(isDisabled(button(html, HANDOVER_REQUEST))).toBe(false);
    });

    it("요청하기 전 작업에는 돌아옴 안내를 보이지 않는다", () => {
      const html = render([inProgress, handover({ status: "SCHEDULED" })]);

      expect(html).not.toContain(RETURNED_NOTE);
      expect(html).not.toContain("돌아옴");
    });
  });

  describe("지급 처리 중", () => {
    const passed = handover({ status: "CONFIRMED", confirmedAt: "2026-09-12T02:00:00Z" });

    it("인계 작업을 통과하고 계약이 끝나기 전이면 지급 처리 중이라고 보인다", () => {
      expect(render([milestone({}), passed], [ready])).toContain(PAYOUT_PENDING_LABEL);
    });

    it("지급이 확정돼 계약이 끝났거나 인계 작업 전이면 보이지 않는다", () => {
      expect(render([milestone({}), passed], [ready], "COMPLETED")).not.toContain(PAYOUT_PENDING_LABEL);
      expect(render([milestone({}), handover({})], [ready])).not.toContain(PAYOUT_PENDING_LABEL);
    });
  });

  it("머리 문구는 인계 작업을 통과하면 지급을 요청하고 지급이 확정돼야 끝난다고 적는다", () => {
    const html = render([milestone({}), handover({})]);

    expect(html).not.toContain("대금이 한 번에 나갑니다");
    expect(html).toContain("계약 총액 지급을 한 번에 요청하고, 지급이 확정되면 계약이");
  });

  it("개발자 화면에는 통과시키는 버튼이 없다", () => {
    const states = [
      render([milestone({ status: "SUBMITTED", confirmedAt: null, submittedAt: "2026-09-08T02:00:00Z" }), handover({})]),
      render([milestone({}), handover({ status: "SUBMITTED", submittedAt: "2026-09-10T02:00:00Z" })], [ready]),
      render([milestone({}), handover({ status: "IN_PROGRESS", qaCriteria: halfChecked(null) })], [ready]),
    ];

    for (const html of states) {
      const labels = html.split("<button").slice(1).map((part) => part.slice(0, part.indexOf("</button>")));
      expect(labels.length).toBeGreaterThan(0);
      for (const label of labels) expect(label).not.toContain("통과");
    }
  });
});
