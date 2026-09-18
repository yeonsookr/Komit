import { NextResponse } from "next/server";
import { readNotification } from "@/lib/api/services";
import { parseNumericParam, toErrorResponse } from "@/lib/api/route";
import { authorized } from "@/lib/auth/session";

const NOT_FOUND = { code: "NOTIFICATION_NOT_FOUND", message: "알림을 찾을 수 없습니다." };

/** PATCH /api/notifications/{notificationId}/read — 알림 하나를 읽음으로 바꿉니다. */
export async function PATCH(
  _request: Request,
  { params }: { params: Promise<{ notificationId: string }> },
) {
  const { notificationId } = await params;
  const id = parseNumericParam(notificationId);
  if (id === null) return NextResponse.json(NOT_FOUND, { status: 404 });

  try {
    await authorized();
    return NextResponse.json(await readNotification(id));
  } catch (error) {
    return toErrorResponse(error);
  }
}
