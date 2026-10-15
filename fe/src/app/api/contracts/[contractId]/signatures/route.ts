import { NextResponse } from "next/server";
import { signContract } from "@/lib/api/services";
import { parseNumericParam, toErrorResponse } from "@/lib/api/route";

const INVALID_REQUEST = { code: "INVALID_REQUEST", message: "서명을 그려 주세요." };
const NOT_FOUND = { code: "CONTRACT_NOT_FOUND", message: "계약서를 찾을 수 없습니다." };

/**
 * POST /api/contracts/{contractId}/signatures — 계약서 서명.
 *
 * 서명판에 그린 그림을 먼저 저장소에 올리고(3콜) 받은 `fileId` 로 서명합니다.
 * 올리기 주소는 브라우저가 알 필요가 없어 이 핸들러가 셋을 모두 처리합니다.
 *
 * **양쪽이 서명해야 계약이 성립하고, 서명한 계약서는 고칠 수 없습니다**
 * (R-CON-01·R-CON-02).
 */
export async function POST(
  request: Request,
  { params }: { params: Promise<{ contractId: string }> },
) {
  const { contractId } = await params;
  const id = parseNumericParam(contractId);
  if (id === null) return NextResponse.json(NOT_FOUND, { status: 404 });

  // 파일은 이 핸들러를 지나가지 않습니다. 브라우저가 저장소로 직접 보내고 fileId 만 옵니다.
  // 형식과 크기는 올리기 시작 단계에서 백엔드가 거릅니다. (R-FILE-01, R-FILE-02)
  let fileId: unknown;
  try {
    ({ fileId } = (await request.json()) as { fileId?: unknown });
  } catch {
    return NextResponse.json(INVALID_REQUEST, { status: 422 });
  }

  if (typeof fileId !== "number" || !Number.isInteger(fileId) || fileId <= 0) {
    return NextResponse.json(INVALID_REQUEST, { status: 422 });
  }

  try {
    const contract = await signContract(id, { signatureImageFileId: fileId });
    return NextResponse.json(contract, { status: 201 });
  } catch (error) {
    return toErrorResponse(error);
  }
}
