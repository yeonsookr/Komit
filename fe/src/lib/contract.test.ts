import { describe, expect, it } from "vitest";
import {
  canCancel,
  checkedCount,
  confirmedAmount,
  developmentStageLabel,
  isFullyChecked,
  isCancelled,
  isHandover,
  isConsultingDraft,
  consultingPaymentStage,
  isDevelopmentDraft,
  isSignedByAnyone,
  isSignedByBoth,
  hasSigned,
  lastCounterpartMessage,
  nearestAutoPassAt,
  periodDays,
  remainingAmount,
  remainingCount,
  totalAmount,
} from "./contract";
import type { Contract, ContractQaCriterion, Milestone } from "@/types/contract";

function qa(id: number, passed: boolean): ContractQaCriterion {
  return {
    qaCriterionId: id,
    testCase: `문장 ${id}`,
    condition: null,
    status: passed ? "RESOLVED" : "UNRESOLVED",
    issueId: null,
  };
}

function milestone(override: Partial<Milestone> = {}): Milestone {
  return {
    milestoneId: 1,
    issueId: 312,
    title: "관리자 API에 인증 추가",
    detail: null,
    location: "app/api/admin/* · 4개 라우트",
    workDate: 10,
    price: 400_000,
    status: "SUBMITTED",
    rejectMessage: null,
    submittedAt: null,
    confirmedAt: null,
    autoPassAt: null,
    qaCriteria: [],
    ...override,
  };
}

function contract(override: Partial<Contract> = {}): Contract {
  return {
    contractId: 130,
    roomId: 93,
    contractType: "DEVELOPMENT",
    status: "NEGOTIATING",
    consulting: null,
    development: null,
    clientSignature: null,
    developerSignature: null,
    messages: [],
    contractText: null,
    updatedAt: "2026-08-23T10:20:00+09:00",
    ...override,
  };
}

describe("lastCounterpartMessage", () => {
  const messages = [
    {
      messageId: 1,
      content: "개발자 첫 메시지",
      isClient: false,
      createdAt: "2026-08-23T10:00:00+09:00",
    },
    {
      messageId: 2,
      content: "의뢰인 답장",
      isClient: true,
      createdAt: "2026-08-23T10:10:00+09:00",
    },
    {
      messageId: 3,
      content: "개발자 마지막 메시지",
      isClient: false,
      createdAt: "2026-08-23T10:20:00+09:00",
    },
  ];

  it("의뢰인은 개발자가 마지막으로 남긴 말을 읽는다", () => {
    expect(lastCounterpartMessage(contract({ messages }), false)?.content).toBe(
      "개발자 마지막 메시지",
    );
  });

  it("개발자는 의뢰인이 마지막으로 남긴 말을 읽는다", () => {
    expect(lastCounterpartMessage(contract({ messages }), true)?.content).toBe(
      "의뢰인 답장",
    );
  });

  it("상대가 남긴 말이 없으면 null이다", () => {
    expect(lastCounterpartMessage(contract({ messages: [] }), false)).toBeNull();
  });
});

describe("totalAmount", () => {
  it("개발 계약은 development 에서 읽는다", () => {
    const development = { totalAmount: 650_000 } as Contract["development"];
    expect(totalAmount(contract({ development }))).toBe(650_000);
  });

  it("컨설팅 계약은 consulting 에서 읽는다", () => {
    const consulting = { totalAmount: 50_000 } as Contract["consulting"];
    expect(totalAmount(contract({ consulting, contractType: "CONSULTING" }))).toBe(50_000);
  });

  it("계약서가 없으면 null", () => {
    expect(totalAmount(null)).toBeNull();
  });
});

describe("isConsultingDraft", () => {
  const consulting = {
    status: "SIGNED",
    totalAmount: 0,
    topic: "결제 실패 원인 확인",
    detail: "재현 조건과 로그를 함께 확인합니다.",
    sessions: [],
  } as NonNullable<Contract["consulting"]>;

  it("컨설팅 내용은 있지만 세션이 없으면 AI version 1이다", () => {
    expect(isConsultingDraft(contract({ contractType: "CONSULTING", consulting }))).toBe(true);
  });

  it("세션을 정한 컨설팅 계약서는 초안이 아니다", () => {
    expect(
      isConsultingDraft(
        contract({
          contractType: "CONSULTING",
          consulting: { ...consulting, sessions: [{} as never] },
        }),
      ),
    ).toBe(false);
  });

  it("개발 계약과 계약 없음은 컨설팅 초안이 아니다", () => {
    expect(isConsultingDraft(contract())).toBe(false);
    expect(isConsultingDraft(null)).toBe(false);
  });
});

