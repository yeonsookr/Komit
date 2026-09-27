import { describe, expect, it } from "vitest";
import {
  applyQaSuggestion,
  canSendMilestones,
  HANDOVER_TITLE,
  toEditorState,
  toWriteMilestones,
  type EditorMilestone,
} from "./contractEditor";
import type { Contract, DevelopmentAgreement, Milestone } from "@/types/contract";
import type { Issue } from "@/types/diagnosis";

function milestone(override: Partial<Milestone> = {}): Milestone {
  return {
    milestoneId: 1,
    issueId: 312,
    title: "관리자 화면에 인증이 없다",
    detail: null,
    location: null,
    workDate: null,
    price: 0,
    isHandover: false,
    status: "PROPOSED",
    rejectMessage: null,
    submittedAt: null,
    confirmedAt: null,
    autoPassAt: null,
    qaCriteria: [],
    ...override,
  };
}

function sentence(testCase: string) {
  return { qaCriterionId: 1, testCase, condition: null, status: "UNRESOLVED" as const, issueId: null };
}

function contract(development: Partial<DevelopmentAgreement>): Contract {
  return {
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
      milestones: [],
      deliverables: [],
      ...development,
    },
    clientSignature: null,
    developerSignature: null,
    messages: [],
    contractText: null,
    updatedAt: "2026-09-26T10:00:00+09:00",
  };
}

function issue(issueId: number, status: Issue["status"], testCases: string[] = []): Issue {
  return {
    issueId,
    title: `요구사항 ${issueId}`,
    detail: null,
    qaCriteria: testCases.map((testCase) => ({ testCase })),
    status,
  } as unknown as Issue;
}

function row(override: Partial<EditorMilestone> = {}): EditorMilestone {
  return {
    key: "k",
    issueId: 312,
    title: "관리자 화면에 인증 추가",
    detail: "",
    price: "400000",
    workDate: "",
    qaCriteria: ["로그인 화면이 뜬다"],
    ...override,
  };
}

describe("toEditorState", () => {
  it("AI 초안은 금액 0 인 작업도 모두 일반 작업으로 풀고 금액 칸을 비운다", () => {
    const state = toEditorState(
      contract({
        milestones: [
          milestone({ issueId: 312, qaCriteria: [sentence("로그인 화면이 뜬다")] }),
          milestone({ issueId: 320, title: "회원 검색 추가", detail: "이름으로 찾는다" }),
        ],
      }),
    );

    expect(state.milestones.map((m) => m.issueId)).toEqual([312, 320]);
    expect(state.milestones.map((m) => m.price)).toEqual(["", ""]);
    expect(state.milestones[0].qaCriteria).toEqual(["로그인 화면이 뜬다"]);
    // 합의한 문장이 없는 작업은 적을 칸 하나를 둔다.
    expect(state.milestones[1].qaCriteria).toEqual([""]);
    expect(state.milestones[1].detail).toBe("이름으로 찾는다");
    expect(state.handover).toEqual({ issueId: null, lines: [""] });
  });

  it("보낸 계약서는 인계 표시가 있는 작업을 인계 작업으로 떼고 그 요구사항을 들고 있는다", () => {
    const state = toEditorState(
      contract({
        startDate: "2026-10-01",
        dueDate: "2026-10-20",
        milestones: [
          milestone({ issueId: 312, price: 400_000 }),
          milestone({
            issueId: 330,
            title: HANDOVER_TITLE,
            price: 0,
            isHandover: true,
            qaCriteria: [sentence("소스를 받았다")],
          }),
        ],
      }),
    );

    expect(state.milestones.map((m) => [m.issueId, m.price])).toEqual([[312, "400000"]]);
    expect(state.handover).toEqual({ issueId: 330, lines: ["소스를 받았다"] });
  });

  it("계약서가 없으면 요청서에 담긴 요구사항만 작업으로 채우고 요청서 문장을 넣는다", () => {
    const state = toEditorState(null, [
      issue(311, "SUGGESTED", ["관리자만 들어간다"]),
      issue(312, "UNRESOLVED"),
      issue(313, "RESOLVED"),
      issue(314, "CONTRACTED"),
    ]);

    expect(state.milestones.map((m) => m.issueId)).toEqual([311, 314]);
    expect(state.milestones[0].qaCriteria).toEqual(["관리자만 들어간다"]);
    expect(state.milestones[0].price).toBe("");
  });

  it("채울 것이 없으면 빈 작업 하나로 시작한다", () => {
    const state = toEditorState(null, []);

    expect(state.milestones).toHaveLength(1);
    expect(state.milestones[0].issueId).toBeNull();
  });
});

