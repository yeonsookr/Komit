import { describe, expect, it } from "vitest";
import {
  canCancel,
  checkedCount,
  confirmedAmount,
  developmentStageLabel,
  handoverChecklist,
  handoverRegistration,
  isFullyChecked,
  isCancelled,
  isAwaitingCheck,
  isHandover,
  isHandoverReady,
  isPayoutPending,
  isReturned,
  isCompleted,
  isConsultingDraft,
  consultingPaymentStage,
  isDevelopmentDraft,
  isHttpUrl,
  isSignedByAnyone,
  isSignedByBoth,
  hasSigned,
  lastCounterpartMessage,
  milestoneActionFailure,
  milestoneStageLabel,
  nearestAutoPassAt,
  PAYOUT_PENDING_LABEL,
  periodDays,
  registrationFailure,
  remainingAmount,
  remainingCount,
  totalAmount,
} from "./contract";
import { ApiError } from "@/types/api";
import type {
  Contract,
  ContractQaCriterion,
  Deliverable,
  DeliverableMaterial,
  DevelopmentAgreement,
  Milestone,
} from "@/types/contract";

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
    isHandover: false,
    status: "SUBMITTED",
    rejectMessage: null,
    submittedAt: null,
    confirmedAt: null,
    autoPassAt: null,
    qaCriteria: [],
    ...override,
  };
}

