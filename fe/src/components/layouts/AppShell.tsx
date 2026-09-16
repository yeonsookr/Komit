import * as React from "react";
import { cn } from "@/lib/cn";
import { Footer, type FooterProps } from "./Footer";
import { Header, type HeaderNavItem, type HeaderProps, type HeaderUser } from "./Header";
import { type ContainerWidth } from "./Container";

/**
 * AppShell — 서비스 화면의 기본 레이아웃.
 *   GNB(56px) + 본문 + 푸터. 본문 폭은 페이지가 <Container> 로 직접 정합니다.
 *   (본문 720 · 관리자 840 · 랜딩 960 으로 화면마다 다르기 때문입니다.)
 *
 * 세션(화상) 화면처럼 GNB·푸터가 없는 화면은 이 레이아웃을 쓰지 않습니다.
 */
export interface AppShellProps extends React.HTMLAttributes<HTMLDivElement> {
  /**
   * 로그인 사용자. 없으면 헤더가 비로그인 상태(로그인 링크)로 그려집니다.
   * 인증 스토어에서 읽어 채우려면 `SessionShell`(domains/auth) 을 대신 씁니다.
   */
  user?: HeaderUser;
  nav?: HeaderNavItem[];
  /** GNB 안쪽 폭 (기본: 화면 전체) */
  headerWidth?: ContainerWidth;
  /** 푸터 안쪽 폭 (기본: 본문과 같은 720) */
  footerWidth?: ContainerWidth;
  /** 푸터 숨기기 (앱 내부 화면) */
  hideFooter?: boolean;
  headerProps?: Omit<HeaderProps, "user" | "nav" | "width">;
  footerProps?: Omit<FooterProps, "width">;
}

export function AppShell({
  user,
  nav,
  headerWidth = "full",
  footerWidth = "content",
  hideFooter,
  headerProps,
  footerProps,
  className,
  children,
  ...props
}: AppShellProps) {
  return (
    <div className={cn("flex min-h-full flex-1 flex-col", className)} {...props}>
      <Header user={user} nav={nav} width={headerWidth} {...headerProps} />
      <main className="flex-1">{children}</main>
      {!hideFooter && <Footer width={footerWidth} {...footerProps} />}
    </div>
  );
}
