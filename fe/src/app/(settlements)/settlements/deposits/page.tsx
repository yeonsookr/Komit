import type { Metadata } from "next";
import { DepositsView, SettlementShell } from "@/components/domains/settlement";
import { getSettlementOverview } from "@/lib/api/services";
import { requireLogin } from "@/lib/auth/guard";
import { MONTH_PARAM, currentMonthKey, readMonthParam } from "@/lib/settlement";

export const metadata: Metadata = {
  title: "예치금 | Komit",
  description: "맡긴 돈이 계약별로 얼마나 남아 있는지 확인합니다.",
};

/**
 * 정산 › 예치금.
 *
 * 본문은 달을 타지 않습니다 — 예치금은 한 달의 집계가 아니라 지금의 잔액이고,
 * 목 명세에도 달 파라미터가 없습니다. 주소의 달은 왼쪽 목록을 위해서만 읽습니다.
 */
export default async function DepositsPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const requested = readMonthParam((await searchParams)[MONTH_PARAM]);
  const { spending, income, deposits } = await requireLogin(() =>
    getSettlementOverview(requested),
  );

  return (
    <SettlementShell
      month={requested ?? currentMonthKey()}
      spending={spending}
      income={income}
      deposits={deposits}
      title="예치금"
    >
      <DepositsView deposits={deposits} />
    </SettlementShell>
  );
}
