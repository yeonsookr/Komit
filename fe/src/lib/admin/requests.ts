import { INTERNAL } from "@/lib/api/endpoints";
import type {
  AdminIdentity,
  AdminLoginRequest,
  AdminReportDetail,
  ContractTemplateDetail,
  ContractTemplatePreview,
  PreviewContractTemplateRequest,
  WriteContractTemplateRequest,
} from "@/types/admin";
import { adminClientApi } from "./client";

/**
 * 브라우저가 부르는 운영 요청. Next 서버(`/api/admin`)만 상대하고 토큰은 만지지 않습니다.
 *
 * `lib/api/services/index.ts` 에 넣지 않습니다. 그 묶음은 server-only 모듈이라
 * 클라이언트 컴포넌트가 import 하면 빌드가 실패합니다. 사용자 쪽
 * `lib/api/services/session.ts` 와 같은 자리입니다.
 *
 * 조회는 여기 없습니다. 목록·상세·본문은 서버 컴포넌트가 읽어 props 로 내려보냅니다
 * (컨벤션 9장). 여기 있는 것은 **누른 뒤에야 생기는 요청**뿐입니다.
 */

/** 운영 로그인. 성공하면 응답이 운영자 쿠키를 심어 주고 화면은 운영자 정보만 받습니다. */
export async function requestAdminLogin(body: AdminLoginRequest): Promise<AdminIdentity> {
  const { data } = await adminClientApi.post<AdminIdentity>(INTERNAL.ADMIN_LOGIN, body);
  return data;
}

/** 운영 로그아웃. 운영자 쿠키만 지워지고 사용자 세션은 그대로입니다. */
export async function requestAdminLogout(): Promise<void> {
  await adminClientApi.post(INTERNAL.ADMIN_LOGOUT);
}

/** 신고 종결. 되돌리는 API는 없습니다. (`R-RPT-03`) */
export async function requestResolveReport(reportId: number): Promise<AdminReportDetail> {
  const { data } = await adminClientApi.post<AdminReportDetail>(
    INTERNAL.ADMIN_REPORT_RESOLVE(reportId),
  );
  return data;
}

/**
 * 계약 양식 등록·개정. 언제나 새 판이 만들어집니다 — 등록과 개정을 가르는 것은
 * `sourceTemplateId` 하나입니다. (`R-CON-05`)
 */
export async function requestWriteContractTemplate(
  body: WriteContractTemplateRequest,
): Promise<ContractTemplateDetail> {
  const { data } = await adminClientApi.post<ContractTemplateDetail>(
    INTERNAL.ADMIN_CONTRACT_TEMPLATES,
    body,
  );
  return data;
}

/**
 * 미리보기. 자리표시자를 보기용 값으로 채운 HTML과, 그 유형에서 채울 수 없는
 * 이름(`unknownPlaceholders`)을 **서버가** 판정해 돌려줍니다. 화면이 다시 세지 않습니다.
 */
export async function requestContractTemplatePreview(
  body: PreviewContractTemplateRequest,
): Promise<ContractTemplatePreview> {
  const { data } = await adminClientApi.post<ContractTemplatePreview>(
    INTERNAL.ADMIN_CONTRACT_TEMPLATE_PREVIEW,
    body,
  );
  return data;
}
