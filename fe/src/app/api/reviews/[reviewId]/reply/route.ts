import { NextResponse } from "next/server";
import { writeReviewReply } from "@/lib/api/services";
import { toErrorResponse } from "@/lib/api/route";
import { authorized } from "@/lib/auth/session";

export async function PUT(
  request: Request,
  { params }: { params: Promise<{ reviewId: string }> },
) {
  try {
    await authorized();
    const reviewId = Number((await params).reviewId);
    const body = (await request.json()) as { reply?: unknown };
    if (!Number.isInteger(reviewId) || typeof body.reply !== "string") {
      return NextResponse.json({ code: "INVALID_REQUEST", message: "답글을 확인해 주세요." }, { status: 422 });
    }
    return NextResponse.json(await writeReviewReply(reviewId, body.reply));
  } catch (error) {
    return toErrorResponse(error);
  }
}
