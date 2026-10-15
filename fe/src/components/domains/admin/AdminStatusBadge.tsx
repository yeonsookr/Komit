import { Badge, type BadgeTone } from "@/components/common";
import {
  ADMIN_REPORT_STATUS_LABEL,
  contractTemplateStatusLabel,
  RECONCILIATION_RESULT_LABEL,
} from "@/lib/admin/labels";
import type { AdminReportStatus, ReconciliationResult } from "@/types/admin";

/**
 * 운영 콘솔의 상태 배지.
 *
 * 색은 상태를 **거드는** 것이고 혼자 뜻을 지지 않습니다. 모든 배지가 문구를 함께
 * 달고 있어 색을 구분하지 못해도 읽을 수 있습니다. 색만으로 「일치」와 「차이 발생」을
 * 가르면 그 차이를 못 보는 사람에게는 대사 결과가 사라집니다.
 */
export function AdminReportStatusBadge({ status }: Readonly<{ status: AdminReportStatus }>) {
  return (
    <Badge tone={status === "OPEN" ? "brand" : "neutral"} shape="square" size="sm">
      {ADMIN_REPORT_STATUS_LABEL[status]}
    </Badge>
  );
}

/**
 * 대사 결과.
 *
 * **`MATCHED` 가 아닌 것을 한 색으로 뭉치지 않습니다.** 장부와 잔고가 다른 것은
 * 사람이 맞춰 봐야 할 일(경고)이고, 잔고를 아예 읽지 못한 것은 대사가 돌지 않은
 * 것(위험)이라 운영자가 할 일이 다릅니다.
 */
export function ReconciliationStatusBadge({ result }: Readonly<{ result: ReconciliationResult }>) {
  const tone: BadgeTone =
    result === "MATCHED" ? "success" : result === "ERROR" ? "danger" : "warning";

  return (
    <Badge tone={tone} shape="square" size="sm" dot>
      {RECONCILIATION_RESULT_LABEL[result]}
    </Badge>
  );
}

/** 계약 양식 판의 사용 여부. 보관된 판도 옛 계약이 가리키고 있어 지워지지 않습니다. (`R-CON-05`) */
export function ContractTemplateStatusBadge({ isActive }: Readonly<{ isActive: boolean }>) {
  return (
    <Badge tone={isActive ? "success" : "neutral"} shape="square" size="sm">
      {contractTemplateStatusLabel(isActive)}
    </Badge>
  );
}
