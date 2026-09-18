import type { Metadata } from "next";
import { AdminShell, ReconciliationStatusBadge } from "@/components/domains/admin";
import { Breadcrumb, Card, Money, Mono, PageHeader } from "@/components/common";
import { Callout } from "@/components/feedback";
import { formatAdminFullDate, formatAdminMonthDay } from "@/lib/admin/format";
import { requireAdminLogin } from "@/lib/admin/guard";
import { ESCROW_STATUS_LABEL, RECONCILIATION_RESULT_LABEL } from "@/lib/admin/labels";
import { getAdminReconciliation, getAdminSummary } from "@/lib/api/services";
import { ApiError } from "@/types/api";
import type { ReconciliationData } from "@/types/admin";

export const metadata: Metadata = {
  title: "정산 대사 | 운영 콘솔",
  robots: { index: false },
};

/** 화면이 보여 주는 이력 기간. 와이어프레임의 「지난 7일」입니다. */
const HISTORY_DAYS = 7;

/**
 * 읽지 못한 잔고는 **0원이 아닙니다.**
 *
 * 대사에서 0원과 「모름」을 같게 그리면 장부와 잔고가 딱 맞는 날처럼 보입니다.
 * 금액 자리에는 읽지 못했다는 사실을 적습니다.
 */
function Balance({ value }: Readonly<{ value: number | null }>) {
  if (value === null) return <span className="text-sm text-fg-disabled">읽지 못함</span>;
  return <Money value={value} />;
}

function Difference({ value }: Readonly<{ value: number | null }>) {
  if (value === null) return <span className="text-sm text-fg-disabled">계산할 수 없음</span>;
  return (
    <Money value={value} signed={value !== 0} tone={value === 0 ? "default" : "danger"} />
  );
}

/**
 * 정산 대사 (`9_정산대사`).
 *
 * **조회 전용입니다.** 대사는 Escrow 서버가 매일 09:00 에 스스로 돌리고 이 화면은 그
 * 결과를 읽을 뿐입니다. 다시 돌리거나 고치는 버튼을 만들지 마세요 — 자금은 Komit 이
 * 아니라 Escrow 쪽 소유이고, 콘솔이 손대는 순간 두 장부가 갈라집니다. (`R-PAY-05`)
 */
export default async function AdminReconciliationPage() {
  const { summary, data, unavailable } = await requireAdminLogin(async () => {
    const summary = await getAdminSummary();
    try {
      return { summary, data: await getAdminReconciliation(HISTORY_DAYS), unavailable: null };
    } catch (error) {
      // 에스크로가 답하지 않는 것은 이 화면만의 일입니다. 콘솔 전체를 오류로 덮지 않습니다.
      // status 0 은 응답 자체가 오지 않은 것(끊김·시간 초과)이라 같이 받습니다.
      if (error instanceof ApiError && (error.status === 0 || error.status >= 500)) {
        return { summary, data: null as ReconciliationData | null, unavailable: error.message };
      }
      throw error;
    }
  });

  return (
    <AdminShell asOf={summary.asOf} admin={summary.admin}>
      <PageHeader
        breadcrumb={<Breadcrumb items={["운영 콘솔", "정산 대사"]} />}
        title="정산 대사"
        meta="매일 09:00 자동 실행"
      />

      {data === null ? (
        <Callout tone="danger" title="대사 결과를 읽지 못했습니다" className="mt-8">
          {unavailable} 잔고가 0원이라는 뜻이 아닙니다. 잠시 후 다시 열어 주세요.
        </Callout>
      ) : (
        <ReconciliationView data={data} />
      )}
    </AdminShell>
  );
}

