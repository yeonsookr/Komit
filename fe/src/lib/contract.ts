/**
 * 계약 화면의 표시 규칙. 화면에서 분기하지 않도록 순수 함수로 빼 둡니다.
 */
import { ApiError } from "@/types/api";
import type {
  Contract,
  ContractMessage,
  DevelopmentAgreement,
  DevelopmentStatus,
  Milestone,
} from "@/types/contract";

/** 계약서와 함께 상대가 마지막으로 남긴 말. */
export function lastCounterpartMessage(
  contract: Contract,
  received: boolean,
): ContractMessage | null {
  return contract.messages.findLast((message) => message.isClient === received) ?? null;
}

/** 계약 금액. 갈래에 따라 한쪽에만 있습니다. 계약서 전이면 null. */
export function totalAmount(contract: Contract | null): number | null {
  return contract?.development?.totalAmount ?? contract?.consulting?.totalAmount ?? null;
}

/** AI version 1은 계약 내용만 있고 개발자가 정할 세션·예약은 아직 없습니다. */
export function isConsultingDraft(contract: Contract | null): boolean {
  return (
    contract?.contractType === "CONSULTING" &&
    contract.consulting !== null &&
    contract.consulting.sessions.length === 0
  );
}

/**
 * 개발 계약의 AI version 1인지. 요청서와 상담 대화로 만든 초안이라 착수일이 비어 있습니다.
 *
 * 개발자가 보낸 계약서에는 착수일이 늘 있습니다. 작성 요청이 착수일을 반드시 받습니다.
 * 초안은 작업 금액이 모두 0 이고 인계 작업이 없습니다. 금액과 일정은 개발자가 채워 보냅니다.
 */
export function isDevelopmentDraft(contract: Contract | null): boolean {
  return (
    contract?.contractType === "DEVELOPMENT" &&
    contract.development !== null &&
    contract.development.startDate === null
  );
}

/** 양쪽이 서명했는지. 양쪽이 서명해야 계약이 성립합니다 (R-CON-01). */
export function isSignedByBoth(contract: Contract): boolean {
  return (
    contract.clientSignature?.isSigned === true &&
    contract.developerSignature?.isSigned === true
  );
}

/**
 * 한 사람이라도 서명했는지. 서명한 계약서는 고칠 수 없습니다 (R-CON-02).
 * 성립(양쪽 서명)을 기다리지 않습니다 — 상대가 서명한 내용이 바뀌면 안 됩니다.
 */
export function isSignedByAnyone(contract: Contract): boolean {
  return (
    contract.clientSignature?.isSigned === true ||
    contract.developerSignature?.isSigned === true
  );
}

/**
 * 거래가 끝났는지. 컨설팅과 개발 둘 다 유료 거래라 어느 쪽이든 끝나면 참입니다.
 * 리뷰는 끝난 거래에만 달립니다 (R-REV-02).
 *
 * 컨설팅은 양쪽이 들어와 시작한 세션이 끝나면 끝난 것입니다. 대금 지급을 기다리지 않습니다.
 * 아무도 들어오지 않고 종료만 눌린 세션은 끝난 것으로 보지 않습니다 (R-CON-12).
 */
export function isCompleted(contract: Contract): boolean {
  if (contract.consulting) {
    const sessions = contract.consulting.sessions;
    return (
      sessions.length > 0 &&
      sessions.every((session) => session.startedAt !== null && session.endedAt !== null)
    );
  }
  const status = contract.development?.status ?? null;
  return status === "COMPLETED" || status === "AFTER_SERVICE";
}

/** 의뢰인이 취소한 계약인지. 취소된 계약은 서명·예치·세션을 받지 않습니다 (R-CON-21). */
export function isCancelled(contract: Contract): boolean {
  return contract.status === "CANCELLED";
}

/**
 * 의뢰인이 지금 이 계약을 취소할 수 있는지 (R-CON-21).
 *
 * 개발자가 서명하기 전에만 됩니다. 양쪽이 서명해 성립한 뒤에는 취소가 아니라 다른 절차를
 * 밟습니다. 이미 취소된 계약에는 버튼을 다시 보이지 않습니다.
 *
 * @param received 내가 이 방에 개발자로 들어갔는지. 개발자는 취소하지 않습니다
 */
