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
