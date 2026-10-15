import "server-only";

import { authenticatedBackendApi } from "../server";
import { BACKEND } from "../endpoints";
import type {
  ConvertRoomRequest,
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
  const { data } = await authenticatedBackendApi().get<RoomListResponse>(BACKEND.ROOMS, { params });
  return data.content;
}

/** 방 상세. 없거나 내가 들어가 있지 않은 방이면 404(ROOM_NOT_FOUND). */
export async function getRoom(roomId: number): Promise<RoomDetail> {
  const { data } = await authenticatedBackendApi().get<RoomDetail>(BACKEND.ROOM(roomId));
  return data;
}

/**
 * 상담에서 갈래 정하기.
 *
 * 무료 상담을 마치고 컨설팅과 개발 중 하나로 이어갑니다. 상담방은 닫히고 고른
 * 갈래의 방이 새로 열리며, 닫힌 상담방도 남아 나눈 이야기를 계속 볼 수 있습니다.
 *
 * **갈래는 의뢰인이 정합니다.** 개발자가 부르면 403 입니다.
 * 이미 갈래가 정해진 방이면 409(ROOM_ALREADY_CONVERTED).
 */
export async function convertRoom(
  roomId: number,
  body: ConvertRoomRequest,
): Promise<RoomDetail> {
  const { data } = await authenticatedBackendApi().post<RoomDetail>(BACKEND.ROOM_CONVERSION(roomId), body);
  return data;
}
