"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/buttons";
import { useToast } from "@/components/feedback";
import { clientApi } from "@/lib/api/client";
import { uploadFromBrowser } from "@/lib/upload/browserUpload";
import { INTERNAL } from "@/lib/api/endpoints";
import { ROUTES } from "@/lib/routes";
import { ApiError } from "@/types/api";
import { SignaturePad } from "./SignaturePad";

export interface SignContractButtonProps {
  roomId: number;
  contractId: number;
  /** 어느 계약서인지. 서명판 부제에 그대로 들어갑니다. */
  subject: string;
  /** 계약서 아래 버튼 문구. */
  label: string;
  /** 서명판 확인 버튼 문구. */
  confirmLabel: string;
  /** 참이면 서명 뒤 예치 화면으로 넘어갑니다. 의뢰인만 예치합니다. */
  depositNext?: boolean;
}

/**
 * SignContractButton — 계약서 서명 버튼과 서명판(`5_서명판`).
 *
 * 누르면 서명판이 열리고, 그린 서명을 올려 서명합니다. 양쪽이 서명해야 계약이
 * 성립하고 그 뒤에는 계약서를 고칠 수 없습니다 (R-CON-01·R-CON-02).
 */
export function SignContractButton({
  roomId,
  contractId,
  subject,
  label,
  confirmLabel,
  depositNext,
}: Readonly<SignContractButtonProps>) {
  const router = useRouter();
  const showToast = useToast();
  const [open, setOpen] = React.useState(false);
  const [saving, setSaving] = React.useState(false);

  const sign = async (signature: File) => {
    setSaving(true);

    try {
      const uploaded = await uploadFromBrowser(signature, "SIGNATURE");
      await clientApi.post(INTERNAL.CONTRACT_SIGNATURES(contractId), {
        fileId: uploaded.fileId,
      });
      setOpen(false);
      showToast({
        tone: "success",
        title: "서명했습니다.",
        description: depositNext
          ? "이어서 계약 금액을 예치합니다."
          : "상대가 서명하면 계약이 성립합니다.",
      });

      if (depositNext) router.push(ROUTES.roomDeposit(roomId));
      else router.refresh();
    } catch (error) {
      // 컨설팅 계약을 성립시키는 서명은 결제 여부를 확인하지 못하면 500 입니다. (R-CON-22)
      // 서버 문구가 「예치」를 말해 서명하던 사람에게는 뜻이 닿지 않아 덮어 씁니다.
      const known = error instanceof ApiError && error.status < 500;
      showToast({
        tone: "danger",
        title: known ? error.message : "지금은 서명할 수 없습니다.",
        description: known ? undefined : "잠시 뒤 다시 시도해 주세요.",
      });
      setSaving(false);
    }
  };

  return (
    <>
      <Button variant="dark" size="lg" onClick={() => setOpen(true)}>
        {label}
      </Button>
      <SignaturePad
        open={open}
        onClose={() => setOpen(false)}
        subject={subject}
        confirmLabel={confirmLabel}
        saving={saving}
        onSign={sign}
      />
    </>
  );
}
