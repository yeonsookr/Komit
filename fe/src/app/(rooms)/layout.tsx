import { redirect } from "next/navigation";
import { AppShell } from "@/components/layouts";
import { RoomSidebar, type RoomPreview } from "@/components/domains/room";
import { getLastMessages, getMe, getRooms } from "@/lib/api/services";
import { formatRelativeTime } from "@/lib/format";
import { ApiError } from "@/types/api";
import { ROUTES } from "@/lib/routes";
import type { Me } from "@/types/user";
import type { RoomSummary } from "@/types/room";

/**
 * 목록에 붙일 마지막 대화 요약.
 * 상대 시각은 서버에서 한 번 계산해 문자열로 내려보냅니다 — 브라우저가 다시
 * 계산하면 hydration 이 어긋납니다.
 */
async function buildPreviews(rooms: RoomSummary[]): Promise<Record<string, RoomPreview>> {
  const lastMessages = await getLastMessages(rooms.map((room) => room.chatRefId));
  const now = new Date();

  return Object.fromEntries(
    Object.entries(lastMessages).map(([chatRefId, message]) => [
      chatRefId,
      message
        ? { body: message.body, time: formatRelativeTime(message.sentAt, now) }
        : {},
    ]),
  );
}

/**
 * 방 목록과 내 정보. 로그인하지 않았으면 로그인 화면으로 보냅니다.
 *
 * 서버 컴포넌트는 데이터를 읽는 것이 곧 렌더라, 여기서 던진 예외는 렌더 실패가
 * 되어 500 으로 나갑니다. 로그인하지 않은 것은 오류가 아니므로 401 만 골라
 * `redirect` 로 바꿉니다. `redirect` 도 예외를 던지지만 Next 가 알아보는
 * 신호여서 리다이렉트 응답이 됩니다 — `try` 안에서 부르면 이 `catch` 가 도로
 * 삼키므로 반드시 `catch` 에서 부릅니다.
 *
 * 401 이 아닌 오류(목 서버 중단 등)는 그대로 올려보내 오류 화면이 뜨게 둡니다.
 */
async function loadSession(): Promise<[RoomSummary[], Me]> {
  try {
    return await Promise.all([getRooms(), getMe()]);
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) redirect(ROUTES.login);
    throw error;
  }
}

/**
 * 의뢰 화면 레이아웃 — GNB + 좌측 스레드 목록(320px) + 본문.
 *
 * (site) 그룹과 골격이 달라 따로 둡니다. 방 화면은 대화에 집중하는 화면이라
 * 푸터를 감춥니다.
 *
 * 목록은 여기서 한 번만 읽습니다. 방을 옮겨도 이 레이아웃은 다시 그려지지 않아
 * 사이드바가 깜빡이지 않습니다.
 */
export default async function RoomsLayout({ children }: { children: React.ReactNode }) {
  const [rooms, me] = await loadSession();
  const previews = await buildPreviews(rooms);

  return (
    <AppShell hideFooter user={{ name: me.name, unreadCount: me.unreadNotificationCount }}>
      <div className="flex min-h-[calc(100dvh-var(--k-gnb-height))] flex-col lg:flex-row">
        <aside className="shrink-0 border-b border-line px-4 lg:w-sidebar lg:border-r lg:border-b-0 lg:px-5">
          <RoomSidebar rooms={rooms} me={me} previews={previews} />
        </aside>

        <div className="min-w-0 flex-1">{children}</div>
      </div>
    </AppShell>
  );
}
