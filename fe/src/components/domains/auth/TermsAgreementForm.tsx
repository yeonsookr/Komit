"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { Button, buttonClass } from "@/components/buttons";
import { Card } from "@/components/common";
import { useToast } from "@/components/feedback";
import { clientApi } from "@/lib/api/client";
import { INTERNAL } from "@/lib/api/endpoints";
import { ROUTES } from "@/lib/routes";
import { ApiError } from "@/types/api";
import type { RequiredTerms } from "@/types/terms";

const AGREEMENTS: { field: keyof RequiredTerms; label: string; href: string }[] = [
  { field: "isServiceTermsAgreed", label: "서비스 이용약관", href: ROUTES.policiesTerms },
  { field: "isPrivacyCollectAgreed", label: "개인정보 수집 및 이용", href: ROUTES.policiesPrivacyCollection },
  { field: "isPrivacyThirdPartyAgreed", label: "개인정보 제3자 제공", href: ROUTES.policiesPrivacyThirdParty },
  { field: "isEscrowTermsAgreed", label: "에스크로 서비스 이용약관", href: ROUTES.policiesEscrow },
];

const INITIAL_AGREEMENTS: RequiredTerms = {
  isEscrowTermsAgreed: false,
  isPrivacyCollectAgreed: false,
  isPrivacyThirdPartyAgreed: false,
  isServiceTermsAgreed: false,
};

export function TermsAgreementForm({ next }: Readonly<{ next?: string }>) {
  const router = useRouter();
  const showToast = useToast();
  const [values, setValues] = useState<RequiredTerms>(INITIAL_AGREEMENTS);
  const [saving, setSaving] = useState(false);
  const allAgreed = AGREEMENTS.every((agreement) => values[agreement.field]);

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!allAgreed || saving) return;

    setSaving(true);
    try {
      await clientApi.put<RequiredTerms>(INTERNAL.REQUIRED_TERMS, values);
      router.replace(ROUTES.signupAccount(next));
    } catch (error) {
      showToast({
        tone: "danger",
        title: error instanceof ApiError ? error.message : "약관 동의를 저장하지 못했습니다.",
      });
      setSaving(false);
    }
  };

  return (
    <Card variant="elevated" padding="lg" className="mx-auto w-full max-w-[560px] sm:p-9">
      <p className="text-sm font-bold text-primary">Komit</p>
      <h1 className="mt-4 text-3xl font-bold tracking-tight text-fg">
        약관에 동의해 주세요
      </h1>
      <p className="mt-4 text-sm leading-6 text-fg-muted">
        처음 가입하는 회원의 필수 동의를 각각 확인합니다.
      </p>

      <form onSubmit={submit} className="mt-7">
        <label className="flex items-center gap-3 rounded-xl border border-line bg-bg p-4 text-base font-bold text-fg">
          <input
            type="checkbox"
            checked={allAgreed}
            onChange={(event) => {
              const agreed = event.target.checked;
              setValues({
                isEscrowTermsAgreed: agreed,
                isPrivacyCollectAgreed: agreed,
                isPrivacyThirdPartyAgreed: agreed,
                isServiceTermsAgreed: agreed,
              });
            }}
            className="size-5 accent-primary"
          />
          필수 약관 모두 동의
        </label>

        <ul className="mt-3 space-y-2">
          {AGREEMENTS.map(({ field, label, href }) => (
            <li key={field} className="flex items-center justify-between gap-3 rounded-xl border border-line-subtle px-4 py-3">
              <label className="flex min-w-0 items-center gap-3 text-sm font-medium text-fg">
                <input
                  type="checkbox"
                  checked={values[field]}
                  onChange={(event) => setValues((current) => ({
                    ...current,
                    [field]: event.target.checked,
                  }))}
                  className="size-4 accent-primary"
                />
                <span>[필수] {label}</span>
              </label>
              <Link href={href} target="_blank" rel="noopener noreferrer" className="shrink-0 text-sm text-primary underline">
                보기
              </Link>
            </li>
          ))}
        </ul>

        <div className="mt-8 flex flex-col gap-3">
          <Button type="submit" size="lg" fullWidth loading={saving} disabled={!allAgreed}>
            동의하고 계속하기
          </Button>
          <Link
            href={ROUTES.login}
            className={buttonClass({ variant: "outline", size: "lg", fullWidth: true })}
          >
            이전으로
          </Link>
        </div>
      </form>
    </Card>
  );
}
