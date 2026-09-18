import type { Metadata } from "next";
import Link from "next/link";
import {
  AdminReportStatusBadge,
  AdminShell,
  ReportStatusFilter,
  ResolveReportButton,
} from "@/components/domains/admin";
import { Breadcrumb, Card, KeyValueRow, Mono, PageHeader } from "@/components/common";
import { Callout } from "@/components/feedback";
import { formatAdminMonthDayTime, formatAdminSlashDate, formatAdminSlashDateTime } from "@/lib/admin/format";
import { requireAdminLogin } from "@/lib/admin/guard";
import {
  ADMIN_CONTRACT_TYPE_LABEL,
  ADMIN_PARTY_ROLE_LABEL,
  ADMIN_REPORT_STATUS_LABEL,
  reportPartyLabel,
} from "@/lib/admin/labels";
import { parseReportQuery, reportsHref } from "@/lib/admin/reportQuery";
import { getAdminReport, getAdminReports, getAdminSummary } from "@/lib/api/services";
import { ApiError } from "@/types/api";
import type { AdminReportDetail } from "@/types/admin";

export const metadata: Metadata = {
  title: "신고 처리 | 운영 콘솔",
  robots: { index: false },
};

const PAGE_SIZE = 20;

/**
 * 고른 신고의 상세.
 *
 * 목록에 있던 줄이 이미 지워졌을 수 있습니다(404). 그때 화면 전체를 오류로 덮으면
 * 운영자는 목록까지 잃습니다 — 상세만 비우고 목록은 그대로 둡니다.
 */
async function loadSelectedReport(reportId?: number): Promise<AdminReportDetail | null> {
  if (reportId === undefined) return null;
  try {
    return await getAdminReport(reportId);
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) return null;
    throw error;
  }
}

/**
 * 신고 처리 (`9_신고처리`).
 *
 * 의뢰인이 낸 문제 신고와 개발자가 낸 이의 제기가 한 목록에 섞이고 `reporter.role` 로
 * 갈립니다. 필터·페이지·고른 신고는 모두 주소에 남습니다(`lib/admin/reportQuery.ts`).
 */
