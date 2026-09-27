"use client";

import * as React from "react";
import { Button } from "@/components/buttons";
import { Badge, Input, NativeSelect } from "@/components/common";
import { useToast } from "@/components/feedback";
import { INTERNAL } from "@/lib/api/endpoints";
import { appPath } from "@/lib/basePath";
import {
  DELIVERABLE_TITLE_MAX_LENGTH,
  DELIVERABLE_URL_MAX_LENGTH,
  isHttpUrl,
  type HandoverRegistration,
} from "@/lib/contract";
import { downloadFromBrowser, oneAtATime } from "@/lib/download/browserDownload";
import type { Deliverable, DeliverableAccessStage, DeliverableMaterial } from "@/types/contract";

/**
 * 진행 중인 요청을 가리키는 열쇠. 작업·산출물·계약 식별자가 같은 숫자여도 겹치지 않게 종류를 붙입니다.
 * 로딩 표시는 이 열쇠가 같은 버튼에만 붙습니다.
 */
export const busyKey = {
  milestone: (milestoneId: number) => `milestone:${milestoneId}`,
  file: (deliverableId: number) => `file:${deliverableId}`,
  url: (deliverableId: number) => `url:${deliverableId}`,
  deliverable: (contractId: number) => `deliverable:${contractId}`,
};

/** 올렸지만 산출물에 붙이지 못한 파일. 파일을 다시 올리지 않고 붙이기만 다시 합니다. */
export interface UnattachedFile {
  deliverableId: number;
  fileName: string;
}

export interface DeliverableFilesProps {
  deliverables: Deliverable[];
  /**
   * 개발자가 보는 화면인지. 개발자는 올리고 의뢰인은 받습니다.
   * 접힘 줄의 문구도 갈립니다 — 자기가 올린 것을 「개발자가」라고 부르지 않습니다.
   */
  received: boolean;
  /**
   * 대금 지급이 끝났는지. 개발 계약이 `COMPLETED` 일 때입니다. 의뢰인은 이때부터
   * 지급 후 해금(PAID) 파일을 받습니다 (R-QA-06).
   */
  paid?: boolean;
  /**
   * 지금 인계 자료를 등록할 수 있는지. 개발자 화면에서만 씁니다. 닫혔으면 파일·URL·산출물 추가를 모두
   * 잠그고 까닭을 적습니다 (R-QA-06). 주지 않으면 열린 것으로 봅니다.
   */
  registration?: HandoverRegistration;
  /** 파일을 골랐을 때. 개발자 화면에서만 씁니다. */
  onPick?: (deliverable: Deliverable, file: File, accessStage: DeliverableAccessStage) => void;
  /** 인수 시험 URL 을 적었을 때. 성공하면 true 를 돌려주고, 그때만 칸을 비웁니다. */
  onAddUrl?: (deliverable: Deliverable, url: string) => Promise<boolean>;
  /** 산출물 항목을 더할 때. 성공하면 true 를 돌려주고, 그때만 칸을 비웁니다. */
  onAddDeliverable?: (title: string) => Promise<boolean>;
  /** 붙이지 못한 파일. 그 산출물 줄에 다시 붙이기를 보입니다. */
  unattached?: UnattachedFile | null;
  onRetryAttach?: () => void;
  /** 진행 중인 요청의 열쇠(`busyKey`). 무엇이든 진행 중이면 입력을 모두 잠급니다. */
  busy?: string | null;
  /** 계약 식별자. 산출물 추가 버튼의 로딩 열쇠에 씁니다. */
  contractId?: number;
  /** 처음에 펼쳐 둘지. 주지 않으면 산출물이 없을 때만 펼칩니다. */
  defaultOpen?: boolean;
}

/** 아무 자료도 없는 산출물인지. 예전 단일 파일 칸(`fileId`)도 자료로 셉니다. */
export function hasNoMaterial(deliverable: Deliverable): boolean {
  return deliverable.materials.length === 0 && deliverable.fileId === null;
}

/** 등록한 자료 수. 자료가 없는 산출물에 남은 예전 단일 파일도 하나로 셉니다. */
export function materialCount(deliverables: Deliverable[]): number {
  return deliverables.reduce(
    (sum, deliverable) =>
      sum + deliverable.materials.length + (deliverable.materials.length === 0 && deliverable.fileId !== null ? 1 : 0),
    0,
  );
}

