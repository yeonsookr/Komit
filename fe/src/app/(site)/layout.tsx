import { SessionShell } from "@/components/domains/auth";

/**
 * (site) 기본 레이아웃 — GNB + 본문 + 푸터.
 * 이 그룹 아래 페이지는 모두 같은 골격을 씁니다. 본문 폭은 각 페이지가
 * <Container width="content | wide | landing"> 로 정합니다.
 *
 * 헤더의 로그인 상태는 인증 스토어(클라이언트)에서 읽습니다. 여기서 쿠키를 읽으면
 * 랜딩까지 동적 렌더링이 되어 정적 생성이 풀리기 때문입니다(컨벤션 7장).
 */
export default function SiteLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <SessionShell>{children}</SessionShell>;
}
