"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/buttons";
import { Modal, ModalFooter, ModalHeader, useToast } from "@/components/feedback";
import { clientApi } from "@/lib/api/client";
import { INTERNAL } from "@/lib/api/endpoints";
import { ApiError } from "@/types/api";

export interface CancelContractButtonProps {
  contractId: number;
  /** 개발자 이름. 확인 팝업이 누구와의 계약인지 적습니다. */
  counterpartName: string;
  /** 이미 예치했는지. 돌려받는다는 안내를 붙일지 정합니다. */
  deposited: boolean;
}

/** 계약은 취소됐고 환불만 늦어지는 경우의 코드입니다. 실패가 아니라 기다림으로 보여줍니다. */
const REFUND_PENDING = "REFUND_PENDING";

/**
 * CancelContractButton — 개발자가 서명하기 전에 의뢰인이 계약을 취소하는 버튼 (R-CON-21).
 *
 * **취소는 되돌릴 수 없습니다.** 그래서 바로 부르지 않고 확인을 한 번 받습니다. 예치금이 있으면
 * 전액을 예치할 때 쓴 회원가입 발급 계좌로 돌려받습니다 (R-PAY-04).
 *
 * 환불이 늦어지면 백엔드가 503 `REFUND_PENDING` 으로 답합니다. 계약은 이미 취소된 상태이므로
 * 실패로 보이지 않고 그 문구를 그대로 보여준 뒤 화면을 새로 그립니다. 같은 버튼을 다시 누르면
 * 환불을 잇습니다 — 돈은 두 번 나가지 않습니다.
 */
export function CancelContractButton({
  contractId,
  counterpartName,
  deposited,
}: Readonly<CancelContractButtonProps>) {
  const router = useRouter();
  const showToast = useToast();
  const [open, setOpen] = React.useState(false);
  const [cancelling, setCancelling] = React.useState(false);

  const cancel = async () => {
    setCancelling(true);
    try {
      await clientApi.post(INTERNAL.CONTRACT_CANCELLATION(contractId));
      setOpen(false);
      showToast({
        tone: "success",
        title: "계약을 취소했습니다.",
        description: deposited ? "예치한 금액은 가입할 때 발급한 계좌로 돌아갑니다." : undefined,
      });
      router.refresh();
    } catch (error) {
      if (error instanceof ApiError && error.code === REFUND_PENDING) {
        setOpen(false);
        showToast({ tone: "warning", title: error.message });
        router.refresh();
        return;
      }
      showToast({
        tone: "danger",
        title: error instanceof ApiError ? error.message : "계약을 취소하지 못했습니다.",
      });
    } finally {
      setCancelling(false);
    }
  };

  return (
    <>
      <Button variant="outline" size="lg" onClick={() => setOpen(true)}>
        계약 취소
      </Button>
      <Modal open={open} onClose={() => setOpen(false)} aria-labelledby="contract-cancel-title">
        <ModalHeader id="contract-cancel-title" title="계약을 취소할까요?" />
        <div className="flex flex-col gap-2 text-sm leading-6 text-fg-muted">
          <p>{counterpartName} 개발자가 아직 서명하지 않은 계약입니다. 취소하면 되돌릴 수 없습니다.</p>
          {deposited && <p>예치한 금액은 가입할 때 발급한 계좌로 전액 돌아갑니다.</p>}
          <p>취소한 계약의 세션은 열리지 않습니다.</p>
        </div>
        <ModalFooter>
          <Button variant="outline" onClick={() => setOpen(false)} disabled={cancelling}>
            돌아가기
          </Button>
          <Button variant="danger" onClick={cancel} loading={cancelling} disabled={cancelling}>
            계약 취소
          </Button>
        </ModalFooter>
      </Modal>
    </>
  );
}