/** 자료를 종류별로 나눕니다. 각 묶음 안에서는 등록한 순서를 지킵니다. */
export function groupMaterials(materials: DeliverableMaterial[]): {
  urls: DeliverableMaterial[];
  testFiles: DeliverableMaterial[];
  paidFiles: DeliverableMaterial[];
} {
  const files = materials.filter((item) => item.url === null);
  return {
    urls: materials.filter((item) => item.url !== null),
    testFiles: files.filter((item) => item.accessStage === "TEST"),
    paidFiles: files.filter((item) => item.accessStage === "PAID"),
  };
}

/**
 * 같은 종류가 여럿이면 번호를 붙인 이름. 자료 응답에 파일 이름이 없어 번호로 구분합니다(`해금 파일 2`).
 * 하나뿐이면 번호를 붙이지 않습니다.
 */
export function numbered(base: string, index: number, count: number): string {
  return count > 1 ? `${base} ${index + 1}` : base;
}

/** 닫힌 까닭. 서버가 같은 경우에 돌려주는 오류와 뜻이 같습니다 (R-QA-06). */
const CLOSED_REASON: Record<Exclude<HandoverRegistration, "OPEN">, string> = {
  HANDOVER_NOT_OPEN: "일반 작업을 모두 통과한 뒤에 인계 자료를 등록할 수 있습니다.",
  HANDOVER_UNDER_REVIEW:
    "최종 인수 시험 중이라 인계 자료를 바꿀 수 없습니다. 의뢰인이 일부만 통과시켜 돌아오면 다시 등록할 수 있습니다.",
  FINAL_ACCEPTANCE_PASSED: "최종 인수 시험을 통과해 인계 자료를 더 바꿀 수 없습니다.",
};

/** 파일 용도의 뜻. 고르기 전에는 무엇을 골라야 하는지 적습니다. */
const STAGE_HELP: Record<DeliverableAccessStage | "NONE", string> = {
  NONE: "용도를 먼저 고르면 파일을 고를 수 있습니다.",
  TEST: "인수 시험용은 지급 전에도 의뢰인이 받아 시험합니다.",
  PAID: "지급 후 해금 파일은 대금 지급이 끝나야 의뢰인에게 열립니다.",
};

const LINK_CLASS =
  "rounded-sm text-[13px] font-semibold text-primary hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-wait disabled:text-fg-subtle disabled:no-underline";
const LOCKED_CLASS = "text-[13px] text-fg-subtle";

/**
 * 파일 받기 버튼. 링크 대신 요청으로 받아, 권한이 없거나 실패하면 서버가 준 사유를 알립니다.
 * 받는 중에는 잠가 같은 파일을 두 번 받지 않습니다.
 *
 * 받을 주소는 `data-download` 에도 싣습니다. 하위 경로(`appPath`)를 붙인 그대로입니다.
 */
function DownloadButton({ path, label }: Readonly<{ path: string; label: string }>) {
  const showToast = useToast();
  const [loading, setLoading] = React.useState(false);
  const url = appPath(path);
  const download = React.useMemo(
    () =>
      oneAtATime(async () => {
        setLoading(true);
        try {
          await downloadFromBrowser(url, (message) => showToast({ tone: "danger", title: message }));
        } finally {
          setLoading(false);
        }
      }),
    [url, showToast],
  );

  return (
    <button
      type="button"
      data-download={url}
      className={LINK_CLASS}
      disabled={loading}
      aria-busy={loading || undefined}
      onClick={() => void download()}
    >
      {label}
    </button>
  );
}

/** 파일 자료 하나. 지급 전 의뢰인에게는 받기 버튼 대신 등록 사실과 받을 수 있는 때만 보입니다. */
function FileResource({
  item,
  name,
  received,
  paid,
}: Readonly<{ item: DeliverableMaterial; name: string; received: boolean; paid: boolean }>) {
  const locked = item.accessStage === "PAID" && !paid;

  if (locked && !received) {
    return <span className={LOCKED_CLASS}>{name} · 지급 완료 후 받을 수 있습니다</span>;
  }
  return (
    <span className="flex flex-wrap items-baseline gap-x-2">
      <DownloadButton path={INTERNAL.DELIVERABLE_MATERIAL_FILE(item.deliverableMaterialId)} label={`${name} 받기`} />
      {/* 올린 개발자는 언제든 받지만, 의뢰인은 아직 받지 못한다는 것을 함께 적습니다. */}
      {locked && <span className={LOCKED_CLASS}>의뢰인은 지급 완료 후 받습니다</span>}
    </span>
  );
}

