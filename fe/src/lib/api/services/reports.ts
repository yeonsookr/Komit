import "server-only";

import { backendApi } from "../server";
import { BACKEND } from "../endpoints";
import type { Report } from "@/types/report";

/** 신고 내용과 선택한 증거 파일 하나를 Service에 전달합니다. */
export async function createReport(contractId: number, body: FormData): Promise<Report> {
  const { data } = await backendApi().post<Report>(BACKEND.CONTRACT_REPORTS(contractId), body);
  return data;
}
