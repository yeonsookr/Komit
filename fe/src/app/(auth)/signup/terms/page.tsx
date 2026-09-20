import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { TermsAgreementForm } from "@/components/domains/auth/TermsAgreementForm";
import { NEXT_PARAM } from "@/lib/auth/params";
import { safeNext } from "@/lib/auth/safeNext";
import { readAccessToken } from "@/lib/auth/session";
import { ROUTES } from "@/lib/routes";
import { getRequiredTerms } from "@/lib/api/services/terms";

export const metadata: Metadata = {
  title: "약관 동의 | Komit",
  robots: { index: false },
};

export default async function SignupTermsPage({
  searchParams,
}: Readonly<{ searchParams: Promise<Record<string, string | string[] | undefined>> }>) {
  const next = safeNext((await searchParams)[NEXT_PARAM]);
  if (!(await readAccessToken())) redirect(ROUTES.login);
  const terms = await getRequiredTerms();
  if (Object.values(terms).every(Boolean)) redirect(ROUTES.signupAccount(next));
  return <TermsAgreementForm next={next} />;
}
