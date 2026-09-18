"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import * as React from "react";
import { Button, buttonClass } from "@/components/buttons";
import { Chip, ChipGroup, Dropdown, Field, Input, SectionCard, Textarea } from "@/components/common";
import { Callout, useToast } from "@/components/feedback";
import { TEMPLATE_ID_PARAM } from "@/lib/admin/params";
import { requestWriteContractTemplate } from "@/lib/admin/requests";
import { ROUTES } from "@/lib/routes";
import { ApiError } from "@/types/api";
import type {
  ContractTemplateDetail,
  ContractTemplateType,
  ContractTemplateTypeOption,
} from "@/types/admin";
import { ContractTemplatePreview } from "./ContractTemplatePreview";

/**
 * 등록과 개정은 **같은 폼이고 같은 요청**입니다. 서버에서도 하는 일이 같아 —
 * 언제나 새 판이 만들어지고 같은 유형에 사용 중이던 판은 보관으로 내려갑니다 —
 * 둘을 가르는 것은 `sourceTemplateId` 하나뿐입니다. (`R-CON-05`)
 *
 * `source` 를 mode 와 묶어 둔 것은 개정인데 고칠 판이 빠지는 조합을 타입에서 막기
 * 위해서입니다. 그 조합이면 원본을 덮어쓰는 것도 아니고 개정도 아닌 요청이 나갑니다.
 */
export type ContractTemplateFormProps =
  | { mode: "create"; types: ContractTemplateTypeOption[]; source?: undefined }
  | { mode: "revise"; types: ContractTemplateTypeOption[]; source: ContractTemplateDetail };

const BODY_HINT = "HTML · 자리표시자는 {{ }} 로 적습니다";

interface FormErrors {
  type?: string;
  title?: string;
  body?: string;
}

/** 422 의 `errors` 를 입력칸에 붙입니다. 어느 칸이 문제인지 서버가 알려준 대로 씁니다. */
function fieldErrors(error: ApiError): FormErrors {
  const found: FormErrors = {};
  for (const item of error.errors ?? []) {
    if (item.field === "title" || item.field === "body" || item.field === "type") {
      found[item.field] = item.reason;
    }
  }
  return found;
}

