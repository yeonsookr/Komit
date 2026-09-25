import { ApiError } from "@/types/api";

/** AI가 초안을 만들지 못했지만 사용자가 빈 계약서를 직접 작성할 수 있는 실패입니다. */
export function canWriteContractWithoutDraft(error: unknown): boolean {
  return error instanceof ApiError && (error.status === 502 || error.status === 503);
}
