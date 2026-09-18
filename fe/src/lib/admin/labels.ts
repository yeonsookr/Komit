import type {
  AdminContractType,
  AdminReportStatus,
  ContractPartyRole,
  EscrowStatus,
  ReconciliationResult,
} from "@/types/admin";

/**
 * 운영 콘솔이 화면에 적는 상태 문구.
 *
 * 서버가 주는 값은 `OPEN` 같은 코드이고 운영자가 읽는 것은 「접수됨」입니다. 대응을
 * 한 곳에 모아 두는 이유는 같은 상태가 목록·상세·배지 세 군데에 나오기 때문입니다 —
 * 화면마다 적으면 한 곳만 고쳐져 같은 신고가 자리에 따라 다른 이름으로 보입니다.
 *
 * **운영 콘솔은 코드도 함께 보입니다.** `9_신고처리` 의 상세가 「OPEN · 접수됨」으로
 * 둘을 같이 적습니다. 내부 화면이라 운영자가 API·로그와 바로 맞춰 볼 수 있어야 해서
 * 일부러 남긴 것이니 코드 쪽을 지우지 마세요.
 *
 * 문구는 `R-RPT-04`(Komit 은 누가 옳은지 정하지 않는다) 를 지킵니다. 「종결됨」은
 * 운영자가 이 건을 다 봤다는 표시일 뿐 승패나 귀책을 뜻하지 않습니다.
 */
export const ADMIN_REPORT_STATUS_LABEL: Record<AdminReportStatus, string> = {
  OPEN: "접수됨",
  RESOLVED: "종결됨",
};

export const ADMIN_PARTY_ROLE_LABEL: Record<ContractPartyRole, string> = {
  CLIENT: "의뢰인",
  DEVELOPER: "개발자",
};

export const ADMIN_CONTRACT_TYPE_LABEL: Record<AdminContractType, string> = {
  CONSULTING: "컨설팅",
  DEVELOPMENT: "개발",
};

export const ESCROW_STATUS_LABEL: Record<EscrowStatus, string> = {
  CREATED: "생성됨",
  DEPOSIT_PENDING: "예치 대기",
  ACTIVE: "정상",
  COMPLETED: "완료",
  REFUNDED: "환불 처리 중",
  CANCELLED: "취소",
};

/**
 * 대사 결과.
 *
 * **`MATCHED` 가 아닌 것을 한 덩어리로 뭉치지 않습니다.** 장부와 잔고가 다른 것
 * (`BANK_MISMATCH`)과 잔고를 아예 읽지 못한 것(`ERROR`)은 운영자가 할 일이 다릅니다.
 */
export const RECONCILIATION_RESULT_LABEL: Record<ReconciliationResult, string> = {
  MATCHED: "일치",
  CACHE_MISMATCH: "캐시 차이",
  BANK_MISMATCH: "차이 발생",
  REVIEW_REQUIRED: "확인 필요",
  ERROR: "조회 오류",
};

/** 계약 양식 판의 상태. 보관된 판도 옛 계약이 가리키고 있어 목록에서 사라지지 않습니다. (`R-CON-05`) */
export function contractTemplateStatusLabel(isActive: boolean): string {
  return isActive ? "사용 중" : "보관";
}

/** 신고 목록의 「신고자」 칸. 와이어프레임 표기가 `김미미 (의뢰인)` 입니다. */
export function reportPartyLabel(party: { name: string; role: ContractPartyRole }): string {
  return `${party.name} (${ADMIN_PARTY_ROLE_LABEL[party.role]})`;
}
