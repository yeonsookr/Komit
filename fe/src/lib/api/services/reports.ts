import "server-only";

import { backendApi } from "../server";
import { BACKEND } from "../endpoints";
import type { Report } from "@/types/report";

/** 신고 내용과 선택한 증거 파일 하나를 Service에 전달합니다. */
export async function createReport(contractId: number, body: FormData): Promise<Report> {
  const { data } = await backendApi().post<Report>(BACKEND.CONTRACT_REPORTS(contractId), body, {
    // FormData 는 경계 문자열이 필요하므로 공통 JSON 헤더를 지웁니다.
    headers: { "Content-Type": undefined },
  });
  return data;
}
