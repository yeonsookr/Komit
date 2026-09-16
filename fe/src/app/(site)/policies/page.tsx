import type { Metadata } from "next";
import Link from "next/link";
import { KeyValueRow, Mono, PageHeader, SectionCard } from "@/components/common";
import { Container } from "@/components/layouts";
import { COMPANY } from "@/lib/company";
import { ROUTES } from "@/lib/routes";

export const metadata: Metadata = {
  title: "약관과 정책 | Komit",
  description: "Komit 이용약관, 개인정보 처리방침, 전자금융거래 이용약관, 환불과 분쟁 처리 기준입니다.",
};

/**
 * 가입할 때 동의한 문서 네 건.
 *
 * 서버가 주는 값이 아니라 화면이 가진 정적 문서입니다(목 명세 영역 8 — 이 화면에는
 * API 가 없습니다). 개정일도 여기서 관리합니다.
 */
const DOCUMENTS = [
  { label: "이용약관", revisedAt: "2026.03.01", href: ROUTES.policiesTerms },
  { label: "개인정보 처리방침", revisedAt: "2026.06.15", href: ROUTES.policiesPrivacy },
  { label: "전자금융거래 이용약관", revisedAt: "2026.03.01", href: ROUTES.policiesElectronicFinance },
  { label: "환불과 분쟁 처리 기준", revisedAt: "2026.03.01", href: ROUTES.policiesRefundDispute },
];

/**
 * 문서를 읽기 전에 먼저 알려야 하는 것. 각 줄이 규칙서의 한 항목입니다.
 * 문구를 고칠 때는 그 규칙을 함께 봅니다.
 */
const NOTICES = [
  // R-LEG-01
  "Komit은 통신판매중개자이며 거래 당사자가 아닙니다. 의뢰인과 개발자 사이의 계약과 이행 책임은 두 분에게 있습니다.",
  // R-PAY — 예치금은 결제대행사가 보관합니다.
  "예치금은 결제대행사 에스크로 계정에 보관됩니다. Komit은 이 돈을 갖고 있지 않습니다.",
  // R-QA — 통과 여부만 확인하고 작업의 품질은 판정하지 않습니다.
  "Komit은 확인 문장이 통과됐는지만 확인하고 대금을 옮깁니다. 작업의 좋고 나쁨을 판정하지 않습니다.",
];

/**
 * 약관과 정책.
 *
 * 문서 목록이고 본문은 문서마다 따로입니다. 서버를 부르지 않는 정적 화면이라
 * 정적으로 생성합니다.
 */
export default function PoliciesPage() {
  return (
    <Container className="py-8 sm:py-10">
      <PageHeader title="약관과 정책" description="가입할 때 동의하신 문서입니다." />

      <SectionCard
        title="문서"
        note={
          <>
            <span className="font-mono tabular-nums">{DOCUMENTS.length}</span>건
          </>
        }
      >
        {DOCUMENTS.map((doc) => (
          <KeyValueRow
            key={doc.href}
            label={<span className="font-semibold text-fg">{doc.label}</span>}
            labelWidth={180}
            value={
              <Mono className="block text-right text-[13px] text-fg-subtle">
                {doc.revisedAt} 개정
              </Mono>
            }
            action={
              <Link
                href={doc.href}
                className="rounded-sm text-[13px] font-semibold text-primary hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
              >
                읽기
              </Link>
            }
          />
        ))}
      </SectionCard>

      <SectionCard title="반드시 알려드리는 것">
        {NOTICES.map((notice) => (
          <p
            key={notice}
            className="py-3.5 text-sm leading-6 text-fg-secondary [&:not(:last-child)]:border-b [&:not(:last-child)]:border-line-subtle"
          >
            {notice}
          </p>
        ))}
      </SectionCard>

      <SectionCard title="사업자 정보">
        <KeyValueRow label="상호" value={COMPANY.name} />
        <KeyValueRow label="대표" value={COMPANY.representative} />
        <KeyValueRow label="사업자등록번호" value={COMPANY.registrationNumber} mono labelWidth={130} />
        <KeyValueRow label="통신판매업 신고" value={COMPANY.mailOrderNumber} labelWidth={130} />
        <KeyValueRow
          label="문의"
          value={
            <a
              href={`mailto:${COMPANY.email}`}
              className="rounded-sm text-primary hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
            >
              {COMPANY.email}
            </a>
          }
        />
      </SectionCard>
    </Container>
  );
}
