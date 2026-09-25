import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";
import type { Contract } from "@/types/contract";
import { ConsultingContractForm } from "./ConsultingContractForm";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
}));
vi.mock("@/components/feedback", () => ({
  useToast: () => vi.fn(),
}));

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

describe("ConsultingContractForm", () => {
  it("AI version 1의 주제와 상담 내용만 작성 초기값으로 채운다", () => {
    const html = renderToStaticMarkup(
      <ConsultingContractForm roomId={92} counterpartName="의뢰인" draft={draft} />,
    );

    expect(html).toContain("컨설팅 계약서 작성");
    expect(html).toContain('value="결제 실패 원인 확인"');
    expect(html).toContain(">재현 조건과 로그를 함께 확인합니다.</textarea>");
    expect(html).toContain('type="date"');
    expect(html).toContain('placeholder="50,000" value=""');
  });

  it("AI version 1은 기존 계약 수정 화면으로 취급하지 않는다", () => {
    const draftHtml = renderToStaticMarkup(
      <ConsultingContractForm roomId={92} counterpartName="의뢰인" draft={draft} />,
    );
    const editHtml = renderToStaticMarkup(
      <ConsultingContractForm roomId={92} counterpartName="의뢰인" contract={draft} />,
    );

    expect(draftHtml).toContain("계약서 보내기");
    expect(draftHtml).not.toContain("계약서 다시 보내기");
    expect(editHtml).toContain("계약서 다시 보내기");
  });
});
