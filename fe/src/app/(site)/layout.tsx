import { AppShell } from "@/components/layouts";

/**
 * (site) 기본 레이아웃 — GNB + 본문 + 푸터.
 * 이 그룹 아래 페이지는 모두 같은 골격을 씁니다. 본문 폭은 각 페이지가
 * <Container width="content | wide | landing"> 로 정합니다.
 *
 * 로그인 상태가 붙기 전까지는 비로그인 헤더로 그려집니다.
 * 인증이 준비되면 여기에서 세션을 읽어 <AppShell user={...}> 로 넘기면 됩니다.
 */
export default function SiteLayout({ children }: { children: React.ReactNode }) {
  return <AppShell>{children}</AppShell>;
}
