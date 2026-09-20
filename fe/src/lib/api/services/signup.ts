import { clientApi } from "../client";
import { INTERNAL } from "../endpoints";

/** 브라우저에서 Next 가입 완료 경로를 호출한다. 성공은 본문 없는 204다. */
export async function finishSignup(): Promise<void> {
  await clientApi.post(INTERNAL.SIGNUP_COMPLETION);
}
