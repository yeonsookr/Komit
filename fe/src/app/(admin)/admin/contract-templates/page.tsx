import type { Metadata } from "next";
import Link from "next/link";
import { buttonClass } from "@/components/buttons";
import {
  AdminShell,
  ContractTemplatePreview,
  ContractTemplateStatusBadge,
} from "@/components/domains/admin";
import { Breadcrumb, Card, Mono, PageHeader } from "@/components/common";
import { Callout } from "@/components/feedback";
import { formatAdminSlashDate } from "@/lib/admin/format";
import { requireAdminLogin } from "@/lib/admin/guard";
import { contractTemplateStatusLabel } from "@/lib/admin/labels";
import { TEMPLATE_ID_PARAM } from "@/lib/admin/params";
import { getAdminContractTemplate, getAdminContractTemplates, getAdminSummary } from "@/lib/api/services";
import { ROUTES } from "@/lib/routes";

export const metadata: Metadata = {
  title: "계약 양식 | 운영 콘솔",
  robots: { index: false },
};

function templateHref(templateId: number): string {
  return `${ROUTES.adminContractTemplates}?${TEMPLATE_ID_PARAM}=${templateId}`;
}

function firstValue(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}

/**
 * 계약 양식 (`9_계약양식`).
 *
 * **한 줄이 한 판입니다.** 본문을 고치면 기존 줄이 바뀌는 것이 아니라 새 줄이 생기고
 * 옛 판은 「보관」으로 남습니다. 보관된 판을 참조하는 옛 계약은 그대로 그 판을 가리키므로
 * 목록에서 감추면 안 됩니다 — 이미 맺은 계약의 글이 뒤늦게 바뀌면 안 되기 때문입니다.
 * (`R-CON-05`)
 */
export default async function AdminContractTemplatesPage({
  searchParams,
}: Readonly<{
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}>) {
  const requested = Number(firstValue((await searchParams)[TEMPLATE_ID_PARAM]));

  const { summary, list, selected, detail } = await requireAdminLogin(async () => {
    const [summary, list] = await Promise.all([getAdminSummary(), getAdminContractTemplates()]);
    // 고르지 않았거나 없는 판을 가리키면 첫 줄을 펼칩니다.
    const selected =
      list.content.find((item) => item.templateId === requested) ?? list.content[0];
    return {
      summary,
      list,
      selected,
      detail: selected ? await getAdminContractTemplate(selected.templateId) : null,
    };
  });

  return (
    <AdminShell asOf={summary.asOf} admin={summary.admin}>
      <PageHeader
        breadcrumb={<Breadcrumb items={["운영 콘솔", "계약 양식"]} />}
        title="계약 양식"
      />

      <section className="mt-8" aria-labelledby="contract-template-list-title">
        <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-baseline gap-3">
            <h2 id="contract-template-list-title" className="text-lg font-bold text-fg">
              등록된 양식
            </h2>
            <Mono className="text-[13px] text-fg-muted">{list.content.length}판</Mono>
          </div>
          <Link
            href={ROUTES.adminContractTemplateNew}
            className={buttonClass({ variant: "dark", size: "sm" })}
          >
            새 양식 등록
          </Link>
        </div>

        <div className="overflow-x-auto rounded-xl border border-line">
          <table className="w-full min-w-[720px] border-collapse text-left text-[13px]">
            <thead className="bg-surface text-xs text-fg-subtle">
              <tr>
                <th scope="col" className="px-4 py-3 font-medium">유형</th>
                <th scope="col" className="px-4 py-3 font-medium">제목</th>
                <th scope="col" className="px-4 py-3 font-medium">등록</th>
                <th scope="col" className="px-4 py-3 font-medium">등록자</th>
                <th scope="col" className="px-4 py-3 text-right font-medium">상태</th>
              </tr>
            </thead>
            <tbody>
              {list.content.map((template) => {
                // 어느 줄을 골랐는지는 **목록**이 압니다. 본문 응답의 id로 정하면
                // 서버가 다른 판을 돌려줬을 때 고른 줄과 표시가 어긋납니다.
                const current = template.templateId === selected?.templateId;
                return (
                  <tr
                    key={template.templateId}
                    /* 줄 전체가 눌립니다 — 이유는 `9_신고처리` 표와 같습니다. */
                    className={`relative border-t border-line-subtle ${
                      current ? "bg-primary-soft" : "bg-bg hover:bg-surface"
                    }`}
                  >
                    <td className="px-4 py-3.5 text-fg-muted">{template.typeName}</td>
                    <td className="px-4 py-3.5">
                      <Link
                        href={templateHref(template.templateId)}
                        aria-current={current ? "true" : undefined}
                        className="font-medium text-fg underline-offset-4 after:absolute after:inset-0 hover:text-primary hover:underline"
                      >
                        {template.title}
                      </Link>
                    </td>
                    <td className="px-4 py-3.5">
                      <Mono className="text-fg-muted">
                        {formatAdminSlashDate(template.createdAt)}
                      </Mono>
                    </td>
                    <td className="px-4 py-3.5 text-fg-muted">{template.createdBy}</td>
                    <td className="px-4 py-3.5 text-right">
                      <ContractTemplateStatusBadge isActive={template.isActive} />
                    </td>
                  </tr>
                );
              })}
              {list.content.length === 0 && (
                <tr className="border-t border-line-subtle">
                  <td colSpan={5} className="px-4 py-12 text-center text-sm text-fg-subtle">
                    등록된 계약 양식이 없습니다. 첫 양식을 등록하면 판별로 관리할 수 있습니다.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>

      {selected && detail && (
        <section className="mt-10" aria-labelledby="contract-template-body-title">
          <h2 id="contract-template-body-title" className="mb-3 text-lg font-bold text-fg">
            양식 본문
          </h2>

          <Card padding="none" variant="outline" className="overflow-hidden">
            <div className="flex flex-wrap items-center justify-between gap-3 border-b border-line px-6 py-4">
              <p className="text-sm font-semibold text-fg">
                {selected.title}{" "}
                <span className="font-normal text-fg-subtle">
                  · {contractTemplateStatusLabel(selected.isActive)}
                </span>
              </p>
              <div className="flex flex-wrap gap-2">
                <ContractTemplatePreview type={selected.type} body={detail.body} />
                <Link
                  href={ROUTES.adminContractTemplateEdit(selected.templateId)}
                  className={buttonClass({ variant: "primary" })}
                >
                  새 판으로 개정
                </Link>
              </div>
            </div>

            {/* 본문은 HTML 원문 그대로 보여 줍니다. 그려진 모습은 미리보기에서 봅니다. */}
            <pre className="overflow-x-auto px-6 py-5 font-mono text-[13px] leading-7 whitespace-pre-wrap text-fg-muted">
              {detail.body}
            </pre>
          </Card>

          <Callout tone="neutral" size="sm" className="mt-4">
            {"{{ }}"} 자리표시자는 계약 데이터로 채워집니다. 본문을 고치면 새 판이 만들어지고
            이전 판은 보관됩니다.
          </Callout>
        </section>
      )}
    </AdminShell>
  );
}
