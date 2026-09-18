import Link from "next/link";
import { Badge, Mono } from "@/components/common";
import { Container } from "@/components/layouts";
import { formatAdminAsOf } from "@/lib/admin/format";
import { ROUTES } from "@/lib/routes";
import type { AdminIdentity } from "@/types/admin";
import { AdminAccountMenu } from "./AdminAccountMenu";

export interface AdminShellProps {
  /** 숫자를 센 시점. 상단바 오른쪽의 「N월 N일 HH:MM 기준」 입니다. */
  asOf: string;
  /** 지금 들어와 있는 운영자. 아바타 메뉴가 누구인지 적습니다. */
  admin: AdminIdentity;
  children: React.ReactNode;
}

/**
 * 운영 콘솔의 골격. 56px 상단바 + 840px 본문입니다.
 *
 * **일반 화면의 `AppShell` 을 쓰지 않습니다.** 운영 콘솔에는 사용자 GNB 메뉴도
 * 푸터도 알림 벨도 없고, 헤더가 읽는 인증 스토어는 사용자 것이라 운영자와 맞지
 * 않습니다(`docs/FRONTEND_COMPONENTS.md` 2장 — 골격이 다른 화면은 별도 레이아웃).
 *
 * 본문 폭 840 은 관리자 화면의 값입니다. `Container width="wide"` 가 그 값입니다.
 */
export function AdminShell({ asOf, admin, children }: Readonly<AdminShellProps>) {
  return (
    <div className="min-h-dvh bg-bg text-fg">
      <header className="h-gnb border-b border-line bg-bg">
        <div className="flex h-full w-full items-center gap-3 px-6 lg:px-8">
          <Link href={ROUTES.adminHome} className="text-[15px] font-bold tracking-tight text-fg">
            Komit
          </Link>
          <Badge tone="neutral" shape="pill" size="sm">
            운영 콘솔
          </Badge>
          <div className="min-w-8 flex-1" />
          <Mono className="text-xs text-fg-subtle">{formatAdminAsOf(asOf)}</Mono>
          <AdminAccountMenu admin={admin} />
        </div>
      </header>
      <Container width="wide" as="main" className="py-8 pb-24 sm:py-10 sm:pb-24">
        {children}
      </Container>
    </div>
  );
}
