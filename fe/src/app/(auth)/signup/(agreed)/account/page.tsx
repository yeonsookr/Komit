import type { Metadata } from "next";
import Link from "next/link";
import { buttonClass } from "@/components/buttons";
import { Card } from "@/components/common";
import { CompleteSignupButton } from "@/components/domains/auth/CompleteSignupButton";
import { NEXT_PARAM } from "@/lib/auth/params";
import { safeNext } from "@/lib/auth/safeNext";
import { ROUTES } from "@/lib/routes";

export const metadata: Metadata = {
  title: "계좌 등록 안내 | Komit",
  robots: { index: false },
};

export default async function SignupAccountPage({
  searchParams,
}: Readonly<{ searchParams: Promise<Record<string, string | string[] | undefined>> }>) {
  const next = safeNext((await searchParams)[NEXT_PARAM]);

  return (
    <Card variant="elevated" padding="lg" className="mx-auto w-full max-w-[520px] sm:p-9">
      <p className="text-sm font-bold text-primary">Komit</p>
      <h1 className="mt-4 text-3xl font-bold tracking-tight text-fg">
        Komit에 오신걸 환영해요!
      </h1>
      <p className="mt-5 text-base leading-7 text-fg-muted">
        간편한 거래를 위해 거래 계좌를 등록할 수 있어요.
      </p>
      <div className="mt-9 flex flex-col gap-3">
        <Link
          href={ROUTES.signupIdentity(next)}
          className={buttonClass({ size: "lg", fullWidth: true })}
        >
          계좌 등록하기
        </Link>
        <CompleteSignupButton next={next} />
      </div>
    </Card>
  );
}
