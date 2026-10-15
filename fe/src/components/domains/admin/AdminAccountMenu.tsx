"use client";

import { useRouter } from "next/navigation";
import * as React from "react";
import { Avatar } from "@/components/common";
import { MenuDivider, MenuItem, Popover, useToast } from "@/components/feedback";
import { requestAdminLogout } from "@/lib/admin/requests";
import { ROUTES } from "@/lib/routes";
import type { AdminIdentity } from "@/types/admin";
import { ApiError } from "@/types/api";

export interface AdminAccountMenuProps {
  admin: AdminIdentity;
}

/**
 * 상단바 오른쪽의 운영자 아바타와 그 메뉴.
 *
 * 로그아웃을 상단바에 글자로 내놓지 않고 아바타 뒤에 둡니다. 와이어프레임의
 * 상단바는 `Komit · 운영 콘솔 · 기준 시각 · 아바타` 넷이고, 사용자 화면도 로그아웃을
 * 아바타 메뉴 안에 두기 때문입니다(`8_아바타메뉴`). 자주 누를 것이 아니라 한 번
 * 누르고 나가는 동작이라 바깥에 상시 노출할 이유도 없습니다.
 *
 * **사용자 로그아웃과 다른 요청을 부릅니다.** 운영자 쿠키만 지워지고, 같은 브라우저에
 * 로그인해 있을 수 있는 사용자 세션은 그대로 둡니다.
 */
export function AdminAccountMenu({ admin }: Readonly<AdminAccountMenuProps>) {
  const router = useRouter();
  const showToast = useToast();
  const [open, setOpen] = React.useState(false);
  const [leaving, setLeaving] = React.useState(false);

  const logout = async () => {
    if (leaving) return;
    setLeaving(true);
    setOpen(false);

    try {
      await requestAdminLogout();
      // 뒤로 가기로 운영 화면에 돌아가면 쿠키가 없어 어차피 로그인으로 튕깁니다.
      // 그 왕복은 사용자 눈에 화면이 깜빡이는 것으로만 보이므로 replace 로 보냅니다.
      router.replace(ROUTES.adminLogin);
      router.refresh();
    } catch (error) {
      showToast({
        tone: "danger",
        title: error instanceof ApiError ? error.message : "로그아웃하지 못했습니다.",
      });
      setLeaving(false);
    }
  };

  return (
    <div className="relative shrink-0">
      <button
        type="button"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label={`${admin.loginId} 메뉴`}
        onClick={() => setOpen((current) => !current)}
        className="flex items-center rounded-full focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
      >
        <Avatar name="운영" size="sm" />
      </button>

      <Popover open={open} onClose={() => setOpen(false)} align="end" className="min-w-[200px] p-2">
        <p className="px-3 py-2 text-sm font-semibold text-fg">{admin.loginId}</p>
        <MenuDivider />
        <MenuItem onClick={logout} disabled={leaving}>
          로그아웃
        </MenuItem>
      </Popover>
    </div>
  );
}