function ReconciliationView({ data }: Readonly<{ data: ReconciliationData }>) {
  const listedAmount = data.escrows.reduce((sum, row) => sum + row.heldAmount, 0);
  const restCount = Math.max(0, data.escrowTotalCount - data.escrows.length);
  const restAmount = data.escrowTotalAmount - listedAmount;

  return (
    <>
      <Card padding="lg" variant="outline" className="mt-8">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div>
            <p className="text-sm font-semibold text-fg">
              {formatAdminFullDate(data.latest.asOf)} 대사
            </p>
            <p className="mt-1 text-xs text-fg-subtle">최근 실행 결과</p>
          </div>
          <ReconciliationStatusBadge result={data.latest.result} />
        </div>

        <div className="mt-6 grid gap-5 border-t border-line-subtle pt-5 sm:grid-cols-3">
          <div>
            <p className="text-xs text-fg-subtle">Komit 장부 합계</p>
            <Money value={data.latest.ledgerTotal} className="mt-1 block text-lg" />
          </div>
          <div>
            <p className="text-xs text-fg-subtle">결제대행사 실제 잔고</p>
            <div className="mt-1 text-lg">
              <Balance value={data.latest.bankBalance} />
            </div>
          </div>
          <div>
            <p className="text-xs text-fg-subtle">차이</p>
            <div className="mt-1 text-lg">
              <Difference value={data.latest.differenceAmount} />
            </div>
          </div>
        </div>
      </Card>

      <section className="mt-10" aria-labelledby="escrow-ledger-title">
        <div className="mb-3 flex flex-wrap items-end justify-between gap-3">
          <h2 id="escrow-ledger-title" className="text-lg font-bold text-fg">
            장부 상세 — 예치중인 금액
          </h2>
          <p className="text-[13px] text-fg-subtle">
            {/*
              계좌는 하나이고 구분은 장부로 합니다. 아래 줄들은 그 하나의 계좌 안에서
              장부상 누구 몫인지를 나눈 것이라 합이 장부 합계와 같아야 합니다.
            */}
            계좌는 하나이고 아래는 장부상 몫입니다
          </p>
        </div>

        <div className="overflow-x-auto rounded-xl border border-line">
          <table className="w-full min-w-[680px] border-collapse text-left text-[13px]">
            <thead className="bg-surface text-xs text-fg-subtle">
              <tr>
                <th scope="col" className="px-4 py-3 font-medium">거래</th>
                <th scope="col" className="px-4 py-3 font-medium">건</th>
                <th scope="col" className="px-4 py-3 font-medium">상태</th>
                <th scope="col" className="px-4 py-3 text-right font-medium">예치중인 금액</th>
              </tr>
            </thead>
            <tbody>
              {data.escrows.map((row) => (
                <tr key={row.escrowId} className="border-t border-line-subtle">
                  <td className="px-4 py-3.5">
                    <p className="font-medium text-fg">{row.partyLabel}</p>
                    <p className="mt-0.5 text-xs text-fg-subtle">{row.projectName}</p>
                  </td>
                  <td className="px-4 py-3.5 text-fg-muted">{row.pendingCount}건 대기</td>
                  <td className="px-4 py-3.5 text-fg-muted">{ESCROW_STATUS_LABEL[row.status]}</td>
                  <td className="px-4 py-3.5 text-right">
                    <Money value={row.heldAmount} />
                  </td>
                </tr>
              ))}
              {restCount > 0 && (
                <tr className="border-t border-line-subtle">
                  <td colSpan={3} className="px-4 py-3.5 text-fg-muted">
                    그 외 {restCount}건
                  </td>
                  <td className="px-4 py-3.5 text-right">
                    <Money value={restAmount} tone="muted" />
                  </td>
                </tr>
              )}
            </tbody>
            <tfoot className="border-t border-line bg-bg">
              <tr>
                <th scope="row" colSpan={3} className="px-4 py-3.5 text-left text-sm font-bold text-fg">
                  장부 합계
                </th>
                <td className="px-4 py-3.5 text-right">
                  <Money value={data.escrowTotalAmount} />
                </td>
              </tr>
            </tfoot>
          </table>
        </div>
      </section>

      <section className="mt-10" aria-labelledby="reconciliation-history-title">
        <h2 id="reconciliation-history-title" className="mb-3 text-lg font-bold text-fg">
          지난 {HISTORY_DAYS}일
        </h2>
        <div className="overflow-x-auto rounded-xl border border-line">
          <table className="w-full min-w-[620px] border-collapse text-left text-[13px]">
            <thead className="bg-surface text-xs text-fg-subtle">
              <tr>
                <th scope="col" className="px-4 py-3 font-medium">날짜</th>
                <th scope="col" className="px-4 py-3 text-right font-medium">장부</th>
                <th scope="col" className="px-4 py-3 text-right font-medium">실제 잔고</th>
                <th scope="col" className="px-4 py-3 text-right font-medium">차이</th>
                <th scope="col" className="px-4 py-3 text-right font-medium">결과</th>
              </tr>
            </thead>
            <tbody>
              {data.history.map((row) => (
                <tr key={row.asOf} className="border-t border-line-subtle">
                  <td className="px-4 py-3.5">
                    <Mono>{formatAdminMonthDay(row.asOf)}</Mono>
                  </td>
                  <td className="px-4 py-3.5 text-right">
                    <Money value={row.ledgerTotal} unit="" />
                  </td>
                  <td className="px-4 py-3.5 text-right">
                    {row.bankBalance === null ? (
                      <span className="text-fg-disabled">읽지 못함</span>
                    ) : (
                      <Money value={row.bankBalance} unit="" />
                    )}
                  </td>
                  <td className="px-4 py-3.5 text-right">
                    {row.differenceAmount === null ? (
                      <span className="text-fg-disabled">—</span>
                    ) : (
                      <Money
                        value={row.differenceAmount}
                        unit=""
                        signed={row.differenceAmount !== 0}
                        tone={row.differenceAmount === 0 ? "muted" : "danger"}
                      />
                    )}
                  </td>
                  <td className="px-4 py-3.5 text-right font-medium text-fg-muted">
                    {RECONCILIATION_RESULT_LABEL[row.result]}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </>
  );
}