export function canCancel(contract: Contract, received: boolean): boolean {
  return !received && !isCancelled(contract) && contract.developerSignature?.isSigned !== true;
}

/** 내가 이미 서명했는지. 서명은 사용자마다 한 번입니다. */
export function hasSigned(contract: Contract, myUserId: number): boolean {
  return [contract.clientSignature, contract.developerSignature].some(
    (signature) => signature?.isSigned === true && signature.userId === myUserId,
  );
}

/**
 * 착수일부터 납기일까지 며칠인지. 화면의 `3건 · 합계 650,000원 · 20일` 뒤 숫자입니다.
 * 둘 중 하나라도 비어 있으면 null 입니다.
 */
export function periodDays(development: DevelopmentAgreement): number | null {
  if (development.startDate === null || development.dueDate === null) return null;

  const start = Date.parse(`${development.startDate}T00:00:00+09:00`);
  const due = Date.parse(`${development.dueDate}T00:00:00+09:00`);
  if (Number.isNaN(start) || Number.isNaN(due)) return null;

  return Math.round((due - start) / 86_400_000);
}

/**
 * 인계 작업인지. 소스·계정·설명서를 넘겨받았는지 확인하는 작업으로,
 * 가격이 0 이며 화면에 금액을 보여주지 않습니다.
 *
 * 금액이 아니라 서버가 준 표시로 가립니다 (R-QA-06).
 */
export function isHandover(milestone: Milestone): boolean {
  return milestone.isHandover;
}

/** 통과 확정된 작업인지. 되돌릴 수 없습니다 (R-QA-03). */
export function isConfirmed(milestone: Milestone): boolean {
  return milestone.status === "CONFIRMED";
}

/**
 * 개발자가 확인을 요청해 의뢰인의 체크를 기다리는 작업. 의뢰인은 이때만 체크합니다.
 * 요청 전 작업을 통과시키면 서버가 409 로 막고, 일부만 체크하면 작업이 개발자에게
 * 돌아갑니다 (R-QA-02).
 */
export function isAwaitingCheck(milestone: Milestone): boolean {
  return milestone.status === "SUBMITTED";
}

/**
 * 인계 자료를 지금 등록할 수 있는지. 닫혔으면 서버가 돌려줄 오류 코드와 같은 이름으로 까닭을 적습니다.
 * 서버의 등록 검사와 같은 순서로 봅니다 (R-QA-06).
 *
 * 일반 작업이 하나라도 남았으면 아직 열리지 않았고, 인계 작업을 통과했으면 최종 인수 시험을 마친 것이며,
 * 인계 작업의 확인을 요청했으면 의뢰인이 시험하는 중입니다. 의뢰인이 일부만 통과시켜 인계 작업이
 * 돌아오면 다시 열립니다. 인계 작업이 여럿이어도 하나라도 통과했거나 요청 중이면 닫습니다(서버와 같음).
 */
export type HandoverRegistration =
  | "OPEN"
  | "HANDOVER_NOT_OPEN"
  | "HANDOVER_UNDER_REVIEW"
  | "FINAL_ACCEPTANCE_PASSED";

export function handoverRegistration(milestones: Milestone[]): HandoverRegistration {
  if (!billableMilestones(milestones).every(isConfirmed)) return "HANDOVER_NOT_OPEN";
  const handovers = milestones.filter(isHandover);
  if (handovers.some(isConfirmed)) return "FINAL_ACCEPTANCE_PASSED";
  if (handovers.some(isAwaitingCheck)) return "HANDOVER_UNDER_REVIEW";
  return "OPEN";
}

/**
 * 의뢰인이 확인 문장 일부만 통과시켜 개발자에게 돌아온 작업인지. 서버는 이때 작업을 진행 중으로 되돌리고
 * 요청 시각을 비웁니다. 통과한 문장이 남아 있는 것으로 요청하기 전 작업과 가립니다 (R-QA-02).
 */
export function isReturned(milestone: Milestone): boolean {
  return !isConfirmed(milestone) && !isAwaitingCheck(milestone) && checkedCount(milestone) > 0;
}

