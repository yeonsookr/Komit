import "server-only";

import { redirect } from "next/navigation";
import { ApiError } from "@/types/api";
import { ROUTES } from "@/lib/routes";

/**
 * 운영 콘솔 서버 컴포넌트에서 데이터를 읽습니다. 사용자 쪽 `requireLogin` 의 운영자판입니다.
 *
 * 평소에는 proxy 가 쿠키 없는 요청을 먼저 돌려보내므로 여기까지 오지 않습니다.
 * 이 겹은 **쿠키는 있는데 토큰이 만료된 경우**를 받습니다 — 그때는 서버가 401 을
 * 주고, 그것은 오류가 아니라 다시 로그인할 일이라 운영 로그인으로 보냅니다.
 *
 * **사용자 로그인으로 보내지 않습니다.** 401 하나를 두 세션이 나눠 쓰면 운영자가
 * 카카오 로그인 화면을 보게 됩니다.
 *
 * `redirect` 도 예외를 던지지만 Next 가 알아보는 신호여서 리다이렉트 응답이 됩니다 —
 * `try` 안에서 부르면 이 `catch` 가 도로 삼키므로 반드시 `catch` 에서 부릅니다.
 */
export async function requireAdminLogin<T>(load: () => Promise<T>): Promise<T> {
  try {
    return await load();
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) redirect(ROUTES.adminLogin);
    throw error;
  }
}
