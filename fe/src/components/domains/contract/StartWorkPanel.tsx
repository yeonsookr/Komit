"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/buttons";
import {
  Card,
  CardHeader,
  KeyValueRow,
  Money,
  StickyBar,
} from "@/components/common";
import { Callout, useToast } from "@/components/feedback";
import { clientApi } from "@/lib/api/client";
import { INTERNAL } from "@/lib/api/endpoints";
import { formatDateTimeLabel, formatDayWithWeekday } from "@/lib/format";
import { ApiError } from "@/types/api";
import type { DevelopmentAgreement, EscrowDeposit } from "@/types/contract";

export interface StartWorkPanelProps {
  contractId: number;
  development: DevelopmentAgreement;
  /** 예치를 건 적이 없으면 null 입니다. */
  deposit: EscrowDeposit | null;
  /** 상대(의뢰인) 이름. 버튼과 안내에 씁니다. */
  counterpartName: string;
}

/**
 * StartWorkPanel — 개발자의 착수 전 화면.
 *
 * 예치와 착수는 다른 단계입니다. 예치는 의뢰인이 돈을 맡기는 것이고, 착수는
 * 개발자가 그것을 확인하고 일을 시작한다고 알리는 것입니다. **예치가 확인되기
 * 전에는 착수할 수 없습니다** — 버튼을 잠그고 서버도 409 로 막습니다.
 */
export function StartWorkPanel({
  contractId,
  development,
  deposit,
  counterpartName,
}: Readonly<StartWorkPanelProps>) {
  const router = useRouter();
  const showToast = useToast();
  const [starting, setStarting] = React.useState(false);

  const deposited = deposit?.depositedAt !== null && deposit !== null;

  const start = async () => {
    if (!deposited || starting) return;
    setStarting(true);

    try {
      await clientApi.post(INTERNAL.CONTRACT_START(contractId));
      showToast({
        tone: "success",
        title: "착수했습니다.",
        description: `${counterpartName}님 화면에 알림이 갑니다.`,
      });
      router.refresh();
    } catch (error) {
      showToast({
        tone: "danger",
        title:
          error instanceof ApiError ? error.message : "착수하지 못했습니다.",
      });
      setStarting(false);
    }
  };

  return (
    <section className="mt-8">
      <header>
        <h2 className="text-xl font-bold text-fg">요청서 받음</h2>
        <p className="mt-1 text-[13px] text-fg-subtle">
          {deposited && deposit
            ? `${counterpartName}님이 계약서에 서명하고 ${deposit.amount.toLocaleString("ko-KR")}원을 예치했습니다.`
            : `${counterpartName}님의 예치를 기다리는 중입니다.`}
        </p>
      </header>

      <div className="mt-6">
        <CardHeader
          title="일정"
          action={
            <span className="text-[13px] text-fg-subtle">계약서에 적힌 것</span>
          }
        />
        <Card className="mt-3" padding="none">
          <div className="divide-y divide-line-subtle">
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
              label="끝나는 날"
              value={
                development.dueDate === null
                  ? "정하지 않음"
                  : formatDayWithWeekday(development.dueDate)
              }
              className="px-4 py-3.5 sm:px-5"
            />
          </div>
        </Card>
        <p className="mt-2 text-[13px] text-fg-subtle">확인 기간은 따로</p>
      </div>

      <div className="mt-8">
        <CardHeader
          title="예치 확인"
          action={
            <span className="text-[13px] text-fg-subtle">
              {deposited ? "Komit이 잡고 있습니다" : "아직 들어오지 않았습니다"}
            </span>
          }
        />
        <Card className="mt-3" padding="none">
          <KeyValueRow
            label="예치금"
            value={
              deposit === null ? (
                <span className="text-fg-muted">예치 전</span>
              ) : (
                <span className="flex items-baseline gap-2">
                  <Money value={deposit.amount} />
                  {deposit.depositedAt && (
                    <span className="font-mono text-[13px] text-fg-subtle tabular-nums">
                      {formatDateTimeLabel(deposit.depositedAt)} 확인
                    </span>
                  )}
                </span>
              )
            }
            className="px-4 py-3.5 sm:px-5"
          />
        </Card>
        {!deposited && (
          <p className="mt-2 text-[13px] text-fg-subtle">
            예치가 확인되면 착수할 수 있습니다. 확인은 보통 몇 분 안에 끝납니다.
          </p>
        )}
      </div>

      <Callout tone="info" className="mt-8">
        {counterpartName}님이 확인 문장을 눌러 통과시키면 그 작업이 완료로
        확정됩니다. 대금은 계약이 전부 끝난 뒤 한 번에 나갑니다.
      </Callout>

      <StickyBar className="mt-10 flex-col items-start gap-2 sm:flex-row sm:items-center">
        <span className="min-w-0 flex-1 text-sm text-fg-secondary">
          {deposited ? "예치 완료 · 착수 전" : "예치 대기"}
          {deposit && (
            <>
              {" · "}
              <Money value={deposit.amount} className="text-fg" />
            </>
          )}
        </span>
        <span className="flex shrink-0 flex-col items-end gap-1">
          <Button
            variant="dark"
            size="lg"
            loading={starting}
            disabled={!deposited}
            onClick={start}
          >
            {deposited
              ? `착수하고 ${counterpartName}님에게 알리기`
              : "예치 확인 후 착수할 수 있습니다"}
          </Button>
          <span className="text-[13px] text-fg-subtle">
            {deposited
              ? `착수하면 ${counterpartName}님 화면에 알림이 갑니다.`
              : "예치가 확인되면 이 버튼이 열립니다."}
          </span>
        </span>
      </StickyBar>
    </section>
  );
}