/** 작업 진행 단계의 이름. 진행 탭과 작업 카드가 같은 말을 씁니다. */
export function milestoneStageLabel(milestone: Milestone): string {
  if (isConfirmed(milestone)) return "통과";
  if (isAwaitingCheck(milestone)) return "확인 요청 중";
  if (isReturned(milestone)) return "돌아옴";
  if (milestone.status === "IN_PROGRESS") return "진행 중";
  return "예정";
}

/**
 * 최종 인수 시험(인계 작업의 확인)을 요청할 수 있는지 셋을 따로 봅니다. 서버가 요청을 받을 때 보는 조건과
 * 같습니다 (R-QA-06). URL 은 언제나 TEST 라 시험 자료로 셉니다. 기존 단일 파일은 어느 쪽에도 들지 않습니다.
 */
export interface HandoverChecklist {
  /** 일반 작업을 모두 통과했는지. */
  regularDone: boolean;
  /** 계약 전체에 인수 시험 자료(URL 또는 TEST 파일)가 하나 이상 있는지. */
  testMaterial: boolean;
  /** 계약 전체에 지급 후 해금 파일(PAID)이 하나 이상 있는지. */
  paidFile: boolean;
}

export function handoverChecklist(development: DevelopmentAgreement): HandoverChecklist {
  const materials = development.deliverables.flatMap((deliverable) => deliverable.materials);
  return {
    regularDone: billableMilestones(development.milestones).every(isConfirmed),
    testMaterial: materials.some((material) => material.accessStage === "TEST"),
    paidFile: materials.some((material) => material.accessStage === "PAID"),
  };
}

/** 체크리스트를 모두 채웠는지. 채우기 전에는 인계 작업의 확인 요청을 막습니다. */
export function isHandoverReady(checklist: HandoverChecklist): boolean {
  return checklist.regularDone && checklist.testMaterial && checklist.paidFile;
}

/**
 * 최종 인수 시험을 통과했지만 지급이 아직 확정되지 않았는지. 인계 작업이 통과되면 계약 총액 지급을 요청하고,
 * 지급액과 수수료가 확정돼야 계약이 완료됩니다 (R-PAY-01). 그 사이 계약은 진행 중으로 남습니다.
 * 지급이 실패했는지·보류됐는지는 이 값으로 알 수 없습니다.
 */
export function isPayoutPending(development: DevelopmentAgreement): boolean {
  return (
    development.status === "IN_PROGRESS" &&
    development.milestones.some((milestone) => isHandover(milestone) && isConfirmed(milestone))
  );
}

/** 최종 인수 시험을 통과하고 지급을 기다리는 동안 확인 탭·방 머리·진행 탭이 함께 쓰는 이름. */
export const PAYOUT_PENDING_LABEL = "최종 인수 시험 통과 · 지급 처리 중";

/** R-QA-06: 인수 시험 URL 길이 상한. */
export const DELIVERABLE_URL_MAX_LENGTH = 2000;

/** R-QA-06: 산출물 이름 길이 상한. 계약서의 항목과 계약 뒤 더한 항목이 같습니다. */
export const DELIVERABLE_TITLE_MAX_LENGTH = 100;