describe("서명", () => {
  const signed = { userId: 12, isSigned: true, signedAt: "2026-08-22T16:10:00+09:00", signatureImageFileId: 41 };
  const unsigned = { userId: 3, isSigned: false, signedAt: null, signatureImageFileId: null };

  it("한쪽만 서명했으면 성립하지 않는다", () => {
    expect(isSignedByBoth(contract({ clientSignature: signed, developerSignature: unsigned }))).toBe(false);
  });

  it("양쪽이 서명하면 성립한다", () => {
    const both = contract({
      clientSignature: signed,
      developerSignature: { ...unsigned, isSigned: true },
    });
    expect(isSignedByBoth(both)).toBe(true);
  });

  it("한쪽만 서명해도 고칠 수 없다", () => {
    expect(isSignedByAnyone(contract({ clientSignature: unsigned, developerSignature: unsigned }))).toBe(false);
    expect(isSignedByAnyone(contract({ clientSignature: unsigned, developerSignature: signed }))).toBe(true);
  });

  it("내 서명만 골라낸다", () => {
    const one = contract({ clientSignature: signed, developerSignature: unsigned });
    expect(hasSigned(one, 12)).toBe(true);
    expect(hasSigned(one, 3)).toBe(false);
  });
});

describe("periodDays", () => {
  it("착수일부터 납기일까지 일수를 센다", () => {
    const development = {
      startDate: "2026-08-26",
      dueDate: "2026-09-16",
    } as NonNullable<Contract["development"]>;
    expect(periodDays(development)).toBe(21);
  });

  it("날짜가 비어 있으면 null", () => {
    const development = { startDate: null, dueDate: "2026-09-16" } as NonNullable<
      Contract["development"]
    >;
    expect(periodDays(development)).toBeNull();
  });
});

describe("작업", () => {
  it("인계 작업은 가격이 0 이다", () => {
    expect(isHandover(milestone({ price: 0 }))).toBe(true);
    expect(isHandover(milestone({ price: 400_000 }))).toBe(false);
  });

  it("체크한 확인 문장을 센다", () => {
    expect(checkedCount(milestone({ qaCriteria: [qa(1, true), qa(2, false)] }))).toBe(1);
  });

  it("모두 체크해야 통과시킬 수 있다", () => {
    expect(isFullyChecked(milestone({ qaCriteria: [qa(1, true), qa(2, false)] }))).toBe(false);
    expect(isFullyChecked(milestone({ qaCriteria: [qa(1, true), qa(2, true)] }))).toBe(true);
  });

  it("확인 문장이 없으면 통과시킬 근거가 없다", () => {
    expect(isFullyChecked(milestone({ qaCriteria: [] }))).toBe(false);
  });
});

describe("금액 집계", () => {
  const milestones = [
    milestone({ milestoneId: 1, price: 400_000, status: "CONFIRMED" }),
    milestone({ milestoneId: 2, price: 200_000 }),
    milestone({ milestoneId: 3, price: 50_000 }),
  ];

  it("통과 확정된 대금만 더한다", () => {
    expect(confirmedAmount(milestones)).toBe(400_000);
  });

  it("남은 작업 수와 대금을 센다", () => {
    expect(remainingCount(milestones)).toBe(2);
    expect(remainingAmount(milestones)).toBe(250_000);
  });
});

describe("nearestAutoPassAt", () => {
  it("가장 이른 자동 통과 시각을 고른다", () => {
    const milestones = [
      milestone({ milestoneId: 1, autoPassAt: "2026-09-03T11:20:00+09:00" }),
      milestone({ milestoneId: 2, autoPassAt: "2026-09-01T11:20:00+09:00" }),
    ];
    expect(nearestAutoPassAt(milestones)).toBe("2026-09-01T11:20:00+09:00");
  });

  it("이미 통과한 작업은 세지 않는다", () => {
    const milestones = [
      milestone({ milestoneId: 1, status: "CONFIRMED", autoPassAt: "2026-09-01T11:20:00+09:00" }),
    ];
    expect(nearestAutoPassAt(milestones)).toBeNull();
  });

  it("확인 요청을 받은 작업이 없으면 null", () => {
    expect(nearestAutoPassAt([milestone()])).toBeNull();
  });
});

