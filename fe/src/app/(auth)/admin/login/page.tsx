import type { Metadata } from "next";
import { AdminLoginCard } from "@/components/domains/admin";
import { safeAdminNext } from "@/lib/admin/params";
import { NEXT_PARAM } from "@/lib/auth/params";

export const metadata: Metadata = {
  title: "운영 콘솔 로그인 | Komit",
  // 내부 직원 전용이라 검색에 싣지 않습니다.
  robots: { index: false },
};

/**
 * 운영 콘솔 로그인 화면 (`9_로그인`).
 *
 * 카드 골격은 사용자 로그인과 같은 `(auth)` 레이아웃을 씁니다. 같은 것은 골격뿐이고
 * 계정도 쿠키도 돌아갈 곳도 서로 다릅니다.
 */
export default async function AdminLoginPage({
  searchParams,
}: Readonly<{
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}>) {
  const params = await searchParams;
  return <AdminLoginCard next={safeAdminNext(params[NEXT_PARAM])} />;
}
