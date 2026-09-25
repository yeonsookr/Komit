"use client";

import * as React from "react";
import { Callout } from "@/components/feedback";
import { clientApi } from "@/lib/api/client";
import { INTERNAL } from "@/lib/api/endpoints";
import { canWriteContractWithoutDraft } from "@/lib/contractDraft";
import { ApiError } from "@/types/api";
import type { Contract } from "@/types/contract";
import { ConsultingContractForm } from "./ConsultingContractForm";

const CONTRACT_DRAFT_TIMEOUT_MS = 30_000;

export interface ConsultingContractDraftFormProps {
  roomId: number;
  counterpartName: string;
  /** 새로고침 전에 이미 만들어 둔 AI version 1. */
  initialDraft?: Contract | null;
}

/** AI 초안을 먼저 받은 뒤, 일정·시간·금액은 개발자가 직접 채우게 합니다. */
export function ConsultingContractDraftForm({
  roomId,
  counterpartName,
  initialDraft = null,
}: Readonly<ConsultingContractDraftFormProps>) {
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
        <h2 className="text-xl font-bold text-fg">컨설팅 계약서 작성</h2>
        <p className="mt-3 text-sm text-fg-muted">상담 대화로 계약 초안을 만들고 있습니다.</p>
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
          빈 계약서에 직접 작성해서 보낼 수 있습니다.
        </Callout>
      )}
      <ConsultingContractForm
        roomId={roomId}
        counterpartName={counterpartName}
        draft={draft}
      />
    </>
  );
}
