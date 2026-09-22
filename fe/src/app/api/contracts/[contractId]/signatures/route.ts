import { NextResponse } from "next/server";
import { signContract, uploadFile } from "@/lib/api/services";
import { parseNumericParam, toErrorResponse } from "@/lib/api/route";

const INVALID_REQUEST = { code: "INVALID_REQUEST", message: "서명을 그려 주세요." };
const NOT_FOUND = { code: "CONTRACT_NOT_FOUND", message: "계약서를 찾을 수 없습니다." };

/** 서명 그림은 캔버스가 만든 PNG 입니다. 다른 형식은 받지 않습니다. */
const SIGNATURE_TYPE = "image/png";
/** 캔버스 한 장이라 이보다 클 이유가 없습니다. */
// R-FILE-01: 모든 파일 용도의 공통 상한.
const MAX_BYTES = 10 * 1024 * 1024;

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

  let file: FormDataEntryValue | null;
  try {
    file = (await request.formData()).get("signature");
  } catch {
    return NextResponse.json(INVALID_REQUEST, { status: 422 });
  }

  if (!(file instanceof File) || file.size === 0 || file.size > MAX_BYTES) {
    return NextResponse.json(INVALID_REQUEST, { status: 422 });
  }
  if (file.type !== SIGNATURE_TYPE) {
    return NextResponse.json(INVALID_REQUEST, { status: 422 });
  }

  try {
    const uploaded = await uploadFile(file, "SIGNATURE");
    const contract = await signContract(id, { signatureImageFileId: uploaded.fileId });
    return NextResponse.json(contract, { status: 201 });
  } catch (error) {
    return toErrorResponse(error);
  }
}
