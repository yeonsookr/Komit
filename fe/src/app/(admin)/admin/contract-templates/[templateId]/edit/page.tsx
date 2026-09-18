import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { AdminShell, ContractTemplateForm } from "@/components/domains/admin";
import { Breadcrumb, PageHeader } from "@/components/common";
import { requireAdminLogin } from "@/lib/admin/guard";
import { getAdminContractTemplate, getAdminContractTemplates, getAdminSummary } from "@/lib/api/services";
import { ApiError } from "@/types/api";

export const metadata: Metadata = {
  title: "양식 개정 | 운영 콘솔",
  robots: { index: false },
};

/**
 * 계약 양식 개정 (`9_양식편집`).
 *
 * **원본을 덮어쓰지 않습니다.** 저장하면 새 판이 만들어지고 지금 판은 보관으로
 * 내려갑니다. 그래서 화면 이름도 「수정」이 아니라 「개정」입니다. (`R-CON-05`)
 */
export default async function EditAdminContractTemplatePage({
  params,
}: Readonly<{ params: Promise<{ templateId: string }> }>) {
  const { templateId: raw } = await params;
  if (!/^\d+$/.test(raw)) notFound();
  const templateId = Number(raw);

  const loaded = await requireAdminLogin(async () => {
    try {
      return await Promise.all([
        getAdminSummary(),
        getAdminContractTemplates(),
        getAdminContractTemplate(templateId),
      ]);
    } catch (error) {
      // 없는 판을 고치러 왔으면 오류 화면이 아니라 404 입니다.
      if (error instanceof ApiError && error.status === 404) return null;
      throw error;
    }
  });
  if (loaded === null) notFound();

  const [summary, list, source] = loaded;

  return (
    <AdminShell asOf={summary.asOf} admin={summary.admin}>
      <PageHeader
        breadcrumb={<Breadcrumb items={["운영 콘솔", "계약 양식", "개정"]} />}
        title="양식 개정"
      />
      <ContractTemplateForm mode="revise" types={list.types} source={source} />
    </AdminShell>
  );
}
