import Link from "next/link";
import { Card, CardHeader, KeyValueRow, Money } from "@/components/common";
import { buttonClass } from "@/components/buttons/buttonStyles";
import { EmptyState } from "@/components/feedback";
import {
  billableMilestones,
  hasSigned,
  isSignedByBoth,
  totalWorkDays,
} from "@/lib/contract";
import { formatDateTimeLabel, formatDayWithWeekday } from "@/lib/format";
import { ROUTES } from "@/lib/routes";
import type { Contract } from "@/types/contract";
import { MilestoneList } from "./MilestoneList";
import { SignContractButton } from "./SignContractButton";

export interface ContractDetailProps {
  roomId: number;
  /** 방 이름. 서명판 부제에 들어갑니다. */
  roomTitle: string;
  /** 상대 이름. 누가 보낸 계약서인지 적는 데 씁니다. */
  counterpartName: string;
  /** 아직 쓰지 않았으면 null. */
  contract: Contract | null;
  /** 내가 이 방에 개발자로 들어갔는지. 아래 버튼이 갈립니다. */
  received: boolean;
  /** 내 식별자. 이미 서명했으면 서명 버튼을 접습니다. */
  myUserId: number;
}

/**
 * ContractDetail — 계약서 탭.
 *
 * 의뢰인과 개발자가 같은 본문을 보고 아래 버튼만 갈립니다.
 *   의뢰인 — 서명하고 예치하기
 *   개발자 — 수정하기 (서명 전에만. R-CON-02) · 서명하고 계약하기
 *
 * **양쪽이 서명해야 계약이 성립합니다** (R-CON-01). 어느 쪽이든 서명 버튼을
 * 누르면 같은 서명판(`5_서명판`)이 열립니다.
 *
 * 계약서가 아직 없으면 개발자에게만 작성 버튼을 보입니다. 계약서는 개발자가
 * 범위·금액·기한을 제안하는 것이기 때문입니다.
 */
