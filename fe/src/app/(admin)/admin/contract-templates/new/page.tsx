import type { Metadata } from "next";
import { AdminShell, ContractTemplateForm } from "@/components/domains/admin";
import { Breadcrumb, PageHeader } from "@/components/common";
import { requireAdminLogin } from "@/lib/admin/guard";
import { getAdminContractTemplates, getAdminSummary } from "@/lib/api/services";

export const metadata: Metadata = {
  title: "새 양식 등록 | 운영 콘솔",
  robots: { index: false },
};

/**
 * 새 계약 양식 등록 (`9_양식등록`).
 *
 * 고를 수 있는 유형과 그 유형에서 쓸 수 있는 자리표시자는 목록 API의 `types` 에서
 * 옵니다. **프론트에 목록을 따로 두지 않습니다** — 서버가 채울 수 있는 자리가 곧
 * 자리표시자이고, 화면이 한 벌 더 가지면 서버가 늘린 날부터 둘이 어긋납니다.
 */
export default async function NewAdminContractTemplatePage() {
  const [summary, list] = await requireAdminLogin(() =>
    Promise.all([getAdminSummary(), getAdminContractTemplates()]),
  );

  return (
    <AdminShell asOf={summary.asOf} admin={summary.admin}>
      <PageHeader
        breadcrumb={<Breadcrumb items={["운영 콘솔", "계약 양식", "새 양식 등록"]} />}
        title="새 양식 등록"
      />
      <ContractTemplateForm mode="create" types={list.types} />
    </AdminShell>
  );
}
