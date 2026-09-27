import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";
import type { Contract, Deliverable } from "@/types/contract";
import { ContractDetail } from "./ContractDetail";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
}));
vi.mock("@/components/feedback", async (importOriginal) => {
  const original = await importOriginal<typeof import("@/components/feedback")>();
  return { ...original, useToast: () => vi.fn() };
});

function deliverable(deliverableId: number, title: string, isContractual: boolean): Deliverable {
  return { deliverableId, title, fileId: null, deliveredAt: null, isContractual, materials: [] };
}

function contract(deliverables: Deliverable[]): Contract {
  return {
    contractId: 130,
    roomId: 93,
    contractType: "DEVELOPMENT",
    status: "SIGNED",
    consulting: null,
    development: {
      status: "IN_PROGRESS",
      totalAmount: 400_000,
      issueIds: [],
      startDate: "2026-08-26",
      dueDate: "2026-09-16",
      freeRepairDays: 14,
      milestones: [],
      deliverables,
    },
    clientSignature: { userId: 3, isSigned: true, signedAt: "2026-08-25T01:00:00Z", signatureImageFileId: 1 },
    developerSignature: { userId: 9, isSigned: true, signedAt: "2026-08-25T01:05:00Z", signatureImageFileId: 2 },
    messages: [],
    contractText: null,
    updatedAt: "2026-08-25T01:05:00Z",
  };
}

function render(deliverables: Deliverable[]): string {
  return renderToStaticMarkup(
    <ContractDetail
      roomId={93}
      roomTitle="관리자 화면 보안"
      counterpartName="의뢰인"
      contract={contract(deliverables)}
      received
      myUserId={9}
    />,
  );
}

describe("ContractDetail 인계물", () => {
  it("계약서의 항목과 체결 뒤 더한 항목을 나눠 적는다", () => {
    const html = render([deliverable(401, "완성된 웹사이트", true), deliverable(402, "운영 설명서 보충", false)]);

    const added = html.indexOf("계약 뒤 추가한 인계물");
    expect(added).toBeGreaterThan(-1);
    expect(html.indexOf("완성된 웹사이트")).toBeLessThan(added);
    expect(html.indexOf("운영 설명서 보충")).toBeGreaterThan(added);
    expect(html).toContain("계약 범위와 금액은 바뀌지 않습니다.");
  });

  it("더한 항목이 없으면 계약서 항목만 적는다", () => {
    const html = render([deliverable(401, "완성된 웹사이트", true)]);

    expect(html).toContain("완성된 웹사이트");
    expect(html).not.toContain("계약 뒤 추가한 인계물");
  });
});