export function ContractDetail({
  roomId,
  roomTitle,
  counterpartName,
  contract,
  received,
  myUserId,
}: Readonly<ContractDetailProps>) {
  if (contract === null) {
    return (
      <EmptyState
        title="아직 계약서가 없습니다"
        description={
          received
            ? "맡을 범위와 금액, 기한을 정해 계약서를 보내주세요."
            : "개발자가 범위와 금액, 기한을 정리해 보내면 여기에 붙습니다."
        }
        action={
          received ? (
            <Link
              href={ROUTES.roomContractNew(roomId)}
              className={buttonClass()}
            >
              계약서 작성하기
            </Link>
          ) : undefined
        }
      />
    );
  }

  const development = contract.development;
  const signed = isSignedByBoth(contract);

  // 컨설팅 계약서는 세션 일정이 본문이라 이 화면이 다룰 내용과 다릅니다.
  if (development === null) {
    return (
      <EmptyState
        title="컨설팅 계약서입니다"
        description="이 화면은 개발 계약서를 보여줍니다."
      />
    );
  }

  // 머리 줄은 금액이 붙는 작업만 셉니다. 인계 작업은 0 원이라 합계에 들어가지 않습니다.
  const billable = billableMilestones(development.milestones);
  const days = totalWorkDays(development.milestones);
  const mine = hasSigned(contract, myUserId);
  const amount = development.totalAmount.toLocaleString("ko-KR");

  return (
    <section className="mt-8">
      <header>
        <h2 className="text-xl font-bold text-fg">계약서</h2>
        <p className="mt-1 text-[13px] text-fg-subtle">
          {/* 계약서는 개발자가 씁니다. 내가 개발자면 내가 보낸 것입니다. */}
          {received ? "내가" : `${counterpartName} 개발자가`}{" "}
          {formatDateTimeLabel(contract.updatedAt)}에 보냄 ·{" "}
          {signed
            ? "양쪽이 서명해 계약이 성립했습니다"
            : "아직 서명하지 않았습니다"}
        </p>
      </header>

      <div className="mt-6">
        <CardHeader
          title="작업과 확인 문장"
          action={
            <span className="text-[13px] text-fg-subtle">
              <span className="font-mono tabular-nums">{billable.length}</span>
              건 · 합계{" "}
              <Money
                value={development.totalAmount}
                className="text-fg-secondary"
              />
              {days > 0 && (
                <>
                  {" · "}
                  <span className="font-mono tabular-nums">{days}</span>일
                </>
              )}
            </span>
          }
        />
        <div className="mt-3">
          <MilestoneList
            milestones={development.milestones}
            showPeriod={!signed}
          />
        </div>
        <p className="mt-3 text-[13px] text-fg-subtle">
          작업{" "}
          <span className="font-mono tabular-nums">
            {development.milestones.length}
          </span>
          건을 모두 통과하면 대금이 한 번에 나갑니다.
        </p>
      </div>

      <div className="mt-10">
        <CardHeader title="계약 조건" />
        <Card className="mt-3" padding="none">
          <div className="divide-y divide-line-subtle">
            {/* 서명하면 이 날짜로 묶입니다. 예치 전에 보이지 않으면 안 됩니다. */}
            <KeyValueRow
              label="착수일"
              value={
                development.startDate === null
                  ? "정하지 않음"
                  : formatDayWithWeekday(development.startDate)
              }
              className="px-4 py-3.5 sm:px-5"
            />
            <KeyValueRow
              label="납기일"
              value={
                development.dueDate === null
                  ? "정하지 않음"
                  : formatDayWithWeekday(development.dueDate)
              }
              className="px-4 py-3.5 sm:px-5"
            />
            <KeyValueRow
              label="하자보수"
              value={`완료 후 ${development.freeRepairDays}일`}
              className="px-4 py-3.5 sm:px-5"
            />
            <KeyValueRow
              label="인계물"
              value={
                development.deliverables.length === 0 ? (
                  "없음"
                ) : (
                  <span className="flex flex-col gap-1">
                    {development.deliverables.map((item) => (
                      <span key={item.deliverableId}>{item.title}</span>
                    ))}
                  </span>
                )
              }
              className="px-4 py-3.5 sm:px-5"
            />
            {/* 의뢰인은 아래 버튼에 금액이 적혀 있어 개발자에게만 적습니다. */}
            {received && (
              <KeyValueRow
                label="예치"
                value={`수락하면 ${amount}원 전액을 먼저 예치합니다`}
                className="px-4 py-3.5 sm:px-5"
              />
            )}
          </div>
        </Card>
      </div>

      {contract.detail && (
        <div className="mt-10">
          <CardHeader title="전달 메시지" />
          <Card variant="surface" className="mt-3">
            <p className="text-sm leading-6 text-fg-muted">{contract.detail}</p>
          </Card>
        </div>
      )}

      {!signed && (
        <div className="mt-10">
          <p className="text-[13px] text-fg-subtle">
            서명하면 계약서는 더 이상 수정할 수 없습니다.
          </p>
          <div className="mt-3 flex flex-wrap justify-end gap-2">
            {received && (
              <Link
                href={ROUTES.roomContractEdit(roomId)}
                className={buttonClass({ variant: "outline", size: "lg" })}
              >
                수정하기
              </Link>
            )}
            {mine ? (
              <p className="self-center text-[13px] text-fg-subtle">
                서명했습니다. {counterpartName}님이 서명하면 계약이 성립합니다.
              </p>
            ) : (
              <SignContractButton
                roomId={roomId}
                contractId={contract.contractId}
                subject={`개발 계약서 · ${roomTitle} · ${amount}원`}
                label={
                  received
                    ? "서명하고 계약하기"
                    : `서명하고 ${amount}원 예치하기`
                }
                confirmLabel={
                  received ? "서명하고 계약" : `서명하고 ${amount}원 예치`
                }
                depositNext={!received}
              />
            )}
          </div>
        </div>
      )}
    </section>
  );
}
