"use client";

import Link from "next/link";
import * as React from "react";
import { Button, buttonClass } from "@/components/buttons";
import { Card, Dropdown, Field, Input, NumberedSteps } from "@/components/common";
import { Callout, useToast } from "@/components/feedback";
import { clientApi } from "@/lib/api/client";
import { INTERNAL } from "@/lib/api/endpoints";
import { ROUTES } from "@/lib/routes";
import type { AccountVerification } from "@/types/account";
import { ApiError } from "@/types/api";
import type { CodeItem } from "@/types/developer";
import type { SettlementAccount } from "@/types/mypage";

export interface AccountVerificationFormProps {
  currentAccount: SettlementAccount | null;
  banks: CodeItem[];
}

const STEPS = [
  { title: "입력한 계좌로 Komit이 1원을 보냅니다" },
  { title: "은행 앱 입금 내역에서 입금자명 뒤 네 자리 숫자를 확인합니다" },
  { title: "확인한 네 자리를 입력하면 계좌 등록이 끝납니다" },
];

/** 계좌 등록·변경의 첫 단계. 입력한 계좌로 1원을 보내 확인 건을 엽니다. */
export function AccountVerificationForm({
  currentAccount,
  banks,
}: Readonly<AccountVerificationFormProps>) {
  const showToast = useToast();
  const [bankCodeId, setBankCodeId] = React.useState("");
  const [accountNumber, setAccountNumber] = React.useState("");
  const [accountHolder, setAccountHolder] = React.useState("");
  const [sending, setSending] = React.useState(false);
  const [verification, setVerification] = React.useState<AccountVerification | null>(null);

  const canSubmit =
    bankCodeId !== "" && accountNumber.length > 0 && accountHolder.trim().length > 0;

  const submit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!canSubmit || sending) return;

    setSending(true);
    try {
      const { data } = await clientApi.post<AccountVerification>(
        INTERNAL.ACCOUNT_VERIFICATIONS,
        {
          bankCodeId: Number(bankCodeId),
          accountNumber,
          accountHolder: accountHolder.trim(),
        },
      );
      setVerification(data);
      showToast({ tone: "success", title: "1원을 보냈습니다." });
    } catch (error) {
      showToast({
        tone: "danger",
        title: error instanceof ApiError ? error.message : "1원을 보내지 못했습니다.",
      });
    } finally {
      setSending(false);
    }
  };

  if (verification) {
    return (
      <Callout tone="success" className="mt-10" title="1원을 보냈습니다">
        {verification.bankName} {verification.maskedNumber}의 입금 내역을 확인해 주세요.
      </Callout>
    );
  }

  return (
    <form onSubmit={submit}>
      <Card variant="surface" padding="sm" className="mt-8">
        <div className="flex flex-wrap items-center justify-between gap-2 text-[13px]">
          <span className="text-fg-subtle">지금 등록된 계좌</span>
          {currentAccount ? (
            <strong className="font-semibold text-fg">
              {currentAccount.bankName} {currentAccount.maskedNumber} · 예금주{" "}
              {currentAccount.accountHolder}
            </strong>
          ) : (
            <strong className="font-semibold text-fg-muted">등록된 계좌가 없습니다</strong>
          )}
          <span className="text-fg-subtle">
            {currentAccount
              ? "새 계좌를 확인하면 이 계좌를 대신합니다"
              : "계좌 확인을 마치면 새 계좌가 등록됩니다"}
          </span>
        </div>
      </Card>

      <section className="mt-10" aria-labelledby="account-information-title">
        <div className="flex flex-wrap items-baseline justify-between gap-2">
          <h2 id="account-information-title" className="text-lg font-bold text-fg">
            계좌 정보
          </h2>
          <p className="text-[13px] text-fg-subtle">본인 명의 계좌만 등록할 수 있습니다</p>
        </div>

        <Card padding="lg" className="mt-3 flex flex-col gap-4">
          <Field label="은행" layout="horizontal" required>
            <Dropdown
              options={banks.map((bank) => ({
                value: String(bank.codeId),
                label: bank.codeName,
              }))}
              value={bankCodeId}
              onChange={setBankCodeId}
              placeholder="은행을 선택해 주세요"
              disabled={sending}
              className="max-w-[240px]"
            />
          </Field>

          <Field label="계좌번호" layout="horizontal" required htmlFor="account-number">
            <Input
              id="account-number"
              type="text"
              inputMode="numeric"
              autoComplete="off"
              value={accountNumber}
              placeholder="하이픈 없이 숫자만 적어주세요"
              disabled={sending}
              onChange={(event) => setAccountNumber(event.target.value.replace(/\D/g, ""))}
            />
          </Field>

          <Field label="예금주" layout="horizontal" required htmlFor="account-holder">
            <Input
              id="account-holder"
              value={accountHolder}
              placeholder="통장에 적힌 이름 그대로 적어주세요"
              autoComplete="name"
              disabled={sending}
              onChange={(event) => setAccountHolder(event.target.value)}
            />
          </Field>
        </Card>
      </section>

      <Callout tone="neutral" className="mt-8" title="등록 절차">
        <NumberedSteps items={STEPS} tone="primary" className="mt-3 gap-3" />
      </Callout>

      <div className="mt-8 flex justify-end gap-2">
        <Link
          href={ROUTES.mypage}
          className={buttonClass({ variant: "outline", size: "lg" })}
        >
          취소
        </Link>
        <Button type="submit" size="lg" loading={sending} disabled={!canSubmit}>
          1원 보내기
        </Button>
      </div>
    </form>
  );
}
