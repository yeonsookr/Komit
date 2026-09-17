import { NextResponse } from "next/server";
import { writeMyDeveloperProfile } from "@/lib/api/services";
import { toErrorResponse } from "@/lib/api/route";
import { authorized } from "@/lib/auth/session";
import type { WriteDeveloperProfileRequest } from "@/types/developer";

export async function PUT(request: Request) {
  try {
    await authorized();
    const body = (await request.json()) as WriteDeveloperProfileRequest;
    const { profile, status } = await writeMyDeveloperProfile(body);
    return NextResponse.json(profile, { status });
  } catch (error) {
    return toErrorResponse(error);
  }
}
