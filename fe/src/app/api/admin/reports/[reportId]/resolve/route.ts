import { NextResponse } from "next/server";
import { adminAuthorized } from "@/lib/admin/session";
import { parseNumericParam, toErrorResponse } from "@/lib/api/route";
import { resolveAdminReport } from "@/lib/api/services";

const REPORT_NOT_FOUND = { code: "REPORT_NOT_FOUND", message: "신고를 찾을 수 없습니다." };

/**
 * POST /api/admin/reports/{reportId}/resolve — 신고 종결.
 * `9_신고처리` 상세의 「종결 처리」가 부릅니다.
 *
 * 종결한 시각이 해결일로 남고 신고한 사람에게 알림이 갑니다. **되돌리는 API는
 * 없습니다** — 같은 일이 다시 생기면 새 신고로 접수합니다. (`R-RPT-03`)
 *
 * 이미 종결된 건은 백엔드가 409 를 주고 그대로 전달합니다. 화면은 그때 목록을
 * 다시 읽어 최신 상태를 보여 줍니다.
 */
export async function POST(
  _request: Request,
  { params }: { params: Promise<{ reportId: string }> },
) {
  try {
    await adminAuthorized();
  } catch (error) {
    return toErrorResponse(error);
  }

  const { reportId } = await params;
  const id = parseNumericParam(reportId);
  if (id === null) return NextResponse.json(REPORT_NOT_FOUND, { status: 404 });

  try {
    return NextResponse.json(await resolveAdminReport(id));
  } catch (error) {
    return toErrorResponse(error);
  }
}
