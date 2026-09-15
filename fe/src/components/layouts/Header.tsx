"use client";

import * as React from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { cn } from "@/lib/cn";
import { Avatar, CountBadge, NavPill } from "@/components/common";
import { MenuDivider, MenuItem, Popover } from "@/components/feedback";
import { Container, type ContainerWidth } from "./Container";
import { ROUTES } from "@/lib/routes";

/**
 * Header — 56px 높이의 GNB.
 *  - 비로그인: 로고 + 메뉴 + 로그인
 *  - 로그인: 로고 + 메뉴 + 알림 + 아바타 메뉴
 *
 * 좁은 화면(md 미만)에서는 메뉴가 햄버거 패널로 접힙니다.
 * 알림과 아바타는 자주 쓰는 동작이라 접지 않고 그대로 둡니다.
 */
export interface HeaderNavItem {
  label: string;
  href: string;
}

export interface HeaderUser {
  name: string;
  avatarSrc?: string;
  /** 안 읽은 알림 수 */
  unreadCount?: number;
}

export interface HeaderProps {
  nav?: HeaderNavItem[];
  user?: HeaderUser;
  loginHref?: string;
  notificationsHref?: string;
  menuItems?: HeaderNavItem[];
  onLogout?: () => void;
  width?: ContainerWidth;
  className?: string;
}

/** 비로그인 화면(랜딩)의 메뉴 */
export const PUBLIC_NAV: HeaderNavItem[] = [
  { label: "이용 방법", href: ROUTES.howTo },
  { label: "의뢰", href: ROUTES.requests },
  { label: "개발자 찾기", href: ROUTES.developers },
  { label: "사전점검", href: ROUTES.checkup },
];

/** 로그인 화면의 메뉴 */
export const APP_NAV: HeaderNavItem[] = [
  { label: "의뢰", href: ROUTES.requests },
  { label: "개발자 찾기", href: ROUTES.developers },
  { label: "사전점검", href: ROUTES.checkup },
  { label: "정산", href: ROUTES.settlements },
];

const DEFAULT_MENU_ITEMS: HeaderNavItem[] = [
  { label: "마이페이지", href: ROUTES.mypage },
  { label: "내 프로필", href: ROUTES.mypageProfile },
  { label: "내 일정", href: ROUTES.mypageSchedule },
  { label: "설정", href: ROUTES.mypageSettings },
];

/** /requests 와 /requests/123 은 같은 메뉴로 봅니다. */
function isActive(pathname: string | null, href: string) {
  if (!pathname) return false;
  return pathname === href || pathname.startsWith(`${href}/`);
}

