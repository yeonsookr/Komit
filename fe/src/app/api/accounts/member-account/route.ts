import { NextResponse } from "next/server";
import { issueMemberAccount } from "@/lib/api/services";
import { toErrorResponse } from "@/lib/api/route";
import { authorized } from "@/lib/auth/session";

/**
 * POST /api/accounts/member-account — 회원가입 때 발급된 내 계좌를 받아옵니다.
 *
 * 대상 회원을 받지 않습니다. 세션의 주인 것만 돌려주므로 남의 번호를 가리킬 자리가
 * 없습니다. (R-ACC-14)
 */
export async function POST() {
  try {
    await authorized();
  } catch (error) {
    return toErrorResponse(error);
  }

  try {
    return NextResponse.json(await issueMemberAccount());
  } catch (error) {
    return toErrorResponse(error);
  }
}
