import "server-only";

import { bearerHeaders } from "@/lib/auth/session";
import type {
  AccountVerification,
  MemberAccount,
  ConfirmAccountVerificationBody,
  ConfirmAccountVerificationResponse,
  RequestAccountVerificationBody,
} from "@/types/account";
import { BACKEND } from "../endpoints";
import { backendApi } from "../server";

/** 회원가입 때 발급된 내 계좌를 받아옵니다. 아직 없으면 이때 발급됩니다. (R-ACC-14) */
export async function issueMemberAccount(): Promise<MemberAccount> {
  const { data } = await backendApi().post<MemberAccount>(
    BACKEND.MEMBER_ACCOUNT,
    null,
    await bearerHeaders(),
  );
  return data;
}

/** 입력한 계좌로 1원을 보내고 확인 건을 엽니다. */
export async function requestAccountVerification(
  body: RequestAccountVerificationBody,
): Promise<AccountVerification> {
  const { data } = await backendApi().post<AccountVerification>(
    BACKEND.ACCOUNT_VERIFICATIONS,
    body,
    await bearerHeaders(),
  );
  return data;
}

/** 입금자명 뒤 네 자리를 보내 계좌 등록을 마칩니다. */
export async function confirmAccountVerification(
  verificationId: number,
  body: ConfirmAccountVerificationBody,
): Promise<ConfirmAccountVerificationResponse> {
  const { data } = await backendApi().post<ConfirmAccountVerificationResponse>(
    BACKEND.ACCOUNT_VERIFICATION_CONFIRM(verificationId),
    body,
    await bearerHeaders(),
  );
  return data;
}
