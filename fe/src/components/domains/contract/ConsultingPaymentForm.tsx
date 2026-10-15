"use client";

import * as React from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Button } from "@/components/buttons";
import { buttonClass } from "@/components/buttons/buttonStyles";
import { Card, CardHeader, Money, NumberedSteps, StickyBar } from "@/components/common";
import { Callout, useToast } from "@/components/feedback";
import { clientApi } from "@/lib/api/client";
import { INTERNAL } from "@/lib/api/endpoints";
import { CONSULTING_DISPUTE_DAYS } from "@/lib/contract";
import { formatDateTimeLabel, formatSessionSchedule } from "@/lib/format";
import { ROUTES } from "@/lib/routes";
import { ApiError } from "@/types/api";
import type { ConsultingAgreement, PaymentMethod } from "@/types/contract";

export interface ConsultingPaymentFormProps {
  contractId: number;
  roomId: number;
  consulting: ConsultingAgreement;
  /** 회원가입 때 발급한 계좌 한 건. 결제는 이 계좌에서만 빠집니다. (R-PAY-09) */
  paymentMethods: PaymentMethod[];
  counterpartName: string;
  /** 계약서를 마지막으로 보낸 시각. 세션 내용 머리에 적습니다. */
  sentAt: string;
  /** 개발자가 이미 서명했는지. 결제하면 곧바로 계약이 성립하고 세션이 확정됩니다. */
  developerSigned: boolean;
}

/**
 * ConsultingPaymentForm — 컨설팅 대금 결제 (`4_의뢰인_컨설팅결제`).
 *
 * 개발 계약의 예치(`DepositForm`)와 같은 API 로 에스크로에 맡기지만 화면은 「결제」라고
 * 적습니다 (R-CON-09). 세션이 끝나고 이의 기간이 지나면 개발자에게 나갑니다 (R-CON-10).
 */
