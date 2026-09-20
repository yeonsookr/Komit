import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { TermsAgreementForm } from "@/components/domains/auth/TermsAgreementForm";
import { NEXT_PARAM } from "@/lib/auth/params";
import { safeNext } from "@/lib/auth/safeNext";
import { readAccessToken } from "@/lib/auth/session";
import { ROUTES } from "@/lib/routes";

export const metadata: Metadata = {
  title: "약관 동의 | Komit",
  robots: { index: false },
};

export default async function SignupTermsPage({
  searchParams,
}: Readonly<{ searchParams: Promise<Record<string, string | string[] | undefined>> }>) {
  const next = safeNext((await searchParams)[NEXT_PARAM]);
  if (!(await readAccessToken())) redirect(ROUTES.login);
  return <TermsAgreementForm next={next} />;
}