/** 스킴 뒤 `//` 부터 경로·질의·조각 앞까지가 주소 부분입니다. 서버 `HttpUrlValidator` 와 같은 식입니다. */
const URL_PATTERN = /^([A-Za-z][A-Za-z0-9+.-]*):\/\/([^/?#]*)/;

/**
 * 인수 시험 URL 로 받는 주소인지. 호스트가 있는 http·https 주소만 받습니다 (R-QA-06).
 * 보내기 전에 걸러 사유를 바로 보여 주려는 것이고, 판정은 서버가 다시 합니다. 서버와 같은 식으로 봐서
 * 여기서 통과한 주소를 서버가 형식 때문에 거부하지 않습니다.
 */
export function isHttpUrl(value: string): boolean {
  const match = URL_PATTERN.exec(value);
  if (match === null) return false;

  const [, scheme, authority] = match;
  // 사용자 정보와 포트를 떼어 낸 호스트. `https://:8080` 처럼 호스트가 비면 거부합니다.
  const hostAndPort = authority.slice(authority.lastIndexOf("@") + 1);
  let host: string;
  if (hostAndPort.startsWith("[")) {
    const end = hostAndPort.indexOf("]");
    host = end < 0 ? "" : hostAndPort.slice(1, end);
  } else {
    const colon = hostAndPort.indexOf(":");
    host = colon < 0 ? hostAndPort : hostAndPort.slice(0, colon);
  }
  const lower = scheme.toLowerCase();
  return (lower === "http" || lower === "https") && host !== "";
}

/** 인계 자료 등록이 실패했을 때 화면이 할 일. */
export interface RegistrationFailure {
  /** 알림에 적을 문구. 필드 오류면 그 사유, 아니면 서버 문구입니다. */
  title: string;
  /**
   * 화면을 다시 읽을지. 409 는 그 사이 계약이 바뀐 것입니다 — 다른 탭에서 확인을 요청했거나 의뢰인이
   * 통과시켰습니다. 다시 읽어야 닫힌 까닭이 화면에 나옵니다.
   */
  refresh: boolean;
  /** 같은 요청을 다시 보내 볼 만한지. 응답이 오지 않았거나 서버 오류일 때입니다. */
  retry: boolean;
}

export function registrationFailure(error: unknown, fallback: string): RegistrationFailure {
  if (!(error instanceof ApiError)) return { title: fallback, refresh: false, retry: true };
  return {
    title: error.errors?.[0]?.reason ?? error.message,
    refresh: error.status === 409,
    retry: error.status === 0 || error.status >= 500,
  };
}

/**
 * 확인 요청·확인 결과 저장·통과시키기가 거부됐을 때 화면이 할 일. 409 는 그 사이 작업 상태가 바뀐 것이고
 * (다른 탭, 자동 통과, 상대의 처리), `QA_NOT_ALL_CHECKED` 는 화면이 본 체크와 서버가 다른 것이라 둘 다
 * 다시 읽습니다.
 */
export function milestoneActionFailure(error: unknown, fallback: string): { title: string; refresh: boolean } {
  if (!(error instanceof ApiError)) return { title: fallback, refresh: false };
  return {
    title: error.errors?.[0]?.reason ?? error.message,
    refresh: error.status === 409 || error.code === "QA_NOT_ALL_CHECKED",
  };
}

/** 의뢰인이 체크한 확인 문장 수. `2 / 3 확인` 의 앞 숫자입니다. */
export function checkedCount(milestone: Milestone): number {
  return milestone.qaCriteria.filter((qa) => qa.status === "RESOLVED").length;
}

/**
 * 확인 문장을 모두 체크했는지. 모두 통과한 작업만 완료로 확정할 수 있습니다
 * (R-QA-01). 문장이 하나도 없으면 통과시킬 근거가 없으므로 거짓입니다.
 */
export function isFullyChecked(milestone: Milestone): boolean {
  return (
    milestone.qaCriteria.length > 0 && checkedCount(milestone) === milestone.qaCriteria.length
  );
}

/** 통과 확정된 작업 대금의 합. 아직 지급되지는 않았습니다 (R-PAY-03). */
export function confirmedAmount(milestones: Milestone[]): number {
  return milestones.filter(isConfirmed).reduce((sum, milestone) => sum + milestone.price, 0);
}

/**
 * 금액이 붙는 작업만. 인계 작업은 0 원이라 금액을 세는 자리에서 빠집니다
 * — 화면의 `외 2건` · `남은 작업 2건` 이 이 기준입니다.
 */
export function billableMilestones(milestones: Milestone[]): Milestone[] {
  return milestones.filter((milestone) => !isHandover(milestone));
}

/** 아직 통과하지 않은 작업 수. 하단 고정 줄의 `남은 작업 2건` 입니다. */
export function remainingCount(milestones: Milestone[]): number {
  return billableMilestones(milestones).filter((milestone) => !isConfirmed(milestone)).length;
}

/** 아직 통과하지 않은 작업 대금의 합. */
export function remainingAmount(milestones: Milestone[]): number {
  return billableMilestones(milestones)
    .filter((milestone) => !isConfirmed(milestone))
    .reduce((sum, milestone) => sum + milestone.price, 0);
}

/** 통과 확정된 작업 수. `통과 1건 · 4건 중` 의 앞 숫자입니다. */
export function confirmedCount(milestones: Milestone[]): number {
  return milestones.filter(isConfirmed).length;
}

/**
 * 가장 이른 자동 통과 시각. 확인 요청을 받은 작업이 여럿이면 제일 먼저 넘어가는
 * 것을 알립니다 (R-QA-02). 해당되는 작업이 없으면 null 입니다.
 */
export function nearestAutoPassAt(milestones: Milestone[]): string | null {
  const times = milestones
    .filter((milestone) => !isConfirmed(milestone))
    .map((milestone) => milestone.autoPassAt)
    .filter((at): at is string => at !== null);

  return times.length === 0 ? null : times.reduce((a, b) => (a < b ? a : b));
}

/**
 * 확인 요청 뒤 답이 없을 때 자동으로 통과되는 기간(일).
 * R-QA-02: 확인 요청 후 무응답 시 자동 통과.
 */
export const AUTO_PASS_DAYS = 3;

/**
 * 컨설팅 세션이 끝난 뒤 의뢰인이 이의를 낼 수 있는 기간(일). 지나면 대금이 개발자에게 나갑니다.
 * R-CON-10: 이의 없으면 자동 지급. 값이 같아도 R-QA-02 와 다른 규칙입니다.
 */
export const CONSULTING_DISPUTE_DAYS = 3;

export type ConsultingPaymentStage = "developer" | "cancelled" | "unsigned" | "paid" | "pay";

/**
 * 컨설팅 결제 화면에서 무엇을 보여 줄지.
 *
 * 결제는 의뢰인이 자기가 서명한 계약서에만 합니다 (R-CON-20). 취소된 계약은 받지 않고
 * (R-CON-21), 이미 결제했으면 다시 받지 않습니다.
 *
 * @param depositedAmount 예치된 금액. 예치를 건 적이 없으면 null. 결과가 나오기 전에는 0 입니다.
 */
export function consultingPaymentStage(
  contract: Contract,
  received: boolean,
  depositedAmount: number | null,
): ConsultingPaymentStage {
  if (received) return "developer";
  if (isCancelled(contract)) return "cancelled";
  if (contract.clientSignature?.isSigned !== true) return "unsigned";
  if (depositedAmount !== null && depositedAmount > 0) return "paid";
  return "pay";
}

/**
 * 방 머리 배지에 적는 개발 계약의 단계.
 *
 * 화면이 계약 상태를 직접 분기하지 않도록 한 곳에서 이름을 붙입니다.
 * 개발 계약이 아니면 null 이고, 그때는 방 상태로 대신 적습니다.
 *
 * 개발 쪽 상태는 계약서를 처음 쓸 때부터 `SIGNED` 입니다. 양쪽이 서명했는지는 공통 계약
 * 상태가 가지므로(R-CON-01) 그것부터 봅니다.
 */
export function developmentStageLabel(contract: Contract | null): string | null {
  if (contract === null) return "계약 전";
  const status = contract.development?.status;
  if (status === undefined) return null;
  if (contract.status === "CANCELLED") return "취소됨";
  if (contract.status !== "SIGNED") return "계약 전";
  if (contract.development !== null && isPayoutPending(contract.development)) return PAYOUT_PENDING_LABEL;

  const labels: Record<DevelopmentStatus, string> = {
    SIGNED: "예치 대기",
    DEPOSITED: "착수 전",
    IN_PROGRESS: "진행 중",
    AFTER_SERVICE: "하자보수",
    COMPLETED: "완료",
    TERMINATED: "중단",
  };
  return labels[status];
}

/**
 * 작업이 돌기 시작했는지. 방 머리가 개설일 대신 작업 건수를 적는 기준입니다.
 * 착수 전까지는 아직 보여줄 진행이 없어 언제 열린 방인지가 더 쓸모 있습니다.
 */
export function isUnderway(contract: Contract | null): boolean {
  const status = contract?.development?.status;
  return (
    status === "IN_PROGRESS" ||
    status === "AFTER_SERVICE" ||
    status === "COMPLETED"
  );
}

/**
 * 작업에 잡은 기간의 합(일). 화면의 `3건 · 합계 650,000원 · 20일` 뒤 숫자입니다.
 * 기간을 정하지 않은 작업은 0 으로 셉니다.
 */
export function totalWorkDays(milestones: Milestone[]): number {
  return milestones.reduce((sum, milestone) => sum + (milestone.workDate ?? 0), 0);
}
