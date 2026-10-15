"use client";

import * as React from "react";
import { Callout } from "@/components/feedback";
import { clientApi } from "@/lib/api/client";
import { INTERNAL } from "@/lib/api/endpoints";
import { canWriteContractWithoutDraft } from "@/lib/contractDraft";
import { ApiError } from "@/types/api";
import type { Contract } from "@/types/contract";
import type { Issue } from "@/types/diagnosis";
import { ContractEditor } from "./ContractEditor";

const CONTRACT_DRAFT_TIMEOUT_MS = 30_000;

export interface DevelopmentContractDraftFormProps {
  roomId: number;
  counterpartName: string;
  /** 새로고침 전에 이미 만들어 둔 AI version 1. */
  initialDraft?: Contract | null;
  /** 요청서의 요구사항. AI 초안을 받지 못하면 이것으로 작업을 채웁니다. */
  requested: Issue[];
}

/**
 * AI 초안을 먼저 받은 뒤, 금액·기간·일정은 개발자가 직접 채우게 합니다.
 *
 * 초안은 요청서의 요구사항을 모두 작업으로 넣고 상담에서 합의한 확인 문장과 작업을 더한
 * 것입니다. 초안을 받지 못하면 요청서만으로 작업을 채워 직접 쓰게 합니다.
 */
export function DevelopmentContractDraftForm({
  roomId,
  counterpartName,
  initialDraft = null,
  requested,
}: Readonly<DevelopmentContractDraftFormProps>) {
  const [draft, setDraft] = React.useState<Contract | null>(initialDraft);
  const [loading, setLoading] = React.useState(initialDraft === null);
  const [fallback, setFallback] = React.useState(false);
  const [errorMessage, setErrorMessage] = React.useState<string | null>(null);
  const requestedRoom = React.useRef<number | null>(null);
  const mounted = React.useRef(false);

  React.useEffect(() => {
    mounted.current = true;
    if (initialDraft !== null || requestedRoom.current === roomId) {
      return () => {
        mounted.current = false;
      };
    }
    requestedRoom.current = roomId;

    clientApi
      .post<Contract>(INTERNAL.CONTRACT_DRAFT(roomId), undefined, {
        timeout: CONTRACT_DRAFT_TIMEOUT_MS,
      })
      .then(({ data }) => {
        if (mounted.current) setDraft(data);
      })
      .catch((error: unknown) => {
        if (!mounted.current) return;
        if (canWriteContractWithoutDraft(error)) {
          setFallback(true);
          return;
        }
        setErrorMessage(
          error instanceof ApiError ? error.message : "계약 초안을 불러오지 못했습니다.",
        );
      })
      .finally(() => {
        if (mounted.current) setLoading(false);
      });

    return () => {
      mounted.current = false;
    };
  }, [initialDraft, roomId]);

  if (loading) {
    return (
      <section className="mt-8" aria-live="polite">
        <h2 className="text-xl font-bold text-fg">개발 계약서 작성</h2>
        <p className="mt-3 text-sm text-fg-muted">요청서와 상담 대화로 계약 초안을 만들고 있습니다.</p>
      </section>
    );
  }

  if (errorMessage !== null) {
    return (
      <Callout tone="danger" title="계약서 작성 화면을 열지 못했습니다." className="mt-8">
        {errorMessage}
      </Callout>
    );
  }

  return (
    <>
      {fallback && (
        <Callout tone="neutral" title="계약 초안을 불러오지 못했습니다." className="mt-8">
          요청서의 요구사항으로 작업을 채웠습니다. 상담에서 더 합의한 것은 직접 적어 보낼 수 있습니다.
        </Callout>
      )}
      <ContractEditor
        roomId={roomId}
        contract={draft}
        requested={requested}
        counterpartName={counterpartName}
      />
    </>
  );
}
