/**
 * 파일 올리기 타입. 목 Swagger `/service/docs` 영역 8 의 업로드 3콜과 같습니다.
 *
 * 파일은 코밋 서버를 거치지 않습니다. 보낼 주소를 먼저 받고 그 주소로 파일을
 * 직접 보낸 뒤 완료를 알려 `fileId` 를 받습니다.
 */

/**
 * 이 파일을 어디에 쓸지. 받는 형식·크기 한도와 둘 자리를 정하는 데까지만 쓰이고
 * 보관되지는 않습니다.
 */
export type UploadPurpose =
  | "PROFILE_IMAGE"
  | "CAREER_EVIDENCE"
  | "PORTFOLIO_IMAGE"
  | "SIGNATURE";

/** 올리기 시작 요청. 형식과 크기는 이 단계에서 걸러집니다. */
export interface CreateUploadRequest {
  originalName: string;
  contentType: string;
  sizeBytes: number;
  purpose: UploadPurpose;
}

/** 파일을 보낼 곳. 완료되면 `uploadId` 가 그대로 파일 식별자가 됩니다. */
export interface UploadTarget {
  uploadId: number;
  /** 저장소 주소. 이 주소로 `PUT` 한 번을 보내고 본문이 파일 내용입니다. */
  uploadUrl: string;
  expiresAt: string;
  /** 보낼 때 그대로 실어야 하는 헤더. 다르면 완료에서 거절됩니다. */
  headers: Record<string, string>;
}

/** 확인을 마쳐 쓸 수 있게 된 파일. 이 `fileId` 를 붙일 곳에 담습니다. */
export interface UploadedFile {
  fileId: number;
  originalName: string;
  contentType: string;
  sizeBytes: number;
  verifiedAt: string;
}
