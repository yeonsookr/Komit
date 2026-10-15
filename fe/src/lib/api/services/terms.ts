import "server-only";

import { authenticatedBackendApi } from "../server";
import { BACKEND } from "../endpoints";
import type { RequiredTerms } from "@/types/terms";

export async function getRequiredTerms(accessToken?: string): Promise<RequiredTerms> {
  const authorization = accessToken
    ? { headers: { Authorization: `Bearer ${accessToken}` } }
    : undefined;
  const { data } = await authenticatedBackendApi().get<RequiredTerms>(
    BACKEND.REQUIRED_TERMS,
    authorization,
  );
  return data;
}

export async function agreeRequiredTerms(body: RequiredTerms): Promise<RequiredTerms> {
  const { data } = await authenticatedBackendApi().put<RequiredTerms>(
    BACKEND.REQUIRED_TERMS,
    body,
  );
  return data;
}
