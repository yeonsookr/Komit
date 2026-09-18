"use client";

import { usePathname, useRouter } from "next/navigation";
import { SegmentedTabs } from "@/components/common";
import { ROUTES } from "@/lib/routes";
import type { RoomType } from "@/types/room";

/** 방 안의 탭. 경로의 조각이 곧 탭 값입니다. */
export type RoomTab = "chat" | "contract" | "progress" | "check" | "review" | "summary";

const LABEL: Record<RoomTab, string> = {
  chat: "채팅",
  contract: "계약서",
  progress: "진행",
  check: "확인",
  review: "리뷰",
  summary: "요약",
};

/**
 * 방 종류마다 탭이 다릅니다.
 *
 * 컨설팅은 한 번 만나고 끝이라 「진행·확인·리뷰」가 없고 대신 세션 요약이 있습니다.
 * 개발은 작업을 여러 번 주고받아 그 반대입니다. 상담은 아직 갈래를 정하기 전이라
 * 계약서도 없어 **탭 자체가 없습니다** — 방 머리에서 갈래를 고르면 그때 컨설팅 방이나
 * 개발 방이 열립니다.
 *
 * **세션은 탭이 아닙니다.** 잡힌 시각에만 들어가는 것이라 탭으로 두면 그 밖의 때에
 * 갈 곳이 없습니다. 채팅 위 배너에서 들어갑니다 (`4_의뢰인_입장전`).
 *
 * 와이어프레임 `3_의뢰인_상담방_대화`·`4_의뢰인_대화`·`5_의뢰인_대화` 가 각각 그 모습입니다.
 */
const TABS_BY_ROOM_TYPE: Record<RoomType, RoomTab[]> = {
  INQUIRY: [],
  CONSULTING: ["chat", "contract", "summary"],
  DEVELOPMENT: ["chat", "contract", "progress", "check", "review"],
};

/**
 * 지금 경로에서 탭을 읽습니다.
 * `/rooms/93` 은 채팅이고, `/rooms/93/contract/edit` 처럼 더 깊은 곳도 그 탭입니다.
 */
export function toRoomTab(pathname: string): RoomTab {
  const segment = /^\/rooms\/\d+\/([a-z-]+)/.exec(pathname)?.[1];
  // 예치는 탭이 아니라 계약서에서 서명하고 넘어오는 화면이라 계약서에 머뭅니다.
  if (segment === "deposit") return "contract";
  // 문제 신고는 진행 화면에서 들어오는 보조 화면이라 진행 탭에 머뭅니다.
  if (segment === "report") return "progress";
  return segment !== undefined && segment in LABEL ? (segment as RoomTab) : "chat";
}

/** 탭 값에서 갈 곳. 채팅은 방의 기본 화면이라 하위 경로가 없습니다. */
const TAB_ROUTE: Record<RoomTab, (roomId: number) => string> = {
  chat: ROUTES.room,
  contract: ROUTES.roomContract,
  progress: ROUTES.roomProgress,
  check: ROUTES.roomCheck,
  review: ROUTES.roomReview,
  summary: ROUTES.roomSummary,
};

export interface RoomTabsProps {
  roomId: number;
  /** 방 종류. 어떤 탭을 놓을지가 여기서 갈립니다. */
  roomType: RoomType;
}

/**
 * RoomTabs — 방 머리의 탭바.
 *
 * 탭마다 다른 화면이라 상태가 아니라 경로로 옮깁니다.
 *
 * 와이어프레임의 `채팅 6` · `확인 3` 같은 숫자는 아직 넣지 않습니다. 안 읽은 수가
 * 방 상세 응답에 없어 채울 값이 없습니다.
 */
export function RoomTabs({ roomId, roomType }: Readonly<RoomTabsProps>) {
  const router = useRouter();
  const pathname = usePathname();
  const tabs = TABS_BY_ROOM_TYPE[roomType];

  if (tabs.length === 0) return null;

  return (
    <SegmentedTabs
      aria-label="방 메뉴"
      value={toRoomTab(pathname)}
      onChange={(tab) => router.push(TAB_ROUTE[tab](roomId))}
      items={tabs.map((tab) => ({ value: tab, label: LABEL[tab] }))}
    />
  );
}
