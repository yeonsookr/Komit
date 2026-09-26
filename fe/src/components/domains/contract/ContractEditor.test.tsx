import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";
import type { Contract, Milestone } from "@/types/contract";
import { ContractEditor } from "./ContractEditor";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
}));
vi.mock("@/components/feedback", () => ({
  useToast: () => vi.fn(),
}));

function milestone(override: Partial<Milestone>): Milestone {
  return {
    milestoneId: 1,
    issueId: 312,
    title: "관리자 화면에 인증이 없다",
    detail: null,
    location: null,
    workDate: null,
    price: 0,
    status: "PROPOSED",
    rejectMessage: null,
    submittedAt: null,
    confirmedAt: null,
    autoPassAt: null,
    qaCriteria: [],
    ...override,
  };
}

const draft: Contract = {
  contractId: 130,
  roomId: 93,
  contractType: "DEVELOPMENT",
  status: "NEGOTIATING",
  consulting: null,
  development: {
    status: "SIGNED",
    totalAmount: 0,
    issueIds: [],
    startDate: null,
    dueDate: null,
    freeRepairDays: 0,
    milestones: [
      milestone({
        qaCriteria: [
          { qaCriterionId: 1, testCase: "로그인 화면이 뜬다", condition: null, status: "UNRESOLVED", issueId: null },
        ],
      }),
      milestone({ milestoneId: 2, issueId: 320, title: "회원 검색 추가", detail: "이름으로 찾는다" }),
    ],
    deliverables: [],
  },
  clientSignature: null,
  developerSignature: null,
  messages: [],
  contractText: null,
  updatedAt: "2026-09-26T10:00:00+09:00",
};

describe("ContractEditor", () => {
  it("AI 초안의 작업 이름·설명·확인 문장을 채우고 금액과 날짜는 비워 둔다", () => {
    const html = renderToStaticMarkup(
      <ContractEditor roomId={93} contract={draft} counterpartName="의뢰인" />,
    );

    expect(html).toContain('value="관리자 화면에 인증이 없다"');
    expect(html).toContain('value="회원 검색 추가"');
    expect(html).toContain(">이름으로 찾는다</textarea>");
    expect(html).toContain('value="로그인 화면이 뜬다"');
    expect(html).not.toContain('value="0"');
    expect(html).toContain('value="14"');
    expect(html).toContain("금액을 정하지 않은 작업이 있습니다");
    expect(html.match(/AI 확인 문장 추천/g)).toHaveLength(2);
  });

  it("AI 초안은 아직 보내지 않은 것이라 새로 쓰는 화면으로 연다", () => {
    const html = renderToStaticMarkup(
      <ContractEditor roomId={93} contract={draft} counterpartName="의뢰인" />,
    );

    expect(html).toContain("개발 계약서 작성");
    expect(html).toContain("계약서 보내기");
    expect(html).not.toContain("계약서 다시 보내기");
  });
});
