"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import * as React from "react";
import { Button, buttonClass } from "@/components/buttons";
import { Card, Dropdown, Field, Input, NumberedSteps, PageHeader } from "@/components/common";
import { Callout, useToast } from "@/components/feedback";
import { clientApi } from "@/lib/api/client";
import { INTERNAL } from "@/lib/api/endpoints";
import { finishSignup } from "@/lib/api/services/signup";
import { ROUTES } from "@/lib/routes";
import type {
  AccountVerification,
  ConfirmAccountVerificationResponse,
} from "@/types/account";
import { ApiError } from "@/types/api";
import type { CodeItem } from "@/types/developer";
import type { SettlementAccount } from "@/types/mypage";
import { AccountVerificationCodeInput } from "./AccountVerificationCodeInput";

export interface AccountVerificationFormProps {
  currentAccount: SettlementAccount | null;
  banks: CodeItem[];
  completionHref?: string;
  cancelHref?: string;
  signup?: boolean;
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
  completionHref = ROUTES.mypage,
  cancelHref = ROUTES.mypage,
  signup = false,
}: Readonly<AccountVerificationFormProps>) {
  const router = useRouter();
  const showToast = useToast();
  const [bankCodeId, setBankCodeId] = React.useState("");
  const [accountNumber, setAccountNumber] = React.useState("");
  const [accountHolder, setAccountHolder] = React.useState("");
  const [sending, setSending] = React.useState(false);
  const [verification, setVerification] = React.useState<AccountVerification | null>(null);
  const [authCode, setAuthCode] = React.useState("");
  const [confirming, setConfirming] = React.useState(false);

  const canSubmit =
    bankCodeId !== "" && /^\d{16}$/.test(accountNumber) && accountHolder.trim().length > 0;

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
      <>
        <PageHeader
          title={signup ? "1원 인증" : "계좌 확인"}
          description="보낸 1원의 입금자명 뒤 네 자리를 적어주세요."
        />

        <Card variant="surface" padding="sm" className="mt-8">
          <div className="flex flex-wrap items-center justify-between gap-2 text-[13px]">
            <span className="text-fg-subtle">확인 중인 계좌</span>
            <strong className="font-semibold text-fg">
              {verification.bankName} {verification.maskedNumber} · 예금주{" "}
              {verification.accountHolder}
            </strong>
            <Button
              variant="ghost"
              size="sm"
              disabled={confirming}
              onClick={() => {
                setVerification(null);
                setAuthCode("");
              }}
            >
              계좌 다시 입력
            </Button>
          </div>
        </Card>

        <section className="mt-10" aria-labelledby="verification-guide-title">
          <div className="flex flex-wrap items-baseline justify-between gap-2">
            <h2 id="verification-guide-title" className="text-lg font-bold text-fg">
              어디서 확인하나요
            </h2>
            <p className="text-[13px] text-fg-subtle">은행 앱 · 입금 내역</p>
          </div>
          <Card className="mt-3">
            <div className="flex items-center justify-between gap-4">
              <div>
                <p className="font-bold text-fg">Komit + 네 자리 숫자</p>
                <p className="mt-0.5 text-[13px] text-fg-subtle">오늘 · 입금</p>
              </div>
              <strong className="text-info">+1원</strong>
            </div>
            <p className="mt-4 border-t border-line-subtle pt-3 text-[13px] text-fg-muted">
              보낸 사람 이름 뒤에 붙은 숫자 네 자리가 확인 번호입니다.
            </p>
          </Card>
        </section>

        <form
          className="mt-8"
          onSubmit={async (event) => {
            event.preventDefault();
            if (!/^\d{4}$/.test(authCode) || confirming) return;

            setConfirming(true);
            try {
              await clientApi.post<ConfirmAccountVerificationResponse>(
                INTERNAL.ACCOUNT_VERIFICATION_CONFIRM(verification.verificationId),
                { authCode },
              );
              if (signup) await finishSignup();
              showToast({ tone: "success", title: "계좌를 등록했습니다." });
              router.replace(completionHref);
              router.refresh();
            } catch (error) {
              showToast({
                tone: "danger",
                title: error instanceof ApiError ? error.message : "계좌를 등록하지 못했습니다.",
              });
              setConfirming(false);
            }
          }}
        >
          <section aria-labelledby="verification-code-title">
            <h2 id="verification-code-title" className="text-lg font-bold text-fg">
              확인 번호
            </h2>
            <Card className="mt-3 py-8">
              <AccountVerificationCodeInput
                value={authCode}
                onChange={setAuthCode}
                disabled={confirming}
              />
              <p className="mt-4 text-center text-[13px] text-fg-subtle">
                입금자명에서 확인한 숫자 네 자리를 차례로 적어주세요.
              </p>
            </Card>
          </section>

          <div className="mt-8 flex justify-end gap-2">
            <Link
              href={cancelHref}
              className={buttonClass({ variant: "outline", size: "lg" })}
            >
              그만두기
            </Link>
            <Button
              type="submit"
              size="lg"
              loading={confirming}
              disabled={!/^\d{4}$/.test(authCode)}
            >
              계좌 등록하기
            </Button>
          </div>
        </form>
      </>
    );
  }

  return (
    <>
      <PageHeader
        title={signup ? "1원 인증" : "계좌 등록"}
        description="거래 대금을 받을 계좌를 1원 인증으로 확인합니다."
      />

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
              maxLength={16}
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

      {signup ? (
        <Callout tone="neutral" className="mt-8">
          계좌로 보낸 1원의 입금자명에서 확인 번호를 찾아 입력해 주세요.
        </Callout>
      ) : (
        <Callout tone="neutral" className="mt-8" title="등록 절차">
          <NumberedSteps items={STEPS} tone="primary" className="mt-3 gap-3" />
        </Callout>
      )}

      <div className="mt-8 flex justify-end gap-2">
        <Link
          href={cancelHref}
          className={buttonClass({ variant: "outline", size: "lg" })}
        >
          취소
        </Link>
        <Button type="submit" size="lg" loading={sending} disabled={!canSubmit}>
          1원 보내기
        </Button>
      </div>
      </form>
    </>
  );
}