export function ContractTemplateForm({ mode, types, source }: Readonly<ContractTemplateFormProps>) {
  const router = useRouter();
  const showToast = useToast();

  const [type, setType] = React.useState<ContractTemplateType | undefined>(
    source?.type ?? types[0]?.type,
  );
  // 개정이어도 제목은 비워 둡니다. 새 판의 이름은 운영자가 정하는 것이고,
  // 옛 제목이 남아 있으면 판을 구분할 수 없는 이름으로 저장되기 쉽습니다.
  const [title, setTitle] = React.useState("");
  const [body, setBody] = React.useState(source?.body ?? "");
  const [errors, setErrors] = React.useState<FormErrors>({});
  const [saving, setSaving] = React.useState(false);
  const bodyRef = React.useRef<HTMLTextAreaElement>(null);

  const selectedType = types.find((option) => option.type === type);
  const revising = mode === "revise";

  /** 칩을 누르면 커서 자리에 자리표시자를 끼웁니다. 손으로 옮겨 적다 오타가 나는 것을 막습니다. */
  const insertPlaceholder = (placeholder: string) => {
    const textarea = bodyRef.current;
    const start = textarea?.selectionStart ?? body.length;
    const end = textarea?.selectionEnd ?? body.length;
    setBody(`${body.slice(0, start)}${placeholder}${body.slice(end)}`);
    setErrors((current) => ({ ...current, body: undefined }));
    requestAnimationFrame(() => {
      textarea?.focus();
      textarea?.setSelectionRange(start + placeholder.length, start + placeholder.length);
    });
  };

  const save = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (saving) return;

    const next: FormErrors = {
      ...(type ? {} : { type: "양식 유형을 선택해 주세요." }),
      ...(title.trim() ? {} : { title: "양식 제목을 입력해 주세요." }),
      ...(body.trim() ? {} : { body: "양식 본문을 입력해 주세요." }),
    };
    setErrors(next);
    if (!type || Object.keys(next).length > 0) return;

    setSaving(true);
    try {
      const created = await requestWriteContractTemplate({
        type,
        title: title.trim(),
        body,
        sourceTemplateId: source?.templateId ?? null,
      });
      showToast({
        tone: "success",
        title: revising ? "새 판으로 저장했습니다." : "양식을 등록했습니다.",
        description: created.title,
      });
      router.push(`${ROUTES.adminContractTemplates}?${TEMPLATE_ID_PARAM}=${created.templateId}`);
      router.refresh();
    } catch (error) {
      if (error instanceof ApiError) {
        setErrors(fieldErrors(error));
        showToast({ tone: "danger", title: error.message });
      } else {
        showToast({ tone: "danger", title: "저장하지 못했습니다." });
      }
      setSaving(false);
    }
  };

  return (
    <form onSubmit={save} noValidate>
      <SectionCard
        title="기본 정보"
        note={
          revising
            ? `${source.title} · 고쳐서 새 판을 만듭니다`
            : "새로 등록할 양식의 유형과 제목을 정합니다"
        }
      >
        <div className="space-y-5 py-5">
          <Field label="유형" layout="horizontal" required error={errors.type}>
            {revising ? (
              // 개정에서는 유형을 바꿀 수 없습니다. 바꿔 보내면 서버가 400 을 줍니다.
              <p className="py-2 text-sm text-fg">{source.typeName}</p>
            ) : (
              <Dropdown<ContractTemplateType>
                options={types.map((option) => ({ value: option.type, label: option.typeName }))}
                value={type}
                onChange={(value) => {
                  setType(value);
                  setErrors((current) => ({ ...current, type: undefined }));
                }}
                placeholder="양식 유형 선택"
                className="max-w-[320px]"
              />
            )}
          </Field>

          <Field
            label="제목"
            layout="horizontal"
            htmlFor="contract-template-title"
            required
            error={errors.title}
          >
            <Input
              id="contract-template-title"
              value={title}
              onChange={(event) => {
                setTitle(event.target.value);
                setErrors((current) => ({ ...current, title: undefined }));
              }}
              placeholder="양식 제목을 적어주세요"
              invalid={Boolean(errors.title)}
              disabled={saving}
              containerClassName="max-w-[420px]"
            />
          </Field>
        </div>
      </SectionCard>

      <SectionCard title="양식 본문" note={BODY_HINT}>
        <div className="space-y-4 py-5">
          <div>
            <p className="text-[13px] font-semibold text-fg-secondary">넣을 값</p>
            <ChipGroup className="mt-2" aria-label="쓸 수 있는 자리표시자">
              {selectedType?.placeholders.map((placeholder) => (
                <Chip
                  key={placeholder}
                  variant="suggest"
                  size="sm"
                  disabled={saving}
                  onClick={() => insertPlaceholder(placeholder)}
                >
                  <span className="font-mono">{placeholder}</span>
                </Chip>
              ))}
            </ChipGroup>
          </div>

          <Field htmlFor="contract-template-body" error={errors.body}>
            <Textarea
              ref={bodyRef}
              id="contract-template-body"
              value={body}
              onChange={(event) => {
                setBody(event.target.value);
                setErrors((current) => ({ ...current, body: undefined }));
              }}
              invalid={Boolean(errors.body)}
              disabled={saving}
              spellCheck={false}
              aria-label="양식 본문"
              className="min-h-[320px] font-mono text-[13px]"
              placeholder="양식 본문을 HTML로 적어주세요"
            />
          </Field>
        </div>
      </SectionCard>

      <Callout tone="neutral" size="sm" className="mt-4">
        {"{{ }}"} 자리표시자는 계약 데이터로 채워집니다.{" "}
        {revising
          ? "저장하면 새 판이 만들어지고 지금 판은 보관으로 내려갑니다."
          : "등록하면 바로 사용 중이 되고, 같은 유형에 사용 중인 판이 있으면 그 판은 보관으로 내려갑니다."}
      </Callout>

      <div className="mt-6 flex flex-wrap justify-end gap-2">
        <Link href={ROUTES.adminContractTemplates} className={buttonClass({ variant: "outline" })}>
          취소
        </Link>
        <ContractTemplatePreview type={type} body={body} />
        <Button type="submit" variant="dark" loading={saving}>
          {revising ? "새 판으로 저장" : "등록"}
        </Button>
      </div>
    </form>
  );
}
