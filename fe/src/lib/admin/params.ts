import { safeNext } from "@/lib/auth/safeNext";
import { ROUTES } from "@/lib/routes";

/**
 * 운영 콘솔 화면이 주소로 주고받는 query 이름과, 그 값을 믿어도 되는지 보는 자리.
 * (컨벤션 8.2)
 *
 * 보내는 쪽과 읽는 쪽이 글자를 따로 적으면 한쪽만 바뀌어도 타입 검사와 빌드가
 * 통과하고 값만 조용히 사라집니다. 이름을 한 곳에서만 정해 그 일을 막습니다.
 *
 * 이름은 목 명세의 파라미터 이름과 같습니다. 중간에서 바꾸지 않습니다.
 */

/** 신고 목록의 상태 필터. 비우면 전부. (`GET /api/admin/reports`) */
export const REPORT_STATUS_PARAM = "status";

/** 신고 목록 페이지. 1부터 시작합니다. */
export const REPORT_PAGE_PARAM = "page";

/** 상세를 펼쳐 둘 신고. 목록에서 줄을 고르면 붙습니다. */
export const REPORT_ID_PARAM = "reportId";

/** 본문을 펼쳐 둘 계약 양식 판. (`9_계약양식`) */
export const TEMPLATE_ID_PARAM = "templateId";

/**
 * 운영 로그인 뒤 돌아갈 곳.
 *
 * 우리 화면인지(`safeNext`)에 더해 **운영 콘솔 안인지**까지 봅니다. 주소는 누구나
 * 고쳐 넣을 수 있고, `?next=/mypage` 로 로그인하면 사용자 계정이 없을 수도 있는
 * 운영자가 사용자 화면에 떨어집니다.
 *
 * 운영 로그인 화면 자신도 뺍니다. 그리로 돌아가면 이미 들어와 있는데 로그인 카드가
 * 다시 보입니다.
 */
export function safeAdminNext(
  value: string | string[] | null | undefined,
): string | undefined {
  const next = safeNext(value);
  if (next === undefined) return undefined;

  const [path] = next.split(/[?#]/, 1);
  if (path === ROUTES.adminLogin) return undefined;
  const insideConsole =
    path === ROUTES.adminHome || path.startsWith(`${ROUTES.adminHome}/`);
  return insideConsole ? next : undefined;
}
