/**
 * 계약 타입. 목 Swagger `/service/docs` 영역 4·5 의 ContractResponse 와
 * 필드가 같습니다. 명세에 없는 필드를 더하지 않습니다.
 */

/** 계약 갈래. 채워지는 쪽만 값이 있고 반대쪽은 늘 null 입니다. */
export type ContractType = "CONSULTING" | "DEVELOPMENT";

/** 계약서 진행. 양쪽이 서명해야 SIGNED 입니다 (R-CON-01). */
/** `CANCELLED` 는 개발자가 서명하기 전에 의뢰인이 취소한 계약입니다. 되돌리지 않습니다 (R-CON-21). */
export type ContractStatus = "NEGOTIATING" | "AGREED" | "SIGNED" | "CANCELLED";

/**
 * 개발 진행. 계약서를 처음 쓸 때부터 SIGNED 이고, 예치가 끝나면 DEPOSITED,
 * 착수하면 IN_PROGRESS 가 됩니다. AFTER_SERVICE · COMPLETED · TERMINATED 는
 * 운영 콘솔에서 다룹니다.
 *
 * SIGNED 여도 계약이 성립한 것은 아닙니다. 양쪽이 서명했는지는 `Contract.status` 로 봅니다 (R-CON-01).
 */
export type DevelopmentStatus =
  | "SIGNED"
  | "DEPOSITED"
  | "IN_PROGRESS"
  | "AFTER_SERVICE"
  | "COMPLETED"
  | "TERMINATED";

/** 컨설팅 진행. */
export type ConsultingStatus =
  | "SIGNED"
  | "DEPOSITED"
  | "IN_PROGRESS"
  | "COMPLETED"
  | "TERMINATED";

/**
 * 작업 진행. 화면에서는 '작업', ERD 에서는 마일스톤입니다.
 * SUBMITTED 는 개발자가 확인을 요청한 상태, CONFIRMED 는 의뢰인이 통과시킨 상태입니다.
 */
export type MilestoneStatus =
  | "PROPOSED"
  | "REJECTED"
  | "SCHEDULED"
  | "IN_PROGRESS"
  | "SUBMITTED"
  | "CONFIRMED";

/**
 * 계약의 확인 문장 하나.
 *
 * 진단 쪽 `QaCriterion`(types/diagnosis.ts)과 이름이 비슷하지만 다릅니다 —
 * 계약에는 개발자가 직접 적은 작업 전용 문장이 있어 `issueId` 가 null 일 수 있습니다.
 * 섞이지 않게 이름을 나눠 둡니다.
 */
export interface ContractQaCriterion {
  qaCriterionId: number;
  /** 무엇을 해보면 되는지. 적지 않았으면 null. */
  testCase: string | null;
  /** 확인하기 전에 갖춰져야 할 조건. 없으면 null. */
  condition: string | null;
  /** RESOLVED 면 의뢰인이 체크한 것입니다. */
  status: "UNRESOLVED" | "RESOLVED";
  /** 진단에서 나온 요구사항이면 그 식별자. 작업 전용 문장이면 null. */
  issueId: number | null;
}

/** 작업 하나. */
export interface Milestone {
  milestoneId: number;
  /** 붙은 요구사항. 계약서를 다시 쓸 때 이 값을 그대로 보냅니다. 요구사항에 붙지 않은 작업이면 null. */
  issueId: number | null;
  title: string;
  /** 작업 설명. 계약서에서 바꾼 설명, 없으면 요구사항의 설명. 둘 다 없으면 null. */
  detail: string | null;
  /** 이 작업이 다루는 요구사항의 위치. 개발자가 직접 만든 작업이면 null. */
  location: string | null;
  /**
   * 이 작업에 잡은 기간(일). 최종 납기일이 어떻게 나왔는지 보여주는 참고값이라
   * **계약 전에만 화면에 보여줍니다.** 정하지 않았으면 null.
   */
  workDate: number | null;
  /** 원 단위 정수. 인계 작업은 0 이며 화면에 가격을 보여주지 않습니다. */
  price: number;
  status: MilestoneStatus;
  /** 거부 사유. 거부되지 않았으면 null. */
  rejectMessage: string | null;
  /** 개발자가 확인 요청을 보낸 시각. 자동 통과 기한을 여기서부터 셉니다 (R-QA-02). */
  submittedAt: string | null;
  /** 의뢰인이 통과시킨 시각. 자동 통과일 때도 채워집니다. */
  confirmedAt: string | null;
  /** 답이 없을 때 자동으로 통과되는 시각. 확인 요청 뒤에만 있습니다. */
  autoPassAt: string | null;
  qaCriteria: ContractQaCriterion[];
}

