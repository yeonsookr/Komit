"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/buttons";
import { Checkbox, Chip, Dropdown, SearchInput } from "@/components/common";
import { Popover } from "@/components/feedback";
import {
  SEARCH_MODE_LABEL,
  SEARCH_MODES,
  SORT_OPTIONS,
  toQueryString,
} from "@/lib/developerSearch";
import { ROUTES } from "@/lib/routes";
import type { CodeItem, DeveloperSearchParams, DeveloperSort, SearchMode } from "@/types/developer";

/** 랜딩·검색바 아래에 붙는 입력 예시. 누르면 그대로 검색어가 됩니다. */
const KEYWORD_EXAMPLES = [
  "딸한테 주소를 보내도 안 열려요",
  "예약 버튼이 아무 반응 없어요",
  "갑자기 돈이 40만원 나갔어요",
];

export interface DeveloperSearchFormProps {
  /** 지금 주소가 담고 있는 검색 조건. */
  params: DeveloperSearchParams;
  /** 분야 선택지. `GET /api/codes/DEV_FIELD` 로 받아 서버에서 내려줍니다. */
  fields: CodeItem[];
}

/**
 * DeveloperSearchForm — 검색바 · 분야 선택 · 정렬.
 *
 * 조건을 컴포넌트 상태에 가두지 않고 URL 로 밀어 넣습니다. 새로고침·뒤로가기·
 * 링크 공유에서 같은 목록이 나와야 하기 때문입니다.
 */
export function DeveloperSearchForm({ params, fields }: Readonly<DeveloperSearchFormProps>) {
  const router = useRouter();

  const [mode, setMode] = React.useState<SearchMode>(params.searchMode ?? "problem");
  const [keyword, setKeyword] = React.useState(params.keyword ?? "");

  // 주소가 바뀌면(뒤로가기 등) 입력칸도 따라갑니다.
  React.useEffect(() => setKeyword(params.keyword ?? ""), [params.keyword]);
  React.useEffect(() => setMode(params.searchMode ?? "problem"), [params.searchMode]);

  /** 조건을 바꾸면 늘 첫 장부터 다시 봅니다. */
  const move = (next: DeveloperSearchParams) => {
    const query = toQueryString({ ...params, ...next, page: 1 });
    router.push(query ? `${ROUTES.developers}?${query}` : ROUTES.developers);
  };

  const submit = (event: React.FormEvent) => {
    event.preventDefault();
    move({ searchMode: mode, keyword });
  };

  return (
    <div className="flex flex-col gap-4">
      <form onSubmit={submit} className="flex flex-wrap items-center gap-3">
        <div className="flex min-w-0 flex-1 items-center gap-2 rounded-lg border border-line bg-bg pl-3 focus-within:border-primary">
          <Dropdown
            trigger="text"
            align="start"
            aria-label="검색 방식"
            value={mode}
            onChange={setMode}
            options={SEARCH_MODES.map((value) => ({
              value,
              label: SEARCH_MODE_LABEL[value],
            }))}
          />
          <span aria-hidden className="h-5 w-px bg-line" />
          <SearchInput
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            placeholder={mode === "problem" ? "어떤 문제가 있나요?" : "개발자 이름을 적어주세요"}
            aria-label="검색어"
            className="border-0 focus:ring-0"
          />
        </div>

        <Button type="submit" size="lg">
          검색하기
        </Button>
      </form>

      {mode === "problem" && (
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-[13px] text-fg-subtle">입력 예시</span>
          {KEYWORD_EXAMPLES.map((example) => (
            <Chip
              key={example}
              variant="suggest"
              size="sm"
              onClick={() => {
                setKeyword(example);
                move({ searchMode: "problem", keyword: example });
              }}
            >
              {example}
            </Chip>
          ))}
        </div>
      )}

      <div className="flex flex-wrap items-center gap-2">
        <FieldFilter
          fields={fields}
          selected={params.fieldCodeIds ?? []}
          onApply={(fieldCodeIds) => move({ fieldCodeIds })}
        />
      </div>
    </div>
  );
}

interface FieldFilterProps {
  fields: CodeItem[];
  selected: number[];
  onApply: (fieldCodeIds: number[]) => void;
}

/** 분야 선택 — 펼쳐서 여러 개를 고르고 「적용하기」로 한 번에 반영합니다. */
function FieldFilter({ fields, selected, onApply }: Readonly<FieldFilterProps>) {
  const [open, setOpen] = React.useState(false);
  const [draft, setDraft] = React.useState<number[]>(selected);

  // 열 때마다 지금 적용된 값에서 시작합니다.
  React.useEffect(() => {
    if (open) setDraft(selected);
  }, [open, selected]);

  const toggle = (codeId: number) =>
    setDraft((prev) =>
      prev.includes(codeId) ? prev.filter((id) => id !== codeId) : [...prev, codeId],
    );

  return (
    <>
      <div className="relative">
        <Chip
          variant="filter"
          selected={selected.length > 0}
          onClick={() => setOpen((prev) => !prev)}
          aria-expanded={open}
        >
          분야 선택 ▾
        </Chip>

        <Popover open={open} onClose={() => setOpen(false)} className="mt-2 w-[420px] max-w-[90vw]">
          <p className="px-1 pb-3 text-sm font-bold text-fg">분야 선택</p>

          <ul className="grid grid-cols-1 gap-x-4 gap-y-2 sm:grid-cols-2">
            {fields.map((field) => (
              <li key={field.codeId}>
                <Checkbox
                  label={field.codeName}
                  checked={draft.includes(field.codeId)}
                  onChange={() => toggle(field.codeId)}
                />
              </li>
            ))}
          </ul>

          <div className="mt-4 flex justify-end gap-2 border-t border-line-subtle pt-3">
            <Button variant="outline" size="sm" onClick={() => setDraft([])}>
              초기화
            </Button>
            <Button
              size="sm"
              onClick={() => {
                onApply(draft);
                setOpen(false);
              }}
            >
              적용하기
            </Button>
          </div>
        </Popover>
      </div>

      {/* 고른 분야는 칩으로 남고 ✕ 로 하나씩 뺍니다. */}
      {fields
        .filter((field) => selected.includes(field.codeId))
        .map((field) => (
          <Chip
            key={field.codeId}
            variant="filter"
            selected
            onRemove={() => onApply(selected.filter((id) => id !== field.codeId))}
          >
            {field.codeName}
          </Chip>
        ))}
    </>
  );
}

export interface DeveloperSortSelectProps {
  params: DeveloperSearchParams;
}

/** 정렬 드롭다운. 목록 머리의 우측에 놓입니다. */
export function DeveloperSortSelect({ params }: Readonly<DeveloperSortSelectProps>) {
  const router = useRouter();

  const change = (sort: DeveloperSort) => {
    const query = toQueryString({ ...params, sort, page: 1 });
    router.push(query ? `${ROUTES.developers}?${query}` : ROUTES.developers);
  };

  return (
    <Dropdown
      trigger="text"
      align="end"
      prefix="정렬"
      aria-label="정렬 기준"
      value={params.sort}
      onChange={change}
      options={SORT_OPTIONS}
    />
  );
}
