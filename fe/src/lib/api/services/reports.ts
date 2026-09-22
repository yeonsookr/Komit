import "server-only";

import { authenticatedBackendApi } from "../server";
import { BACKEND } from "../endpoints";
import type { CreateReportBody, Report } from "@/types/report";

/**
 * 신고 내용과 증거 파일 식별자를 Service에 전달합니다.
 *
 * 파일은 업로드 3콜로 먼저 저장소에 올라가므로 여기서는 `fileId` 만 넘깁니다.
 */
export async function createReport(
  contractId: number,
  body: CreateReportBody,
): Promise<Report> {
  const { data } = await authenticatedBackendApi().post<Report>(BACKEND.CONTRACT_REPORTS(contractId), body);
  return data;
}