/** 인계물 하나. 소스·계정·설명서처럼 마지막에 넘겨받는 것들입니다 (R-QA-04). */
export interface Deliverable {
  deliverableId: number;
  title: string;
  /** 아직 안 올렸으면 null. */
  fileId: number | null;
  deliveredAt: string | null;
}

/** 계약서에 남긴 서명 하나. */
export interface ContractSignature {
  userId: number;
  isSigned: boolean;
  signedAt: string | null;
  /** 서명판에 그린 이미지. 서명 전이면 null. */
  signatureImageFileId: number | null;
}

/** 계약 전에 서로 남기는 전달 메시지 하나. */
export interface ContractMessage {
  messageId: number;
  content: string;
  /** 거짓이면 개발자가 남긴 것입니다. */
  isClient: boolean;
  createdAt: string;
}

/** 세션 예약. 한 세션에 하나만 있습니다. */
export interface Reservation {
  reservationId: number;
  scheduledAt: string;
  /** 양쪽이 서명하면 CONFIRMED 가 되고 그 뒤에는 일정을 바꾸지 않습니다. */
  status: "PROPOSED" | "CONFIRMED" | "DISABLED";
}

/** 컨설팅 세션 하나. */
export interface ConsultingSession {
  sessionId: number;
  /**
   * 이 세션이 달린 계약. 세션 화면은 방 밖에 있는 별도 화면이라 세션 식별자만으로
   * 열리는데, 끝낸 뒤 어느 방으로 돌아갈지가 이 값에서 나옵니다.
   */
  contractId: number;
  price: number;
  durationMinutes: number;
  reservation: Reservation;
  startedAt: string | null;
  endedAt: string | null;
  /** 세션 중 실시간 채팅 참조 키. 메시지와 화상은 이 API 가 다루지 않습니다. */
  chatRefId: string;
}

/**
 * 세션 참가자 한 사람의 기록.
 *
 * **여기 있다는 것이 곧 동의했다는 뜻입니다** (`R-CON-17`). 화면은 이 목록으로 자기가
 * 이미 동의했는지 가려, 세션 화면을 열지 방으로 돌려보낼지 정합니다.
 */
export interface SessionParticipation {
  userId: number;
  consentedAt: string;
  /** 실제로 들어간 시각. 상대 동의를 기다리는 중이면 `null` 입니다. */
  joinedAt: string | null;
}

/**
 * 올라간 세션 녹음 하나.
 *
 * **참가자마다 한 건입니다.** 두 사람 소리를 한 파일로 합치지 않습니다 — 파일이 곧
 * 화자라서 나중에 누가 말했는지 가리는 데 손이 들지 않습니다. (`R-CON-14`)
 */
export interface SessionRecording {
  recordingId: number;
  speaker: "CLIENT" | "DEVELOPER";
  fileId: number;
  uploadedAt: string;
}

/** 컨설팅 계약에만 있는 내용. 개발 계약이면 null 입니다. */
export interface ConsultingAgreement {
  status: ConsultingStatus;
  /** 속한 세션 금액의 합. */
  totalAmount: number;
  /**
   * 이 세션에서 다룰 것. 개발자가 한 줄로 적습니다.
   *
   * **요구사항 식별자를 담지 않습니다.** 컨설팅은 요구사항 하나를 작업으로 만드는 것이
   * 아니라 한 번 만나 여러 갈래를 함께 보는 자리라 요청서의 어느 줄에 매이지 않습니다.
   */
  topic: string;
  /** 양쪽이 합의하고 서명할 구체적인 상담 내용. 적지 않았으면 null. */
  detail: string | null;
  sessions: ConsultingSession[];
}

