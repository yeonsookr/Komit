import "server-only";

import { authenticatedBackendApi } from "../server";
import { BACKEND } from "../endpoints";
import { ApiError, UNKNOWN_ERROR_CODE } from "@/types/api";
import type {
  CreateUploadRequest,
  UploadPurpose,
  UploadTarget,
  UploadedFile,
} from "@/types/upload";

/** 저장소로 보내는 데 걸리는 시간. 서명 그림은 작지만 회선이 느릴 수 있습니다. */
const PUT_TIMEOUT_MS = 15_000;

/** 올리기 시작. 형식·크기가 맞지 않으면 주소를 받지 못합니다. */
export async function createUpload(body: CreateUploadRequest): Promise<UploadTarget> {
  const { data } = await authenticatedBackendApi().post<UploadTarget>(BACKEND.UPLOADS, body);
  return data;
}

/** 올리기 완료. 여기까지 마쳐야 `fileId` 를 어딘가에 붙일 수 있습니다. */
export async function completeUpload(uploadId: number): Promise<UploadedFile> {
  const { data } = await authenticatedBackendApi().post<UploadedFile>(
    BACKEND.UPLOAD_COMPLETE(uploadId),
    {},
  );
  return data;
}

/**
 * 파일을 저장소로 보냅니다.
 *
 * 코밋 서버를 거치지 않는 단계라 `backendApi` 를 쓰지 않습니다. 주소는 우리
 * 서버가 내준 것이고 헤더도 함께 받은 것을 그대로 싣습니다 — 바꿔 보내면
 * 완료 요청에서 거절됩니다.
 */
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
    console.error("[uploads] 저장소로 보내지 못함", cause);
    throw new ApiError(502, UNKNOWN_ERROR_CODE, "파일을 올리지 못했습니다.");
  }

  if (!response.ok) {
    console.error("[uploads] 저장소가 거절함", response.status);
    throw new ApiError(502, UNKNOWN_ERROR_CODE, "파일을 올리지 못했습니다.");
  }
}

/**
 * 파일 올리기 3콜.
 *
 * 보낼 주소 받기 → 그 주소로 보내기 → 완료 확인. 셋을 다 마쳐야 붙일 수 있는
 * `fileId` 가 나옵니다. 올리기만 해서는 어디에도 붙지 않습니다.
 */
export async function uploadFile(
  file: File,
  purpose: UploadPurpose,
): Promise<UploadedFile> {
  const target = await createUpload({
    originalName: file.name,
    contentType: file.type,
    sizeBytes: file.size,
    purpose,
  });

  await putToStorage(target, file);
  return completeUpload(target.uploadId);
}