describe("canCancel", () => {
  const signedBy = (userId: number) => ({ userId, isSigned: true, signedAt: null, signatureImageFileId: 1 });

  it("개발자가 서명하기 전이면 의뢰인이 취소할 수 있다 (R-CON-21)", () => {
    expect(canCancel(contract(), false)).toBe(true);
    expect(canCancel(contract({ clientSignature: signedBy(12) }), false)).toBe(true);
  });

  it("개발자가 서명했으면 취소할 수 없다", () => {
    expect(canCancel(contract({ developerSignature: signedBy(9) }), false)).toBe(false);
  });

  it("개발자에게는 취소 버튼이 없다", () => {
    expect(canCancel(contract(), true)).toBe(false);
  });

  it("이미 취소된 계약에는 다시 보이지 않는다", () => {
    const cancelled = contract({ status: "CANCELLED" });
    expect(isCancelled(cancelled)).toBe(true);
    expect(canCancel(cancelled, false)).toBe(false);
  });
});

describe("consultingPaymentStage", () => {
  const signedBy = (userId: number) => ({ userId, isSigned: true, signedAt: null, signatureImageFileId: 1 });
  const clientSigned = contract({ clientSignature: signedBy(12) });

  it("개발자에게는 결제 화면을 주지 않는다", () => {
    expect(consultingPaymentStage(clientSigned, true, null)).toBe("developer");
  });

  it("취소된 계약은 결제받지 않는다", () => {
    expect(consultingPaymentStage({ ...clientSigned, status: "CANCELLED" }, false, null)).toBe("cancelled");
  });

  it("의뢰인이 서명하기 전에는 결제받지 않는다", () => {
    expect(consultingPaymentStage(contract(), false, null)).toBe("unsigned");
  });

  it("예치된 금액이 있으면 결제를 마친 것이다", () => {
    expect(consultingPaymentStage(clientSigned, false, 50_000)).toBe("paid");
  });

  it("예치를 건 적이 없거나 결과가 나오기 전이면 결제 화면을 준다", () => {
    expect(consultingPaymentStage(clientSigned, false, null)).toBe("pay");
    expect(consultingPaymentStage(clientSigned, false, 0)).toBe("pay");
  });
});

describe("isDevelopmentDraft", () => {
  const development = (startDate: string | null) =>
    ({ status: "SIGNED", startDate, milestones: [] }) as unknown as Contract["development"];

  it("착수일이 비어 있는 개발 계약서는 AI version 1이다", () => {
    expect(isDevelopmentDraft(contract({ development: development(null) }))).toBe(true);
  });

  it("개발자가 보낸 계약서는 착수일이 있어 초안이 아니다", () => {
    expect(isDevelopmentDraft(contract({ development: development("2026-10-01") }))).toBe(false);
  });

  it("컨설팅 계약과 계약 없음은 개발 초안이 아니다", () => {
    expect(isDevelopmentDraft(contract({ contractType: "CONSULTING" }))).toBe(false);
    expect(isDevelopmentDraft(null)).toBe(false);
  });
});

describe("developmentStageLabel", () => {
  const development = { status: "SIGNED" } as Contract["development"];

  it("개발 쪽이 SIGNED 여도 양쪽이 서명하기 전이면 계약 전이다 (R-CON-01)", () => {
    expect(developmentStageLabel(contract({ status: "NEGOTIATING", development }))).toBe("계약 전");
    expect(developmentStageLabel(contract({ status: "AGREED", development }))).toBe("계약 전");
  });

  it("양쪽이 서명하면 개발 쪽 상태로 단계를 적는다", () => {
    expect(developmentStageLabel(contract({ status: "SIGNED", development }))).toBe("예치 대기");
  });

  it("취소된 계약은 취소됨이다", () => {
    expect(developmentStageLabel(contract({ status: "CANCELLED", development }))).toBe("취소됨");
  });

  it("계약서가 없으면 계약 전이다", () => {
    expect(developmentStageLabel(null)).toBe("계약 전");
  });
});
