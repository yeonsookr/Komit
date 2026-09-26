"use client";

import * as React from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Button, buttonClass } from "@/components/buttons";
import { ChoiceCard } from "@/components/common";
import { Callout } from "@/components/feedback";
import { clientApi } from "@/lib/api/client";
import { INTERNAL } from "@/lib/api/endpoints";
import { formatDateLabel } from "@/lib/format";
import { ROUTES } from "@/lib/routes";
import { ApiError } from "@/types/api";
import type { MyProject } from "@/types/mypage";
import type { RoomSummary } from "@/types/room";

export interface InquiryRequestPickerProps {
  developerId: number;
  /** 진단을 한 번 이상 한 프로젝트. 진단이 있어야 요청서가 방에 함께 걸립니다. */
  projects: MyProject[];
}

/**
 * InquiryRequestPicker — 무료로 물어보기 전에 요청서를 고릅니다 (`3_의뢰인_요청서선택`).
 *
 * 고른 프로젝트로 그 개발자와의 무료 상담방을 열고 그 방으로 보냅니다. 요청은
 * 멱등해서 이미 방이 있으면 그 방이 그대로 옵니다.
 */
export function InquiryRequestPicker({
  developerId,
  projects,
}: Readonly<InquiryRequestPickerProps>) {
  const router = useRouter();
  const [picked, setPicked] = React.useState<number | null>(projects[0]?.projectId ?? null);
  const [sending, setSending] = React.useState(false);
  const [errorMessage, setErrorMessage] = React.useState<string | null>(null);

  const send = async () => {
    if (picked === null || sending) return;
    setSending(true);
    setErrorMessage(null);

    try {
      const { data } = await clientApi.post<{ content: RoomSummary[] }>(
        INTERNAL.DIRECT_CONSULTATION_REQUESTS(picked),
        { developerIds: [developerId] },
      );
      router.push(ROUTES.room(data.content[0].roomId));
    } catch (error) {
      setErrorMessage(
        error instanceof ApiError ? error.message : "상담방을 열지 못했습니다.",
      );
      setSending(false);
    }
  };

  return (
    <div className="mt-8">
      <p className="text-[13px] font-semibold text-fg-muted">요청서</p>
      <div className="mt-3 flex flex-col gap-2">
        {projects.map((project) => (
          <ChoiceCard
            key={project.projectId}
            type="radio"
            name="inquiryProject"
            label={
              <span className="flex flex-col gap-0.5">
                <span>{project.name}</span>
                {project.latestDiagnosedAt !== null && (
                  <span className="text-[13px] font-normal text-fg-subtle">
                    최근 진단 · {formatDateLabel(project.latestDiagnosedAt)}
                  </span>
                )}
              </span>
            }
            checked={picked === project.projectId}
            onChange={() => setPicked(project.projectId)}
          />
        ))}

        <div className="flex items-center justify-between gap-4 rounded-md border border-line bg-bg px-4 py-3">
          <div className="flex flex-col gap-0.5">
            <span className="text-sm font-medium text-fg">아직 요청서가 없나요?</span>
            <span className="text-[13px] text-fg-subtle">사전점검을 하면 요청서가 만들어집니다</span>
          </div>
          <Link
            href={ROUTES.checkup}
            className="shrink-0 rounded-sm text-[13px] font-semibold text-primary hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          >
            사전점검 하기
          </Link>
        </div>
      </div>

      {errorMessage !== null && (
        <Callout tone="danger" title="상담방을 열지 못했습니다." className="mt-6">
          {errorMessage}
        </Callout>
      )}

      <div className="mt-8 flex items-center justify-end gap-2">
        <Link
          href={ROUTES.developer(developerId)}
          className={buttonClass({ variant: "outline", size: "lg" })}
        >
          취소
        </Link>
        <Button size="lg" onClick={send} disabled={picked === null} loading={sending}>
          무료로 물어보기 →
        </Button>
      </div>
    </div>
  );
}
