"use client";

import { usePathname, useRouter } from "next/navigation";
import { SegmentedTabs } from "@/components/common";
import { ROUTES } from "@/lib/routes";

/** 방 안의 탭. 경로의 조각이 곧 탭 값입니다. */
export type RoomTab = "chat" | "contract" | "progress" | "check" | "review";

const TABS: { value: RoomTab; label: string }[] = [
  { value: "chat", label: "채팅" },
  { value: "contract", label: "계약서" },
  { value: "progress", label: "진행" },
  { value: "check", label: "확인" },
  { value: "review", label: "리뷰" },
];

/**
 * 지금 경로에서 탭을 읽습니다.
 * `/rooms/93` 은 채팅이고, `/rooms/93/contract/edit` 처럼 더 깊은 곳도 그 탭입니다.
 */
export function toRoomTab(pathname: string): RoomTab {
  const segment = /^\/rooms\/\d+\/([a-z-]+)/.exec(pathname)?.[1];
  // 예치는 탭이 아니라 계약서에서 서명하고 넘어오는 화면이라 계약서에 머뭅니다.
  if (segment === "deposit") return "contract";
  return TABS.find((tab) => tab.value === segment)?.value ?? "chat";
}

/** 탭 값에서 갈 곳. 채팅은 방의 기본 화면이라 하위 경로가 없습니다. */
const TAB_ROUTE: Record<RoomTab, (roomId: number) => string> = {
  chat: ROUTES.room,
  contract: ROUTES.roomContract,
  progress: ROUTES.roomProgress,
  check: ROUTES.roomCheck,
  review: ROUTES.roomReview,
};

export interface RoomTabsProps {
  roomId: number;
}

/**
 * RoomTabs — 방 머리의 탭바.
 *
 * 탭마다 다른 화면이라 상태가 아니라 경로로 옮깁니다.
 *
 * 와이어프레임의 `채팅 6` · `확인 3` 같은 숫자는 아직 넣지 않습니다. 안 읽은 수가
 * 방 상세 응답에 없어 채울 값이 없습니다.
 */
export function RoomTabs({ roomId }: Readonly<RoomTabsProps>) {
  const router = useRouter();
  const pathname = usePathname();

  return (
    <SegmentedTabs
      aria-label="방 메뉴"
      value={toRoomTab(pathname)}
      onChange={(tab) => router.push(TAB_ROUTE[tab](roomId))}
      items={TABS}
    />
  );
}
