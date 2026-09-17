import type { Metadata } from "next";
import { IncomeView, SettlementShell } from "@/components/domains/settlement";
import { getSettlementOverview } from "@/lib/api/services";
import { requireLogin } from "@/lib/auth/guard";
import { MONTH_PARAM, currentMonthKey, readMonthParam } from "@/lib/settlement";

export const metadata: Metadata = {
  title: "소득 내역 | Komit",
  description: "계약이 끝나면 받을 돈과 정산 계좌를 확인합니다.",
};

/**
 * 정산 › 소득 내역.
 *
 * 본문은 달을 타지 않습니다 — 목 명세의 소득 조회에 달 파라미터가 없습니다. 주소의
 * 달은 왼쪽 목록의 소비 금액과 화면을 옮길 때의 달을 이어 주기 위해서만 읽습니다.
 */
export default async function IncomePage({
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
      title="소득 내역"
    >
      <IncomeView income={income} />
    </SettlementShell>
  );
}