export default async function AdminReportsPage({
  searchParams,
}: Readonly<{
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}>) {
  const query = parseReportQuery(await searchParams);

  const { summary, reports, selectedId, selected } = await requireAdminLogin(async () => {
    const [summary, reports] = await Promise.all([
      getAdminSummary(),
      getAdminReports({ status: query.status, page: query.page, size: PAGE_SIZE }),
    ]);
    // 고르지 않았으면 목록의 첫 줄을 펼쳐 둡니다. 상세가 빈 채로 시작하지 않게.
    const selectedId = query.reportId ?? reports.content[0]?.reportId;
    return { summary, reports, selectedId, selected: await loadSelectedReport(selectedId) };
  });

  return (
    <AdminShell asOf={summary.asOf} admin={summary.admin}>
      <PageHeader
        breadcrumb={<Breadcrumb items={["운영 콘솔", "신고 처리"]} />}
        title="신고 처리"
      />

      <section className="mt-8" aria-labelledby="report-list-title">
        <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
          <h2 id="report-list-title" className="text-lg font-bold text-fg">
            들어온 신고
          </h2>
          <Mono className="text-[13px] text-fg-muted">{reports.totalElements}건</Mono>
        </div>

        <ReportStatusFilter
          status={query.status}
          openCount={reports.openCount}
          resolvedCount={reports.resolvedCount}
        />

        {/*
          목록에는 **어느 건인지 가리는 값만** 둡니다. 신고 대상과 내용은 아래 상세에
          전문이 있어, 목록에 겹쳐 두면 표만 넓어지고 가로 스크롤이 생깁니다.
          그래도 좁은 화면에서는 표가 제 영역 안에서만 스크롤됩니다(컨벤션 12장).
        */}
        <div className="mt-3 overflow-x-auto rounded-xl border border-line">
          <table className="w-full min-w-[560px] border-collapse text-left text-[13px]">
            <thead className="bg-surface text-xs text-fg-subtle">
              <tr>
                <th scope="col" className="px-4 py-3 font-medium">접수</th>
                <th scope="col" className="px-4 py-3 font-medium">계약</th>
                <th scope="col" className="px-4 py-3 font-medium">신고자</th>
                <th scope="col" className="px-4 py-3 font-medium">해결일</th>
                <th scope="col" className="px-4 py-3 text-right font-medium">상태</th>
              </tr>
            </thead>
            <tbody>
              {reports.content.map((report) => {
                const current = report.reportId === selectedId;
                return (
                  <tr
                    key={report.reportId}
                    /*
                      줄 전체가 눌립니다. 운영자는 날짜가 아니라 눈에 띄는 칸(계약 이름)을
                      누르므로 링크를 한 칸에만 두면 아무 일도 일어나지 않는 것처럼 보입니다.
                      `<tr>` 자체는 링크가 될 수 없어, 링크 하나를 줄 위로 펼쳐 덮습니다 —
                      이렇게 해야 읽어 주는 도구에도 줄마다 링크가 하나만 남습니다.
                    */
                    className={`relative border-t border-line-subtle ${
                      current ? "bg-primary-soft" : "bg-bg hover:bg-surface"
                    }`}
                  >
                    <td className="px-4 py-3.5 align-top">
                      <Link
                        href={reportsHref({ ...query, reportId: report.reportId })}
                        aria-current={current ? "true" : undefined}
                        aria-label={`${formatAdminSlashDateTime(report.createdAt)} ${report.projectName} 신고 열기`}
                        className="font-mono font-medium text-fg tabular-nums underline-offset-4 after:absolute after:inset-0 hover:text-primary hover:underline"
                      >
                        {formatAdminSlashDateTime(report.createdAt)}
                      </Link>
                    </td>
                    <td className="px-4 py-3.5 align-top text-fg-muted">
                      {report.projectName} · {ADMIN_CONTRACT_TYPE_LABEL[report.contractType]}
                    </td>
                    <td className="px-4 py-3.5 align-top text-fg-muted">
                      {reportPartyLabel(report.reporter)}
                    </td>
                    <td className="px-4 py-3.5 align-top">
                      {report.resolvedAt ? (
                        <Mono className="text-fg-muted">
                          {formatAdminSlashDate(report.resolvedAt)}
                        </Mono>
                      ) : (
                        <span className="text-fg-disabled">—</span>
                      )}
                    </td>
                    <td className="px-4 py-3.5 text-right align-top">
                      <AdminReportStatusBadge status={report.status} />
                    </td>
                  </tr>
                );
              })}
              {reports.content.length === 0 && (
                <tr className="border-t border-line-subtle">
                  <td colSpan={5} className="px-4 py-12 text-center text-sm text-fg-subtle">
                    이 조건에 맞는 신고가 없습니다. 위에서 다른 상태를 골라 보세요.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        {reports.totalPages > 1 && (
          <nav className="mt-4 flex items-center justify-end gap-2" aria-label="신고 목록 페이지">
            <PageLink
              href={reportsHref({ ...query, page: query.page - 1 })}
              disabled={query.page <= 1}
            >
              이전
            </PageLink>
            <Mono className="px-2 text-xs text-fg-subtle">
              {query.page} / {reports.totalPages}
            </Mono>
            <PageLink
              href={reportsHref({ ...query, page: query.page + 1 })}
              disabled={query.page >= reports.totalPages}
            >
              다음
            </PageLink>
          </nav>
        )}
      </section>

      <section className="mt-10" aria-labelledby="report-detail-title">
        <h2 id="report-detail-title" className="mb-3 text-lg font-bold text-fg">
          신고 상세
        </h2>

        {selected ? (
          <Card padding="none" variant="outline" className="overflow-hidden">
            <div className="flex flex-wrap items-start justify-between gap-4 border-b border-line px-6 py-5">
              <div>
                <p className="text-base font-bold text-fg">{selected.projectName}</p>
                <p className="mt-1 text-xs text-fg-subtle">
                  {ADMIN_CONTRACT_TYPE_LABEL[selected.contractType]} 계약 ·{" "}
                  {/* 원본 상태값도 함께 적습니다. 운영자가 API·로그와 바로 맞춰 보는 화면입니다. */}
                  <Mono>{selected.status}</Mono>
                </p>
              </div>
              <AdminReportStatusBadge status={selected.status} />
            </div>

            <div className="grid lg:grid-cols-[minmax(0,1.2fr)_minmax(320px,0.8fr)]">
              <div className="border-b border-line-subtle p-6 lg:border-r lg:border-b-0">
                <h3 className="text-sm font-bold text-fg">신고 내용</h3>
                <p className="mt-4 text-sm leading-6 whitespace-pre-wrap text-fg-muted">
                  {selected.reason}
                </p>
                <p className="mt-5 text-xs text-fg-subtle">
                  {reportPartyLabel(selected.reporter)} ·{" "}
                  <Mono>{formatAdminMonthDayTime(selected.createdAt)}</Mono> 접수
                </p>
              </div>

              <div className="p-6">
                <h3 className="text-sm font-bold text-fg">신고 정보</h3>
                <div className="mt-2">
                  <KeyValueRow
                    label="신고 대상"
                    value={`${selected.target.name} ${ADMIN_PARTY_ROLE_LABEL[selected.target.role]}`}
                    labelWidth={76}
                  />
                  <KeyValueRow label="첨부파일" labelWidth={76}>
                    {selected.evidenceFileName ? (
                      <span className="text-fg-muted">
                        {selected.evidenceFileName}
                        {/*
                          운영자용 내려받기 API가 아직 없습니다
                          (`.omx/plans/area9-console-implementation.md` 3장).
                          주소를 짐작해 링크를 만들지 않고, 파일이 붙어 있다는 사실만 적습니다.
                        */}
                        <span className="ml-2 text-xs text-fg-disabled">내려받기 준비 중</span>
                      </span>
                    ) : (
                      <span className="text-fg-disabled">없음</span>
                    )}
                  </KeyValueRow>
                  <KeyValueRow label="접수 상태" labelWidth={76}>
                    <span className="flex items-center gap-2">
                      <Mono>{selected.status}</Mono>
                      <span className="text-fg-muted">
                        · {ADMIN_REPORT_STATUS_LABEL[selected.status]}
                      </span>
                    </span>
                  </KeyValueRow>
                  <KeyValueRow label="해결일" labelWidth={76}>
                    {selected.resolvedAt ? (
                      <Mono>{formatAdminMonthDayTime(selected.resolvedAt)}</Mono>
                    ) : (
                      <span className="text-fg-subtle">아직 종결되지 않았습니다</span>
                    )}
                  </KeyValueRow>
                </div>
              </div>
            </div>

            <div className="flex flex-wrap items-center gap-4 border-t border-line-subtle px-6 py-4">
              <h3 className="text-sm font-bold text-fg">계약 자료</h3>
              <span className="text-sm text-fg-muted">
                {ADMIN_CONTRACT_TYPE_LABEL[selected.contractType]} 계약서 원문
              </span>
              {/*
                계약서 원문은 사용자 화면의 계약 API를 그대로 씁니다. 그 API가 운영자
                토큰을 받아 주는지 아직 확인되지 않아
                (`.omx/plans/area9-console-implementation.md` 3장) 주소를 만들지
                않습니다. 확인되면 여기만 링크로 바꿉니다.
              */}
              <span className="text-sm text-fg-disabled">연결 준비 중</span>
            </div>

            <div className="flex flex-wrap items-center justify-between gap-4 border-t border-line bg-surface px-6 py-5">
              <div>
                <p className="text-sm font-semibold text-fg">이 신고를 종결합니다</p>
                <p className="mt-1 text-xs text-fg-subtle">
                  종결하면 오늘 날짜가 해결일로 기록되고 목록에서 종결됨으로 바뀝니다.
                </p>
              </div>
              <ResolveReportButton
                reportId={selected.reportId}
                status={selected.status}
                projectName={selected.projectName}
              />
            </div>
          </Card>
        ) : (
          <Callout tone="neutral">
            {query.reportId === undefined
              ? "상세를 볼 신고를 목록에서 골라 주세요."
              : "고른 신고를 찾지 못했습니다. 목록에서 다시 골라 주세요."}
          </Callout>
        )}
      </section>
    </AdminShell>
  );
}

/**
 * 페이지 이동 링크. 끝에 닿으면 누를 수 없게 두되 **자리는 지킵니다** — 사라지면
 * 남은 버튼이 옆으로 밀려 잘못 눌리기 쉽습니다.
 */
function PageLink({
  href,
  disabled,
  children,
}: Readonly<{ href: string; disabled: boolean; children: React.ReactNode }>) {
  return (
    <Link
      href={href}
      aria-disabled={disabled}
      tabIndex={disabled ? -1 : undefined}
      className={`rounded-md border border-line px-3 py-1.5 text-xs font-medium ${
        disabled ? "pointer-events-none text-fg-disabled" : "text-fg hover:bg-surface"
      }`}
    >
      {children}
    </Link>
  );
}