export function Header({
  nav,
  user,
  loginHref = ROUTES.login,
  notificationsHref = ROUTES.notifications,
  menuItems = DEFAULT_MENU_ITEMS,
  onLogout,
  width = "full",
  className,
}: HeaderProps) {
  const pathname = usePathname();
  const items = nav ?? (user ? APP_NAV : PUBLIC_NAV);

  const [accountOpen, setAccountOpen] = React.useState(false);
  const [mobileOpen, setMobileOpen] = React.useState(false);

  /** 화면을 옮기면 열려 있던 패널을 닫습니다. */
  React.useEffect(() => {
    setMobileOpen(false);
    setAccountOpen(false);
  }, [pathname]);

  React.useEffect(() => {
    if (!mobileOpen) return;
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && setMobileOpen(false);
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [mobileOpen]);

  return (
    <header className={cn("sticky top-0 z-30 border-b border-line bg-bg", className)}>
      <Container width={width} className="flex h-gnb items-center gap-2 sm:gap-4 sm:px-8">
        <Link href={ROUTES.home} className="mr-1 shrink-0 text-lg font-bold text-fg">
          Komit
        </Link>

        {/* 넓은 화면: 메뉴를 그대로 펼칩니다 */}
        <nav aria-label="주요 메뉴" className="hidden min-w-0 flex-1 items-center gap-1 md:flex">
          {items.map((item) => (
            <NavPill key={item.href} as={Link} href={item.href} active={isActive(pathname, item.href)}>
              {item.label}
            </NavPill>
          ))}
        </nav>

        <div className="ml-auto flex shrink-0 items-center gap-1 sm:gap-3">
          {user ? (
            <>
              <Link
                href={notificationsHref}
                aria-label={user.unreadCount ? `알림 ${user.unreadCount}건 안 읽음` : "알림"}
                className="relative inline-flex size-9 shrink-0 items-center justify-center rounded-md text-fg-secondary transition-colors hover:bg-surface hover:text-fg"
              >
                <BellIcon />
                {user.unreadCount !== undefined && user.unreadCount > 0 && (
                  <CountBadge count={user.unreadCount} className="absolute top-0.5 right-0.5" />
                )}
              </Link>

              <div className="relative shrink-0">
                <button
                  type="button"
                  aria-haspopup="menu"
                  aria-expanded={accountOpen}
                  aria-label={`${user.name} 메뉴`}
                  onClick={() => setAccountOpen((v) => !v)}
                  className="flex items-center rounded-full"
                >
                  <Avatar name={user.name} src={user.avatarSrc} size="md" />
                </button>

                <Popover
                  open={accountOpen}
                  onClose={() => setAccountOpen(false)}
                  align="end"
                  className="max-w-[calc(100vw-2rem)] p-2"
                >
                  <p className="px-3 py-2 text-sm font-semibold text-fg">{user.name}</p>
                  <MenuDivider />
                  {menuItems.map((item) => (
                    <MenuItem key={item.href} as={Link} href={item.href} onClick={() => setAccountOpen(false)}>
                      {item.label}
                    </MenuItem>
                  ))}
                  {onLogout && (
                    <>
                      <MenuDivider />
                      <MenuItem
                        onClick={() => {
                          setAccountOpen(false);
                          onLogout();
                        }}
                      >
                        로그아웃
                      </MenuItem>
                    </>
                  )}
                </Popover>
              </div>
            </>
          ) : (
            <Link
              href={loginHref}
              className="hidden shrink-0 rounded-md px-2 py-1 text-sm font-medium whitespace-nowrap text-fg-secondary transition-colors hover:text-fg md:inline-flex"
            >
              로그인
            </Link>
          )}

          {/* 좁은 화면: 메뉴를 햄버거로 접습니다 */}
          <button
            type="button"
            aria-label="메뉴"
            aria-expanded={mobileOpen}
            aria-controls="gnb-mobile-menu"
            onClick={() => setMobileOpen((v) => !v)}
            className="inline-flex size-9 shrink-0 items-center justify-center rounded-md text-fg-secondary transition-colors hover:bg-surface hover:text-fg md:hidden"
          >
            {mobileOpen ? <CloseIcon /> : <MenuIcon />}
          </button>
        </div>
      </Container>

      {mobileOpen && (
        <>
          <div
            className="fixed inset-0 top-gnb z-10 bg-ink/20 md:hidden"
            onClick={() => setMobileOpen(false)}
            aria-hidden
          />
          <div id="gnb-mobile-menu" className="relative z-20 border-t border-line-subtle bg-bg md:hidden">
            <Container width={width} className="flex flex-col gap-1 py-3">
              <nav aria-label="주요 메뉴" className="flex flex-col gap-1">
                {items.map((item) => (
                  <NavPill
                    key={item.href}
                    as={Link}
                    href={item.href}
                    active={isActive(pathname, item.href)}
                    className="h-10 w-full"
                    onClick={() => setMobileOpen(false)}
                  >
                    {item.label}
                  </NavPill>
                ))}
              </nav>

              {!user && (
                <>
                  <MenuDivider />
                  <NavPill
                    as={Link}
                    href={loginHref}
                    className="h-10 w-full"
                    onClick={() => setMobileOpen(false)}
                  >
                    로그인
                  </NavPill>
                </>
              )}
            </Container>
          </div>
        </>
      )}
    </header>
  );
}

function BellIcon() {
  return (
    <svg viewBox="0 0 20 20" className="size-5" fill="none" stroke="currentColor" strokeWidth="1.6" aria-hidden>
      <path
        d="M10 3a4.5 4.5 0 0 0-4.5 4.5c0 3-1 4-1.5 4.5h12c-.5-.5-1.5-1.5-1.5-4.5A4.5 4.5 0 0 0 10 3Z"
        strokeLinejoin="round"
      />
      <path d="M8.25 15a1.75 1.75 0 0 0 3.5 0" strokeLinecap="round" />
    </svg>
  );
}

function MenuIcon() {
  return (
    <svg viewBox="0 0 20 20" className="size-5" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" aria-hidden>
      <path d="M3.5 6h13M3.5 10h13M3.5 14h13" />
    </svg>
  );
}

function CloseIcon() {
  return (
    <svg viewBox="0 0 20 20" className="size-5" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" aria-hidden>
      <path d="M5 5l10 10M15 5L5 15" />
    </svg>
  );
}
