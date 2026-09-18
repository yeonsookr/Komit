import "server-only";

import { adminAuthorized } from "@/lib/admin/session";
import type {
  AdminLoginRequest,
  AdminLoginResponse,
  AdminReportDetail,
  AdminReportList,
  AdminReportStatus,
  AdminSummary,
  ContractTemplateDetail,
  ContractTemplateList,
  ContractTemplatePreview,
  PreviewContractTemplateRequest,
  ReconciliationData,
  WriteContractTemplateRequest,
} from "@/types/admin";
import { BACKEND } from "../endpoints";
import { backendApi } from "../server";

/**
 * 운영 콘솔(영역 9) 요청.
 *
 * **모든 요청에 운영자 토큰을 붙입니다.** 사용자 쪽 `authorized()` 는 사용자 쿠키를
 * 읽으므로 여기서 쓰면 사용자로만 로그인한 사람이 운영 API를 부르게 됩니다. 목 서버는
 * 토큰을 검사하지 않아 그래도 200 이 오고, 그 구멍은 실서버가 붙을 때에야 드러납니다.
 *
 * 로그인만 예외입니다. 토큰을 받으러 가는 요청이라 붙일 토큰이 아직 없습니다.
 */

/** 운영 로그인. 실패하면 401(ADMIN_LOGIN_FAILED) 이 ApiError 로 올라옵니다. */
export async function loginAsAdmin(body: AdminLoginRequest): Promise<AdminLoginResponse> {
  const { data } = await backendApi().post<AdminLoginResponse>(BACKEND.ADMIN_LOGIN, body);
  return data;
}

/** 콘솔 홈의 업무 카드 세 장에 들어갈 숫자와 기준 시각. */
export async function getAdminSummary(): Promise<AdminSummary> {
  const { data } = await backendApi().get<AdminSummary>(
    BACKEND.ADMIN_SUMMARY,
    await adminAuthorized(),
  );
  return data;
}

/**
 * 신고 목록. 의뢰인의 문제 신고와 개발자의 이의 제기가 한 목록에 섞이고
 * `reporter.role` 로 갈립니다. `openCount`·`resolvedCount` 는 필터와 무관한 전체 수입니다.
 */
export async function getAdminReports(params?: {
  status?: AdminReportStatus;
  page?: number;
  size?: number;
}): Promise<AdminReportList> {
  const { data } = await backendApi().get<AdminReportList>(BACKEND.ADMIN_REPORTS, {
    ...(await adminAuthorized()),
    params,
  });
  return data;
}

/** 신고 한 건의 전문과 첨부. 계약서 원문은 여기 없고 `contractId` 로 따로 읽습니다. */
export async function getAdminReport(reportId: number): Promise<AdminReportDetail> {
  const { data } = await backendApi().get<AdminReportDetail>(
    BACKEND.ADMIN_REPORT(reportId),
    await adminAuthorized(),
  );
  return data;
}

/** 신고 종결. 종결한 시각이 해결일로 남습니다. (`R-RPT-03`) */
export async function resolveAdminReport(reportId: number): Promise<AdminReportDetail> {
  const { data } = await backendApi().post<AdminReportDetail>(
    BACKEND.ADMIN_REPORT_RESOLVE(reportId),
    undefined,
    await adminAuthorized(),
  );
  return data;
}

/**
 * 정산 대사 결과 조회. **대사는 Escrow 서버가 매일 스스로 돌립니다.**
 * 이 요청은 그 결과를 읽을 뿐이고 다시 돌리는 API는 없습니다.
 */
export async function getAdminReconciliation(days = 7): Promise<ReconciliationData> {
  const { data } = await backendApi().get<ReconciliationData>(BACKEND.ADMIN_RECONCILIATION, {
    ...(await adminAuthorized()),
    params: { days },
  });
  return data;
}

/** 계약 양식 목록. 한 줄이 한 판이고 보관된 옛 판도 함께 옵니다. (`R-CON-05`) */
export async function getAdminContractTemplates(): Promise<ContractTemplateList> {
  const { data } = await backendApi().get<ContractTemplateList>(
    BACKEND.ADMIN_CONTRACT_TEMPLATES,
    await adminAuthorized(),
  );
  return data;
}

/** 판 하나의 본문. */
export async function getAdminContractTemplate(
  templateId: number,
): Promise<ContractTemplateDetail> {
  const { data } = await backendApi().get<ContractTemplateDetail>(
    BACKEND.ADMIN_CONTRACT_TEMPLATE(templateId),
    await adminAuthorized(),
  );
  return data;
}

/**
 * 계약 양식 등록·개정. 등록과 개정이 같은 요청이고 언제나 새 판이 만들어집니다.
 * 가르는 것은 `sourceTemplateId` 하나입니다.
 */
export async function writeAdminContractTemplate(
  body: WriteContractTemplateRequest,
): Promise<ContractTemplateDetail> {
  const { data } = await backendApi().post<ContractTemplateDetail>(
    BACKEND.ADMIN_CONTRACT_TEMPLATES,
    body,
    await adminAuthorized(),
  );
  return data;
}

/** 저장하지 않은 본문의 미리보기. 채울 수 없는 자리표시자도 서버가 가려 줍니다. */
export async function previewAdminContractTemplate(
  body: PreviewContractTemplateRequest,
): Promise<ContractTemplatePreview> {
  const { data } = await backendApi().post<ContractTemplatePreview>(
    BACKEND.ADMIN_CONTRACT_TEMPLATE_PREVIEW,
    body,
    await adminAuthorized(),
  );
  return data;
}