/** 착수한 개발 계약. 작업과 산출물은 각 테스트가 채웁니다. */
function agreement(override: Partial<DevelopmentAgreement> = {}): DevelopmentAgreement {
  return {
    status: "IN_PROGRESS",
    totalAmount: 400_000,
    issueIds: [312],
    startDate: "2026-09-01",
    dueDate: "2026-09-21",
    freeRepairDays: 30,
    milestones: [],
    deliverables: [],
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
    expect(isSignedByAnyone(contract({ clientSignature: signed, developerSignature: unsigned }))).toBe(true);
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
  it("인계 작업은 금액이 아니라 서버가 준 표시로 가린다", () => {
    expect(isHandover(milestone({ price: 0, isHandover: true }))).toBe(true);
    expect(isHandover(milestone({ price: 400_000 }))).toBe(false);
    // 표시가 없으면 금액이 0 이어도 인계 작업으로 읽지 않는다 (R-QA-06).
    expect(isHandover(milestone({ price: 0, isHandover: false }))).toBe(false);
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

  it("인계 작업을 통과하고 지급이 확정되기 전이면 지급 처리 중이다 (R-PAY-01)", () => {
    const milestones = [
      milestone({ milestoneId: 1, status: "CONFIRMED" }),
      milestone({ milestoneId: 2, isHandover: true, price: 0, status: "CONFIRMED" }),
    ];
    const signed = (status: DevelopmentAgreement["status"]) =>
      contract({ status: "SIGNED", development: agreement({ status, milestones }) });

    expect(developmentStageLabel(signed("IN_PROGRESS"))).toBe(PAYOUT_PENDING_LABEL);
    expect(developmentStageLabel(signed("COMPLETED"))).toBe("완료");
  });

  it("인계 작업을 통과하기 전에는 진행 중이다", () => {
    const milestones = [
      milestone({ milestoneId: 1, status: "CONFIRMED" }),
      milestone({ milestoneId: 2, isHandover: true, price: 0, status: "SUBMITTED" }),
    ];

    expect(developmentStageLabel(contract({ status: "SIGNED", development: agreement({ milestones }) }))).toBe(
      "진행 중",
    );
  });
});

describe("isCompleted", () => {
  const consulting = (startedAt: string | null, endedAt: string | null) =>
    contract({
      contractType: "CONSULTING",
      consulting: {
        status: "SIGNED",
        totalAmount: 50_000,
        topic: "결제 실패 원인 확인",
        detail: null,
        sessions: [
          {
            sessionId: 80,
            contractId: 130,
            price: 50_000,
            durationMinutes: 30,
            reservation: { reservationId: 1, scheduledAt: "2026-08-29T14:00:00+09:00", status: "CONFIRMED" },
            startedAt,
            endedAt,
            chatRefId: "room-93",
          },
        ],
      } as unknown as Contract["consulting"],
    });

  it("양쪽이 들어와 시작한 세션이 끝나면 컨설팅은 끝난 거래다 (R-REV-02)", () => {
    expect(isCompleted(consulting("2026-08-29T14:00:00+09:00", "2026-08-29T14:32:00+09:00"))).toBe(true);
  });

  it("시작하지 않고 종료만 눌린 세션은 끝난 거래가 아니다 (R-CON-12)", () => {
    expect(isCompleted(consulting(null, "2026-08-29T14:30:00+09:00"))).toBe(false);
  });

  it("세션이 끝나기 전에는 끝난 거래가 아니다", () => {
    expect(isCompleted(consulting("2026-08-29T14:00:00+09:00", null))).toBe(false);
  });
});

describe("isAwaitingCheck", () => {
  it("확인 요청을 받은 작업만 의뢰인이 체크한다", () => {
    expect(isAwaitingCheck(milestone({ status: "SUBMITTED" }))).toBe(true);
    for (const status of ["IN_PROGRESS", "SCHEDULED", "CONFIRMED", "PROPOSED"] as const) {
      expect(isAwaitingCheck(milestone({ status }))).toBe(false);
    }
  });
});

describe("handoverRegistration", () => {
  const regular = (status: Milestone["status"]) => milestone({ milestoneId: 1, status });
  const handover = (status: Milestone["status"], milestoneId = 2) =>
    milestone({ milestoneId, isHandover: true, price: 0, status });

  it("일반 작업이 하나라도 남았으면 아직 열리지 않는다", () => {
    expect(handoverRegistration([regular("CONFIRMED"), regular("SUBMITTED"), handover("SCHEDULED")])).toBe(
      "HANDOVER_NOT_OPEN",
    );
  });

  it("일반 작업을 모두 통과하면 인계 작업의 확인을 요청하기 전까지 열린다", () => {
    expect(handoverRegistration([regular("CONFIRMED"), handover("IN_PROGRESS")])).toBe("OPEN");
  });

  it("인계 작업의 확인을 요청하면 시험 중이라 닫힌다", () => {
    expect(handoverRegistration([regular("CONFIRMED"), handover("SUBMITTED")])).toBe("HANDOVER_UNDER_REVIEW");
  });

  it("인계 작업을 통과하면 최종 인수 시험을 마쳐 닫힌다", () => {
    expect(handoverRegistration([regular("CONFIRMED"), handover("CONFIRMED")])).toBe("FINAL_ACCEPTANCE_PASSED");
  });

  it("일부 통과로 인계 작업이 돌아오면 다시 열린다", () => {
    // 서버는 일부만 체크하면 작업을 IN_PROGRESS 로 되돌리고 요청 시각을 비운다.
    const returned = milestone({
      milestoneId: 2,
      isHandover: true,
      price: 0,
      status: "IN_PROGRESS",
      submittedAt: null,
      qaCriteria: [qa(1, true), qa(2, false)],
    });
    expect(handoverRegistration([regular("CONFIRMED"), returned])).toBe("OPEN");
  });

  it("인계 작업이 여럿이면 하나라도 통과했거나 요청 중일 때 닫는다", () => {
    expect(handoverRegistration([regular("CONFIRMED"), handover("IN_PROGRESS"), handover("CONFIRMED", 3)])).toBe(
      "FINAL_ACCEPTANCE_PASSED",
    );
    expect(handoverRegistration([regular("CONFIRMED"), handover("IN_PROGRESS"), handover("SUBMITTED", 3)])).toBe(
      "HANDOVER_UNDER_REVIEW",
    );
  });
});

describe("isReturned", () => {
  it("일부만 통과해 돌아온 작업은 통과한 문장이 남아 있다 (R-QA-02)", () => {
    // 서버는 일부만 체크하면 작업을 IN_PROGRESS 로 되돌리고 요청 시각을 비운다.
    const returned = milestone({ status: "IN_PROGRESS", submittedAt: null, qaCriteria: [qa(1, true), qa(2, false)] });
    expect(isReturned(returned)).toBe(true);
  });

  it("요청하기 전 작업, 확인 요청 중 작업, 통과한 작업은 돌아온 것이 아니다", () => {
    expect(isReturned(milestone({ status: "IN_PROGRESS", qaCriteria: [qa(1, false), qa(2, false)] }))).toBe(false);
    expect(
      isReturned(
        milestone({
          status: "SUBMITTED",
          submittedAt: "2026-09-10T10:00:00+09:00",
          qaCriteria: [qa(1, true), qa(2, false)],
        }),
      ),
    ).toBe(false);
    expect(isReturned(milestone({ status: "CONFIRMED", qaCriteria: [qa(1, true), qa(2, true)] }))).toBe(false);
  });
});

describe("milestoneStageLabel", () => {
  it("작업 단계를 예정·진행 중·확인 요청 중·돌아옴·통과로 나눈다", () => {
    const submitted = milestone({
      status: "SUBMITTED",
      submittedAt: "2026-09-10T10:00:00+09:00",
      qaCriteria: [qa(1, true), qa(2, false)],
    });
    const returned = milestone({ status: "IN_PROGRESS", submittedAt: null, qaCriteria: [qa(1, true), qa(2, false)] });

    expect(milestoneStageLabel(milestone({ status: "SCHEDULED", qaCriteria: [qa(1, false)] }))).toBe("예정");
    expect(milestoneStageLabel(milestone({ status: "IN_PROGRESS", qaCriteria: [qa(1, false)] }))).toBe("진행 중");
    expect(milestoneStageLabel(submitted)).toBe("확인 요청 중");
    expect(milestoneStageLabel(returned)).toBe("돌아옴");
    expect(milestoneStageLabel(milestone({ status: "CONFIRMED", qaCriteria: [qa(1, true)] }))).toBe("통과");
  });

  it("같은 진행 중이어도 통과한 문장이 있어야 돌아옴이다", () => {
    const inProgress = milestone({ status: "IN_PROGRESS", submittedAt: null, qaCriteria: [qa(1, false), qa(2, false)] });
    const returned = milestone({ status: "IN_PROGRESS", submittedAt: null, qaCriteria: [qa(1, true), qa(2, false)] });

    expect(milestoneStageLabel(inProgress)).toBe("진행 중");
    expect(milestoneStageLabel(returned)).toBe("돌아옴");
  });
});

describe("handoverChecklist", () => {
  type Kind = "URL" | "TEST" | "PAID";

  const regular = (status: Milestone["status"]) => milestone({ milestoneId: 1, status });
  const handover = milestone({ milestoneId: 2, isHandover: true, price: 0, status: "IN_PROGRESS" });

  function material(kind: Kind, id: number): DeliverableMaterial {
    return {
      deliverableMaterialId: id,
      fileId: kind === "URL" ? null : id + 100,
      url: kind === "URL" ? "https://demo.example.com" : null,
      // URL 자료는 언제나 TEST 다.
      accessStage: kind === "PAID" ? "PAID" : "TEST",
      deliveredAt: "2026-09-10T06:00:00Z",
    };
  }

  function deliverableOf(
    materials: DeliverableMaterial[],
    deliverableId = 401,
    fileId: number | null = null,
  ): Deliverable {
    return {
      deliverableId,
      title: `산출물 ${deliverableId}`,
      fileId,
      deliveredAt: null,
      isContractual: true,
      materials,
    };
  }

  /** 한 산출물에 모두 담은 계약. */
  const together = (kinds: Kind[]) =>
    agreement({
      milestones: [regular("CONFIRMED"), handover],
      deliverables: [deliverableOf(kinds.map((kind, index) => material(kind, 501 + index)))],
    });

  /** 자료마다 다른 산출물에 나눠 담은 계약. 자료 없는 산출물도 하나 둡니다. */
  const spread = (kinds: Kind[]) =>
    agreement({
      milestones: [regular("CONFIRMED"), handover],
      deliverables: [
        ...kinds.map((kind, index) => deliverableOf([material(kind, 501 + index)], 401 + index)),
        deliverableOf([], 499),
      ],
    });

  // 계획 7절 자료 표. URL 과 TEST 가 함께 있어도 거부하지 않는다 (R-QA-06).
  const table: { kinds: Kind[]; testMaterial: boolean; paidFile: boolean }[] = [
    { kinds: [], testMaterial: false, paidFile: false },
    { kinds: ["URL"], testMaterial: true, paidFile: false },
    { kinds: ["TEST"], testMaterial: true, paidFile: false },
    { kinds: ["PAID"], testMaterial: false, paidFile: true },
    { kinds: ["URL", "TEST"], testMaterial: true, paidFile: false },
    { kinds: ["URL", "PAID"], testMaterial: true, paidFile: true },
    { kinds: ["TEST", "PAID"], testMaterial: true, paidFile: true },
    { kinds: ["URL", "TEST", "PAID"], testMaterial: true, paidFile: true },
  ];

  it.each(table)(
    "한 산출물에 $kinds 가 있으면 시험 자료 $testMaterial · 해금 파일 $paidFile",
    ({ kinds, testMaterial, paidFile }) => {
      const checklist = handoverChecklist(together(kinds));

      expect(checklist).toEqual({ regularDone: true, testMaterial, paidFile });
      expect(isHandoverReady(checklist)).toBe(testMaterial && paidFile);
    },
  );

  it.each(table)(
    "여러 산출물에 $kinds 가 나뉘어 있으면 시험 자료 $testMaterial · 해금 파일 $paidFile",
    ({ kinds, testMaterial, paidFile }) => {
      const checklist = handoverChecklist(spread(kinds));

      expect(checklist).toEqual({ regularDone: true, testMaterial, paidFile });
      expect(isHandoverReady(checklist)).toBe(testMaterial && paidFile);
    },
  );

  it("기존 단일 파일만 있으면 어느 조건도 채우지 않는다", () => {
    const checklist = handoverChecklist(
      agreement({ milestones: [regular("CONFIRMED"), handover], deliverables: [deliverableOf([], 401, 88)] }),
    );

    expect(checklist).toEqual({ regularDone: true, testMaterial: false, paidFile: false });
    expect(isHandoverReady(checklist)).toBe(false);
  });

  it("일반 작업이 하나라도 남았으면 자료가 모두 있어도 요청할 수 없다", () => {
    const checklist = handoverChecklist(
      agreement({
        milestones: [regular("CONFIRMED"), milestone({ milestoneId: 3, status: "SUBMITTED" }), handover],
        deliverables: [deliverableOf([material("URL", 501), material("PAID", 502)])],
      }),
    );

    expect(checklist).toEqual({ regularDone: false, testMaterial: true, paidFile: true });
    expect(isHandoverReady(checklist)).toBe(false);
  });

  it("인계 작업은 일반 작업 통과에 세지 않는다", () => {
    expect(handoverChecklist(agreement({ milestones: [regular("CONFIRMED"), handover] })).regularDone).toBe(true);
  });
});

describe("isPayoutPending", () => {
  const regular = milestone({ milestoneId: 1, status: "CONFIRMED" });
  const handover = (status: Milestone["status"]) =>
    milestone({ milestoneId: 2, isHandover: true, price: 0, status });

  it("인계 작업을 통과했고 계약이 아직 진행 중이면 지급 처리 중이다 (R-PAY-01)", () => {
    expect(isPayoutPending(agreement({ status: "IN_PROGRESS", milestones: [regular, handover("CONFIRMED")] }))).toBe(
      true,
    );
  });

  it("지급이 확정돼 계약이 완료되면 지급 처리 중이 아니다", () => {
    expect(isPayoutPending(agreement({ status: "COMPLETED", milestones: [regular, handover("CONFIRMED")] }))).toBe(
      false,
    );
  });

  it("하자보수나 중단으로 넘어간 계약은 지급 처리 중으로 보지 않는다", () => {
    for (const status of ["AFTER_SERVICE", "TERMINATED"] as const) {
      expect(isPayoutPending(agreement({ status, milestones: [regular, handover("CONFIRMED")] }))).toBe(false);
    }
  });

  it("인계 작업을 통과하기 전에는 지급 처리 중이 아니다", () => {
    for (const status of ["IN_PROGRESS", "SUBMITTED"] as const) {
      expect(isPayoutPending(agreement({ status: "IN_PROGRESS", milestones: [regular, handover(status)] }))).toBe(
        false,
      );
    }
  });
});

describe("milestoneActionFailure", () => {
  it("409 는 그 사이 작업 상태가 바뀐 것이라 다시 읽는다", () => {
    expect(
      milestoneActionFailure(
        new ApiError(409, "MILESTONE_STATE_CONFLICT", "작업 상태가 바뀌었습니다."),
        "통과시키지 못했습니다.",
      ),
    ).toEqual({ title: "작업 상태가 바뀌었습니다.", refresh: true });
    expect(
      milestoneActionFailure(
        new ApiError(
          409,
          "HANDOVER_MATERIALS_MISSING",
          "인수 시험 자료와 해금 파일을 등록한 뒤에 확인을 요청할 수 있습니다.",
        ),
        "요청하지 못했습니다.",
      ),
    ).toEqual({ title: "인수 시험 자료와 해금 파일을 등록한 뒤에 확인을 요청할 수 있습니다.", refresh: true });
  });

  it("QA_NOT_ALL_CHECKED 는 400 이어도 화면이 본 체크와 서버가 달라 다시 읽는다", () => {
    expect(
      milestoneActionFailure(
        new ApiError(400, "QA_NOT_ALL_CHECKED", "확인 문장을 모두 통과해야 합니다."),
        "통과시키지 못했습니다.",
      ),
    ).toEqual({ title: "확인 문장을 모두 통과해야 합니다.", refresh: true });
  });

  it("400 필드 오류면 그 사유를 알리고 다시 읽지 않는다", () => {
    expect(
      milestoneActionFailure(
        new ApiError(400, "INVALID_INPUT", "입력값이 올바르지 않습니다.", [
          { field: "qaResults", reason: "확인 문장 결과가 비어 있습니다." },
        ]),
        "저장하지 못했습니다.",
      ),
    ).toEqual({ title: "확인 문장 결과가 비어 있습니다.", refresh: false });
  });

  it("ApiError 가 아니면 화면이 준 문구를 쓴다", () => {
    expect(milestoneActionFailure(new Error("boom"), "요청하지 못했습니다.")).toEqual({
      title: "요청하지 못했습니다.",
      refresh: false,
    });
  });
});

describe("isHttpUrl", () => {
  // 서버 HttpUrlValidatorTest 와 같은 예시.
  it.each([
    "http://localhost:3000",
    "https://demo.example.com/path?q=1",
    "HTTPS://Demo.Example.com",
    "https://user@demo.example.com:8443/a b",
    "https://[::1]:8080/",
  ])("호스트가 있는 http·https 주소는 받는다: %s", (value) => {
    expect(isHttpUrl(value)).toBe(true);
  });

  it.each([
    "ftp://demo.example.com",
    "javascript:alert(1)",
    "demo.example.com",
    "https://",
    "https://:8080",
    "https:demo.example.com",
    // 서버 식에서도 대괄호 안이 비어 호스트가 없다.
    "https://[]:8080",
  ])("다른 스킴이나 호스트가 없는 주소는 거부한다: %s", (value) => {
    expect(isHttpUrl(value)).toBe(false);
  });
});

describe("registrationFailure", () => {
  it("409 는 그 사이 계약이 바뀐 것이라 다시 읽는다", () => {
    const failure = registrationFailure(
      new ApiError(409, "HANDOVER_UNDER_REVIEW", "최종 인수 시험 중에는 인계 자료를 바꿀 수 없습니다."),
      "더하지 못했습니다.",
    );

    expect(failure).toEqual({
      title: "최종 인수 시험 중에는 인계 자료를 바꿀 수 없습니다.",
      refresh: true,
      retry: false,
    });
  });

  it("필드 오류면 그 사유를 알리고 다시 읽지 않는다", () => {
    const failure = registrationFailure(
      new ApiError(400, "INVALID_INPUT", "입력값이 올바르지 않습니다.", [
        { field: "url", reason: "http 또는 https 주소여야 합니다." },
      ]),
      "더하지 못했습니다.",
    );

    expect(failure).toEqual({ title: "http 또는 https 주소여야 합니다.", refresh: false, retry: false });
  });

  it("응답이 오지 않았거나 서버 오류면 다시 보내 볼 수 있다", () => {
    expect(registrationFailure(new ApiError(0, "NETWORK_ERROR", "연결 실패"), "x").retry).toBe(true);
    expect(registrationFailure(new ApiError(502, "UNKNOWN_ERROR", "실패"), "x").retry).toBe(true);
    expect(registrationFailure(new Error("boom"), "올리지 못했습니다.")).toEqual({
      title: "올리지 못했습니다.",
      refresh: false,
      retry: true,
    });
  });
});
