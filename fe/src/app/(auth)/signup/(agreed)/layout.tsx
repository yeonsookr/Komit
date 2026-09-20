import { redirect } from "next/navigation";
import { getRequiredTerms } from "@/lib/api/services/terms";
import { ROUTES } from "@/lib/routes";
import { ApiError } from "@/types/api";

export default async function AgreedSignupLayout({ children }: { children: React.ReactNode }) {
  const terms = await getRequiredTerms().catch((error: unknown) => {
    if (error instanceof ApiError && error.status === 401) return null;
    throw error;
  });
  if (!terms) redirect(ROUTES.login);
  if (Object.values(terms).some((agreed) => !agreed)) redirect(ROUTES.signupTerms());
  return children;
}
