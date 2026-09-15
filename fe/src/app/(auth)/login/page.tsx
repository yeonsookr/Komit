import type { Metadata } from "next";
import { LoginCard } from "@/components/domains/auth";

export const metadata: Metadata = {
  title: "로그인 | Komit",
  description: "카카오 계정으로 Komit을 시작합니다.",
  // 로그인 화면은 검색 결과에 실을 이유가 없습니다.
  robots: { index: false },
};

/**
 * 돌아갈 곳으로 쓸 수 있는 값인지 봅니다.
 *
 * `/` 로 시작하는 우리 화면의 경로만 통과시킵니다. `//evil.com` 처럼 슬래시가 둘이면
 * 브라우저가 다른 사이트로 읽으므로 막습니다. 로그인을 거쳐 낯선 곳으로 튕기는 것을
 * 막기 위한 최소한의 검사이고, 실제 이동은 백엔드 연동 단계에서 다시 검사합니다.
 */
function safeNext(value: string | string[] | undefined): string | undefined {
  const next = Array.isArray(value) ? value[0] : value;
  if (next === undefined) return undefined;
  return next.startsWith("/") && !next.startsWith("//") ? next : undefined;
}

/**
 * 로그인 화면.
 *
 * `next` 를 읽으므로 정적으로 생성되지 않습니다. 어디서 들어왔는지에 따라 달라지는
 * 화면이라 그래야 맞고, 검색에도 싣지 않습니다(컨벤션 7장).
 */
export default async function LoginPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const { next } = await searchParams;

  return <LoginCard next={safeNext(next)} />;
}
