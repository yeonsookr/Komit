import { completeSignup, getSignupCompletion } from "@/lib/api/services/auth";
import { toErrorResponse } from "@/lib/api/route";

export async function GET() {
  try {
    await getSignupCompletion();
    return new Response(null, { status: 204 });
  } catch (error) {
    return toErrorResponse(error);
  }
}

export async function POST() {
  try {
    await completeSignup();
    return new Response(null, { status: 204 });
  } catch (error) {
    return toErrorResponse(error);
  }
}
