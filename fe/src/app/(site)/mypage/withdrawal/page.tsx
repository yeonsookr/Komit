import type { Metadata } from "next";
import { KeyValueRow, PageHeader, SectionCard } from "@/components/common";
import { Callout } from "@/components/feedback";
import { Container } from "@/components/layouts";
import {
  WithdrawButton,
  WithdrawalBlockers,
} from "@/components/domains/mypage";
import { checkWithdrawal } from "@/lib/api/services";
import type { WithdrawalBlocker } from "@/types/mypage";

export const metadata: Metadata = {
  title: "회원 탈퇴 | Komit",
  description: "탈퇴하면 되돌릴 수 없습니다. 정리해야 할 것을 먼저 확인합니다.",
  robots: { index: false },
};

// R-ACC-06: 탈퇴 회원 데이터 보관 기간
const DATA_RETENTION_DAYS = 30;

/**
 * 탈퇴하면 무엇이 어떻게 되는지. 규칙서를 사람 말로 옮긴 것이라 문구를 고칠 때는
 * 괄호 안의 규칙을 함께 봅니다.
 */
const CONSEQUENCES = [
  {
    label: "대화와 요청서",
    // 상대가 있는 기록이라 지우지 않습니다.
    body: "상대가 있는 기록이라 상대 화면에는 남고, 이름은 탈퇴한 사용자로 바뀝니다",
  },
  {
    // R-ACC-07
    label: "리뷰",
    body: "남기신 리뷰는 그대로 남고 작성자만 가려집니다",
  },
  {
    // R-ACC-06
    label: "개인정보",
    body: `${DATA_RETENTION_DAYS}일 보관 후 완전히 삭제합니다. 세금계산서 등 법으로 보관해야 하는 자료는 법정 기간 동안 남습니다.`,
  },
];

/** 같은 갈래의 금액을 더합니다. 금액이 없는 항목은 0 으로 봅니다. */
function sumAmount(blockers: WithdrawalBlocker[], type: WithdrawalBlocker["type"]): number {
  return blockers
    .filter((blocker) => blocker.type === type)
    .reduce((total, blocker) => total + (blocker.amount ?? 0), 0);
}

/**
 * 막고 있는 것을 한 문장으로.
 *
 * 조각이 모두 "건" 또는 "원" 으로 끝나 뒤에 붙는 조사가 언제나 「과」와 「이」입니다.
 * 조각 이름을 바꿀 때는 이 조사도 함께 봐야 합니다.
 */
function summarize(blockers: WithdrawalBlocker[]): string {
  const parts: string[] = [];

  const contracts = blockers.filter((blocker) => blocker.type === "CONTRACT").length;
  if (contracts > 0) parts.push(`진행 중인 의뢰 ${contracts}건`);

  const deposit = sumAmount(blockers, "DEPOSIT");
  if (deposit > 0) parts.push(`예치금 ${deposit.toLocaleString("ko-KR")}원`);

  const income = sumAmount(blockers, "INCOME");
  if (income > 0) parts.push(`못 받은 소득 ${income.toLocaleString("ko-KR")}원`);

  if (parts.length === 0) return "정리해야 할 것이 남아 있습니다. 정리한 뒤에 탈퇴할 수 있습니다.";

  const listed =
    parts.length > 1
      ? `${parts.slice(0, -1).join(", ")}과 ${parts[parts.length - 1]}`
      : parts[0];

  return `${listed}이 남아 있습니다. 정리한 뒤에 탈퇴할 수 있습니다.`;
}

/**
 * 회원 탈퇴.
 *
 * 마이페이지의 「탈퇴하기」가 옵니다. 되돌릴 수 없는 일이라 바로 처리하지 않고
 * 무엇이 막고 있는지와 무엇이 남는지를 먼저 보여주는 화면입니다. (R-ACC-05)
 *
 * 로그인한 사람의 화면이라 정적으로 만들지 않습니다.
 */
export default async function WithdrawalPage() {
  const { canWithdraw, blockers } = await checkWithdrawal();

  return (
    <Container className="py-8 sm:py-10">
      <PageHeader
        title="회원 탈퇴"
        description="탈퇴하면 되돌릴 수 없습니다. 아래 항목을 먼저 확인해 주세요."
      />

      {!canWithdraw && (
        <Callout tone="danger" className="mt-6" title="지금은 탈퇴할 수 없습니다">
          {summarize(blockers)}
        </Callout>
      )}

      <WithdrawalBlockers blockers={blockers} />

      <SectionCard title="탈퇴하면 이렇게 됩니다">
        {CONSEQUENCES.map((item) => (
          <KeyValueRow key={item.label} label={item.label} value={item.body} labelWidth={110} />
        ))}
      </SectionCard>

      <WithdrawButton canWithdraw={canWithdraw} />
    </Container>
  );
}
