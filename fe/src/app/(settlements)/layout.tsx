import { SessionShell } from "@/components/domains/auth";

/**
 * 정산 화면 레이아웃 — GNB + 본문. 푸터는 감춥니다.
 *
 * 왼쪽 목록은 여기 두지 않습니다. 목록이 고른 달을 가리켜야 하는데 layout 은
 * `searchParams` 를 받지 못합니다. 화면마다 `SettlementShell` 로 그립니다.
 */
export default function SettlementsLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <SessionShell hideFooter>{children}</SessionShell>;
}
