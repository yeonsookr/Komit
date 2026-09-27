import Link from "next/link";
import {
  Card,
  CardHeader,
  Money,
  ProgressList,
  StickyBar,
  type ProgressItem,
} from "@/components/common";
import { buttonClass } from "@/components/buttons/buttonStyles";
import { Callout } from "@/components/feedback";
import {
  AUTO_PASS_DAYS,
  billableMilestones,
  isConfirmed,
  isHandover,
  milestoneStageLabel,
  nearestAutoPassAt,
  PAYOUT_PENDING_LABEL,
  payoutStage,
  type PayoutStage,
  remainingAmount,
  remainingCount,
} from "@/lib/contract";
import { formatDateLabel } from "@/lib/format";
import { ROUTES } from "@/lib/routes";
import type {
  DevelopmentAgreement,
  EscrowDeposit,
  Milestone,
} from "@/types/contract";
import { PayoutRefresh } from "./PayoutRefresh";

/** 마지막 지급 줄. 지급 단계는 `payoutStage` 한 곳에서 가릅니다. */
const PAYOUT_ROW: Record<PayoutStage, { label: string; status: ProgressItem["status"] }> = {
  BEFORE_ACCEPTANCE: { label: "계약 완료 후 지급", status: "pending" },
  PENDING: { label: PAYOUT_PENDING_LABEL, status: "active" },
  PAID: { label: "지급 완료", status: "done" },
};

/**
 * 작업 한 줄. 이름 뒤에 진행 단계를 붙이고, 통과한 것은 대금이 확정됐다는 표시를 함께 답니다.
 * 예정인 작업은 흐리게 두고, 통과 전의 다른 단계는 진행 표시로 둡니다.
 */
function milestoneItem(milestone: Milestone): ProgressItem {
  const confirmed = isConfirmed(milestone);
  const stage = milestoneStageLabel(milestone);
  let status: ProgressItem["status"] = "active";
  if (confirmed) status = "done";
  else if (stage === "예정") status = "pending";

  return {
    label: `${milestone.title} · ${stage}`,
    right: isHandover(milestone) ? undefined : confirmed ? (
      <span className="text-fg">
        <Money value={milestone.price} /> 통과 확정
      </span>
    ) : (
      <Money value={milestone.price} className="text-fg-subtle" />
    ),
    status,
  };
}

export interface ProgressPanelProps {
  roomId: number;
  development: DevelopmentAgreement;
  deposit: EscrowDeposit | null;
  /** 상대 이름. 안내 문구에 씁니다. */
  counterpartName: string;
  /** 내가 개발자로 들어갔는지. 신고 안내 문구가 갈립니다. */
  received: boolean;
}

/**
 * ProgressPanel — 진행 탭.
 *
 * 예치부터 지급까지를 한 줄씩 늘어놓습니다. 대금은 작업마다 나가지 않고 전부
 * 통과한 뒤 한 번에 나갑니다 (R-PAY-01 · R-PAY-03). 인계 작업을 통과하면 지급을 요청하고,
 * 지급이 확정돼야 계약이 끝납니다. 그 사이 마지막 줄은 지급 처리 중으로 두고, 지급 완료가
 * 새로고침 없이 보이도록 화면을 다시 읽습니다. 지급 완료는 계약 완료로만 압니다.
 */
export function ProgressPanel({
  roomId,
  development,
  deposit,
  counterpartName,
  received,
}: Readonly<ProgressPanelProps>) {
  const milestones = development.milestones;
  // 제목과 남은 작업 수는 금액이 붙는 작업만 셉니다. 인계는 0 원입니다.
  const billable = billableMilestones(milestones);
  // 예치 시각은 아직 오지 않아 금액으로 봅니다. 착수 API 도 같은 기준입니다.
  const deposited = deposit !== null && deposit.amount > 0;
  const autoPassAt = nearestAutoPassAt(milestones);
  const stage = payoutStage(development);
  const payoutRow = PAYOUT_ROW[stage];

  const items: ProgressItem[] = [
    {
      label: deposited ? "예치 완료" : "예치 대기",
      right: deposit && <Money value={deposit.amount} />,
      status: deposited ? "done" : "active",
    },
    ...milestones.map(milestoneItem),
    {
      label: payoutRow.label,
      right: <Money value={development.totalAmount} />,
      status: payoutRow.status,
    },
  ];

  return (
    <section className="mt-8">
      {stage === "PENDING" && <PayoutRefresh active />}
      <header>
        <h2 className="text-xl font-bold text-fg">
          {billable[0]?.title ?? "진행"}
          {billable.length > 1 && ` 외 ${billable.length - 1}건`}
        </h2>
        <p className="mt-1.5 text-[13px] leading-6 text-fg-subtle">
          {received
            ? "끝나는 작업마다 확인 요청을 보내면 됩니다."
            : `${counterpartName} 개발자가 작업 중입니다. 끝나는 작업마다 확인 요청이 옵니다.`}{" "}
          대금은 {milestones.length}건을 모두 확인하면 한 번에 지급을 요청하고, 지급이 확정되면
          계약이 끝납니다.
        </p>
      </header>

      <div className="mt-6">
        <CardHeader
          title="진행 상황"
          action={
            deposit && (
              <span className="text-[13px] text-fg-subtle">
                예치금{" "}
                <Money value={deposit.amount} className="text-fg-secondary" />
              </span>
            )
          }
        />
        <Card className="mt-3">
          <ProgressList items={items} />
        </Card>
      </div>

      <Callout
        tone="neutral"
        className="mt-8"
        action={
          <Link
            href={ROUTES.roomReport(roomId)}
            className={buttonClass({ variant: "danger", size: "sm" })}
          >
            문제 신고
          </Link>
        }
      >
        답이 {AUTO_PASS_DAYS}일 넘게 없거나 말이 안 통할 때 Komit을 부르십시오.
        고칠 수 있는 것은 대화가 빠릅니다.
      </Callout>

      <StickyBar className="mt-10 flex-col items-stretch gap-1">
        <span className="flex items-center justify-between gap-4">
          <span className="text-sm text-fg-secondary">
            남은 작업{" "}
            <span className="font-mono tabular-nums">
              {remainingCount(milestones)}
            </span>
            건{" · "}
            <Money value={remainingAmount(milestones)} className="text-fg" />
          </span>
          {autoPassAt && (
            <span className="shrink-0 font-mono text-[13px] text-fg-subtle tabular-nums">
              {formatDateLabel(autoPassAt)} 자동 통과
            </span>
          )}
        </span>
        <span className="text-[13px] text-fg-subtle">
          {stage === "PAID"
            ? "계약 대금 지급이 끝나 계약이 완료됐습니다."
            : "통과한 작업의 대금도 계약이 끝날 때까지 예치금에 있습니다."}
        </span>
      </StickyBar>
    </section>
  );
}