export function ConsultingPaymentForm({
  contractId,
  roomId,
  consulting,
  paymentMethods,
  counterpartName,
  sentAt,
  developerSigned,
}: Readonly<ConsultingPaymentFormProps>) {
  const router = useRouter();
  const showToast = useToast();
  const [paying, setPaying] = React.useState(false);

  const method = paymentMethods.find((found) => found.isDefault) ?? paymentMethods[0] ?? null;
  const session = consulting.sessions[0] ?? null;
  const amount = consulting.totalAmount;
  const amountLabel = amount.toLocaleString("ko-KR");

  const pay = async () => {
    if (paying || method === null) return;
    setPaying(true);

    try {
      await clientApi.post(INTERNAL.CONTRACT_DEPOSIT(contractId), {
        paymentMethodId: method.paymentMethodId,
        idempotencyKey: crypto.randomUUID(),
      });
      showToast({
        tone: "success",
        title: "결제했습니다.",
        description: developerSigned
          ? "세션 시간이 되면 입장할 수 있습니다."
          : "개발자가 서명하면 세션이 확정됩니다.",
      });
      router.push(ROUTES.roomContract(roomId));
      router.refresh();
    } catch (error) {
      // 서버는 「예치」라고 답하지만 이 화면은 「결제」입니다.
      const known = error instanceof ApiError && error.status < 500;
      showToast({
        tone: "danger",
        title: known ? error.message : "결제하지 못했습니다.",
        description: known ? undefined : "잠시 뒤 다시 시도해 주세요.",
      });
      setPaying(false);
    }
  };

  return (
    <section className="mt-8">
      <header>
        <h2 className="text-xl font-bold text-fg">Komit 안심결제</h2>
      </header>

      <div className="mt-6">
        <CardHeader
          title="세션 내용"
          action={
            <span className="text-[13px] text-fg-subtle">
              {counterpartName} 개발자가 {formatDateTimeLabel(sentAt)}에 보냄
            </span>
          }
        />
        <Card className="mt-3" padding="none">
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1 px-4 py-3.5 sm:px-5">
            <span className="min-w-0 flex-1 text-sm text-fg">{consulting.topic}</span>
            {session && (
              <span className="font-mono text-[13px] text-fg-subtle tabular-nums">
                {formatSessionSchedule(session.reservation.scheduledAt)} ·{" "}
                {session.durationMinutes}분
              </span>
            )}
            <Money value={amount} className="text-sm text-fg-secondary" />
          </div>
        </Card>
      </div>

      <div className="mt-8">
        <CardHeader title="결제 금액" />
        <Card className="mt-3" padding="none">
          <ul className="divide-y divide-line-subtle">
            {consulting.sessions.map((item, index) => (
              <li key={item.sessionId} className="flex items-center gap-3 px-4 py-3.5 sm:px-5">
                <span className="min-w-0 flex-1 text-sm text-fg">세션 {index + 1}회</span>
                <Money value={item.price} className="text-sm text-fg-secondary" />
              </li>
            ))}
            <li className="flex items-center gap-3 px-4 py-3.5 sm:px-5">
              <span className="min-w-0 flex-1 text-sm font-semibold text-fg">합계</span>
              <Money value={amount} className="text-base font-bold text-fg" />
            </li>
          </ul>
        </Card>
      </div>

      <div className="mt-8">
        <CardHeader title="결제 수단" />
        <Card className="mt-3">
          <p className="text-sm font-semibold text-fg">{method?.maskedNumber ?? "발급 계좌"}</p>
          <p className="mt-0.5 text-[13px] text-fg-subtle">
            회원가입 때 발급한 계좌에서 실시간으로 이체합니다
          </p>
        </Card>
      </div>

      <div className="mt-8">
        <CardHeader title="결제금 처리 방식" />
        <Card className="mt-3">
          <NumberedSteps
            items={[
              {
                title: `지금 ${amountLabel}원을 결제합니다`,
                description: "대금은 에스크로에 보관되고, 개발자에게 바로 가지 않습니다.",
              },
              {
                title: `세션이 끝나고 ${CONSULTING_DISPUTE_DAYS}일 안에 이의가 없으면 개발자에게 지급됩니다`,
                description: "이의를 내거나 세션이 열리지 않으면 지급을 멈춥니다.",
              },
              developerSigned
                ? {
                    title: "개발자가 서명해 계약이 성립합니다",
                    description: "결제하면 취소할 수 없습니다.",
                  }
                : {
                    title: "개발자가 서명하기 전에는 취소할 수 있습니다",
                    description: "취소하면 결제한 계좌로 전액 환불됩니다.",
                  },
            ]}
          />
        </Card>
        <Callout tone="warning" className="mt-3">
          세션이 끝나고 {CONSULTING_DISPUTE_DAYS}일이 지나면 대금이 개발자에게 지급되고 되돌릴 수
          없습니다.
        </Callout>
      </div>

      <p className="mt-8 text-xs leading-5 text-fg-subtle">
        결제한 돈은 결제대행사 에스크로 계정에 보관됩니다. Komit은 이 돈을 갖고 있지 않으며,
        통신판매중개자로서 거래의 당사자가 아닙니다.
      </p>

      <StickyBar className="mt-10">
        <span className="text-sm text-fg-secondary">
          결제할 금액 <Money value={amount} className="text-fg" /> · 세션 뒤{" "}
          {CONSULTING_DISPUTE_DAYS}일 이의 기간
        </span>
        <span className="flex items-center gap-2">
          <Link
            href={ROUTES.roomContract(roomId)}
            className={buttonClass({ variant: "outline", size: "sm" })}
          >
            계약서 다시 보기
          </Link>
          <Button
            variant="dark"
            size="lg"
            loading={paying}
            disabled={method === null}
            onClick={pay}
          >
            {amountLabel}원 결제하기
          </Button>
        </span>
      </StickyBar>
    </section>
  );
}
