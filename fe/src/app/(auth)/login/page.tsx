import type { Metadata } from "next";
import { LoginCard } from "@/components/domains/auth";
import { ERROR_PARAM, NEXT_PARAM } from "@/lib/auth/params";
import { safeNext } from "@/lib/auth/safeNext";

export const metadata: Metadata = {
  title: "로그인 | Komit",
  description: "카카오 계정으로 Komit을 시작합니다.",
  // 로그인 화면은 검색 결과에 실을 이유가 없습니다.
  robots: { index: false },
};

function first(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}

/**
 * 로그인 화면.
 *
 * `next`·`error` 를 읽으므로 정적으로 생성되지 않습니다. 어디서 들어왔는지, 왜 돌아왔는지에
 * 따라 달라지는 화면이라 그래야 맞고, 검색에도 싣지 않습니다(컨벤션 7장).
 */
export default async function LoginPage({
  searchParams,
}: Readonly<{
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}>) {
  const params = await searchParams;

  return (
    <LoginCard
      next={safeNext(params[NEXT_PARAM])}
      errorCode={first(params[ERROR_PARAM])}
    />
  );
}
