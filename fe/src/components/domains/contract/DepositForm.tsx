"use client";

import * as React from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Button } from "@/components/buttons";
import { buttonClass } from "@/components/buttons/buttonStyles";
import {
  Card,
  CardHeader,
  ChoiceCard,
  Money,
  NumberedSteps,
  StickyBar,
} from "@/components/common";
import { Callout, EmptyState, useToast } from "@/components/feedback";
import { clientApi } from "@/lib/api/client";
import { INTERNAL } from "@/lib/api/endpoints";
import { AUTO_PASS_DAYS, isHandover } from "@/lib/contract";
import { formatDateTimeLabel } from "@/lib/format";
import { ROUTES } from "@/lib/routes";
import { ApiError } from "@/types/api";
import type { DevelopmentAgreement, PaymentMethod } from "@/types/contract";

export interface DepositFormProps {
  contractId: number;
  roomId: number;
  development: DevelopmentAgreement;
  /** 등록해 둔 결제 수단. 비어 있으면 먼저 등록해야 합니다. */
  paymentMethods: PaymentMethod[];
  /** 요청서로 돌아가는 경로. 진단이 없으면 null. */
  requestHref: string | null;
  counterpartName: string;
  /** 계약서를 마지막으로 보낸 시각. 계약 내용 머리에 적습니다. */
  sentAt: string;
}

/**
 * DepositForm — 계약 금액 예치.
 *
 * 전액을 한 번에 맡기고, 대금은 작업이 모두 통과된 뒤 한 번에 나갑니다
 * (R-PAY-01 · R-PAY-03). 예치금은 결제대행사 에스크로 계정에 있고 Komit 이
 * 갖고 있지 않습니다 (R-PAY-05).
 */
