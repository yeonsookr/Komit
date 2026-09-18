"use client";

import { useRouter } from "next/navigation";
import * as React from "react";
import { Button } from "@/components/buttons";
import { Modal, ModalBody, ModalFooter, ModalHeader, useToast } from "@/components/feedback";
import { requestResolveReport } from "@/lib/admin/requests";
import { ApiError } from "@/types/api";
import type { AdminReportStatus } from "@/types/admin";

export interface ResolveReportButtonProps {
  reportId: number;
  status: AdminReportStatus;
  /** 목록에 적히는 이름. 확인 창에서 어느 건인지 알아보게 씁니다. */
  projectName: string;
}

/**
 * 신고 종결 (`9_신고처리` › 종결 처리).
 *
 * **되돌릴 수 없어 확인을 한 번 받습니다.** 종결한 시각이 해결일로 남고 신고한
 * 사람에게 알림이 갑니다. 같은 일이 다시 생기면 새 신고로 접수합니다. (`R-RPT-03`)
 *
 * 문구는 「이 건을 다 봤다」는 뜻으로만 씁니다. Komit 은 누가 옳은지 정하지 않습니다
 * (`R-RPT-04`) — 승패·귀책·판정으로 읽힐 말을 넣지 마세요.
 *
 * 목 서버는 상태를 저장하지 않으므로 종결한 뒤 다시 읽으면 도로 「접수됨」 으로
 * 보입니다. 목의 원칙이라 화면에서 우회하지 않습니다(`mock/AGENTS.md`).
 */
export function ResolveReportButton({
  reportId,
  status,
  projectName,
}: Readonly<ResolveReportButtonProps>) {
  const router = useRouter();
  const showToast = useToast();
  const [open, setOpen] = React.useState(false);
  const [resolving, setResolving] = React.useState(false);
  const resolved = status === "RESOLVED";

  const resolve = async () => {
    if (resolving) return;
    setResolving(true);

    try {
      const report = await requestResolveReport(reportId);
      setOpen(false);
      showToast({
        tone: "success",
        title: "신고를 종결했습니다.",
        description: `${report.reporter.name}님에게 알림이 갑니다.`,
      });
    } catch (error) {
      setOpen(false);
      // 404(없는 신고)와 409(이미 종결됨) 모두 화면이 쥐고 있는 상태가 낡았다는 뜻이라
      // 알린 뒤 서버에서 다시 읽습니다.
      showToast({
        tone: error instanceof ApiError && error.status === 409 ? "neutral" : "danger",
        title: error instanceof ApiError ? error.message : "종결하지 못했습니다.",
      });
    } finally {
      setResolving(false);
      router.refresh();
    }
  };

  return (
    <>
      <Button variant="dark" disabled={resolved} onClick={() => setOpen(true)}>
        {resolved ? "종결된 신고입니다" : "종결 처리"}
      </Button>
      <Modal
        open={open}
        onClose={() => setOpen(false)}
        size="sm"
        dismissible={!resolving}
        aria-labelledby="resolve-report-title"
      >
        <ModalHeader
          id="resolve-report-title"
          title="이 신고를 종결할까요?"
          description={projectName}
        />
        <ModalBody>
          <p>
            종결하면 오늘 날짜가 해결일로 기록되고 목록에서 종결됨으로 바뀝니다. 신고한
            분에게 알림이 가며, <strong className="font-semibold text-fg">되돌릴 수 없습니다.</strong>
          </p>
          <p className="mt-3 text-[13px] text-fg-subtle">
            종결은 운영자가 이 건을 다 봤다는 표시입니다. Komit은 누가 옳은지 정하지 않습니다.
          </p>
        </ModalBody>
        <ModalFooter>
          <Button variant="outline" onClick={() => setOpen(false)} disabled={resolving}>
            취소
          </Button>
          <Button variant="dark" onClick={resolve} loading={resolving}>
            종결 처리
          </Button>
        </ModalFooter>
      </Modal>
    </>
  );
}
