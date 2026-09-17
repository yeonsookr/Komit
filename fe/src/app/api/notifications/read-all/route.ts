import { NextResponse } from "next/server";
import { readAllNotifications } from "@/lib/api/services";
import { toErrorResponse } from "@/lib/api/route";
import { authorized } from "@/lib/auth/session";

/** POST /api/notifications/read-all — 내 알림을 모두 읽음으로 바꿉니다. */
export async function POST() {
  try {
    await authorized();
    return NextResponse.json(await readAllNotifications());
  } catch (error) {
    return toErrorResponse(error);
  }
}
