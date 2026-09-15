import { AppShell } from "@/components/layouts";
import { RoomSidebar, type RoomPreview } from "@/components/domains/room";
import { getLastMessages, getMe, getRooms } from "@/lib/api/services";
import { formatRelativeTime } from "@/lib/format";
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
 * 의뢰 화면 레이아웃 — GNB + 좌측 스레드 목록(320px) + 본문.
 *
 * (site) 그룹과 골격이 달라 따로 둡니다. 방 화면은 대화에 집중하는 화면이라
 * 푸터를 감춥니다.
 *
 * 목록은 여기서 한 번만 읽습니다. 방을 옮겨도 이 레이아웃은 다시 그려지지 않아
 * 사이드바가 깜빡이지 않습니다.
 */
export default async function RoomsLayout({ children }: { children: React.ReactNode }) {
  const [rooms, me] = await Promise.all([getRooms(), getMe()]);
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
