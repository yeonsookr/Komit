import type { Metadata } from "next";
import { IdentityVerificationForm } from "@/components/domains/auth/IdentityVerificationForm";
import { NEXT_PARAM } from "@/lib/auth/params";
import { safeNext } from "@/lib/auth/safeNext";

export const metadata: Metadata = {
  title: "본인인증 | Komit",
  robots: { index: false },
};

export default async function SignupIdentityPage({
  searchParams,
}: Readonly<{ searchParams: Promise<Record<string, string | string[] | undefined>> }>) {
  const next = safeNext((await searchParams)[NEXT_PARAM]);
  return <IdentityVerificationForm next={next} />;
}
