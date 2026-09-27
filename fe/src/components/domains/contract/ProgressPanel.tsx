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
  nearestAutoPassAt,
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

/** 작업 한 줄. 통과한 것은 대금이 확정됐다는 표시를 함께 답니다. */
function milestoneItem(milestone: Milestone): ProgressItem {
  const confirmed = isConfirmed(milestone);

  return {
    label: `${milestone.title} · ${confirmed ? "통과" : "확인 대기"}`,
    right: isHandover(milestone) ? undefined : confirmed ? (
      <span className="text-fg">
        <Money value={milestone.price} /> 통과 확정
      </span>
    ) : (
      <Money value={milestone.price} className="text-fg-subtle" />
    ),
    status: confirmed ? "done" : "active",
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
 * 통과한 뒤 한 번에 나갑니다 (R-PAY-01 · R-PAY-03).
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

  const items: ProgressItem[] = [
    {
      label: deposited ? "예치 완료" : "예치 대기",
      right: deposit && <Money value={deposit.amount} />,
      status: deposited ? "done" : "active",
    },
    ...milestones.map(milestoneItem),
    {
      label: "계약 완료 후 지급",
      right: <Money value={development.totalAmount} />,
      status: "pending",
    },
  ];

  return (
    <section className="mt-8">
      <header>
        <h2 className="text-xl font-bold text-fg">
          {billable[0]?.title ?? "진행"}
          {billable.length > 1 && ` 외 ${billable.length - 1}건`}
        </h2>
        <p className="mt-1.5 text-[13px] leading-6 text-fg-subtle">
          {received
            ? "끝나는 작업마다 확인 요청을 보내면 됩니다."
            : `${counterpartName} 개발자가 작업 중입니다. 끝나는 작업마다 확인 요청이 옵니다.`}{" "}
          대금은 {milestones.length}건을 모두 확인한 뒤 한 번에 나갑니다.
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
          통과한 작업의 대금도 계약이 끝날 때까지 예치금에 있습니다.
        </span>
      </StickyBar>
    </section>
  );
}
