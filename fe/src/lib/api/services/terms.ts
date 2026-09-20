import "server-only";

import { backendApi } from "../server";
import { BACKEND } from "../endpoints";
import { authorized } from "@/lib/auth/session";
import type { RequiredTerms } from "@/types/terms";

export async function getRequiredTerms(accessToken?: string): Promise<RequiredTerms> {
  const authorization = accessToken
    ? { headers: { Authorization: `Bearer ${accessToken}` } }
    : await authorized();
  const { data } = await backendApi().get<RequiredTerms>(BACKEND.REQUIRED_TERMS, authorization);
  return data;
}

export async function agreeRequiredTerms(body: RequiredTerms): Promise<RequiredTerms> {
  const { data } = await backendApi().put<RequiredTerms>(
    BACKEND.REQUIRED_TERMS,
    body,
    await authorized(),
  );
  return data;
}
