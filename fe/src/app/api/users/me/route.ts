import { NextResponse } from "next/server";
import { getMe, updateMyName } from "@/lib/api/services";
import { toErrorResponse } from "@/lib/api/route";
import { authorized } from "@/lib/auth/session";

/** 목 명세가 정한 길이. 더 길면 서버도 422 를 줍니다. */
const MAX_NAME_LENGTH = 20;

const INVALID_REQUEST = { code: "INVALID_REQUEST", message: "요청 값을 확인해 주세요." };

/**
 * GET /api/users/me — 헤더와 의뢰 구분에 쓰는 내 정보.
 * 토큰 쿠키가 없으면 서버를 부르지 않고 401 입니다. 헤더의 로그인 상태가 이 응답으로
 * 정해지므로, 목이 빈 요청에 200 을 주더라도 비로그인은 비로그인으로 보여야 합니다.
 */
export async function GET() {
  try {
    await authorized();
    return NextResponse.json(await getMe());
  } catch (error) {
    return toErrorResponse(error);
  }
}

/** 보낸 값이 쓸 만한 이름인지 봅니다. 아니면 서버를 부르지 않습니다. */
function parseName(body: unknown): string | null {
  if (typeof body !== "object" || body === null) return null;

  const name = (body as Record<string, unknown>).name;
  if (typeof name !== "string") return null;

  const trimmed = name.trim();
  return trimmed.length > 0 && trimmed.length <= MAX_NAME_LENGTH ? trimmed : null;
}

/**
 * PATCH /api/users/me — 이름 바꾸기. 마이페이지 계정 칸이 부릅니다.
 *
 * 요청을 그대로 흘려보내지 않고 이름만, 그것도 길이를 확인해 통과시킵니다.
 * 응답은 마이페이지 계정 칸이지 헤더가 쓰는 내 정보가 아닙니다. 화면은 이 값으로
 * 헤더를 갱신하지 말고 GET 으로 다시 읽어야 합니다.
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

  const name = parseName(body);
  if (name === null) {
    return NextResponse.json(INVALID_REQUEST, { status: 422 });
  }

  try {
    return NextResponse.json(await updateMyName({ name }));
  } catch (error) {
    return toErrorResponse(error);
  }
}
