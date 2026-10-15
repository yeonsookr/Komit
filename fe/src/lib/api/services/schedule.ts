import "server-only";

import { BACKEND } from "../endpoints";
import { authenticatedBackendApi } from "../server";
import type { MySchedule } from "@/types/schedule";

/** 의뢰인과 개발자로 참여한 컨설팅 일정을 함께 읽습니다. */
export async function getMySchedule(): Promise<MySchedule> {
  const { data } = await authenticatedBackendApi().get<MySchedule>(
    BACKEND.MY_SESSIONS,
  );
  return data;
}
