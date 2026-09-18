export interface AdminIdentity {
  adminId: number;
  loginId: string;
}

export interface AdminLoginRequest {
  loginId: string;
  password: string;
}

export interface AdminLoginResponse {
  accessToken: string;
  expiresIn: number;
  admin: AdminIdentity;
}

export type ReconciliationResult =
  | "MATCHED"
  | "CACHE_MISMATCH"
  | "BANK_MISMATCH"
  | "REVIEW_REQUIRED"
  | "ERROR";

export interface AdminSummary {
  admin: AdminIdentity;
  asOf: string;
  reports: { openCount: number; resolvedCount: number };
  reconciliation: {
    lastResult: ReconciliationResult;
    lastRunAt: string;
    exceptionCount: number;
  };
  contractTemplates: { totalCount: number; activeCount: number };
}

export type AdminReportStatus = "OPEN" | "RESOLVED";
export type ContractPartyRole = "CLIENT" | "DEVELOPER";
export type AdminContractType = "CONSULTING" | "DEVELOPMENT";

export interface AdminReportParty {
  userId: number;
  name: string;
  role: ContractPartyRole;
}

export interface AdminReportSummary {
  reportId: number;
  createdAt: string;
  contractId: number;
  projectName: string;
  contractType: AdminContractType;
  reporter: AdminReportParty;
  target: AdminReportParty;
  reason: string;
  status: AdminReportStatus;
  resolvedAt: string | null;
}

export interface AdminReportList {
  content: AdminReportSummary[];
  openCount: number;
  resolvedCount: number;
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface AdminReportDetail extends AdminReportSummary {
  evidenceFileId: number | null;
  evidenceFileName: string | null;
}

export type EscrowStatus =
  | "CREATED"
  | "DEPOSIT_PENDING"
  | "ACTIVE"
  | "COMPLETED"
  | "REFUNDED"
  | "CANCELLED";

export interface ReconciliationEscrow {
  contractId: number;
  escrowId: number;
  partyLabel: string;
  projectName: string;
  pendingCount: number;
  status: EscrowStatus;
  heldAmount: number;
}

export interface ReconciliationRun {
  asOf: string;
  ledgerTotal: number;
  bankBalance: number | null;
  differenceAmount: number | null;
  result: ReconciliationResult;
}

export interface ReconciliationData {
  latest: ReconciliationRun;
  escrows: ReconciliationEscrow[];
  escrowTotalAmount: number;
  escrowTotalCount: number;
  history: ReconciliationRun[];
}

export type ContractTemplateType =
  | "DEV_STANDARD"
  | "CONSULT_STANDARD"
  | "AMENDMENT"
  | "ACCEPTANCE";

export interface ContractTemplateSummary {
  templateId: number;
  type: ContractTemplateType;
  typeName: string;
  title: string;
  isActive: boolean;
  createdAt: string;
  createdBy: string;
}

export interface ContractTemplateTypeOption {
  type: ContractTemplateType;
  typeName: string;
  placeholders: string[];
}

export interface ContractTemplateList {
  content: ContractTemplateSummary[];
  types: ContractTemplateTypeOption[];
}

export interface ContractTemplateDetail extends ContractTemplateSummary {
  body: string;
  placeholders: string[];
}

export interface WriteContractTemplateRequest {
  type: ContractTemplateType;
  title: string;
  body: string;
  sourceTemplateId: number | null;
}

export interface PreviewContractTemplateRequest {
  type: ContractTemplateType;
  body: string;
}

export interface ContractTemplatePreview {
  html: string;
  unknownPlaceholders: string[];
}
