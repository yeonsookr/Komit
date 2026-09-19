import { redirect } from "next/navigation";
import { AppShell } from "@/components/layouts";
import { getMe } from "@/lib/api/services";
import { ROUTES } from "@/lib/routes";
import { ApiError } from "@/types/api";

/**
 * (session) 레이아웃 — 화상 세션처럼 화면을 가득 쓰는 골격.
 *
 * GNB만 남기고 푸터와 본문 폭 제한을 두지 않습니다. 세션은 상대의 화면을 함께 보는
 * 자리라 가운데 720px 안에 가두면 볼 것이 보이지 않습니다.
 *
 * 방 사이드바도 두지 않습니다. 세션 중에 다른 방으로 옮겨 갈 일이 없고, 나가는 길은
 * 화면 머리의 「컨설팅 방」과 「세션 종료」 둘뿐이어야 실수로 빠져나가지 않습니다.
 */
export default async function SessionLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  let me;
  try {
    me = await getMe();
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) redirect(ROUTES.login);
    throw error;
  }

  return (
    <AppShell user={{ name: me.name, unreadCount: me.unreadNotificationCount }} hideFooter>
      {children}
    </AppShell>
  );
}