/**
 * 산출물 하나에 등록한 자료. URL·인수 시험 파일·해금 파일 순서로 묶어 보이고, URL 이 있다고 파일을
 * 숨기지 않습니다.
 *
 * 지급 전 PAID 파일은 의뢰인에게 받기 버튼 대신 받을 수 있는 때를 알립니다. 서버도 409 로 막지만,
 * 누른 뒤에야 막히지 않게 여기서 먼저 가립니다 (R-QA-06). URL 자료는 새 창 링크 그대로입니다.
 */
export function DeliverableResources({
  deliverable,
  received = false,
  paid = false,
}: Readonly<{ deliverable: Deliverable; received?: boolean; paid?: boolean }>) {
  const { urls, testFiles, paidFiles } = groupMaterials(deliverable.materials);

  return (
    <>
      {urls.map((item) => (
        <a
          key={item.deliverableMaterialId}
          href={item.url ?? undefined}
          target="_blank"
          rel="noopener noreferrer"
          className="break-all text-[13px] text-fg-secondary hover:underline"
        >
          인수 시험 URL: {item.url}
        </a>
      ))}
      {testFiles.map((item, index) => (
        <FileResource
          key={item.deliverableMaterialId}
          item={item}
          name={numbered("인수 시험 파일", index, testFiles.length)}
          received={received}
          paid={paid}
        />
      ))}
      {paidFiles.map((item, index) => (
        <FileResource
          key={item.deliverableMaterialId}
          item={item}
          name={numbered("해금 파일", index, paidFiles.length)}
          received={received}
          paid={paid}
        />
      ))}
      {/* 예전 단일 파일은 TEST·PAID 구분이 없어 해금 파일로 다룹니다 (R-QA-06). */}
      {deliverable.materials.length === 0 && deliverable.fileId !== null && (
        received || paid ? (
          <DownloadButton path={INTERNAL.DELIVERABLE_FILE(deliverable.deliverableId)} label="기존 파일 받기" />
        ) : (
          <span className={LOCKED_CLASS}>기존 파일은 지급 완료 후 받을 수 있습니다</span>
        )
      )}
    </>
  );
}

/**
 * 적은 값을 보내고 칸에 남길 값을 정합니다. 형식이 틀리면 보내지 않고 사유를 돌려주고, 보낸 뒤에는
 * 성공했을 때만 칸을 비웁니다. 실패하면 적은 것을 그대로 두어 고쳐서 다시 보낼 수 있습니다.
 */
export async function submitInput(
  value: string,
  send: (trimmed: string) => Promise<boolean>,
  validate?: (trimmed: string) => string | null,
): Promise<{ value: string; error: string | null }> {
  const trimmed = value.trim();
  if (trimmed === "") return { value, error: null };

  const error = validate?.(trimmed) ?? null;
  if (error !== null) return { value, error };

  return { value: (await send(trimmed)) ? "" : value, error: null };
}

/** 인수 시험 URL 형식 검사. 서버와 같은 기준입니다 (R-QA-06). */
function urlError(value: string): string | null {
  return isHttpUrl(value) ? null : "http:// 또는 https:// 로 시작하는 주소를 적어 주십시오.";
}

/** 한 줄 입력과 보내기 버튼. 비어 있으면 보내지 않습니다. */
function AddRow({
  label,
  placeholder,
  action,
  maxLength,
  disabled,
  loading,
  validate,
  onSubmit,
}: Readonly<{
  label: string;
  placeholder: string;
  action: string;
  maxLength: number;
  disabled?: boolean;
  loading?: boolean;
  validate?: (value: string) => string | null;
  onSubmit: (value: string) => Promise<boolean>;
}>) {
  const [value, setValue] = React.useState("");
  const [error, setError] = React.useState<string | null>(null);
  const submit = async () => {
    if (disabled || loading) return;
    const next = await submitInput(value, onSubmit, validate);
    setValue(next.value);
    setError(next.error);
  };

  return (
    <div className="flex min-w-0 flex-1 flex-col gap-1">
      <div className="flex min-w-0 items-center gap-2">
        <Input
          aria-label={label}
          size="sm"
          placeholder={placeholder}
          value={value}
          maxLength={maxLength}
          invalid={error !== null}
          disabled={disabled}
          onChange={(event) => {
            setValue(event.target.value);
            setError(null);
          }}
          onKeyDown={(event) => {
            if (event.key === "Enter") void submit();
          }}
        />
        <Button
          size="sm"
          variant="outline"
          loading={loading}
          disabled={disabled || value.trim() === ""}
          onClick={() => void submit()}
        >
          {action}
        </Button>
      </div>
      {error !== null && (
        <p role="alert" className="text-xs text-danger">
          {error}
        </p>
      )}
    </div>
  );
}

