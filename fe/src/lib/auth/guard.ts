import "server-only";

import { redirect } from "next/navigation";
import { ApiError } from "@/types/api";
import { ROUTES } from "@/lib/routes";

/**
 * 로그인이 필요한 서버 컴포넌트에서 데이터를 읽습니다.
 *
 * 서버 컴포넌트는 데이터를 읽는 것이 곧 렌더라, 여기서 던진 예외는 렌더 실패가 되어
 * 500 으로 나갑니다. 로그인하지 않은 것은 오류가 아니므로 401 만 골라 로그인 화면으로
 * 보냅니다. `redirect` 도 예외를 던지지만 Next 가 알아보는 신호여서 리다이렉트 응답이
 * 됩니다 — `try` 안에서 부르면 이 `catch` 가 도로 삼키므로 반드시 `catch` 에서 부릅니다.
 *
 * 401 이 아닌 오류(목 서버 중단 등)는 그대로 올려보내 오류 화면이 뜨게 둡니다.
 *
 * @example
 * const [spending, income] = await requireLogin(() =>
 *   Promise.all([getSpending(month), getIncome()]),
 * );
 */
export async function requireLogin<T>(load: () => Promise<T>): Promise<T> {
  try {
    return await load();
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) redirect(ROUTES.login);
    throw error;
  }
}
