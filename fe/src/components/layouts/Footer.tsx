import * as React from "react";
import Link from "next/link";
import { cn } from "@/lib/cn";
import { COMPANY } from "@/lib/company";
import { Container, type ContainerWidth } from "./Container";
import { ROUTES } from "@/lib/routes";

/**
 * Footer — 모든 공개 화면 하단의 공통 푸터.
 * 회색 면(surface) 위에 브랜드 · 링크 3열 · 고지 · 사업자 정보 순으로 놓입니다.
 * 링크와 사업자 정보는 값이 확정되면 props 로 주입합니다.
 */
export interface FooterLink {
  label: string;
  href: string;
}
export interface FooterColumn {
  title: string;
  links: FooterLink[];
}

export interface BusinessInfo {
  /** 사업자등록번호 */
  registrationNumber: string;
  /** 대표자명 */
  representative: string;
  /** 소재지 */
  address: string;
  /** 문의 메일 */
  email: string;
}

export interface FooterProps extends React.HTMLAttributes<HTMLElement> {
  columns?: FooterColumn[];
  notices?: React.ReactNode[];
  business?: BusinessInfo;
  tagline?: React.ReactNode;
  width?: ContainerWidth;
}

export const DEFAULT_FOOTER_COLUMNS: FooterColumn[] = [
  {
    title: "서비스",
    links: [
      { label: "이용 방법", href: ROUTES.howTo },
      { label: "개발자 찾기", href: ROUTES.developers },
    ],
  },
  {
    title: "개발자",
    links: [
      { label: "프로필 등록", href: ROUTES.mypageProfile },
      { label: "정산 방식", href: ROUTES.settlementsGuide },
    ],
  },
  {
    title: "정책",
    links: [
      { label: "이용약관", href: ROUTES.policiesTerms },
      { label: "개인정보 처리", href: ROUTES.policiesPrivacy },
      { label: "분쟁 처리", href: ROUTES.policiesRefundDispute },
    ],
  },
];

export const DEFAULT_FOOTER_NOTICES = [
  "Komit은 통신판매중개자이며 거래의 당사자가 아닙니다. 상담·컨설팅·개발의 이행 책임은 각 개발자에게 있습니다.",
  "결제는 갤럭시아머니트리를 통해 처리됩니다. 예치한 대금은 계약이 전부 끝난 뒤 한 번에 지급됩니다.",
];

/** 값은 `lib/company.ts` 한 곳에서 관리합니다. 약관 화면도 같은 값을 씁니다. */
export const DEFAULT_BUSINESS_INFO: BusinessInfo = {
  registrationNumber: COMPANY.registrationNumber,
  representative: COMPANY.representative,
  address: COMPANY.address,
  email: COMPANY.email,
};

export function Footer({
  columns = DEFAULT_FOOTER_COLUMNS,
  notices = DEFAULT_FOOTER_NOTICES,
  business = DEFAULT_BUSINESS_INFO,
  tagline = (
    <>
      출시는 스스로, 못 하면 맡기고,
      <br />
      맡길 땐 안전하게.
    </>
  ),
  width = "content",
  className,
  ...props
}: FooterProps) {
  return (
    <footer className={cn("mt-auto border-t border-line-subtle bg-surface", className)} {...props}>
      <Container width={width} className="py-12 sm:py-14">
        <div className="flex flex-col gap-10 sm:flex-row sm:justify-between sm:gap-8">
          <div className="sm:max-w-[260px]">
            <p className="text-base font-bold text-fg">Komit</p>
            <p className="mt-3 text-[13px] leading-6 text-fg-muted">{tagline}</p>
          </div>

          <nav aria-label="푸터" className="grid grid-cols-2 gap-x-10 gap-y-8 sm:flex sm:gap-x-14">
            {columns.map((column) => (
              <div key={column.title}>
                <p className="text-[13px] font-semibold text-fg-secondary">{column.title}</p>
                <ul className="mt-2.5 flex flex-col gap-2">
                  {column.links.map((link) => (
                    <li key={link.href}>
                      <Link
                        href={link.href}
                        className="text-[13px] whitespace-nowrap text-fg-muted transition-colors hover:text-fg"
                      >
                        {link.label}
                      </Link>
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </nav>
        </div>

        {notices.length > 0 && (
          <div className="mt-10 flex flex-col gap-1.5 border-t border-line pt-5">
            {notices.map((notice, i) => (
              <p key={i} className="text-xs leading-5 text-fg-subtle">
                {notice}
              </p>
            ))}
          </div>
        )}

        <div className="mt-4 flex flex-col gap-2 text-xs text-fg-subtle sm:flex-row sm:items-center sm:justify-between">
          <p className="font-mono tabular-nums">
            사업자등록 {business.registrationNumber} · 대표 {business.representative} · {business.address}
          </p>
          <a href={`mailto:${business.email}`} className="font-mono transition-colors hover:text-fg">
            {business.email}
          </a>
        </div>
      </Container>
    </footer>
  );
}
