import type { Metadata } from "next";
import { SettlementShell, SpendingView } from "@/components/domains/settlement";
import { getSettlementOverview } from "@/lib/api/services";
import { requireLogin } from "@/lib/auth/guard";
import { MONTH_PARAM, currentMonthKey, readMonthParam } from "@/lib/settlement";

export const metadata: Metadata = {
  title: "소비 내역 | Komit",
  description: "그 달에 쓴 돈과 결제 목록을 확인합니다.",
};

/**
 * 정산 › 소비 내역. 로그인한 사용자의 데이터라 요청마다 그립니다.
 *
 * 달은 주소의 `?month=` 로 넘깁니다. 상태로 들고 있지 않아 새로고침과 공유에 그대로
 * 남습니다. 형식이 아니면 서버에 넘기지 않고 이번 달을 봅니다.
 *
 * 이번 달은 서버에서 계산해 props 로 내려보냅니다. 왼쪽 목록이 클라이언트 컴포넌트라
 * 거기서 다시 계산하면 자정 언저리에 서버와 다른 달이 나옵니다.
 */
export default async function SpendingPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const requested = readMonthParam((await searchParams)[MONTH_PARAM]);
  const month = requested ?? currentMonthKey();
  const { spending, income, deposits } = await requireLogin(() =>
    getSettlementOverview(requested),
  );

  return (
    <SettlementShell
      month={month}
      spending={spending}
      income={income}
      deposits={deposits}
      title="소비 내역"
    >
      <SpendingView month={month} spending={spending} />
    </SettlementShell>
  );
}
