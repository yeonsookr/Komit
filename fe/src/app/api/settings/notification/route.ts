import { NextResponse } from "next/server";
import { updateNotificationSetting } from "@/lib/api/services";
import { toErrorResponse } from "@/lib/api/route";
import { authorized } from "@/lib/auth/session";

const INVALID_REQUEST = { code: "INVALID_REQUEST", message: "요청 값을 확인해 주세요." };

/** 보낸 값이 참·거짓인지 봅니다. 아니면 서버를 부르지 않습니다. */
function parseEnabled(body: unknown): boolean | null {
  if (typeof body !== "object" || body === null) return null;

  const enabled = (body as Record<string, unknown>).notificationEnabled;
  return typeof enabled === "boolean" ? enabled : null;
}

/**
 * PATCH /api/settings/notification — 알림톡 켜고 끄기. 마이페이지 알림 칸이 부릅니다.
 *
 * 요청을 그대로 흘려보내지 않고 참·거짓 하나만 통과시킵니다(컨벤션 5장).
 */
export async function PATCH(request: Request) {
  try {
    await authorized();
  } catch (error) {
    return toErrorResponse(error);
  }

  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return NextResponse.json(INVALID_REQUEST, { status: 422 });
  }

  const notificationEnabled = parseEnabled(body);
  if (notificationEnabled === null) {
    return NextResponse.json(INVALID_REQUEST, { status: 422 });
  }

  try {
    return NextResponse.json(await updateNotificationSetting({ notificationEnabled }));
  } catch (error) {
    return toErrorResponse(error);
  }
}
