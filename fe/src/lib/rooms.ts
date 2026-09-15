/**
 * 방 목록을 사이드바 모양으로 다듬는 순수 함수.
 * 화면과 떼어 두고 여기서만 규칙을 정합니다.
 */
import type { RoomSummary, RoomType } from "@/types/room";
import type { Me } from "@/types/user";

/** 사이드바 상단 탭. */
export type RequestSide = "SENT" | "RECEIVED";

/** 한 프로젝트에 달린 방 묶음. */
export interface ProjectGroup {
  projectId: number;
  projectName: string;
  /** 이 프로젝트에서 상대가 누구인지. 맡긴 의뢰는 여러 개발자가 섞일 수 있습니다. */
  rooms: RoomSummary[];
}

/** 방 용도 표기. */
export const ROOM_TYPE_LABEL: Record<RoomType, string> = {
  INQUIRY: "상담",
  CONSULTING: "컨설팅",
  DEVELOPMENT: "개발",
};

/**
 * 내가 이 방에 개발자로 들어갔는지.
 * 목 명세대로 developerId 하나만 견줍니다 — 같으면 '맡은 의뢰'입니다.
 */
export function isReceived(room: RoomSummary, me: Me): boolean {
  return me.developerId !== null && me.developerId === room.developerId;
}

/** 탭에 해당하는 방만 거릅니다. */
export function filterBySide(rooms: RoomSummary[], me: Me, side: RequestSide): RoomSummary[] {
  return rooms.filter((room) => (isReceived(room, me) ? side === "RECEIVED" : side === "SENT"));
}

/**
 * 프로젝트별로 묶습니다.
 *
 * 방 목록은 이미 마지막으로 움직인 순서로 오므로 그 순서를 그대로 씁니다.
 * 그룹 순서는 '그 안에서 가장 먼저 나온 방'을 따릅니다 — 목 명세가 정한 방식입니다.
 * 여기서 다시 정렬하면 서버가 정한 순서를 덮어쓰게 됩니다.
 */
export function groupByProject(rooms: RoomSummary[]): ProjectGroup[] {
  const groups = new Map<number, ProjectGroup>();

  for (const room of rooms) {
    const group = groups.get(room.projectId);
    if (group) {
      group.rooms.push(room);
      continue;
    }
    groups.set(room.projectId, {
      projectId: room.projectId,
      projectName: room.projectName,
      rooms: [room],
    });
  }

  return [...groups.values()];
}