/** 파일 고르기 버튼. 용도를 고르기 전이거나 등록이 닫혔으면 잠깁니다. */
function FilePickButton({
  label,
  disabled,
  loading,
  onPick,
}: Readonly<{ label: string; disabled: boolean; loading: boolean; onPick: (file: File) => void }>) {
  const inputRef = React.useRef<HTMLInputElement>(null);

  return (
    <>
      <input
        ref={inputRef}
        type="file"
        className="sr-only"
        tabIndex={-1}
        aria-hidden
        disabled={disabled || loading}
        onChange={(event) => {
          const file = event.target.files?.[0];
          if (file) onPick(file);
          event.target.value = "";
        }}
      />
      <Button
        size="sm"
        variant="outline"
        aria-label={label}
        loading={loading}
        disabled={disabled}
        onClick={() => inputRef.current?.click()}
      >
        파일 추가
      </Button>
    </>
  );
}

export interface DeliverableRowProps {
  deliverable: Deliverable;
  received: boolean;
  paid: boolean;
  /** 등록이 닫혔거나 다른 요청이 진행 중이면 입력을 모두 잠급니다. */
  locked: boolean;
  busy: string | null;
  /** 고른 파일 용도. 고르기 전에는 파일을 고를 수 없습니다. */
  stage?: DeliverableAccessStage;
  onStage?: (stage: DeliverableAccessStage) => void;
  /** 이 산출물에 붙이지 못한 파일. 없으면 null. */
  unattached: UnattachedFile | null;
  onRetryAttach?: () => void;
  onPick?: (deliverable: Deliverable, file: File, accessStage: DeliverableAccessStage) => void;
  onAddUrl?: (deliverable: Deliverable, url: string) => Promise<boolean>;
}

/** 산출물 한 줄. 이름, 등록한 자료, 개발자에게는 등록 입력까지 보입니다. */
export function DeliverableRow({
  deliverable,
  received,
  paid,
  locked,
  busy,
  stage,
  onStage,
  unattached,
  onRetryAttach,
  onPick,
  onAddUrl,
}: Readonly<DeliverableRowProps>) {
  const fileKey = busyKey.file(deliverable.deliverableId);

  return (
    <li className="flex flex-col gap-2 px-3 py-2">
      <div className="flex min-w-0 flex-wrap items-center gap-2">
        <span
          className={
            hasNoMaterial(deliverable)
              ? "min-w-0 truncate text-[13px] text-fg-subtle"
              : "min-w-0 truncate text-[13px] text-fg"
          }
        >
          {deliverable.title}
        </span>
        {/* 체결 뒤 더한 항목은 계약 범위·금액·확인 문장을 바꾸지 않습니다 (R-QA-06). */}
        {!deliverable.isContractual && (
          <Badge size="sm" tone="neutral">
            계약 뒤 추가
          </Badge>
        )}
        {!received && hasNoMaterial(deliverable) && (
          <span className="text-[13px] text-fg-subtle">아직 올라오지 않았습니다</span>
        )}
      </div>

      {!hasNoMaterial(deliverable) && (
        <div className="flex min-w-0 flex-col items-start gap-1">
          <DeliverableResources deliverable={deliverable} received={received} paid={paid} />
        </div>
      )}

      {received && unattached !== null && (
        <div className="flex flex-wrap items-center gap-2 text-[13px] text-danger">
          <span className="break-all">‘{unattached.fileName}’ 을 올렸지만 붙이지 못했습니다.</span>
          <Button
            size="sm"
            variant="outline"
            loading={busy === fileKey}
            disabled={locked}
            onClick={onRetryAttach}
          >
            다시 붙이기
          </Button>
        </div>
      )}

      {received && (
        <div className="flex flex-col gap-1">
          <div className="flex flex-wrap items-center gap-2">
            <NativeSelect
              aria-label={`${deliverable.title} 파일 용도`}
              placeholder="파일 용도 선택"
              disabled={locked}
              value={stage ?? ""}
              onChange={(event) => {
                const next = event.target.value;
                if (next === "TEST" || next === "PAID") onStage?.(next);
              }}
            >
              <option value="TEST">인수 시험용</option>
              <option value="PAID">지급 후 해금</option>
            </NativeSelect>
            <FilePickButton
              label={`${deliverable.title} 파일 추가`}
              disabled={locked || stage === undefined}
              loading={busy === fileKey}
              onPick={(file) => {
                if (stage) onPick?.(deliverable, file, stage);
              }}
            />
          </div>
          <p className="text-xs leading-5 text-fg-subtle">{STAGE_HELP[stage ?? "NONE"]}</p>
        </div>
      )}

      {received && onAddUrl && (
        <AddRow
          label={`${deliverable.title} 인수 시험 URL`}
          placeholder="https:// 로 시작하는 인수 시험 주소"
          action="URL 추가"
          maxLength={DELIVERABLE_URL_MAX_LENGTH}
          disabled={locked}
          loading={busy === busyKey.url(deliverable.deliverableId)}
          validate={urlError}
          onSubmit={(url) => onAddUrl(deliverable, url)}
        />
      )}
    </li>
  );
}

