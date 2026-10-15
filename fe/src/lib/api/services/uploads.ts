import "server-only";

import { authenticatedBackendApi } from "../server";
import { BACKEND } from "../endpoints";
import type { CreateUploadRequest, UploadTarget, UploadedFile } from "@/types/upload";

/**
 * 파일 올리기 3콜 중 첫째와 셋째.
 *
 * **가운데 PUT 은 여기 없습니다.** 브라우저가 받은 주소로 저장소에 직접 보냅니다
 * (`lib/upload/browserUpload.ts`). 파일 본문이 Next 서버를 지나가면 파일 크기만큼 메모리를
 * 쓰고 두 번 이동합니다.
 */

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