/** 개발 계약에만 있는 내용. 컨설팅 계약이면 null 입니다. */
export interface DevelopmentAgreement {
  status: DevelopmentStatus;
  /** 계약 금액. 작업별 금액은 이것의 배분 표시입니다 (R-CON-04). */
  totalAmount: number;
  /**
   * 이 계약에서 다룰 요구사항. 개발 계약에만 있습니다 — 요구사항 하나가 작업 하나가
   * 되므로 어느 줄을 맡기로 했는지가 곧 계약의 내용입니다.
   */
  issueIds: number[];
  /** 정하기 전이면 null. */
  startDate: string | null;
  /** 최종 납기일. 작업마다 따로 납기를 두지 않습니다. */
  dueDate: string | null;
  /** 무상 하자보수 기간(일). */
  freeRepairDays: number;
  /** 화면에 보여줄 순서대로 옵니다. */
  milestones: Milestone[];
  deliverables: Deliverable[];
}

/** 계약서 한 건. */
export interface Contract {
  contractId: number;
  roomId: number;
  contractType: ContractType;
  status: ContractStatus;
  consulting: ConsultingAgreement | null;
  development: DevelopmentAgreement | null;
  clientSignature: ContractSignature | null;
  developerSignature: ContractSignature | null;
  /** 계약 전에 주고받은 전달 메시지. */
  messages: ContractMessage[];
  /** 체결된 계약서 원문. 체결 전이면 null. */
  contractText: string | null;
  updatedAt: string;
}

/* ------------------------------------------------------------------ */
/* 요청                                                                */
/* ------------------------------------------------------------------ */

/**
 * 계약서에 담을 작업 하나.
 *
 * 작업은 언제나 요구사항 하나에 붙습니다. 요청서나 이 계약에 있던 요구사항이면 `issueId` 를
 * 보내고, 새로 합의한 작업이면 빼고 `title` 을 보냅니다. 서버가 계약용 요구사항을 만들어 붙입니다.
 */
export interface WriteMilestoneRequest {
  issueId?: number | null;
  /** 계약서에서 쓰는 이름. 새 작업이면 반드시 보냅니다. 1~200자. */
  title?: string | null;
  /** 계약서에서 쓰는 설명. 1~1000자. 기존 요구사항에서 보내지 않으면 그 요구사항의 설명을 씁니다. */
  detail?: string | null;
  /** 인계 작업은 0 으로 보냅니다. */
  price: number;
  /** 이 작업에 잡은 기간(일). 정하지 않아도 됩니다. */
  workDate?: number | null;
  /** 하나 이상이어야 합니다. */
  qaCriteria: string[];
}

/**
 * 개발 계약서 작성 요청.
 *
 * 계약에서 다룰 요구사항은 작업이 가리키는 것입니다. 목록을 따로 보내지 않습니다.
 * 같은 요구사항을 두 작업에 담을 수 없습니다.
 */
export interface WriteDevelopmentContractRequest {
  startDate: string;
  dueDate: string;
  freeRepairDays: number;
  /** 가격의 합이 계약 금액이 됩니다. 하나 이상이어야 합니다. */
  milestones: WriteMilestoneRequest[];
  deliverableTitles?: string[];
  message?: string | null;
}

/** 계약서 서명 요청. 서명판에 그린 그림을 먼저 올려 식별자를 받습니다. */
export interface SignContractRequest {
  signatureImageFileId: number;
}

/**
 * 예치 요청.
 *
 * 출금 계좌는 담지 않습니다. 의뢰인이 마이페이지에 등록해 둔 실시간 계좌이체
 * 수단을 고르고, 서버가 그 수단으로 뺍니다. 카드 결제는 쓰지 않습니다.
 */
export interface DepositRequest {
  /** 등록해 둔 수단으로 낼 때만 보냅니다. 비우면 결제창에서 계좌를 고릅니다. */
  paymentMethodId?: number | null;
  /** 같은 결제가 두 번 나가지 않게 하는 키. 재시도할 때 같은 값을 보냅니다. */
  idempotencyKey: string;
}

/**
 * 예치에 쓸 결제 수단 하나.
 * 카드 결제는 쓰지 않으므로 실시간 계좌이체만 내려옵니다.
 */