export function DepositForm({
  contractId,
  roomId,
  development,
  paymentMethods,
  requestHref,
  counterpartName,
  sentAt,
}: Readonly<DepositFormProps>) {
  const router = useRouter();
  const showToast = useToast();

  const [picked, setPicked] = React.useState<number | null>(
    paymentMethods.find((method) => method.isDefault)?.paymentMethodId ??
      paymentMethods[0]?.paymentMethodId ??
      null,
  );
  const [paying, setPaying] = React.useState(false);

  /** 금액이 붙는 작업만 예치 내역에 올립니다. 인계 작업은 0 원입니다. */
  const billable = development.milestones.filter(
    (milestone) => !isHandover(milestone),
  );

  const deposit = async () => {
    if (paying) return;
    setPaying(true);

    try {
      await clientApi.post(INTERNAL.CONTRACT_DEPOSIT(contractId), {
        // 비우면 결제창에서 계좌를 고릅니다.
        ...(picked === null ? {} : { paymentMethodId: picked }),
        // 재시도해도 결제가 두 번 나가지 않게 하는 키입니다.
        idempotencyKey: crypto.randomUUID(),
      });
      showToast({
        tone: "success",
        title: "예치했습니다.",
        description: `${counterpartName} 개발자에게 착수 알림이 갑니다.`,
      });
      router.push(ROUTES.roomProgress(roomId));
    } catch (error) {
      showToast({
        tone: "danger",
        title:
          error instanceof ApiError ? error.message : "예치하지 못했습니다.",
      });
      setPaying(false);
    }
  };

  return (
    <section className="mt-8">
      <header>
        <h2 className="text-xl font-bold text-fg">Komit 안심예치</h2>
      </header>

      <div className="mt-6">
        <CardHeader
          title="계약 내용"
          action={
            <span className="text-[13px] text-fg-subtle">
              {counterpartName} 개발자가 {formatDateTimeLabel(sentAt)}에 보냄
            </span>
          }
        />
        <Card className="mt-3" padding="none">
          <ul className="divide-y divide-line-subtle">
            {development.milestones.map((milestone) => (
              <li
                key={milestone.milestoneId}
                className="flex flex-wrap items-center gap-x-3 gap-y-1 px-4 py-3.5 sm:px-5"
              >
                <span className="min-w-0 flex-1 text-sm text-fg">
                  {milestone.title}
                </span>
                <span className="font-mono text-[13px] text-fg-subtle tabular-nums">
                  확인 문장 {milestone.qaCriteria.length}줄
                </span>
                {!isHandover(milestone) && (
                  <Money
                    value={milestone.price}
                    className="text-sm text-fg-secondary"
                  />
                )}
              </li>
            ))}
          </ul>
        </Card>
        <ul className="mt-2 flex flex-col gap-1">
          <li className="text-[13px] leading-6 text-fg-subtle before:mr-1.5 before:content-['·']">
            계약이 전부 끝나면 대금이 한 번에 지급됩니다.
          </li>
          <li className="text-[13px] leading-6 text-fg-subtle before:mr-1.5 before:content-['·']">
            확인 요청을 받고 {AUTO_PASS_DAYS}일 안에 답하지 않으면 자동으로
            통과됩니다.
          </li>
        </ul>
      </div>

      <div className="mt-8">
        <CardHeader title="예치 금액" />
        <Card className="mt-3" padding="none">
          <ul className="divide-y divide-line-subtle">
            {billable.map((milestone) => (
              <li
                key={milestone.milestoneId}
                className="flex items-center gap-3 px-4 py-3.5 sm:px-5"
              >
                <span className="min-w-0 flex-1 text-sm text-fg">
                  {milestone.title}
                </span>
                <Money
                  value={milestone.price}
                  className="text-sm text-fg-secondary"
                />
              </li>
            ))}
            <li className="flex items-center gap-3 px-4 py-3.5 sm:px-5">
              <span className="min-w-0 flex-1 text-sm font-semibold text-fg">
                합계
              </span>
              <Money
                value={development.totalAmount}
                className="text-base font-bold text-fg"
              />
            </li>
          </ul>
        </Card>
      </div>

      <div className="mt-8">
        <CardHeader title="결제 수단" />
        {paymentMethods.length === 0 ? (
          <EmptyState
            className="mt-3"
            title="등록한 결제 수단이 없습니다"
            description="예치는 등록해 둔 실시간 계좌이체 수단에서 빠집니다. 먼저 등록해 주세요."
            action={
              <Link href={ROUTES.accountRegister} className={buttonClass()}>
                결제 수단 등록
              </Link>
            }
          />
        ) : (
          <div className="mt-3 flex flex-col gap-2">
            {paymentMethods.map((method) => (
              <ChoiceCard
                key={method.paymentMethodId}
                type="radio"
                name="paymentMethod"
                label={method.maskedNumber ?? "등록한 수단"}
                meta="등록해 둔 실시간 계좌이체 수단"
                checked={picked === method.paymentMethodId}
                onChange={() => setPicked(method.paymentMethodId)}
              />
            ))}
            {/* 등록해 두지 않은 계좌로 내는 길. 결제창이 은행을 고르게 합니다. */}
            <ChoiceCard
              type="radio"
              name="paymentMethod"
              label="다른 계좌로 이체"
              meta="결제창에서 은행을 고르고 바로 이체합니다"
              checked={picked === null}
              onChange={() => setPicked(null)}
            />
          </div>
        )}
      </div>

      <div className="mt-8">
        <CardHeader title="예치금 처리 방식" />
        <Card className="mt-3">
          <NumberedSteps
            items={[
              {
                title: `지금 ${development.totalAmount.toLocaleString("ko-KR")}원 전액을 예치합니다`,
                description: `예치가 확인되면 ${counterpartName} 개발자에게 착수 알림이 가고, 요청서에서 고르신 날짜에 작업이 시작됩니다.`,
              },
              {
                title:
                  "작업은 하나씩 확인하고, 대금은 전부 끝난 뒤 한 번에 지급됩니다",
                description: `위 ${billable.length}건이 대상입니다. 통과 확인은 직접 하십니다.`,
              },
              {
                title: "통과하지 않은 대금은 예치금에 남습니다",
                description:
                  "중간에 끝나면 통과된 작업분만 지급되고 남은 금액은 등록한 계좌로 환불됩니다.",
              },
            ]}
          />
        </Card>
        <Callout tone="warning" className="mt-3">
          통과시키면 그 작업은 완료로 확정되고 되돌릴 수 없습니다.
        </Callout>
      </div>

      <p className="mt-8 text-xs leading-5 text-fg-subtle">
        예치금은 결제대행사 에스크로 계정에 보관됩니다. Komit은 이 돈을 갖고
        있지 않으며, 통신판매중개자로서 거래의 당사자가 아닙니다.
      </p>

      <StickyBar className="mt-10">
        <span className="text-sm text-fg-secondary">
          예치할 금액{" "}
          <Money value={development.totalAmount} className="text-fg" /> · 완료
          후 한 번에 지급
        </span>
        <span className="flex items-center gap-2">
          {requestHref && (
            <Link
              href={requestHref}
              className={buttonClass({ variant: "outline", size: "sm" })}
            >
              요청서 다시 보기
            </Link>
          )}
          <Button variant="dark" size="lg" loading={paying} onClick={deposit}>
            {development.totalAmount.toLocaleString("ko-KR")}원 예치하기
          </Button>
        </span>
      </StickyBar>
    </section>
  );
}
