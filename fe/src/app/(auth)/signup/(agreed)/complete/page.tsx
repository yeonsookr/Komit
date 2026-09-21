import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { Card } from "@/components/common";
import { EnterKomitButton } from "@/components/domains/auth";
import { NEXT_PARAM } from "@/lib/auth/params";
import { safeNext } from "@/lib/auth/safeNext";
import { ROUTES } from "@/lib/routes";
import { getSignupCompletion } from "@/lib/api/services/auth";
import { ApiError } from "@/types/api";

export const metadata: Metadata = {
  title: "가입 완료 | Komit",
  robots: { index: false },
};

export default async function SignupCompletePage({
  searchParams,
}: Readonly<{ searchParams: Promise<Record<string, string | string[] | undefined>> }>) {
  const next = safeNext((await searchParams)[NEXT_PARAM]);
  try {
    await getSignupCompletion();
  } catch (error) {
    if (error instanceof ApiError && error.status === 409) redirect(ROUTES.signupAccount(next));
    if (error instanceof ApiError && error.status === 401) redirect(ROUTES.login);
    throw error;
  }

  return (
    <Card variant="elevated" padding="lg" className="mx-auto w-full max-w-[520px] sm:p-9">
      <p className="text-sm font-bold text-primary">Komit</p>
      <h1 className="mt-4 text-3xl font-bold tracking-tight text-fg">가입을 완료했어요!</h1>
      <p className="mt-5 text-base leading-7 text-fg-muted">
        이제 Komit에서 프로젝트를 살펴보고 개발자와 거래할 수 있어요.
      </p>
      <EnterKomitButton href={next ?? ROUTES.home} />
    </Card>
  );
}
