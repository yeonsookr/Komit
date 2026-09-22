"use client";

import { clientApi } from "@/lib/api/client";
import { INTERNAL } from "@/lib/api/endpoints";
import { ApiError, UNKNOWN_ERROR_CODE } from "@/types/api";
import type { CreateUploadRequest, UploadTarget, UploadedFile, UploadPurpose } from "@/types/upload";

/**
 * 브라우저에서 저장소로 파일을 직접 보냅니다.
 *
 * **3콜 중 가운데 하나만 우리 서버를 비켜 갑니다.** 주소 받기와 완료 확인은 Route Handler 를
 * 거치므로 백엔드 주소도 인증 토큰도 브라우저에 나가지 않습니다. 파일 본문만 받은 주소로
 * 곧장 갑니다 — 녹음은 십수 MiB 라 서버가 받았다 다시 보내면 메모리를 그만큼 쓰고 두 번
 * 이동합니다.
 *
 * <p>이 한 걸음은 `FRONTEND_CONVENTIONS` 5장의 「브라우저는 Route Handler 만 호출한다」에
 * 걸립니다. 서명된 저장소 주소에 한해 두는 예외이며, 주소도 헤더도 우리 서버가 내준 것을
 * 그대로 씁니다.
 */

/**
 * 저장소로 보내는 데 걸리는 시간.
 *
 * 발급받은 주소가 만료되는 시각과 같게 둡니다 (`R-FILE-03`, 5분). 더 짧게 두면 주소는
 * 살아 있는데 브라우저가 먼저 포기합니다 — 90분 녹음(14MiB)을 15초에 보내려면 7.8Mbps 가
 * 꾸준히 나와야 해서, 회선이 처지는 곳에서는 늘 끊깁니다.
 */
const PUT_TIMEOUT_MS = 5 * 60 * 1_000;

async function putToStorage(target: UploadTarget, file: File): Promise<void> {
  let response: Response;
  try {
    response = await fetch(target.uploadUrl, {
      method: "PUT",
      headers: target.headers,
      body: file,
      signal: AbortSignal.timeout(PUT_TIMEOUT_MS),
    });
  } catch (cause) {
    console.error("[upload] 저장소로 보내지 못함", cause);
    throw new ApiError(502, UNKNOWN_ERROR_CODE, "파일을 올리지 못했습니다.");
  }

  if (!response.ok) {
    console.error("[upload] 저장소가 거절함", response.status);
    throw new ApiError(502, UNKNOWN_ERROR_CODE, "파일을 올리지 못했습니다.");
  }
}

/**
 * 파일 올리기 3콜.
 *
 * @returns 어딘가에 붙일 수 있는 파일. 셋을 다 마쳐야 나옵니다
 */
export async function uploadFromBrowser(
  file: File,
  purpose: UploadPurpose,
): Promise<UploadedFile> {
  const body: CreateUploadRequest = {
    originalName: file.name,
    contentType: file.type,
    sizeBytes: file.size,
    purpose,
  };

  const { data: target } = await clientApi.post<UploadTarget>(INTERNAL.UPLOADS, body);
  await putToStorage(target, file);
  const { data: uploaded } = await clientApi.post<UploadedFile>(
    INTERNAL.UPLOAD_COMPLETE(target.uploadId),
    {},
  );
  return uploaded;
}