export interface PaymentMethod {
  paymentMethodId: number;
  methodType: "CARD" | "REALTIME_TRANSFER";
  /** 가린 계좌번호. 화면에 그대로 보여줍니다. */
  maskedNumber: string | null;
  /** 기본 수단인지. 예치 화면이 처음에 고르는 것입니다. */
  isDefault: boolean;
}

/** 예치 상태. 돈의 원장은 Escrow 서버가 가지고 여기서는 확인만 합니다. */
export interface EscrowDeposit {
  contractId: number;
  amount: number;
  /** 예치가 확인된 시각. 확인 전이면 null. */
  depositedAt: string | null;
}

/** 작업 확인 요청. 개발자가 보냅니다. */
export interface SubmitMilestoneRequest {
  /** 의뢰인에게 함께 남길 말. 적지 않아도 됩니다. */
  message?: string | null;
}

/**
 * 확인 결과 저장. 다 체크하지 않아도 보낼 수 있습니다.
 * 체크는 되돌릴 수 없으므로 이번에 체크한 것만 보내고 서버가 더합니다.
 */
export interface SaveQaResultsRequest {
  qaCriterionIds: number[];
}

/** 작업 통과 처리. 확인 문장을 모두 체크해야 합니다 (R-QA-01). */
export interface ConfirmMilestoneRequest {
  qaCriterionIds: number[];
}

/** 계약을 마친 뒤 남기는 리뷰. 점수만 주고 본문을 비워도 됩니다 (R-REV-04). */
export interface WriteReviewRequest {
  /** 개발 실력. 1~5. */
  devScore: number;
  /** 설명력. 1~5. */
  expScore: number;
  content?: string | null;
}

/**
 * 컨설팅 계약서 작성. 개발자가 상담방에서 보냅니다 (`4_개발자_계약서작성`).
 *
 * 개발 계약과 달리 작업을 쪼개지 않습니다. 한 번 만나서 무엇을 다룰지(`topic`),
 * 언제 얼마나(`scheduledAt`·`durationMinutes`), 얼마에(`price`) 하는지가 전부입니다.
 */
export interface WriteConsultingContractRequest {
  /** 이 세션에서 다룰 것. 한 줄로 적습니다. */
  topic: string;
  scheduledAt: string;
  durationMinutes: number;
  price: number;
  detail?: string | null;
  /** 계약서와 함께 상대에게 남기는 말. 계약 조건이 아닙니다. */
  message?: string | null;
}

/** 컨설팅 계약서 수정 제안. **보낸 항목만** 바뀝니다. */
export interface AmendConsultingContractRequest {
  topic?: string | null;
  scheduledAt?: string | null;
  durationMinutes?: number | null;
  price?: number | null;
  detail?: string | null;
  message?: string | null;
}

/** 세션 요약의 항목 하나. 논의·결정·다음 할 일이 같은 모양입니다. */
export interface SummaryItem {
  title: string;
  detail: string | null;
}

/**
 * 요청서에 반영할 수 있는 변경 후보.
 *
 * 요약을 읽는 것과 요청서를 고치는 것은 다른 일입니다. 세션에서 정해진 것이 곧바로
 * 요청서에 들어가지 않고, 의뢰인이 골라 반영해야 바뀝니다.
 */
export interface SummaryChange {
  changeId: number;
  /** UPDATE 는 있는 요구사항을 고치고, ADD 는 새로 넣습니다. */
  kind: "UPDATE" | "ADD";
  /** 고칠 요구사항. 새로 넣는 것이면 null 입니다. */
  issueId: number | null;
  title: string;
  detail: string | null;
  isApplied: boolean;
}

/** 세션이 끝난 뒤 만들어지는 요약 (`4_의뢰인_요약`). */
export interface SessionSummary {
  sessionId: number;
  /** 논의한 것. 요청서에는 반영되지 않습니다. */
  discussed: SummaryItem[];
  decided: SummaryItem[];
  nextActions: SummaryItem[];
  changes: SummaryChange[];
  updatedAt: string;
}

/** 고른 변경 후보를 요청서에 반영합니다. */
export interface ApplySummaryChangesRequest {
  changeIds: number[];
}
