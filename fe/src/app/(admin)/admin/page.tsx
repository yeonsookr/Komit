import type { Metadata } from "next";
import Link from "next/link";
import { AdminShell, ReconciliationStatusBadge } from "@/components/domains/admin";
import { Card, Mono, PageHeader } from "@/components/common";
import { Callout } from "@/components/feedback";
import { formatAdminFullDate } from "@/lib/admin/format";
import { requireAdminLogin } from "@/lib/admin/guard";
import { getAdminSummary } from "@/lib/api/services";
import { ROUTES } from "@/lib/routes";

export const metadata: Metadata = {
  title: "운영 콘솔 | Komit",
  // 내부 직원 전용이라 검색에 싣지 않습니다.
  robots: { index: false },
};

/**
 * 운영 콘솔 홈 (`9_홈`).
 *
 * 업무 세 가지의 숫자를 한 번에 보여 주고 각 화면으로 보냅니다. 숫자는 `asOf` 시점에
 * 센 것이라 새로고침하면 바뀝니다 — 상단바의 「기준」이 그 시각입니다.
 */
export default async function AdminHomePage() {
  const summary = await requireAdminLogin(getAdminSummary);

  const work = [
    {
      title: "신고 처리",
      description: `접수됨 ${summary.reports.openCount}건 · 종결됨 ${summary.reports.resolvedCount}건`,
      href: ROUTES.adminReports,
    },
    {
      title: "정산 대사",
      description: `결제대행사 입출금과 원장 대조 · 예외 ${summary.reconciliation.exceptionCount}건`,
      href: ROUTES.adminReconciliation,
      // 홈에서 대사가 어긋난 날을 바로 알아채야 해서 최근 결과를 배지로 답니다.
      badge: <ReconciliationStatusBadge result={summary.reconciliation.lastResult} />,
    },
    {
      title: "계약 양식",
      description: `개발·컨설팅·검수 확인서 양식 · ${summary.contractTemplates.totalCount}판`,
      href: ROUTES.adminContractTemplates,
    },
  ];

  return (
    <AdminShell asOf={summary.asOf} admin={summary.admin}>
      <PageHeader
        title="운영 콘솔"
        description="Komit 내부 직원 전용입니다."
        meta={
          <Mono>
            {formatAdminFullDate(summary.asOf)} · {summary.admin.loginId}
          </Mono>
        }
      />

      <section className="mt-8" aria-labelledby="admin-work-title">
        <div className="flex items-center gap-3">
          <h2 id="admin-work-title" className="text-lg font-bold text-fg">
            업무
          </h2>
          <span className="h-px flex-1 bg-line-subtle" />
          <Mono className="text-[13px] text-fg-muted">{work.length}개</Mono>
        </div>

        <Card variant="outline" padding="none" className="mt-3 px-4">
          {work.map((item, index) => (
            <article
              key={item.title}
              className={`flex items-center gap-3 py-4 ${index > 0 ? "border-t border-line-subtle" : ""}`}
            >
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2">
                  <h3 className="text-[15px] font-semibold text-fg">{item.title}</h3>
                  {item.badge}
                </div>
                <p className="mt-0.5 text-xs text-fg-subtle">{item.description}</p>
              </div>
              <Link
                href={item.href}
                className="shrink-0 rounded-sm text-[13px] font-semibold text-primary hover:underline"
              >
                열기
              </Link>
            </article>
          ))}
        </Card>
      </section>

      <Callout tone="neutral" size="sm" className="mt-6">
        Komit은 확인 문장이 통과됐는지만 확인합니다. 작업의 좋고 나쁨을 판정하지 않습니다.
      </Callout>
    </AdminShell>
  );
}
