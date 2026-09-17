/**
 * 정산(영역 7) 계약. 목 Swagger `/service/docs` 의 「7 정산」 태그와 필드가 같습니다.
 * 명세에 없는 필드를 더하지 않습니다.
 *
 * 한 사람이 의뢰인이면서 개발자일 수 있어 소비와 소득을 함께 봅니다.
 * 두 응답은 서로 다른 API 이고 합치지 않습니다.
 */

/**
 * 돈이 한 번 움직인 기록. 소비·소득·예치금이 모두 이 모양을 씁니다.
 */
export interface MoneyFlowItem {
  /** 관련 계약 식별자. */
  contractId: number;
  /** 무엇에 대한 돈인지. */
  title: string;
  /** 상대 이름. 의뢰인이 보면 개발자, 개발자가 보면 의뢰인입니다. */
  counterpartName: string;
  /** 원 단위 정수. 환불처럼 되돌아온 돈은 음수입니다. */
  amount: number;
  occurredAt: string;
  state: MoneyFlowState;
}

/**
 * 한 줄의 상태.
 * - `DEPOSITED` 예치됨 · `CONFIRMED` 통과됨 · `AWAITING` 확인 대기
 * - `PAID` 지급 완료 · `REFUNDED` 환불됨
 *
 * 목 명세가 문자열로 두어 서버가 새 값을 더할 수 있습니다. 화면은 모르는 값을
 * 만나도 깨지지 않게 다룹니다(`lib/settlement.ts`).
 */
export type MoneyFlowState =
  | "DEPOSITED"
  | "CONFIRMED"
  | "AWAITING"
  | "PAID"
  | "REFUNDED";

/** GET /api/settlements/spending — 의뢰인이 이번 달 쓴 돈. */
export interface SpendingResponse {
  /** 이번 달 합계. 환불이 이미 반영된 값입니다. */
  monthlyTotal: number;
  /** 거래가 없으면 빈 배열입니다. */
  items: MoneyFlowItem[];
}

/** 정산 계좌. 전체 번호는 내려오지 않습니다. */
export interface SettlementAccount {
  accountId: number;
  bankName: string;
  /** 가려진 계좌번호. */
  maskedNumber: string;
  accountHolder: string;
}

/** GET /api/settlements/income — 개발자가 받을 돈. */
export interface IncomeResponse {
  /** 계약이 끝나면 받을 돈. */
  expectedAmount: number;
  /** 그중 아직 확인이 남은 금액. */
  awaitingAmount: number;
  awaitingCount: number;
  items: MoneyFlowItem[];
  /** 등록하지 않았으면 null. 화면은 등록 안내로 바뀝니다. */
  settlementAccount: SettlementAccount | null;
}

/** 계약 하나에 걸린 예치금. */
export interface ContractDeposit {
  contractId: number;
  projectName: string;
  counterpartName: string;
  /** 맡긴 돈. */
  depositedAmount: number;
  /** 이미 나간 돈. */
  releasedAmount: number;
  /** 아직 남은 돈. */
  remainingAmount: number;
  items: MoneyFlowItem[];
}

/** GET /api/settlements/deposits — 맡긴 돈이 지금 어디에 걸려 있는지. */
export interface DepositsResponse {
  totalRemaining: number;
  awaitingCount: number;
  contracts: ContractDeposit[];
}

/** 원장 한 줄. 이미 일어난 입출금입니다. */
export interface LedgerEntry {
  occurredAt: string;
  description: string;
  /** 들어오면 양수, 나가면 음수. */
  amount: number;
  /** 이 줄이 반영된 뒤의 예치 잔액. */
  balance: number;
}

/**
 * GET /api/contracts/{contractId}/ledger — 계약 하나의 예치금 원장.
 * `depositedAmount` 는 `confirmedAmount` 와 `remainingAmount` 의 합입니다.
 */
export interface ContractLedger {
  contractId: number;
  projectName: string;
  counterpartName: string;
  depositedAmount: number;
  confirmedAmount: number;
  remainingAmount: number;
  /** 오래된 것이 먼저입니다. */
  entries: LedgerEntry[];
  /** 앞으로 나갈 돈. */
  upcoming: MoneyFlowItem[];
}
