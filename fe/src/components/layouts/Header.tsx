"use client";

import * as React from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { cn } from "@/lib/cn";
import { Avatar, CountBadge, NavPill, SegmentedTabs } from "@/components/common";
import { MenuDivider, MenuItem, Popover } from "@/components/feedback";
import { Container, type ContainerWidth } from "./Container";

/**
 * Header — 56px 높이의 GNB.
 *  - 비로그인: 로고 + 메뉴 + 로그인 버튼
 *  - 로그인: 로고 + 메뉴 + 역할 전환(의뢰인|개발자) + 알림 + 아바타 메뉴
 * 로그인 상태는 아직 없으므로 `user` 를 넘겨 주는 쪽에서 결정합니다.
 */
export type UserRole = "client" | "developer";

export interface HeaderNavItem {
  label: string;
  href: string;
}

export interface HeaderUser {
  name: string;
  avatarSrc?: string;
  role?: UserRole;
  /** 안 읽은 알림 수 */
  unreadCount?: number;
}

export interface HeaderProps {
  nav?: HeaderNavItem[];
  user?: HeaderUser;
  /** 역할 전환. 넘기지 않으면 헤더 안에서만 상태가 바뀝니다. */
  onRoleChange?: (role: UserRole) => void;
  loginHref?: string;
  notificationsHref?: string;
  menuItems?: HeaderNavItem[];
  onLogout?: () => void;
  width?: ContainerWidth;
  className?: string;
}

/** 비로그인 화면(랜딩)의 메뉴 */
export const PUBLIC_NAV: HeaderNavItem[] = [
  { label: "이용 방법", href: "/how-to" },
  { label: "의뢰", href: "/requests" },
  { label: "개발자 찾기", href: "/developers" },
  { label: "사전점검", href: "/checkup" },
];

/** 로그인 화면의 메뉴 */
export const APP_NAV: HeaderNavItem[] = [
  { label: "의뢰", href: "/requests" },
  { label: "개발자 찾기", href: "/developers" },
  { label: "사전점검", href: "/checkup" },
  { label: "정산", href: "/settlements" },
];

const DEFAULT_MENU_ITEMS: HeaderNavItem[] = [
  { label: "마이페이지", href: "/mypage" },
  { label: "내 프로필", href: "/mypage/profile" },
  { label: "내 일정", href: "/mypage/schedule" },
  { label: "설정", href: "/mypage/settings" },
];

const ROLE_TABS = [
  { value: "client" as const, label: "의뢰인" },
  { value: "developer" as const, label: "개발자" },
];

/** /requests 와 /requests/123 은 같은 메뉴로 봅니다. */
function isActive(pathname: string | null, href: string) {
  if (!pathname) return false;
  return pathname === href || pathname.startsWith(`${href}/`);
}

export function Header({
  nav,
  user,
  onRoleChange,
  loginHref = "/login",
  notificationsHref = "/notifications",
  menuItems = DEFAULT_MENU_ITEMS,
  onLogout,
  width = "full",
  className,
}: HeaderProps) {
  const pathname = usePathname();
  const items = nav ?? (user ? APP_NAV : PUBLIC_NAV);

  const [role, setRole] = React.useState<UserRole>(user?.role ?? "client");
  const [menuOpen, setMenuOpen] = React.useState(false);

  const changeRole = (next: UserRole) => {
    setRole(next);
    onRoleChange?.(next);
  };

  return (
    <header className={cn("sticky top-0 z-30 border-b border-line bg-bg", className)}>
      <Container width={width} className="flex h-gnb items-center gap-4 sm:px-8">
        <Link href="/" className="mr-2 text-lg font-bold text-fg">
          Komit
        </Link>

        <nav aria-label="주요 메뉴" className="flex min-w-0 flex-1 items-center gap-1 overflow-x-auto">
          {items.map((item) => (
            <NavPill key={item.href} as={Link} href={item.href} active={isActive(pathname, item.href)}>
              {item.label}
            </NavPill>
          ))}
        </nav>

        {user ? (
          <div className="flex shrink-0 items-center gap-3">
            <SegmentedTabs
              aria-label="역할 전환"
              size="sm"
              items={ROLE_TABS}
              value={role}
              onChange={changeRole}
              className="hidden sm:inline-flex"
            />

            <Link
              href={notificationsHref}
              aria-label={
                user.unreadCount ? `알림 ${user.unreadCount}건 안 읽음` : "알림"
              }
              className="relative inline-flex size-8 items-center justify-center rounded-md text-fg-secondary transition-colors hover:bg-surface hover:text-fg"
            >
              <BellIcon />
              {user.unreadCount !== undefined && user.unreadCount > 0 && (
                <CountBadge count={user.unreadCount} className="absolute -top-0.5 -right-1" />
              )}
            </Link>

            <div className="relative">
              <button
                type="button"
                aria-haspopup="menu"
                aria-expanded={menuOpen}
                aria-label={`${user.name} 메뉴`}
                onClick={() => setMenuOpen((v) => !v)}
                className="flex items-center rounded-full"
              >
                <Avatar name={user.name} src={user.avatarSrc} size="md" />
              </button>

              <Popover open={menuOpen} onClose={() => setMenuOpen(false)} align="end" className="p-2">
                <p className="px-3 py-2 text-sm font-semibold text-fg">{user.name}</p>
                <MenuDivider />
                {menuItems.map((item) => (
                  <MenuItem key={item.href} as={Link} href={item.href} onClick={() => setMenuOpen(false)}>
                    {item.label}
                  </MenuItem>
                ))}
                {onLogout && (
                  <>
                    <MenuDivider />
                    <MenuItem
                      onClick={() => {
                        setMenuOpen(false);
                        onLogout();
                      }}
                    >
                      로그아웃
                    </MenuItem>
                  </>
                )}
              </Popover>
            </div>
          </div>
        ) : (
          <Link
            href={loginHref}
            className="shrink-0 rounded-md px-2 py-1 text-sm font-medium text-fg-secondary transition-colors hover:text-fg"
          >
            로그인
          </Link>
        )}
      </Container>
    </header>
  );
}

function BellIcon() {
  return (
    <svg viewBox="0 0 20 20" className="size-5" fill="none" stroke="currentColor" strokeWidth="1.6" aria-hidden>
      <path d="M10 3a4.5 4.5 0 0 0-4.5 4.5c0 3-1 4-1.5 4.5h12c-.5-.5-1.5-1.5-1.5-4.5A4.5 4.5 0 0 0 10 3Z" strokeLinejoin="round" />
      <path d="M8.25 15a1.75 1.75 0 0 0 3.5 0" strokeLinecap="round" />
    </svg>
  );
}