describe("canSendMilestones", () => {
  const handover = { issueId: null, lines: ["소스를 받았다"] };

  it("이름·확인 문장·1 원 이상의 금액이 있으면 보낼 수 있다", () => {
    expect(canSendMilestones([row()], handover)).toBe(true);
  });

  it("금액을 비우거나 0 원으로 둔 일반 작업은 보낼 수 없다", () => {
    expect(canSendMilestones([row({ price: "" })], handover)).toBe(false);
    expect(canSendMilestones([row({ price: "0" })], handover)).toBe(false);
  });

  it("확인 문장이 없는 작업은 보낼 수 없다", () => {
    expect(canSendMilestones([row({ qaCriteria: [" "] })], handover)).toBe(false);
  });

  it("이름이 없는 작업은 보낼 수 없다", () => {
    expect(canSendMilestones([row({ title: " " })], handover)).toBe(false);
  });

  it("인계 확인 문장이 없으면 보낼 수 없다", () => {
    expect(canSendMilestones([row()], { issueId: null, lines: [""] })).toBe(false);
  });
});

describe("applyQaSuggestion", () => {
  it("빈 입력 칸 하나뿐이면 후보로 교체한다", () => {
    expect(applyQaSuggestion([""], " 로그인 화면이 뜬다 ")).toEqual([
      "로그인 화면이 뜬다",
    ]);
  });

  it("기존 문장은 보존하고 새 후보만 마지막에 더한다", () => {
    expect(applyQaSuggestion(["관리자만 들어간다"], "로그인 화면이 뜬다")).toEqual([
      "관리자만 들어간다",
      "로그인 화면이 뜬다",
    ]);
  });

  it("앞뒤와 연속 공백만 다른 후보는 중복으로 넣지 않는다", () => {
    const lines = ["로그인하지 않으면 로그인 화면이 뜬다"];

    expect(applyQaSuggestion(lines, " 로그인하지  않으면 로그인 화면이 뜬다 ")).toBe(lines);
  });
});

describe("toWriteMilestones", () => {
  it("요구사항에 붙은 작업은 issueId 를 보내고 새 작업은 빼고 보낸다", () => {
    const written = toWriteMilestones(
      [
        row({ detail: " 관리자 주소를 막는다 ", workDate: "5" }),
        row({ issueId: null, title: " 회원 검색 추가 ", price: "200000", qaCriteria: ["이름으로 찾는다", ""] }),
      ],
      { issueId: 330, lines: ["소스를 받았다", " "] },
    );

    expect(written).toEqual([
      {
        issueId: 312,
        title: "관리자 화면에 인증 추가",
        detail: "관리자 주소를 막는다",
        price: 400_000,
        workDate: 5,
        qaCriteria: ["로그인 화면이 뜬다"],
      },
      { title: "회원 검색 추가", price: 200_000, qaCriteria: ["이름으로 찾는다"] },
      { issueId: 330, title: HANDOVER_TITLE, isHandover: true, price: 0, qaCriteria: ["소스를 받았다"] },
    ]);
  });

  it("처음 보내는 인계 작업은 요구사항 없이 보낸다", () => {
    const written = toWriteMilestones([row()], { issueId: null, lines: ["소스를 받았다"] });

    expect(written.at(-1)).toEqual({
      title: HANDOVER_TITLE,
      isHandover: true,
      price: 0,
      qaCriteria: ["소스를 받았다"],
    });
  });
});
