/**
 * 신고 접수 요청.
 *
 * 증거 파일은 `uploadFile(file, "REPORT_EVIDENCE")` 로 먼저 올리고 받은
 * `fileId` 만 보냅니다. 파일 자체는 이 요청에 싣지 않습니다.
 */
export interface CreateReportBody {
  reason: string;
  fileId: number | null;
}

/** 목 Swagger 영역 6의 ReportResponse와 같은 신고 응답입니다. */
export interface Report {
  reportId: number;
  contractId: number;
  reporterUserId: number;
  targetUserId: number;
  reason: string;
  fileId: number | null;
  status: "OPEN" | "RESOLVED";
  createdAt: string;
  resolvedAt: string | null;
}
