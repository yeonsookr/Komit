import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import type { Contract } from "@/types/contract";
import { ConsultingContractDetail } from "./ConsultingContractDetail";
import { ContractDetail } from "./ContractDetail";

const draft: Contract = {
  contractId: 120,
  roomId: 92,
  contractType: "CONSULTING",
  status: "NEGOTIATING",
  consulting: {
    status: "SIGNED",
    totalAmount: 0,
    topic: "결제 실패 원인 확인",
    detail: "재현 조건과 로그를 함께 확인합니다.",
    sessions: [],
  },
  development: null,
  clientSignature: null,
  developerSignature: null,
  messages: [],
  contractText: null,
  updatedAt: "2026-09-26T00:00:00Z",
};

describe("컨설팅 계약 초안 화면 흐름", () => {
  it("세션과 예약을 채우기 전에는 서명 대신 작성 계속하기를 보여준다", () => {
    const html = renderToStaticMarkup(
      <ContractDetail
        roomId={92}
        roomTitle="결제 오류 상담"
        counterpartName="의뢰인"
        contract={draft}
        received
        myUserId={9}
      />,
    );

    expect(html).toContain("계약서 초안을 작성하고 있습니다");
    expect(html).toContain("계약서 작성 계속하기");
    expect(html).not.toContain("서명하고 계약하기");
  });

  it("개발 계약 AI 초안도 서명 대신 작성 계속하기를 보여주고 의뢰인에게는 버튼을 보이지 않는다", () => {
    const development: Contract = {
      ...draft,
      contractType: "DEVELOPMENT",
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
      },
    };
    const render = (received: boolean) =>
      renderToStaticMarkup(
        <ContractDetail
          roomId={93}
          roomTitle="관리자 화면 보안"
          counterpartName="의뢰인"
          contract={development}
          received={received}
          myUserId={9}
        />,
      );

    expect(render(true)).toContain("계약서 작성 계속하기");
    expect(render(false)).toContain("계약서 초안을 작성하고 있습니다");
    expect(render(false)).not.toContain("서명");
  });

  it("작성 완료된 계약 상세에서 상담 내용을 확인할 수 있다", () => {
    const signed = {
      ...draft,
      status: "SIGNED" as const,
      consulting: {
        ...draft.consulting!,
        totalAmount: 50_000,
        sessions: [
          {
            sessionId: 55,
            contractId: 120,
            price: 50_000,
            durationMinutes: 30,
            reservation: {
              reservationId: 80,
              scheduledAt: "2026-10-01T14:00:00+09:00",
              status: "CONFIRMED" as const,
            },
            startedAt: null,
            endedAt: null,
            chatRefId: "consulting-session-55",
          },
        ],
      },
      clientSignature: {
        userId: 3,
        isSigned: true,
        signedAt: "2026-09-26T01:00:00Z",
        signatureImageFileId: 1,
      },
      developerSignature: {
        userId: 9,
        isSigned: true,
        signedAt: "2026-09-26T01:05:00Z",
        signatureImageFileId: 2,
      },
    } satisfies Contract;

    const html = renderToStaticMarkup(
      <ConsultingContractDetail
        roomId={92}
        roomTitle="결제 오류 상담"
        counterpartName="의뢰인"
        contract={signed}
        received
        myUserId={9}
      />,
    );

    expect(html).toContain("상담 내용");
    expect(html).toContain("재현 조건과 로그를 함께 확인합니다.");
  });
});