/**
 * DeliverableFiles — 인계 작업 안의 인계 자료 줄.
 *
 * **확인 문장과 짝을 짓지 않습니다.** 문장은 의뢰인이 체크하는 것이고 자료는
 * 주고받는 것이라 서로 셈이 다릅니다. 둘을 잇는 연결은 ERD 에도 없습니다.
 *
 * 기본은 접어 둡니다. 자료가 몇 개든 접힌 높이가 한 줄이라, 확인 문장이 자료
 * 목록에 밀려 내려가지 않습니다. 산출물이 하나도 없으면 개발자가 바로 더할 수 있게 펼쳐 둡니다.
 * 등록이 닫힌 까닭은 접혀 있어도 보입니다 — 개발자가 언제 등록할 수 있는지 펼치지 않고도 압니다.
 */
export function DeliverableFiles({
  deliverables,
  received,
  paid = false,
  registration = "OPEN",
  onPick,
  onAddUrl,
  onAddDeliverable,
  unattached = null,
  onRetryAttach,
  busy = null,
  contractId,
  defaultOpen,
}: Readonly<DeliverableFilesProps>) {
  const canAddDeliverable = received && onAddDeliverable !== undefined;
  const [open, setOpen] = React.useState(defaultOpen ?? deliverables.length === 0);
  const [stages, setStages] = React.useState<Record<number, DeliverableAccessStage>>({});

  if (deliverables.length === 0 && !canAddDeliverable) return null;

  const closed = registration !== "OPEN";
  const locked = closed || busy !== null;
  const label = received ? "인계 자료" : "개발자가 등록한 인계 자료";

  return (
    <div className="mt-3 px-4 sm:px-5">
      <button
        type="button"
        onClick={() => setOpen((prev) => !prev)}
        aria-expanded={open}
        className="flex w-full items-center justify-between gap-3 rounded-md bg-surface px-3 py-2.5 text-[13px] font-semibold text-fg-secondary hover:bg-surface-2 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
      >
        <span>
          {label} · 산출물 <span className="font-mono tabular-nums">{deliverables.length}</span>개 · 자료{" "}
          <span className="font-mono tabular-nums">{materialCount(deliverables)}</span>개
        </span>
        <span aria-hidden className="text-fg-subtle">
          {open ? "▴" : "▾"}
        </span>
      </button>

      {received && closed && (
        <p className="mt-2 px-3 text-[13px] leading-6 text-fg-muted">{CLOSED_REASON[registration]}</p>
      )}

      {open && (
        <ul className="mt-1 divide-y divide-line-subtle">
          {deliverables.map((deliverable) => (
            <DeliverableRow
              key={deliverable.deliverableId}
              deliverable={deliverable}
              received={received}
              paid={paid}
              locked={locked}
              busy={busy}
              stage={stages[deliverable.deliverableId]}
              onStage={(stage) => setStages((previous) => ({ ...previous, [deliverable.deliverableId]: stage }))}
              unattached={unattached?.deliverableId === deliverable.deliverableId ? unattached : null}
              onRetryAttach={onRetryAttach}
              onPick={onPick}
              onAddUrl={onAddUrl}
            />
          ))}

          {canAddDeliverable && (
            <li className="px-3 py-2">
              <AddRow
                label="산출물 항목 이름"
                placeholder="더할 산출물 이름 (계약 범위·금액은 바뀌지 않습니다)"
                action="산출물 추가"
                maxLength={DELIVERABLE_TITLE_MAX_LENGTH}
                disabled={locked}
                loading={contractId !== undefined && busy === busyKey.deliverable(contractId)}
                onSubmit={onAddDeliverable}
              />
            </li>
          )}
        </ul>
      )}
    </div>
  );
}
