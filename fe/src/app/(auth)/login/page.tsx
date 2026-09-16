import type { Metadata } from "next";
import { LoginCard } from "@/components/domains/auth";
import { NEXT_PARAM, safeNext } from "@/lib/auth";

export const metadata: Metadata = {
  title: "로그인 | Komit",
  description: "카카오 계정으로 Komit을 시작합니다.",
  // 로그인 화면은 검색 결과에 실을 이유가 없습니다.
  robots: { index: false },
};

/**
 * 로그인 화면.
 *
 * 돌아갈 곳을 읽으므로 정적으로 생성되지 않습니다. 어디서 들어왔는지에 따라 달라지는
 * 화면이라 그래야 맞고, 검색에도 싣지 않습니다(컨벤션 7장).
 */
export default async function LoginPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const next = safeNext((await searchParams)[NEXT_PARAM]);

  return <LoginCard next={next} />;
}
