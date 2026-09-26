/**
 * 계약 화면의 표시 규칙. 화면에서 분기하지 않도록 순수 함수로 빼 둡니다.
 */
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
 * 초안은 작업 금액이 모두 0 이라 금액으로 인계 작업을 가리는 규칙(`isHandover`)을
 * 쓰지 못합니다. 초안인지 먼저 가린 뒤에 씁니다.
 */
export function isDevelopmentDraft(contract: Contract | null): boolean {
  return (
    contract?.contractType === "DEVELOPMENT" &&
    contract.development !== null &&
    contract.development.startDate === null
  );
}

/**
 * 양쪽이 서명했는지. 서명해야 계약이 성립하고 그 뒤에는 고칠 수 없습니다
 * (R-CON-01 · R-CON-02).
 */
export function isSignedByBoth(contract: Contract): boolean {
  return (
    contract.clientSignature?.isSigned === true &&
    contract.developerSignature?.isSigned === true
  );
}

/**
 * 거래가 끝났는지. 컨설팅과 개발 둘 다 유료 거래라 어느 쪽이든 끝나면 참입니다.
 * 리뷰는 끝난 거래에만 달립니다 (R-REV-02).
 */
export function isCompleted(contract: Contract): boolean {
  const status = contract.development?.status ?? contract.consulting?.status ?? null;
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
 */
export function isHandover(milestone: Milestone): boolean {
  return milestone.price === 0;
}

/** 통과 확정된 작업인지. 되돌릴 수 없습니다 (R-QA-03). */
export function isConfirmed(milestone: Milestone): boolean {
  return milestone.status === "CONFIRMED";
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
