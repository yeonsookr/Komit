"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { AppShell, type AppShellProps, type HeaderUser } from "@/components/layouts";
import { ROUTES } from "@/lib/routes";
import { useAuthStore } from "@/stores/auth";
import type { Me } from "@/types/user";

/**
 * 내 정보를 헤더가 아는 모양으로 바꿉니다.
 * 프로필 사진은 파일 식별자만 오고 주소를 주는 API 가 아직 없어 이름 첫 글자를 씁니다.
 */
function toHeaderUser(me: Me): HeaderUser {
  return { name: me.name, unreadCount: me.unreadNotificationCount };
}

export type SessionShellProps = Omit<AppShellProps, "user" | "headerProps"> & {
  headerProps?: Omit<NonNullable<AppShellProps["headerProps"]>, "onLogout" | "pending">;
};

/**
 * SessionShell — 인증 스토어를 읽어 헤더를 채우는 AppShell.
 *
 * 로그인 여부를 서버에서 읽으면 그 레이아웃 아래 화면이 모두 동적 렌더링이 되어
 * 랜딩의 정적 생성이 풀립니다(컨벤션 7장). 그래서 헤더만 클라이언트에서 채우고
 * 본문(children)은 서버 컴포넌트 그대로 지나갑니다.
 */
export function SessionShell({ children, headerProps, ...props }: Readonly<SessionShellProps>) {
  const router = useRouter();
  const status = useAuthStore((s) => s.status);
  const me = useAuthStore((s) => s.user);
  const logout = useAuthStore((s) => s.logout);

  const handleLogout = React.useCallback(async () => {
    await logout();
    // 로그인이 필요한 화면에 서 있었을 수 있어 랜딩으로 보냅니다.
    router.replace(ROUTES.home);
  }, [logout, router]);

  return (
    <AppShell
      user={me ? toHeaderUser(me) : undefined}
      headerProps={{ ...headerProps, pending: status === "loading", onLogout: handleLogout }}
      {...props}
    >
      {children}
    </AppShell>
  );
}
