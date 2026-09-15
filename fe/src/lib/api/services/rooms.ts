import "server-only";

import { backendApi } from "../server";
import { BACKEND } from "../endpoints";
import type {
  RoomDetail,
  RoomListParams,
  RoomListResponse,
  RoomSummary,
} from "@/types/room";

/**
 * 방 목록. 내가 들어가 있는 방이 전부 옵니다(페이지 정보 없음).
 * 마지막으로 움직인 방이 먼저 옵니다.
 */
export async function getRooms(params: RoomListParams = {}): Promise<RoomSummary[]> {
  const { data } = await backendApi.get<RoomListResponse>(BACKEND.ROOMS, { params });
  return data.content;
}

/** 방 상세. 없거나 내가 들어가 있지 않은 방이면 404(ROOM_NOT_FOUND). */
export async function getRoom(roomId: number): Promise<RoomDetail> {
  const { data } = await backendApi.get<RoomDetail>(BACKEND.ROOM(roomId));
  return data;
}
