import type { Metadata } from "next";
import { AccountVerificationForm } from "@/components/domains/mypage";
import { NEXT_PARAM } from "@/lib/auth/params";
import { safeNext } from "@/lib/auth/safeNext";
import { getCodes } from "@/lib/api/services";
import { ROUTES } from "@/lib/routes";

export const metadata: Metadata = {
  title: "1원 인증 | Komit",
  robots: { index: false },
};

export default async function SignupVerificationPage({
  searchParams,
}: Readonly<{ searchParams: Promise<Record<string, string | string[] | undefined>> }>) {
  const next = safeNext((await searchParams)[NEXT_PARAM]);
  const banks = await getCodes("BANK_CODE");

  return (
    <div className="mx-auto w-full max-w-[720px]">
      <AccountVerificationForm
        currentAccount={null}
        banks={banks}
        completionHref={ROUTES.signupComplete(next)}
        cancelHref={ROUTES.signupAccount(next)}
        signup
      />
    </div>
  );
}
